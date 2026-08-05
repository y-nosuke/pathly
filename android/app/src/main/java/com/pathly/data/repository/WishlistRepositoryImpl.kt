package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.PlaceWithWishlist
import com.pathly.data.local.entity.WishlistEntity
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
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
  private val placeResolutionDao: PlaceResolutionDao,
  private val googlePlaceDao: GooglePlaceDao,
  private val stopDao: StopDao,
  private val placeRepository: PlaceRepository,
) : WishlistRepository {

  private val logger = Logger("WishlistRepositoryImpl")

  override fun getPlaces(): Flow<List<PlaceListItem>> = placeDao.getPlacesWithWishlist().map { list -> list.map { it.toPlaceListItem() } }

  override fun getVisits(placeId: Long): Flow<List<PlaceVisit>> = stopDao.getVisitsForPlace(placeId).map { list ->
    list.map {
      PlaceVisit(
        trackId = it.trackId,
        outingDate = it.trackStartTime,
        arrivalTime = it.arrivalTime,
        departureTime = it.departureTime,
        note = it.note,
      )
    }
  }

  override suspend fun registerPlace(latitude: Double, longitude: Double, name: String?, note: String?): Long {
    val placeId = placeRepository.findOrCreatePlace(latitude, longitude)
    // 名前が指定され、かつ場所が未命名のときだけ命名する（既存の命名は上書きしない）。
    val trimmedName = name?.trim()?.ifBlank { null }
    if (trimmedName != null && placeDao.getById(placeId)?.name == null) {
      placeDao.updateName(placeId, trimmedName, Date())
    }
    val trimmedNote = note?.trim()?.ifBlank { null }
    if (trimmedNote != null) {
      placeDao.updateNote(placeId, trimmedNote, Date())
    }
    return placeId
  }

  override suspend fun registerSearchedPlace(result: PlaceSearchResult): Long {
    val placeId = placeRepository.findOrCreatePlace(result.latitude, result.longitude)
    // 検索結果は Google 由来なので google_places に記録（places.name はユーザー名専用）。
    // 表示は google_places.name にフォールバックするので、名前は自動で出る。
    googlePlaceDao.upsert(
      GooglePlaceEntity(placeId, result.googlePlaceId, result.name, result.address),
    )
    // 問い合わせlog に記録 → 以後は自動命名で Nearby を叩かない。
    placeResolutionDao.upsert(PlaceResolutionEntity(placeId, Date()))
    logger.i("Registered searched place $placeId (google=${result.googlePlaceId})")
    return placeId
  }

  override suspend fun renamePlace(placeId: Long, name: String) {
    placeDao.updateName(placeId, name.trim().ifBlank { null }, Date())
  }

  override suspend fun updatePlaceNote(placeId: Long, note: String?) {
    placeDao.updateNote(placeId, note?.trim()?.ifBlank { null }, Date())
  }

  override suspend fun addToWishlist(placeId: Long, priority: Priority): Long {
    // 同じ場所を二重登録しない（placeId は UNIQUE）。既にあれば既存を返す。
    wishlistDao.getByPlaceId(placeId)?.let { return it.id }
    val now = Date()
    val id = wishlistDao.insert(
      WishlistEntity(
        placeId = placeId,
        priority = priority.value,
        visitedAt = null,
        createdAt = now,
        updatedAt = now,
      ),
    )
    logger.i("Added wishlist item $id for place $placeId")
    return id
  }

  override suspend fun updateWishlist(id: Long, priority: Priority) {
    wishlistDao.updateFields(id, priority.value, Date())
  }

  override suspend fun setVisited(id: Long, visited: Boolean) {
    wishlistDao.updateVisited(id, if (visited) Date() else null, Date())
  }

  override suspend fun removeFromWishlist(id: Long) {
    wishlistDao.deleteById(id)
  }

  // 直近の削除の取り消し用スナップショット（1件だけ保持。次の削除で置き換わる）。
  // 画面のスナックバーは常に最新の1件しか出さないため、単一スロットで十分。
  private var lastDeletedPlace: DeletedPlaceSnapshot? = null

  override suspend fun deletePlace(placeId: Long) {
    // 立ち寄り記録（stops）は残す方針。呼び出し側で stops のある場所は削除させない（UIで非活性）。
    // place を消すと wishlist / place_resolutions は CASCADE で消える。
    // 取り消し（Undo）で元IDのまま戻せるよう、削除前に実体を控える。
    val place = placeDao.getById(placeId) ?: return
    lastDeletedPlace = DeletedPlaceSnapshot(
      place = place,
      wishlist = wishlistDao.getByPlaceId(placeId),
      resolution = placeResolutionDao.getByPlace(placeId),
      google = googlePlaceDao.getByPlace(placeId),
    )
    placeDao.deleteById(placeId)
    logger.i("Deleted place $placeId")
  }

  override suspend fun undoLastPlaceDeletion(): Boolean {
    val snap = lastDeletedPlace ?: return false
    // FK 順に復元する: 先に place（子が参照）→ 子（明示IDのまま再挿入）。
    placeDao.insert(snap.place)
    snap.google?.let { googlePlaceDao.upsert(it) }
    snap.resolution?.let { placeResolutionDao.upsert(it) }
    snap.wishlist?.let { wishlistDao.insert(it) }
    lastDeletedPlace = null
    logger.i("Undid place deletion: restored ${snap.place.id}")
    return true
  }

  /** 場所削除の取り消しに必要な実体一式（元IDのまま再挿入して復元する）。 */
  private class DeletedPlaceSnapshot(
    val place: PlaceEntity,
    val wishlist: WishlistEntity?,
    val resolution: PlaceResolutionEntity?,
    val google: GooglePlaceEntity?,
  )

  private fun PlaceWithWishlist.toPlaceListItem(): PlaceListItem = PlaceListItem(
    place = Place(
      id = id,
      name = name,
      latitude = latitude,
      longitude = longitude,
      note = note,
      googleName = googleName,
      googleAddress = googleAddress,
      category = category,
      createdAt = createdAt,
      updatedAt = updatedAt,
    ),
    wishlistId = wishlistId,
    priority = priority?.let { Priority.fromValue(it) },
    visitedAt = visitedAt,
    visitCount = visitCount,
  )
}
