package com.pathly.domain.repository

import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.StopDeletionResult
import com.pathly.domain.model.StopMergeResult
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 記録された立ち寄り（stops）の取得・追加・編集・削除と、その取り消しを担う。
 * 記録中の立ち寄り中は [LiveStopRepository]、場所そのもの（places）は [PlaceRepository]。
 * 詳細は docs/designs/places-and-stops.md を参照。分け方は adr/0049。
 */
interface StopRepository {

  /** 経路の立ち寄り一覧（場所つき）をリアクティブに取得する。 */
  fun getStopsForTrack(trackId: Long): Flow<List<Stop>>

  /**
   * 再解析（追加提案）: その track を検出し直し、既存の立ち寄りと**時間帯が重ならない**
   * 「一覧に無い」候補だけを返す。**永続化はしない（非破壊）**。到着時刻の昇順。
   * 各候補には**追加前の判断用の表示名**を添える（近くの命名済み place を再利用＝無料、
   * 無ければオンライン時のみ Places で1回。オフライン等は名前 null）。
   * 記録中に取りこぼした／誤って削除した立ち寄りを、ユーザーが選んで追加するために使う。
   */
  suspend fun detectMissingStops(trackId: Long): List<StopCandidate>

  /**
   * [detectMissingStops] で選んだ候補だけを立ち寄りとして追加保存する。
   * findOrCreatePlace で place を確保（30m以内の命名済み place は再利用）し、検出時に引いた
   * 名前があればそれを焼き込む（Places を二度叩かない）。付いていない place はオンライン時に命名する。
   * **既存の立ち寄りには触れない**。追加件数を返す。
   */
  suspend fun addStops(trackId: Long, candidates: List<StopCandidate>): Int

  /**
   * 手動追加: 検出に頼らず、ユーザーが地図で指した地点を立ち寄りとして追加する（完全手動）。
   * findOrCreatePlace で place を確保（30m以内の命名済み place は再利用）し、[name] があれば
   * 未命名の place にだけ焼き込む。POI 由来の [googlePlaceId] があれば解決記録に控えて
   * Places を叩かないようにする。
   * 到着 [arrivalTime]／出発 [departureTime] は呼び出し側が最寄り軌跡点から決める。作成した stop の id を返す。
   *
   * 名前は出どころで列を分ける（[com.pathly.domain.model.Place] の設計）。
   * @param name ユーザーが自分で入力した名前 → `places.name`。
   * @param googleName POI から取れた施設名 → `google_places.name`（ユーザー名を上書きしない）。
   */
  suspend fun addManualStop(
    trackId: Long,
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
    // true のとき座標30m同定をせず必ず新しい place を作る（近接確認で「新規」を選んだとき）。
    forceNewPlace: Boolean = false,
    googleName: String? = null,
  ): Long

  /**
   * 地図の「登録済みの場所」マーカーを選んで、**既存の [placeId]** にこの訪問を紐付ける（新規 place を作らない）。
   * 手動追加で近くの既存の場所に確定したいとき用（重複 place を増やさない）。作成した stop の id を返す。
   */
  suspend fun addManualStopForPlace(
    trackId: Long,
    placeId: Long,
    arrivalTime: Date,
    departureTime: Date,
  ): Long

  /**
   * 誤検知の訂正: **この訪問（[stopId]）だけ**を、選び直した場所に付け替える（他の経路・訪問は不変）。
   * [chosen]（POI 候補）があれば施設の同一性で同定した place へ、無ければ [customName] で新しい USER 場所を作る
   * （どちらも座標同定はせず、隣接する別施設と分離する）。付け替えで参照が無くなった元の場所が
   * 検出由来（DETECTED）なら自動回収する。名前・候補のどちらも無ければ何もしない。
   */
  suspend fun reassignStopPlace(stopId: Long, chosen: PlaceSearchResult?, customName: String?)

  /** 立ち寄り（訪問）のメモを更新する（stop 単位。空文字なら null に戻す）。場所名とは別物。 */
  suspend fun updateStopNote(stopId: Long, note: String?)

  /**
   * 立ち寄り（訪問）の滞在期間を手で直す。**GPS 点・補正後の点は一切触らない**
   * （観測した事実は変えない → adr/0012・adr/0024）。他の立ち寄りとの重なり（入れ子を含む）は
   * 禁止しない。到着が出発以降になる指定は無視する。
   */
  suspend fun updateStopDuration(stopId: Long, arrivalTime: Date, departureTime: Date)

  /**
   * 選んだ立ち寄り（訪問）を1件にまとめる。到着は**最も早い到着**、出発は**最も遅い出発**、
   * 訪問メモは連結する。残すのは到着が最も早かった1件で、他は削除する。間に挟まっていた
   * 別の場所の立ち寄りは消さない（→ adr/0024）。
   *
   * まとめられるのは**同じ経路の・同じ場所への**訪問が2件以上あるときだけ。条件を満たさない
   * ときは何もせず null を返す。取り消し（[undoLastStopChange]）用に、変更前の内容を控える。
   */
  suspend fun mergeStops(stopIds: List<Long>): StopMergeResult?

  /**
   * 立ち寄り（訪問）を削除する（1件でも複数でも同じ経路）。選択した訪問はすべて消し、
   * その結果どこからも参照されなくなった場所だけを場所ごと削除する。
   * 他に訪問が残る場所（＝他の履歴でも使われている）や、行きたい登録がある場所は保持する。
   * 取り消し（[undoLastStopChange]）用に、直近の削除内容を1件だけ控える。
   */
  suspend fun deleteStops(stopIds: List<Long>): StopDeletionResult

  /**
   * 直近の [deleteStops] / [mergeStops] を取り消す。消した訪問と回収した場所を**元のIDのまま**
   * 復元し、統合で書き換えた行は変更前に戻す。控えは1件だけなので、取り消せるのは直近の1回。
   * 控えが無ければ何もしない。復元したら true を返す。
   */
  suspend fun undoLastStopChange(): Boolean
}
