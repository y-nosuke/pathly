package com.pathly.domain.repository

import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.StopDeletionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date

/**
 * 場所（places）と立ち寄り（stops）の永続化・命名を担う。
 * 検出は「記録中（自動）」と「再解析（追加提案・非破壊）」、命名は加えて「場所を取得ボタン」で行う。
 * 詳細は docs/designs/places-and-stops.md を参照。
 */
interface PlaceRepository {

  /** 経路の立ち寄り一覧（場所つき）をリアクティブに取得する。 */
  fun getStopsForTrack(trackId: Long): Flow<List<Stop>>

  /** 記録中の「立ち寄り中」（メモリ保持・非永続）。3分超で place を先行確定して表示する。 */
  val currentStop: StateFlow<Stop?>

  /** その経路の未取得（googlePlaceId 無し）の place 件数（「場所を取得」ボタン表示用）。 */
  fun unresolvedCountForTrack(trackId: Long): Flow<Int>

  /**
   * 記録中に呼ばれ、確定した立ち寄り（離れたもの）だけを差分保存する（案A）。
   * 末尾の滞在中クラスタが3分を超えたら place を先行確定して [currentStop] に流す。
   * オンラインなら未解決の place を名前解決する。[isFinal] で末尾も確定する。
   */
  suspend fun updateStopsForTrack(trackId: Long, isFinal: Boolean)

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
   * Places を叩かないようにする（名前が無ければ place は未取得のまま。あとで「場所を取得」で命名可）。
   * 到着 [arrivalTime]／出発 [departureTime] は呼び出し側が最寄り軌跡点から決める。作成した stop の id を返す。
   */
  suspend fun addManualStop(
    trackId: Long,
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
  ): Long

  /**
   * 座標の近くの POI 候補を複数返す（距離が近い順）。手動追加の「候補から選ぶ」に使う。
   * 最寄り1件だけの自動命名と違い、隣接する別施設の取り違えを避けてユーザーに選ばせる。
   * オフライン・失敗時は空リスト。
   */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult>

  /** その経路の未取得（googlePlaceId 無し）の place を Places で取り直す（手動「場所を取得」）。 */
  suspend fun resolveUnresolvedNames(trackId: Long)

  /** 場所の表示名を手動で更新する（命名。空文字なら未命名に戻す）。 */
  suspend fun updatePlaceName(placeId: Long, name: String)

  /** 立ち寄り（訪問）のメモを更新する（stop 単位。空文字なら null に戻す）。場所名とは別物。 */
  suspend fun updateStopNote(stopId: Long, note: String?)

  /**
   * 近く（30m以内）に既存の場所があれば再利用し、無ければ新規作成して place の id を返す。
   * 立ち寄りと行きたい場所で同じ場所を共有するための同定（重複排除）。
   *
   * [source] は新規作成時の由来（既定は自動検出＝[PlaceSource.DETECTED]）。再利用時、既存が
   * DETECTED でも [source] が USER なら USER に昇格する（ユーザーが触った場所を自動回収から守る）。
   */
  suspend fun findOrCreatePlace(
    latitude: Double,
    longitude: Double,
    source: PlaceSource = PlaceSource.DETECTED,
  ): Long

  /**
   * **施設の同一性（googlePlaceId）で**同定する。同じ googlePlaceId を持つ place があれば再利用し、
   * 無ければ新規作成する（座標同定はしない＝30m以内の隣接する別施設に相乗りしない）。座標は POI の
   * Google 座標を渡す前提。返り値は (placeId, 既に登録済みだったか)。
   * 呼び出し側は必要なら google_places に名前・住所・カテゴリを控える。
   */
  suspend fun findOrCreateByGooglePlaceId(
    googlePlaceId: String,
    latitude: Double,
    longitude: Double,
    source: PlaceSource = PlaceSource.USER,
  ): Pair<Long, Boolean>

  /**
   * 立ち寄り（訪問）を削除する（1件でも複数でも同じ経路）。選択した訪問はすべて消し、
   * その結果どこからも参照されなくなった場所だけを場所ごと削除する。
   * 他に訪問が残る場所（＝他の履歴でも使われている）や、行きたい登録がある場所は保持する。
   * 取り消し（[undoLastDeletion]）用に、直近の削除内容を1件だけ控える。
   */
  suspend fun deleteStops(stopIds: List<Long>): StopDeletionResult

  /**
   * 直近の [deleteStops] を取り消し、消した訪問と回収した場所を**元のIDのまま**復元する。
   * 控えが無ければ何もしない。復元したら true を返す。
   */
  suspend fun undoLastDeletion(): Boolean
}
