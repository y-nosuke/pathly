package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pathly.data.local.entity.GpsTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsTrackDao {

  @Query("SELECT * FROM gps_tracks ORDER BY startTime DESC")
  fun getAllTracks(): Flow<List<GpsTrackEntity>>

  @Query("SELECT * FROM gps_tracks WHERE id = :trackId")
  suspend fun getTrackById(trackId: Long): GpsTrackEntity?

  @Query("SELECT * FROM gps_tracks WHERE isActive = 1 LIMIT 1")
  suspend fun getActiveTrack(): GpsTrackEntity?

  @Insert
  suspend fun insertTrack(track: GpsTrackEntity): Long

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
}