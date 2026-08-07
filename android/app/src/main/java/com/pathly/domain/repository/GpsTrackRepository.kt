package com.pathly.domain.repository

import com.pathly.domain.model.GpsTrack
import kotlinx.coroutines.flow.Flow

interface GpsTrackRepository {
  fun getAllTracks(): Flow<List<GpsTrack>>
  fun getActiveTrackRealtime(): Flow<GpsTrack?>
  suspend fun getTrackById(trackId: Long): GpsTrack?
  suspend fun getActiveTrack(): GpsTrack?
  suspend fun deleteTrack(track: GpsTrack)
  suspend fun finishTrack(trackId: Long, endTime: java.util.Date)

  /** 経路名を更新する（null/空文字で未命名に戻す）。 */
  suspend fun renameTrack(trackId: Long, name: String?)

  /** お気に入り登録を切り替える。 */
  suspend fun setFavorite(trackId: Long, favorite: Boolean)

  /**
   * 補正後の点列を更新する。記録中に呼ばれ、確定済み（末尾の暫定分を除く）だけを
   * smoothed_points に差分保存する。[isFinal] が true なら末尾も確定して保存する
   * （docs/designs/gps-smoothing.md）。
   */
  suspend fun updateSmoothedForTrack(trackId: Long, isFinal: Boolean)
}
