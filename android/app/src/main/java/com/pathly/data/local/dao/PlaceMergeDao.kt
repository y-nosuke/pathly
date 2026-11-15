package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.VisitedPlaceEntity
import com.pathly.data.local.entity.WishlistEntity
import com.pathly.data.local.entity.WishlistFulfillmentEntity
import com.pathly.domain.model.PlaceSource
import java.util.Date

/**
 * 2 つの場所を 1 つにまとめる・その取り消し（→ adr/0037）。
 *
 * 立ち寄り・行きたい・行きたいの履歴・訪問済みの印・施設情報を**ひとつのトランザクションで**動かす。
 * 途中で止まると、立ち寄りだけ移って場所が 2 つ残る・行きたいが消える、といった半端な状態になるため。
 */
@Dao
abstract class PlaceMergeDao {

  /** まとめる前の、ある場所の行一式（取り消しで元の id のまま戻すための控え）。 */
  data class PlaceRows(
    val place: PlaceEntity,
    val wishlist: WishlistEntity?,
    val visited: VisitedPlaceEntity?,
    val fulfillments: List<WishlistFulfillmentEntity>,
    val resolution: PlaceResolutionEntity?,
    val google: GooglePlaceEntity?,
  )

  /** まとめる前の控え。[from] はまとめられて消える方、[into] は残る方、[movedStopIds] は付け替えた立ち寄り。 */
  data class MergeSnapshot(
    val from: PlaceRows,
    val into: PlaceRows,
    val movedStopIds: List<Long>,
  )

  /**
   * [fromPlaceId] を [intoPlaceId] にまとめる（[fromPlaceId] は消える）。決めごとは adr/0037:
   *
   * - 立ち寄り・行きたいの履歴は、すべて残る方へ付け替える。
   * - 名前は残る方を優先し、空ならもう一方を使う。メモは両方にあればつなげる（残る方が先）。
   * - 行きたい・訪問済みの印は、どちらかにあれば付ける。行きたいが両方にあれば、優先度は高い方。
   * - 施設情報は残る方を優先し、残る方に無ければもう一方のものを移す。
   * - どちらかが自分で作った場所（USER）なら、残る方も USER にする（自動回収から守る）。
   *
   * どちらかが無い・同じ場所なら何もせず null を返す。
   */
  @Transaction
  open suspend fun merge(fromPlaceId: Long, intoPlaceId: Long, now: Date): MergeSnapshot? {
    if (fromPlaceId == intoPlaceId) return null
    val from = rowsOf(fromPlaceId) ?: return null
    val into = rowsOf(intoPlaceId) ?: return null
    val movedStopIds = stopIdsOf(fromPlaceId)

    repointStops(fromPlaceId, intoPlaceId)
    repointFulfillments(fromPlaceId, intoPlaceId)

    val fromWish = from.wishlist
    val intoWish = into.wishlist
    if (fromWish != null) {
      if (intoWish == null) {
        repointWishlist(fromWish.id, intoPlaceId)
      } else if (fromWish.priority > intoWish.priority) {
        updateWishlistPriority(intoWish.id, fromWish.priority, now)
      }
    }
    val fromVisited = from.visited
    if (fromVisited != null && into.visited == null) repointVisited(fromVisited.id, intoPlaceId)
    if (from.google != null && into.google == null) {
      // 施設は一意なので、消える方の行の主キーを残る方へ付け替える。
      repointGoogle(fromPlaceId, intoPlaceId)
      if (into.resolution == null) insertResolution(PlaceResolutionEntity(intoPlaceId, from.resolution?.resolvedAt ?: now))
    }

    val notes = listOfNotNull(into.place.note?.ifBlank { null }, from.place.note?.ifBlank { null })
    val isUser = into.place.source == PlaceSource.USER.name || from.place.source == PlaceSource.USER.name
    updatePlace(
      into.place.copy(
        name = into.place.name ?: from.place.name,
        note = notes.joinToString("\n").ifBlank { null },
        source = if (isUser) PlaceSource.USER.name else into.place.source,
        updatedAt = now,
      ),
    )
    // 付け替えなかった子（行きたい・訪問済み・解決記録・施設情報）は CASCADE で一緒に消える。
    deletePlace(fromPlaceId)
    return MergeSnapshot(from, into, movedStopIds)
  }

  /** [merge] を取り消す。両方の場所を、まとめる前の行（元の id）に戻す。 */
  @Transaction
  open suspend fun restore(snapshot: MergeSnapshot) {
    val fromId = snapshot.from.place.id
    val intoId = snapshot.into.place.id
    // 残った方の子は、まとめた後に足された・移された行ごと消してから、控えを入れ直す。
    deleteWishlistOf(intoId)
    deleteVisitedOf(intoId)
    deleteFulfillmentsOf(intoId)
    deleteResolutionOf(intoId)
    deleteGoogleOf(intoId)
    updatePlace(snapshot.into.place)
    insertPlace(snapshot.from.place)
    if (snapshot.movedStopIds.isNotEmpty()) repointStopsByIds(snapshot.movedStopIds, fromId)
    for (rows in listOf(snapshot.into, snapshot.from)) {
      rows.google?.let { upsertGoogle(it) }
      rows.resolution?.let { insertResolution(it) }
      rows.wishlist?.let { insertWishlist(it) }
      rows.visited?.let { insertVisited(it) }
      if (rows.fulfillments.isNotEmpty()) insertFulfillments(rows.fulfillments)
    }
  }

  private suspend fun rowsOf(placeId: Long): PlaceRows? {
    val place = placeOf(placeId) ?: return null
    return PlaceRows(
      place = place,
      wishlist = wishlistOf(placeId),
      visited = visitedOf(placeId),
      fulfillments = fulfillmentsOf(placeId),
      resolution = resolutionOf(placeId),
      google = googleOf(placeId),
    )
  }

  @Query("SELECT * FROM places WHERE id = :placeId")
  protected abstract suspend fun placeOf(placeId: Long): PlaceEntity?

  @Query("SELECT * FROM wishlist WHERE placeId = :placeId")
  protected abstract suspend fun wishlistOf(placeId: Long): WishlistEntity?

  @Query("SELECT * FROM visited_places WHERE placeId = :placeId")
  protected abstract suspend fun visitedOf(placeId: Long): VisitedPlaceEntity?

  @Query("SELECT * FROM wishlist_fulfillments WHERE placeId = :placeId")
  protected abstract suspend fun fulfillmentsOf(placeId: Long): List<WishlistFulfillmentEntity>

  @Query("SELECT * FROM place_resolutions WHERE placeId = :placeId")
  protected abstract suspend fun resolutionOf(placeId: Long): PlaceResolutionEntity?

  @Query("SELECT * FROM google_places WHERE placeId = :placeId")
  protected abstract suspend fun googleOf(placeId: Long): GooglePlaceEntity?

  @Query("SELECT id FROM stops WHERE placeId = :placeId")
  protected abstract suspend fun stopIdsOf(placeId: Long): List<Long>

  @Query("UPDATE stops SET placeId = :intoPlaceId WHERE placeId = :fromPlaceId")
  protected abstract suspend fun repointStops(fromPlaceId: Long, intoPlaceId: Long)

  @Query("UPDATE stops SET placeId = :placeId WHERE id IN (:stopIds)")
  protected abstract suspend fun repointStopsByIds(stopIds: List<Long>, placeId: Long)

  @Query("UPDATE wishlist_fulfillments SET placeId = :intoPlaceId WHERE placeId = :fromPlaceId")
  protected abstract suspend fun repointFulfillments(fromPlaceId: Long, intoPlaceId: Long)

  @Query("UPDATE wishlist SET placeId = :placeId WHERE id = :wishlistId")
  protected abstract suspend fun repointWishlist(wishlistId: Long, placeId: Long)

  @Query("UPDATE wishlist SET priority = :priority, updatedAt = :updatedAt WHERE id = :wishlistId")
  protected abstract suspend fun updateWishlistPriority(wishlistId: Long, priority: Int, updatedAt: Date)

  @Query("UPDATE visited_places SET placeId = :placeId WHERE id = :visitedId")
  protected abstract suspend fun repointVisited(visitedId: Long, placeId: Long)

  @Query("UPDATE google_places SET placeId = :intoPlaceId WHERE placeId = :fromPlaceId")
  protected abstract suspend fun repointGoogle(fromPlaceId: Long, intoPlaceId: Long)

  @Update
  protected abstract suspend fun updatePlace(place: PlaceEntity)

  @Insert
  protected abstract suspend fun insertPlace(place: PlaceEntity)

  @Query("DELETE FROM places WHERE id = :placeId")
  protected abstract suspend fun deletePlace(placeId: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  protected abstract suspend fun insertResolution(resolution: PlaceResolutionEntity)

  @Upsert
  protected abstract suspend fun upsertGoogle(google: GooglePlaceEntity)

  @Insert
  protected abstract suspend fun insertWishlist(wishlist: WishlistEntity)

  @Insert
  protected abstract suspend fun insertVisited(visited: VisitedPlaceEntity)

  @Insert
  protected abstract suspend fun insertFulfillments(fulfillments: List<WishlistFulfillmentEntity>)

  @Query("DELETE FROM wishlist WHERE placeId = :placeId")
  protected abstract suspend fun deleteWishlistOf(placeId: Long)

  @Query("DELETE FROM visited_places WHERE placeId = :placeId")
  protected abstract suspend fun deleteVisitedOf(placeId: Long)

  @Query("DELETE FROM wishlist_fulfillments WHERE placeId = :placeId")
  protected abstract suspend fun deleteFulfillmentsOf(placeId: Long)

  @Query("DELETE FROM place_resolutions WHERE placeId = :placeId")
  protected abstract suspend fun deleteResolutionOf(placeId: Long)

  @Query("DELETE FROM google_places WHERE placeId = :placeId")
  protected abstract suspend fun deleteGoogleOf(placeId: Long)
}
