package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.WishlistEntity
import java.util.Date

@Dao
interface WishlistDao {

  @Insert
  suspend fun insert(item: WishlistEntity): Long

  /** その place が既に行きたいに入っているか（重複登録の防止に使う）。 */
  @Query("SELECT * FROM wishlist WHERE placeId = :placeId LIMIT 1")
  suspend fun getByPlaceId(placeId: Long): WishlistEntity?

  /** その place を参照する行きたい登録の件数（場所の自動回収の可否判定）。 */
  @Query("SELECT COUNT(*) FROM wishlist WHERE placeId = :placeId")
  suspend fun countByPlace(placeId: Long): Int

  @Query("UPDATE wishlist SET priority = :priority, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateFields(id: Long, priority: Int, updatedAt: Date)

  @Query("DELETE FROM wishlist WHERE id = :id")
  suspend fun deleteById(id: Long)
}
