package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.GpsPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsPointDao {

  @Query("SELECT * FROM gps_points WHERE trackId = :trackId ORDER BY timestamp ASC")
  fun getPointsByTrackId(trackId: Long): Flow<List<GpsPointEntity>>

  @Query("SELECT * FROM gps_points WHERE trackId = :trackId ORDER BY timestamp ASC")
  suspend fun getPointsByTrackIdSync(trackId: Long): List<GpsPointEntity>

  @Insert
  suspend fun insertPoint(point: GpsPointEntity): Long

  @Insert
  suspend fun insertPoints(points: List<GpsPointEntity>)

  @Delete
  suspend fun deletePoint(point: GpsPointEntity)

  @Query("DELETE FROM gps_points WHERE trackId = :trackId")
  suspend fun deletePointsByTrackId(trackId: Long)

  @Query("SELECT COUNT(*) FROM gps_points WHERE trackId = :trackId")
  suspend fun getPointCountByTrackId(trackId: Long): Int

  @Query("SELECT COUNT(*) FROM gps_points")
  suspend fun getPointCount(): Int

  @Query("SELECT COUNT(*) FROM gps_points")
  suspend fun getTotalPointsCount(): Int

  @Query("SELECT * FROM gps_points ORDER BY timestamp DESC")
  suspend fun getAllPointsSync(): List<GpsPointEntity>

  @Query("SELECT COUNT(*) FROM gps_points WHERE trackId NOT IN (SELECT id FROM gps_tracks)")
  suspend fun getOrphanedPointsCount(): Int

  @Query("DELETE FROM gps_points WHERE trackId NOT IN (SELECT id FROM gps_tracks)")
  suspend fun deleteOrphanedPoints(): Int

  @Query("SELECT * FROM gps_points WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
  suspend fun getPointsByTimeRange(startTime: Long, endTime: Long): List<GpsPointEntity>

  @Query("DELETE FROM gps_points WHERE timestamp < :cutoffTime")
  suspend fun deletePointsOlderThan(cutoffTime: Long): Int
}