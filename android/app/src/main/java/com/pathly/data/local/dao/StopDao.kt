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

  @Query("DELETE FROM stops WHERE id = :stopId")
  suspend fun deleteById(stopId: Long)

  @Query("DELETE FROM stops WHERE placeId = :placeId")
  suspend fun deleteByPlace(placeId: Long)

  /** その場所への訪問のうち、指定経路以外のもの件数（場所ごと削除の可否判定）。 */
  @Query("SELECT COUNT(*) FROM stops WHERE placeId = :placeId AND trackId != :trackId")
  suspend fun countByPlaceInOtherTracks(placeId: Long, trackId: Long): Int

  @Transaction
  @Query("SELECT * FROM stops WHERE trackId = :trackId ORDER BY arrivalTime ASC")
  fun getStopsWithPlaceByTrack(trackId: Long): Flow<List<StopWithPlace>>
}
