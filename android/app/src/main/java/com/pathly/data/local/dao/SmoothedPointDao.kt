package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.SmoothedPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmoothedPointDao {

  @Insert
  suspend fun insert(point: SmoothedPointEntity): Long

  @Insert
  suspend fun insertAll(points: List<SmoothedPointEntity>)

  @Query("SELECT * FROM smoothed_points WHERE trackId = :trackId ORDER BY seq ASC")
  suspend fun getByTrack(trackId: Long): List<SmoothedPointEntity>

  @Query("SELECT * FROM smoothed_points WHERE trackId = :trackId ORDER BY seq ASC")
  fun getByTrackFlow(trackId: Long): Flow<List<SmoothedPointEntity>>

  @Query("SELECT COUNT(*) FROM smoothed_points WHERE trackId = :trackId")
  suspend fun countByTrack(trackId: Long): Int

  @Query("DELETE FROM smoothed_points WHERE trackId = :trackId")
  suspend fun deleteByTrack(trackId: Long)
}
