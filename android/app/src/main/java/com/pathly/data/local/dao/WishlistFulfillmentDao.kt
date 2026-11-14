package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.WishlistFulfillmentEntity

/** 入れた後（到着が `wishlist.createdAt` 以降）の立ち寄りがある行きたい。 */
private const val REACHED =
  "EXISTS (SELECT 1 FROM stops s WHERE s.placeId = w.placeId AND s.arrivalTime >= w.createdAt)"

/**
 * 行きたいの履歴（→ [WishlistFulfillmentEntity]）。
 */
@Dao
interface WishlistFulfillmentDao {

  /**
   * 行った行きたいを終わらせる。入れた後の立ち寄りがある行きたいを、履歴へ移して wishlist から消す
   * （`fulfilledAt` はその中で最初の立ち寄りの到着時刻 → adr/0036）。
   *
   * 立ち寄りを書き込んだ後に毎回呼ぶ。どの場所の立ち寄りが変わったかを問わず全部を見る
   * （行きたいは数が少なく、立ち寄りは placeId で引けるので軽い）。
   *
   * @return 終わらせた行きたいの件数。
   */
  @Transaction
  suspend fun fulfillReachedWishes(): Int {
    val count = countReachedWishes()
    if (count == 0) return 0
    insertReachedWishes()
    deleteReachedWishes()
    return count
  }

  @Query("SELECT COUNT(*) FROM wishlist w WHERE $REACHED")
  suspend fun countReachedWishes(): Int

  @Query(
    "INSERT INTO wishlist_fulfillments (placeId, priority, wishedAt, fulfilledAt) " +
      "SELECT w.placeId, w.priority, w.createdAt, " +
      "(SELECT MIN(s.arrivalTime) FROM stops s WHERE s.placeId = w.placeId AND s.arrivalTime >= w.createdAt) " +
      "FROM wishlist w WHERE $REACHED",
  )
  suspend fun insertReachedWishes()

  @Query("DELETE FROM wishlist WHERE id IN (SELECT w.id FROM wishlist w WHERE $REACHED)")
  suspend fun deleteReachedWishes()

  @Insert
  suspend fun insertAll(items: List<WishlistFulfillmentEntity>)

  /** その place の履歴（場所を消すときの控え＝取り消しでの復元に使う）。 */
  @Query("SELECT * FROM wishlist_fulfillments WHERE placeId = :placeId")
  suspend fun getByPlaceId(placeId: Long): List<WishlistFulfillmentEntity>

  /** その place に履歴があるか（自動回収・自動統合の可否判定に使う）。 */
  @Query("SELECT COUNT(*) FROM wishlist_fulfillments WHERE placeId = :placeId")
  suspend fun countByPlace(placeId: Long): Int
}
