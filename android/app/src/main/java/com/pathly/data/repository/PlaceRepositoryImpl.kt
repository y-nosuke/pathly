package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.StopWithPlace
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Place
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopDetector
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class PlaceRepositoryImpl @Inject constructor(
  private val placeDao: PlaceDao,
  private val stopDao: StopDao,
  private val placesNameResolver: PlacesNameResolver,
) : PlaceRepository {

  private val logger = Logger("PlaceRepositoryImpl")

  override fun getStopsForTrack(trackId: Long): Flow<List<Stop>> = stopDao.getStopsWithPlaceByTrack(trackId).map { list -> list.map { it.toStop() } }

  override suspend fun ensureStopsDetected(track: GpsTrack) {
    try {
      // 未検出のときだけ検出して保存する（冪等）。
      if (stopDao.countByTrack(track.id) == 0) {
        val detected = StopDetector.detect(track.smoothedPoints)
        for (d in detected) {
          val placeId = findOrCreatePlace(d.latitude, d.longitude)
          stopDao.insert(
            StopEntity(
              placeId = placeId,
              trackId = track.id,
              arrivalTime = d.arrivalTime,
              departureTime = d.departureTime,
            ),
          )
        }
        logger.i("Stored ${detected.size} stops for track ${track.id}")
      }
      // 名前の無い場所を Places で命名（オンライン時のみ・キャッシュ）。
      resolveMissingNames(track.id)
    } catch (e: Exception) {
      logger.e("ensureStopsDetected failed for track ${track.id}", e)
    }
  }

  override suspend fun updatePlaceName(placeId: Long, name: String) {
    placeDao.updateName(placeId, name.trim().ifBlank { null }, Date())
  }

  /** 近く（[DEDUPE_RADIUS_METERS] 以内）に既存の場所があれば再利用、無ければ新規作成する。 */
  private suspend fun findOrCreatePlace(latitude: Double, longitude: Double): Long {
    val existing = placeDao.getAll().firstOrNull {
      distanceMeters(it.latitude, it.longitude, latitude, longitude) <= DEDUPE_RADIUS_METERS
    }
    if (existing != null) return existing.id
    return placeDao.insert(PlaceEntity(latitude = latitude, longitude = longitude))
  }

  private suspend fun resolveMissingNames(trackId: Long) {
    val unnamed = placeDao.getUnnamedPlacesForTrack(trackId)
    for (place in unnamed) {
      val result = placesNameResolver.resolve(place.latitude, place.longitude) ?: continue
      placeDao.updateNameAndAddress(place.id, result.name, result.address, Date())
    }
  }

  private fun StopWithPlace.toStop(): Stop = Stop(
    id = stop.id,
    place = place.toPlace(),
    trackId = stop.trackId,
    arrivalTime = stop.arrivalTime,
    departureTime = stop.departureTime,
  )

  private fun PlaceEntity.toPlace(): Place = Place(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    address = address,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

  private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
      sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(h), sqrt(1 - h))
  }

  companion object {
    /** 同一場所とみなす距離（重複排除）。 */
    private const val DEDUPE_RADIUS_METERS = 30.0
    private const val EARTH_RADIUS_METERS = 6371000.0
  }
}
