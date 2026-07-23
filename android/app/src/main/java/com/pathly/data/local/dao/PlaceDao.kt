package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.PlaceEntity
import java.util.Date

@Dao
interface PlaceDao {

  @Insert
  suspend fun insert(place: PlaceEntity): Long

  @Query("SELECT * FROM places")
  suspend fun getAll(): List<PlaceEntity>

  @Query("SELECT * FROM places WHERE id = :id")
  suspend fun getById(id: Long): PlaceEntity?

  /** ある経路で立ち寄った場所のうち、まだ名前が無いもの（Places 解決対象）。 */
  @Query(
    "SELECT p.* FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE s.trackId = :trackId AND p.name IS NULL",
  )
  suspend fun getUnnamedPlacesForTrack(trackId: Long): List<PlaceEntity>

  @Query("UPDATE places SET name = :name, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateName(id: Long, name: String?, updatedAt: Date)

  @Query("UPDATE places SET name = :name, address = :address, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateNameAndAddress(id: Long, name: String?, address: String?, updatedAt: Date)
}
