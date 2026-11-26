package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceMergeDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.entity.RegisteredPlaceRow
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.Geo
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPending
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.model.StopDetector
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
  private val placeDao: PlaceDao,
  private val placeResolutionDao: PlaceResolutionDao,
  private val placeMergeDao: PlaceMergeDao,
  private val placesNameResolver: PlacesNameResolver,
  private val places: PlaceOperations,
  private val session: PlaceStopSession,
) : PlaceRepository {

  private val logger = Logger("PlaceRepositoryImpl")

  private val mutex get() = session.mutex

  override fun observeRegisteredPlaces(): Flow<List<RegisteredPlace>> = placeDao.observeRegisteredPlaces().map { rows ->
    rows.map { it.toRegisteredPlace() }
  }

  override suspend fun findNearbyPlaces(latitude: Double, longitude: Double): List<RegisteredPlace> = findPlacesAround(latitude, longitude, StopDetector.RADIUS_METERS).map { it.place }

  override suspend fun findPlacesAround(latitude: Double, longitude: Double, radiusMeters: Double): List<PlaceWithDistance> {
    // まず矩形（座標索引が効く）で絞り、そのうえで正確な距離で判定する。
    val bounds = Geo.boundsAround(latitude, longitude, radiusMeters)
    return placeDao.getRegisteredPlacesInBounds(
      bounds.minLatitude,
      bounds.maxLatitude,
      bounds.minLongitude,
      bounds.maxLongitude,
    )
      .map { PlaceWithDistance(it.toRegisteredPlace(), Geo.distanceMeters(it.latitude, it.longitude, latitude, longitude)) }
      .filter { it.distanceMeters <= radiusMeters }
      .sortedBy { it.distanceMeters }
  }

  private fun RegisteredPlaceRow.toRegisteredPlace(): RegisteredPlace = RegisteredPlace(
    placeId = placeId,
    name = name,
    latitude = latitude,
    longitude = longitude,
    isWishlisted = wishlistCount > 0,
    fulfilledWishCount = fulfilledWishCount,
    // 「場所」タブと同じ判定: 立ち寄り記録があるか、手動で訪問済みにしたら訪問済み。
    isVisited = visitCount > 0 || markedVisitedAt != null,
    categoryCode = categoryCode,
    hasGoogleInfo = hasGoogleInfo,
  )

  override fun unresolvedCountForTrack(trackId: Long): Flow<Int> = placeDao.countPlacesWithoutGoogleIdForTrack(trackId)

  override suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = placesNameResolver.searchNearbyCandidates(latitude, longitude)

  override suspend fun resolveUnresolvedNames(trackId: Long): Int {
    try {
      return mutex.withLock {
        // 手動再取得: googlePlaceId が無い place を対象に（NoMatch・過去失敗も）叩き直す。
        var skipped = 0
        for (place in placeDao.getPlacesWithoutGoogleIdForTrack(trackId)) {
          if (places.resolvePlace(place).skippedByConflict) skipped++
        }
        skipped
      }
    } catch (e: Exception) {
      logger.e("resolveUnresolvedNames failed for track $trackId", e)
      return 0
    }
  }

  /**
   * 例外はここで握らず呼び出し元へ返す。再試行するかどうかは呼び出し元（WorkManager）の判断で、
   * ここで飲んでしまうと「失敗したのに成功として扱われる」ため。
   */
  override suspend fun resolveAllUnresolvedNames() {
    // 未解決（place_resolutions 行が無い）立ち寄り場所を全経路から拾い、オンライン時のみ解決する。
    // resolvePlace はオフラインで NotAttempted（行を残さない）＝再訪時にまた拾えるので安全。
    val unresolved = mutex.withLock { placeDao.getUnresolvedPlaces() }
    if (unresolved.isEmpty()) return
    logger.i("Catching up ${unresolved.size} unresolved places")
    for (place in unresolved) {
      mutex.withLock { places.resolvePlace(place) }
    }
  }

  override fun observeSameFacilityPending(trackId: Long): Flow<List<SameFacilityPending>> = placeDao.observeSameFacilityPendingForTrack(trackId).map { rows ->
    rows.map { SameFacilityPending(it.placeId, it.placeName, SameFacilityPlace(it.otherPlaceId, it.otherName)) }
  }

  override suspend fun mergePlaces(fromPlaceId: Long, intoPlaceId: Long): Boolean = mutex.withLock {
    val snapshot = placeMergeDao.merge(fromPlaceId, intoPlaceId, Date()) ?: return@withLock false
    // 滞在中に覚えていたのがまとめられた側なら、残る方に差し替える（消えた id で立ち寄りを作らない）。
    // 取り消したときに戻せるよう、差し替えた経路を覚えておく。
    session.lastMergeLiveTracks = session.liveTracksAt(fromPlaceId)
    session.repointLiveStopPlace(fromPlaceId, intoPlaceId)
    // 付け替えた立ち寄りが、残る方の行きたいを「行った」にすることがある（→ adr/0036）。
    places.fulfillReachedWishes()
    session.lastPlaceMerge = snapshot
    // 立ち寄りの変更の控えは、消えた場所を指していることがあるので捨てる（取り消すと外部キーで失敗する）。
    session.lastStopChange = null
    logger.i("Merged place $fromPlaceId into $intoPlaceId by user (${snapshot.movedStopIds.size} stops)")
    true
  }

  override suspend fun undoLastPlaceMerge(): Boolean = mutex.withLock {
    val snapshot = session.lastPlaceMerge ?: return@withLock false
    placeMergeDao.restore(snapshot)
    // まとめたときに差し替えた滞在中の place を、元の場所へ戻す。
    val fromPlaceId = snapshot.from.place.id
    val intoPlaceId = snapshot.into.place.id
    for (trackId in session.lastMergeLiveTracks) {
      session.liveStopPlaces[trackId]?.takeIf { it.placeId == intoPlaceId }?.let { live ->
        session.liveStopPlaces[trackId] = PlaceStopSession.LiveStopPlace(live.arrivalMillis, fromPlaceId)
      }
      session.manualLiveStops[trackId]?.takeIf { it.placeId == intoPlaceId }?.let { live ->
        session.manualLiveStops[trackId] = live.copy(placeId = fromPlaceId)
      }
    }
    session.lastMergeLiveTracks = emptySet()
    session.lastPlaceMerge = null
    logger.i("Undid place merge: restored $fromPlaceId")
    true
  }

  override suspend fun keepAsSeparatePlace(placeId: Long) {
    placeResolutionDao.keepSeparate(placeId)
    logger.i("Kept place $placeId separate from the place of the same facility")
  }

  override suspend fun updatePlaceName(placeId: Long, name: String) {
    placeDao.updateName(placeId, name.trim().ifBlank { null }, Date())
    // 立ち寄り中の場所なら、次の位置の更新を待たずに表示を変える。
    mutex.withLock {
      val current = session.currentStop.value ?: return@withLock
      if (current.place.id != placeId) return@withLock
      places.placeOf(placeId)?.let { session.currentStop.value = current.copy(place = it) }
    }
  }

  override suspend fun findOrCreatePlace(latitude: Double, longitude: Double, source: PlaceSource): Long = places.findOrCreatePlace(latitude, longitude, source)

  override suspend fun findOrCreateByGooglePlaceId(
    googlePlaceId: String,
    latitude: Double,
    longitude: Double,
    source: PlaceSource,
  ): Pair<Long, Boolean> = places.findOrCreateByGooglePlaceId(googlePlaceId, latitude, longitude, source)
}
