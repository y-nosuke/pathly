package com.pathly.domain.repository

import com.pathly.domain.model.Stop
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

  /** 立ち寄り（訪問）1件を削除する。場所（place）は残す。 */
  suspend fun deleteStop(stopId: Long)

  /**
   * 場所（place）ごと削除する（その場所の訪問・解決記録も消える）。
   * ただし**他の経路にもその場所への訪問が残っている場合は削除せず false を返す**。
   */
  suspend fun deletePlace(placeId: Long, trackId: Long): Boolean
}
