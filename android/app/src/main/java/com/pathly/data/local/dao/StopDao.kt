package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.StopWithPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {

  @Insert
  suspend fun insert(stop: StopEntity): Long

  @Query("SELECT COUNT(*) FROM stops WHERE trackId = :trackId")
  suspend fun countByTrack(trackId: Long): Int

  @Query("DELETE FROM stops WHERE trackId = :trackId")
  suspend fun deleteByTrack(trackId: Long)

  @Transaction
  @Query("SELECT * FROM stops WHERE trackId = :trackId ORDER BY arrivalTime ASC")
  fun getStopsWithPlaceByTrack(trackId: Long): Flow<List<StopWithPlace>>
}
