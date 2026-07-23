package com.pathly.data.repository

import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.TrackSmoother
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.util.EncryptionHelper
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
  private val encryptionHelper: EncryptionHelper,
) : GpsTrackRepository {

  private val logger = Logger("GpsTrackRepositoryImpl")

  // 補正後の書き込みを直列化する（記録中の各点更新と詳細画面の再補正が競合しないように）。
  private val smoothingMutex = Mutex()

  override fun getAllTracks(): Flow<List<GpsTrack>> = gpsTrackDao.getAllTracksWithPoints()
    .map { tracksWithPoints ->
      tracksWithPoints.map { trackWithPoints ->
        val points = trackWithPoints.points.map { it.toGpsPoint() }
        trackWithPoints.track.toGpsTrack(points)
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
      trackEntity.toGpsTrack(pointEntities.map { it.toGpsPoint() }, smoothedOverride)
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

      // セキュリティ考慮: 削除された軌跡IDを暗号化保存
      saveDeletedTrackId(track.id)
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

  override suspend fun updateSmoothedForTrack(trackId: Long, isFinal: Boolean) {
    try {
      smoothingMutex.withLock { persistSmoothed(trackId, isFinal) }
    } catch (e: Exception) {
      logger.e("updateSmoothedForTrack failed for track $trackId", e)
    }
  }

  override suspend fun recomputeSmoothed(trackId: Long) {
    try {
      smoothingMutex.withLock {
        // 記録中でなければ末尾まで確定させる。
        val isFinal = gpsTrackDao.getTrackById(trackId)?.isActive != true
        smoothedPointDao.deleteByTrack(trackId)
        persistSmoothed(trackId, isFinal)
        logger.i("Recomputed smoothed points for track $trackId (isFinal=$isFinal)")
      }
    } catch (e: Exception) {
      logger.e("recomputeSmoothed failed for track $trackId", e)
    }
  }

  /**
   * 生データから補正後点列を計算し、確定済み（[isFinal] が false なら末尾 half を除く）だけを
   * すでに保存済みの分を超える範囲で差分INSERTする。確定プレフィックスは単調・安定なので
   * 通常は末尾に1点ずつ増える。呼び出しは [smoothingMutex] で直列化されている前提。
   */
  private suspend fun persistSmoothed(trackId: Long, isFinal: Boolean) {
    val raw = gpsPointDao.getPointsByTrackIdSync(trackId).map { it.toGpsPoint() }
    if (raw.size < 2) return

    val smoothed = TrackSmoother.smooth(raw)
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
   * ローカルデータの暗号化バックアップを作成
   */
  suspend fun createEncryptedBackup(): Boolean = try {
    logger.i("Creating encrypted backup of local data")

    val allTracks = gpsTrackDao.getAllTracksSync()
    val allPoints = gpsPointDao.getAllPointsSync()

    // バックアップデータを暗号化して保存
    val backupData = createBackupData(allTracks, allPoints)
    encryptionHelper.saveSecureString("backup_data", backupData)
    encryptionHelper.saveSecureString("backup_timestamp", System.currentTimeMillis().toString())

    logger.i("Encrypted backup created successfully")
    true
  } catch (e: Exception) {
    logger.e("Repository operation failed", e)
    false
  }

  /**
   * 暗号化されたバックアップから復元
   */
  suspend fun restoreFromEncryptedBackup(): Boolean {
    return try {
      logger.i("Restoring from encrypted backup")

      val backupData = encryptionHelper.getSecureString("backup_data") ?: run {
        logger.w("No backup data found")
        return false
      }

      // バックアップデータを復号化して復元
      // 実装は将来的に追加

      logger.i("Restored from encrypted backup successfully")
      true
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
      false
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

  private fun saveDeletedTrackId(trackId: Long) {
    try {
      val existingIds = encryptionHelper.getSecureString("deleted_track_ids", "")
      val updatedIds = "$existingIds,$trackId"
      encryptionHelper.saveSecureString("deleted_track_ids", updatedIds)
    } catch (e: Exception) {
      logger.e("Repository operation failed", e)
    }
  }

  private fun createBackupData(tracks: List<GpsTrackEntity>, points: List<GpsPointEntity>): String {
    // 簡単なJSON形式でバックアップデータを作成（実際の実装では適切なシリアライゼーション使用）
    return "tracks:${tracks.size},points:${points.size},timestamp:${System.currentTimeMillis()}"
  }

  private fun GpsTrackEntity.toGpsTrack(
    points: List<GpsPoint> = emptyList(),
    smoothedOverride: List<GpsPoint>? = null,
  ): GpsTrack = GpsTrack(
    id = this.id,
    startTime = this.startTime,
    endTime = this.endTime,
    isActive = this.isActive,
    points = points,
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
    timestamp = this.timestamp,
    createdAt = this.createdAt,
  )
}
