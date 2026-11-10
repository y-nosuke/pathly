package com.pathly.domain.repository

import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsPoint
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
   * 地図の範囲に入る完了済み経路の ID（記録の点か、立ち寄った場所が範囲に入るもの）。
   * 記録画面の「経路」で使う（docs/specs/screens.md）。
   */
  suspend fun getFinishedTrackIdsInBounds(bounds: GeoBounds): Set<Long>

  /**
   * 地図に描く補正後の点列（欠落で分けた区間ごと）。保存済みの補正点だけを読み、生の点は読まない。
   * 保存済みが無い古い経路だけ、生の点から計算する。経路が無ければ null。
   */
  suspend fun getDisplaySegments(trackId: Long): List<List<GpsPoint>>?

  /**
   * 補正後の点列を更新する。記録中に呼ばれ、確定済み（末尾の暫定分を除く）だけを
   * smoothed_points に差分保存する。[isFinal] が true なら末尾も確定して保存する
   * （docs/designs/gps-smoothing.md）。
   */
  suspend fun updateSmoothedForTrack(trackId: Long, isFinal: Boolean)

  /**
   * 総移動距離が未計算の完了済み経路（v11 より前に記録したもの）を埋める。
   * 起動時に一度だけ走ればよい（対象が無ければ即 return）。
   */
  suspend fun backfillMissingDistances()

  /**
   * 欠落をまたいで平均された補正点を持つ完了済み経路を作り直す（→ adr/0022）。
   * 起動時に一度だけ走ればよい。**やり切れたら true**（false なら次の起動でやり直す）。
   */
  suspend fun resmoothGappedTracks(): Boolean
}
