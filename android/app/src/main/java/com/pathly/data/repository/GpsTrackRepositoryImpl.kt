package com.pathly.data.repository

import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.TrackSmoother
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsTrackRepositoryImpl @Inject constructor(
  private val gpsTrackDao: GpsTrackDao,
  private val gpsPointDao: GpsPointDao,
  private val smoothedPointDao: SmoothedPointDao,
  private val stopDao: StopDao,
) : GpsTrackRepository {

  private val logger = Logger("GpsTrackRepositoryImpl")

  // 補正後の書き込みを直列化する（記録中の各点更新と詳細画面の再補正が競合しないように）。
  private val smoothingMutex = Mutex()

  /**
   * 履歴一覧。**GPS点はロードしない**（点数は集計、距離は gps_tracks の焼き込み値）。
   * 以前は全経路の全点を読み込み、表示のたびに平滑化して距離を計算し直していた。
   */
  override fun getAllTracks(): Flow<List<GpsTrack>> = combine(
    gpsTrackDao.getTrackListRows(),
    stopDao.observeStopCountsByTrack(),
  ) { rows, stopCounts ->
    val countByTrack = stopCounts.associate { it.trackId to it.count }
    rows.map { row ->
      row.track.toGpsTrack(
        stopCount = countByTrack[row.track.id] ?: 0,
        pointCount = row.pointCount,
      )
    }
  }
    .onEach { tracks ->
      logger.d("Retrieved ${tracks.size} tracks from local database")
    }
    .catch { exception ->
      logger.e("Failed to retrieve tracks from local database", exception)
      emit(emptyList()) // オフライン時は空リストを返す
    }

  override fun getActiveTrackRealtime(): Flow<GpsTrack?> = gpsTrackDao.getActiveTrackWithPoints()
    .map { trackWithPoints ->
      trackWithPoints?.let {
        val points = it.points.map { point -> point.toGpsPoint() }
        it.track.toGpsTrack(points)
      }
    }
    .onEach { track ->
      if (track != null) {
        logger.d("Retrieved active track ${track.id} with ${track.points.size} points")
      }
    }
    .catch { exception ->
      logger.e("Failed to retrieve active track", exception)
      emit(null)
    }

  override suspend fun getTrackById(trackId: Long): GpsTrack? {
    return try {
      val trackEntity = gpsTrackDao.getTrackById(trackId) ?: run {
        logger.w("Track with ID $trackId not found")
        return null
      }
      val pointEntities = gpsPointDao.getPointsByTrackIdSync(trackId)

      // 記録が終わったトラックは保存済みの補正後点列を使う（都度計算を避ける）。
      // 記録中は末尾が未確定なので override せず、都度計算で末尾まで表示する。
      val smoothedOverride = if (!trackEntity.isActive) {
        smoothedPointDao.getByTrack(trackId).map { it.toGpsPoint() }.ifEmpty { null }
      } else {
        null
      }

      logger.d("Retrieved track $trackId with ${pointEntities.size} points")
      trackEntity.toGpsTrack(
        points = pointEntities.map { it.toGpsPoint() },
        stopCount = stopDao.countByTrack(trackId),
        smoothedOverride = smoothedOverride,
      )
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      null
    }
  }

  override suspend fun getActiveTrack(): GpsTrack? {
    return try {
      val activeTrackEntity = gpsTrackDao.getActiveTrack() ?: run {
        logger.d("No active track found")
        return null
      }
      val pointEntities = gpsPointDao.getPointsByTrackIdSync(activeTrackEntity.id)

      logger.d("Retrieved active track ${activeTrackEntity.id} with ${pointEntities.size} points")
      activeTrackEntity.toGpsTrack(pointEntities.map { it.toGpsPoint() })
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      null
    }
  }

  override suspend fun deleteTrack(track: GpsTrack) {
    try {
      val entity = GpsTrackEntity(
        id = track.id,
        startTime = track.startTime,
        endTime = track.endTime,
        isActive = track.isActive,
        createdAt = track.createdAt,
        updatedAt = track.updatedAt,
      )
      gpsTrackDao.deleteTrack(entity)
      logger.i("Successfully deleted track ${track.id}")
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      throw e
    }
  }

  override suspend fun finishTrack(trackId: Long, endTime: java.util.Date) {
    try {
      gpsTrackDao.finishTrack(trackId, endTime)
      logger.i("Successfully finished track $trackId")
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      throw e
    }
  }

  override suspend fun renameTrack(trackId: Long, name: String?) {
    try {
      // 空白のみは未命名（null）に正規化する。
      gpsTrackDao.updateName(trackId, name?.trim()?.ifEmpty { null })
      logger.i("Renamed track $trackId")
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      throw e
    }
  }

  override suspend fun setFavorite(trackId: Long, favorite: Boolean) {
    try {
      gpsTrackDao.updateFavorite(trackId, favorite)
      logger.i("Set favorite=$favorite for track $trackId")
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      throw e
    }
  }

  override suspend fun updateSmoothedForTrack(trackId: Long, isFinal: Boolean) {
    try {
      smoothingMutex.withLock { persistSmoothed(trackId, isFinal) }
    } catch (e: Exception) {
      logger.e("updateSmoothedForTrack failed for track $trackId", e)
    }
  }

  /**
   * 生データから補正後点列を計算し、確定済み（[isFinal] が false なら末尾 half を除く）だけを
   * すでに保存済みの分を超える範囲で差分INSERTする。確定プレフィックスは単調・安定なので
   * 通常は末尾に1点ずつ増える。呼び出しは [smoothingMutex] で直列化されている前提。
   */
  private suspend fun persistSmoothed(trackId: Long, isFinal: Boolean) {
    val raw = gpsPointDao.getPointsByTrackIdSync(trackId).map { it.toGpsPoint() }
    if (raw.size < 2) {
      // 点が無い／1点だけの経路も距離を確定させる（一覧が未計算のまま残らないように）。
      if (isFinal) gpsTrackDao.updateTotalDistance(trackId, 0.0)
      return
    }

    val smoothed = TrackSmoother.smooth(raw)
    // 確定時に総移動距離を焼き込む。一覧はこの値だけを読み、点をロードしない。
    if (isFinal) gpsTrackDao.updateTotalDistance(trackId, TrackSmoother.totalDistanceMeters(smoothed))
    val half = SmoothingParams().window / 2
    val finalizedCount = if (isFinal) smoothed.size else (smoothed.size - half).coerceAtLeast(0)

    val persisted = smoothedPointDao.countByTrack(trackId)
    if (finalizedCount <= persisted) return

    val rows = (persisted until finalizedCount).map { i ->
      val p = smoothed[i]
      SmoothedPointEntity(
        trackId = trackId,
        seq = i,
        latitude = p.latitude,
        longitude = p.longitude,
        timestamp = p.timestamp,
        sourcePointId = p.id.takeIf { it != 0L },
      )
    }
    smoothedPointDao.insertAll(rows)
    logger.d("Persisted ${rows.size} smoothed points for track $trackId (total $finalizedCount)")
  }

  /**
   * v11 以前に記録した経路は総移動距離を持たないので、初回起動時にまとめて計算して埋める。
   * 保存済みの補正後点列があればそれを使い（再平滑化しない）、無ければ生点から計算する。
   * 一度埋めれば二度と走らない。
   */
  override suspend fun backfillMissingDistances() {
    try {
      val ids = gpsTrackDao.getFinishedTrackIdsWithoutDistance()
      if (ids.isEmpty()) return
      logger.i("Backfilling total distance for ${ids.size} tracks")
      for (trackId in ids) {
        val smoothed = smoothedPointDao.getByTrack(trackId).map { it.toGpsPoint() }
          .ifEmpty { TrackSmoother.smooth(gpsPointDao.getPointsByTrackIdSync(trackId).map { it.toGpsPoint() }) }
        gpsTrackDao.updateTotalDistance(trackId, TrackSmoother.totalDistanceMeters(smoothed))
      }
      logger.i("Backfilled total distance for ${ids.size} tracks")
    } catch (e: Exception) {
      logger.e("backfillMissingDistances failed", e)
    }
  }

  /**
   * ローカルデータベースの健全性チェック
   */
  suspend fun performDataIntegrityCheck(): Boolean = try {
    logger.i("Performing data integrity check")

    val trackCount = gpsTrackDao.getTrackCount()
    val pointCount = gpsPointDao.getPointCount()
    val orphanedPoints = gpsPointDao.getOrphanedPointsCount()

    logger.i("Data integrity: $trackCount tracks, $pointCount points, $orphanedPoints orphaned")

    val isHealthy = orphanedPoints == 0
    if (!isHealthy) {
      logger.w("Data integrity issues detected: $orphanedPoints orphaned points")
    }

    isHealthy
  } catch (e: Exception) {
    logger.e("Repository operation failed", e)
    false
  }

  /**
   * オフライン専用データクリーンアップ
   */
  suspend fun cleanupOldOfflineData(daysToKeep: Int = 30): Int = try {
    logger.i("Cleaning up offline data older than $daysToKeep days")

    val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
    val cutoffDate = java.util.Date(cutoffTime)

    val deletedCount = gpsTrackDao.deleteTracksOlderThan(cutoffDate)
    logger.i("Cleaned up $deletedCount old tracks")

    deletedCount
  } catch (e: Exception) {
    logger.e("Repository operation failed", e)
    0
  }

  private fun GpsTrackEntity.toGpsTrack(
    points: List<GpsPoint> = emptyList(),
    stopCount: Int = 0,
    smoothedOverride: List<GpsPoint>? = null,
    pointCount: Int = points.size,
  ): GpsTrack = GpsTrack(
    id = this.id,
    startTime = this.startTime,
    endTime = this.endTime,
    isActive = this.isActive,
    name = this.name,
    isFavorite = this.isFavorite,
    stopCount = stopCount,
    points = points,
    pointCount = pointCount,
    storedDistanceMeters = this.totalDistanceMeters,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    smoothedOverride = smoothedOverride,
  )

  private fun SmoothedPointEntity.toGpsPoint(): GpsPoint = GpsPoint(
    id = this.sourcePointId ?: 0L,
    trackId = this.trackId,
    latitude = this.latitude,
    longitude = this.longitude,
    altitude = null,
    accuracy = 0f,
    speed = null,
    bearing = null,
    timestamp = this.timestamp,
    createdAt = this.createdAt,
  )

  private fun GpsPointEntity.toGpsPoint(): GpsPoint = GpsPoint(
    id = this.id,
    trackId = this.trackId,
    latitude = this.latitude,
    longitude = this.longitude,
    altitude = this.altitude,
    accuracy = this.accuracy,
    speed = this.speed,
    bearing = this.bearing,
    provider = this.provider,
    verticalAccuracyMeters = this.verticalAccuracyMeters,
    speedAccuracyMetersPerSecond = this.speedAccuracyMetersPerSecond,
    bearingAccuracyDegrees = this.bearingAccuracyDegrees,
    mslAltitudeMeters = this.mslAltitudeMeters,
    mslAltitudeAccuracyMeters = this.mslAltitudeAccuracyMeters,
    elapsedRealtimeNanos = this.elapsedRealtimeNanos,
    isMock = this.isMock,
    extrasJson = this.extrasJson,
    timestamp = this.timestamp,
    createdAt = this.createdAt,
  )
}
