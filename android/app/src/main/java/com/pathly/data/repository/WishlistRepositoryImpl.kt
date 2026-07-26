package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.entity.PlaceWithWishlist
import com.pathly.data.local.entity.WishlistEntity
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.Priority
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
  private val wishlistDao: WishlistDao,
  private val placeDao: PlaceDao,
  private val placeRepository: PlaceRepository,
) : WishlistRepository {

  private val logger = Logger("WishlistRepositoryImpl")

  override fun getPlaces(): Flow<List<PlaceListItem>> = placeDao.getPlacesWithWishlist().map { list -> list.map { it.toPlaceListItem() } }

  override suspend fun registerPlace(latitude: Double, longitude: Double, name: String?): Long {
    val placeId = placeRepository.findOrCreatePlace(latitude, longitude)
    // 名前が指定され、かつ場所が未命名のときだけ命名する（既存の命名は上書きしない）。
    val trimmedName = name?.trim()?.ifBlank { null }
    if (trimmedName != null && placeDao.getById(placeId)?.name == null) {
      placeDao.updateName(placeId, trimmedName, Date())
    }
    return placeId
  }

  override suspend fun addToWishlist(placeId: Long, priority: Priority, memo: String?): Long {
    // 同じ場所を二重登録しない（placeId は UNIQUE）。既にあれば既存を返す。
    wishlistDao.getByPlaceId(placeId)?.let { return it.id }
    val now = Date()
    val id = wishlistDao.insert(
      WishlistEntity(
        placeId = placeId,
        priority = priority.value,
        memo = memo?.trim()?.ifBlank { null },
        visitedAt = null,
        createdAt = now,
        updatedAt = now,
      ),
    )
    logger.i("Added wishlist item $id for place $placeId")
    return id
  }

  override suspend fun updateWishlist(id: Long, priority: Priority, memo: String?) {
    wishlistDao.updateFields(id, priority.value, memo?.trim()?.ifBlank { null }, Date())
  }

  override suspend fun setVisited(id: Long, visited: Boolean) {
    wishlistDao.updateVisited(id, if (visited) Date() else null, Date())
  }

  override suspend fun removeFromWishlist(id: Long) {
    wishlistDao.deleteById(id)
  }

  override suspend fun deletePlace(placeId: Long) {
    // 立ち寄り記録（stops）は残す方針。呼び出し側で stops のある場所は削除させない（UIで非活性）。
    // place を消すと wishlist / place_resolutions は CASCADE で消える。
    placeDao.deleteById(placeId)
    logger.i("Deleted place $placeId")
  }

  private fun PlaceWithWishlist.toPlaceListItem(): PlaceListItem = PlaceListItem(
    place = Place(
      id = id,
      name = name,
      latitude = latitude,
      longitude = longitude,
      address = address,
      createdAt = createdAt,
      updatedAt = updatedAt,
    ),
    wishlistId = wishlistId,
    priority = priority?.let { Priority.fromValue(it) },
    memo = memo,
    visitedAt = visitedAt,
    visitCount = visitCount,
  )
}
