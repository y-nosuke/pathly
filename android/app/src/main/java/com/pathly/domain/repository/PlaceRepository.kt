package com.pathly.domain.repository

import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopDeletionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 場所（places）と立ち寄り（stops）の永続化・命名を担う。
 * 検出・命名は「記録中」と「再解析／場所を取得ボタン」でのみ行う。
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

  /** 立ち寄りを作り直す（再解析）：stops を消して検出し直し、命名する。 */
  suspend fun redetectStops(trackId: Long)

  /** その経路の未取得（googlePlaceId 無し）の place を Places で取り直す（手動「場所を取得」）。 */
  suspend fun resolveUnresolvedNames(trackId: Long)

  /** 場所の表示名を手動で更新する（命名。空文字なら未命名に戻す）。 */
  suspend fun updatePlaceName(placeId: Long, name: String)

  /**
   * 近く（30m以内）に既存の場所があれば再利用し、無ければ新規作成して place の id を返す。
   * 立ち寄りと行きたい場所で同じ場所を共有するための同定（重複排除）。
   */
  suspend fun findOrCreatePlace(latitude: Double, longitude: Double): Long

  /**
   * 立ち寄り（訪問）を削除する（1件でも複数でも同じ経路）。選択した訪問はすべて消し、
   * その結果どこからも参照されなくなった場所だけを場所ごと削除する。
   * 他に訪問が残る場所（＝他の履歴でも使われている）や、行きたい登録がある場所は保持する。
   */
  suspend fun deleteStops(stopIds: List<Long>): StopDeletionResult
}
