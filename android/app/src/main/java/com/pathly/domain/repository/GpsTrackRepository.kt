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

  /**
   * 補正後の点列を更新する。記録中に呼ばれ、確定済み（末尾の暫定分を除く）だけを
   * smoothed_points に差分保存する。[isFinal] が true なら末尾も確定して保存する
   * （docs/designs/gps-smoothing.md）。
   */
  suspend fun updateSmoothedForTrack(trackId: Long, isFinal: Boolean)

  /**
   * 補正後の点列を全消去して生データから作り直す（詳細画面の「再補正」）。
   * アルゴリズム／しきい値を直したあとの反映や、記録中に保存できなかったときの保険。
   */
  suspend fun recomputeSmoothed(trackId: Long)
}
