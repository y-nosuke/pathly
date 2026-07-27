package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.WishlistEntity
import com.pathly.data.local.entity.WishlistWithPlace
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface WishlistDao {

  @Insert
  suspend fun insert(item: WishlistEntity): Long

  /** 行きたい一覧（場所つき）。優先度の高い順、次に登録が新しい順。 */
  @Transaction
  @Query("SELECT * FROM wishlist ORDER BY priority DESC, createdAt DESC")
  fun getAllWithPlace(): Flow<List<WishlistWithPlace>>

  /** その place が既に行きたいに入っているか（重複登録の防止に使う）。 */
  @Query("SELECT * FROM wishlist WHERE placeId = :placeId LIMIT 1")
  suspend fun getByPlaceId(placeId: Long): WishlistEntity?

  /** その place を参照する行きたい登録の件数（場所の自動回収の可否判定）。 */
  @Query("SELECT COUNT(*) FROM wishlist WHERE placeId = :placeId")
  suspend fun countByPlace(placeId: Long): Int

  @Query("UPDATE wishlist SET priority = :priority, memo = :memo, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateFields(id: Long, priority: Int, memo: String?, updatedAt: Date)

  @Query("UPDATE wishlist SET visitedAt = :visitedAt, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateVisited(id: Long, visitedAt: Date?, updatedAt: Date)

  @Query("DELETE FROM wishlist WHERE id = :id")
  suspend fun deleteById(id: Long)
}
