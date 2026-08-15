package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.GpsTrackWithPoints
import com.pathly.data.local.entity.TrackListRow
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsTrackDao {

  @Query("SELECT * FROM gps_tracks ORDER BY startTime DESC")
  fun getAllTracks(): Flow<List<GpsTrackEntity>>

  @Transaction
  @Query("SELECT * FROM gps_tracks ORDER BY startTime DESC")
  fun getAllTracksWithPoints(): Flow<List<GpsTrackWithPoints>>

  /**
   * 履歴一覧用。点はロードせず件数だけを集計する（距離は gps_tracks の焼き込み値を使う）。
   * 点の増減でも再発行されるが、完了済み経路の行は値が変わらないため、
   * 上流の StateFlow が同値を弾いて再描画には至らない。
   */
  @Query(
    "SELECT t.*, (SELECT COUNT(*) FROM gps_points p WHERE p.trackId = t.id) AS pointCount " +
      "FROM gps_tracks t ORDER BY t.startTime DESC",
  )
  fun getTrackListRows(): Flow<List<TrackListRow>>

  /** 確定した総移動距離（メートル）を焼き込む。 */
  @Query("UPDATE gps_tracks SET totalDistanceMeters = :meters WHERE id = :trackId")
  suspend fun updateTotalDistance(trackId: Long, meters: Double)

  /** 距離が未計算の完了済み経路（v11 以前に記録した分のバックフィル対象）。 */
  @Query("SELECT id FROM gps_tracks WHERE isActive = 0 AND totalDistanceMeters IS NULL")
  suspend fun getFinishedTrackIdsWithoutDistance(): List<Long>

  @Query("SELECT * FROM gps_tracks WHERE id = :trackId")
  suspend fun getTrackById(trackId: Long): GpsTrackEntity?

  @Query("SELECT * FROM gps_tracks WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
  suspend fun getActiveTrack(): GpsTrackEntity?

  @Transaction
  @Query("SELECT * FROM gps_tracks WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
  fun getActiveTrackWithPoints(): Flow<GpsTrackWithPoints?>

  @Insert
  suspend fun insertTrack(track: GpsTrackEntity): Long

  @Insert
  suspend fun insertTracks(tracks: List<GpsTrackEntity>): List<Long>

  @Update
  suspend fun updateTrack(track: GpsTrackEntity)

  @Delete
  suspend fun deleteTrack(track: GpsTrackEntity)

  @Query("UPDATE gps_tracks SET isActive = 0, endTime = :endTime, updatedAt = :updatedAt WHERE id = :trackId")
  suspend fun finishTrack(
    trackId: Long,
    endTime: java.util.Date,
    updatedAt: java.util.Date = java.util.Date(),
  )

  /** 経路名を更新する（null/空で未命名に戻す）。 */
  @Query("UPDATE gps_tracks SET name = :name, updatedAt = :updatedAt WHERE id = :trackId")
  suspend fun updateName(
    trackId: Long,
    name: String?,
    updatedAt: java.util.Date = java.util.Date(),
  )

  /** お気に入りフラグを更新する。 */
  @Query("UPDATE gps_tracks SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :trackId")
  suspend fun updateFavorite(
    trackId: Long,
    favorite: Boolean,
    updatedAt: java.util.Date = java.util.Date(),
  )

  @Query("SELECT COUNT(*) FROM gps_tracks")
  suspend fun getTrackCount(): Int

  @Query("SELECT * FROM gps_tracks ORDER BY startTime DESC")
  suspend fun getAllTracksSync(): List<GpsTrackEntity>

  @Query("DELETE FROM gps_tracks WHERE createdAt < :cutoffDate")
  suspend fun deleteTracksOlderThan(cutoffDate: java.util.Date): Int

  @Query("SELECT COUNT(*) FROM gps_tracks WHERE createdAt >= :since")
  suspend fun getTrackCountSince(since: java.util.Date): Int

  @Query("SELECT * FROM gps_tracks WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY startTime DESC")
  suspend fun getTracksByDateRange(
    startDate: java.util.Date,
    endDate: java.util.Date,
  ): List<GpsTrackEntity>
}
