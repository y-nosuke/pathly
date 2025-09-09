package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.GpsTrackWithPoints
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsTrackDao {

  @Query("SELECT * FROM gps_tracks ORDER BY startTime DESC")
  fun getAllTracks(): Flow<List<GpsTrackEntity>>

  @Transaction
  @Query("SELECT * FROM gps_tracks ORDER BY startTime DESC")
  fun getAllTracksWithPoints(): Flow<List<GpsTrackWithPoints>>

  @Query("SELECT * FROM gps_tracks WHERE id = :trackId")
  suspend fun getTrackById(trackId: Long): GpsTrackEntity?

  @Query("SELECT * FROM gps_tracks WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
  suspend fun getActiveTrack(): GpsTrackEntity?

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
    updatedAt: java.util.Date = java.util.Date()
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
    endDate: java.util.Date
  ): List<GpsTrackEntity>
}