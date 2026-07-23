package com.pathly.domain.repository

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import kotlinx.coroutines.flow.Flow

/**
 * 場所（places）と立ち寄り（stops）の永続化・命名を担う。
 * 詳細は docs/designs/places-and-stops.md を参照。
 */
interface PlaceRepository {

  /** 経路の立ち寄り一覧（場所つき）をリアクティブに取得する。 */
  fun getStopsForTrack(trackId: Long): Flow<List<Stop>>

  /**
   * 経路の立ち寄りを確定する（冪等）。未検出なら検出して保存し、
   * 名前の無い場所は Places で命名する（ベストエフォート）。
   */
  suspend fun ensureStopsDetected(track: GpsTrack)

  /** 場所の表示名を手動で更新する（空文字なら未命名に戻す）。 */
  suspend fun updatePlaceName(placeId: Long, name: String)
}
