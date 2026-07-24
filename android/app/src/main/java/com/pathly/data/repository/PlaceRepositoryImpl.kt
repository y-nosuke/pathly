package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.StopWithPlace
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.DetectedStop
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.Place
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopDetector
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
  private val smoothedPointDao: SmoothedPointDao,
  private val placeResolutionDao: PlaceResolutionDao,
  private val placesNameResolver: PlacesNameResolver,
) : PlaceRepository {

  private val logger = Logger("PlaceRepositoryImpl")

  // 記録中の検出・保存・命名と再解析を直列化する。
  private val mutex = Mutex()

  private val _currentStop = MutableStateFlow<Stop?>(null)
  override val currentStop: StateFlow<Stop?> = _currentStop.asStateFlow()

  override fun getStopsForTrack(trackId: Long): Flow<List<Stop>> = stopDao.getStopsWithPlaceByTrack(trackId).map { list -> list.map { it.toStop() } }

  override fun unresolvedCountForTrack(trackId: Long): Flow<Int> = placeDao.countPlacesWithoutGoogleIdForTrack(trackId)

  override suspend fun updateStopsForTrack(trackId: Long, isFinal: Boolean) {
    try {
      mutex.withLock { detectAndPersist(trackId, isFinal) }
    } catch (e: Exception) {
      logger.e("updateStopsForTrack failed for track $trackId", e)
    }
  }

  override suspend fun redetectStops(trackId: Long) {
    try {
      mutex.withLock {
        stopDao.deleteByTrack(trackId)
        // 再解析は終了済みトラックが対象なので末尾まで確定させる。
        detectAndPersist(trackId, isFinal = true)
        logger.i("Redetected stops for track $trackId")
      }
    } catch (e: Exception) {
      logger.e("redetectStops failed for track $trackId", e)
    }
  }

  override suspend fun resolveUnresolvedNames(trackId: Long) {
    try {
      mutex.withLock {
        // 手動再取得: googlePlaceId が無い place を対象に（NoMatch・過去失敗も）叩き直す。
        for (place in placeDao.getPlacesWithoutGoogleIdForTrack(trackId)) {
          resolvePlace(place)
        }
      }
    } catch (e: Exception) {
      logger.e("resolveUnresolvedNames failed for track $trackId", e)
    }
  }

  override suspend fun updatePlaceName(placeId: Long, name: String) {
    placeDao.updateName(placeId, name.trim().ifBlank { null }, Date())
  }

  override suspend fun deleteStop(stopId: Long) {
    mutex.withLock { stopDao.deleteById(stopId) }
  }

  override suspend fun deletePlace(placeId: Long, trackId: Long): Boolean = mutex.withLock {
    // 他の経路にも訪問が残っているなら場所は消さない（誤削除防止）。
    if (stopDao.countByPlaceInOtherTracks(placeId, trackId) > 0) {
      return@withLock false
    }
    stopDao.deleteByPlace(placeId) // この経路の訪問を消す
    placeDao.deleteById(placeId) // place_resolutions は CASCADE で消える
    true
  }

  /**
   * 補正後の点列から立ち寄りを検出し、確定分だけを差分保存する。末尾の滞在中クラスタが
   * 3分を超えたら place を先行確定して [currentStop] に流す（案B・メモリ保持）。
   * 呼び出しは [mutex] で直列化されている前提。
   */
  private suspend fun detectAndPersist(trackId: Long, isFinal: Boolean) {
    val smoothed = smoothedPointDao.getByTrack(trackId).map { it.toGpsPoint() }
    val detected = StopDetector.detect(smoothed)

    // 末尾点を含む最後のクラスタは「滞在中」＝暫定。isFinal なら末尾も確定。
    val lastTimestamp = smoothed.lastOrNull()?.timestamp
    val provisional = if (!isFinal) {
      detected.lastOrNull()?.takeIf { it.departureTime == lastTimestamp }
    } else {
      null
    }
    val finalized = if (provisional != null) detected.dropLast(1) else detected

    // 確定した立ち寄りのうち、まだ保存していないぶんだけ追記する（プレフィックスは単調・安定）。
    val persisted = stopDao.countByTrack(trackId)
    if (finalized.size > persisted) {
      for (d in finalized.subList(persisted, finalized.size)) {
        val placeId = findOrCreatePlace(d.latitude, d.longitude)
        stopDao.insert(
          StopEntity(
            placeId = placeId,
            trackId = trackId,
            arrivalTime = d.arrivalTime,
            departureTime = d.departureTime,
          ),
        )
      }
      logger.i("Persisted ${finalized.size - persisted} stops for track $trackId")
    }

    // 確定した立ち寄りの未解決 place をオンラインなら命名する（オフラインは行を作らずキャッチアップ）。
    for (place in placeDao.getUnresolvedPlacesForTrack(trackId)) {
      resolvePlace(place)
    }

    // 「立ち寄り中」: place を先行確定＋命名し、メモリで公開する。
    _currentStop.value = provisional?.let { toLiveStop(trackId, it) }
  }

  /** 「立ち寄り中」の place を先行確定して名前解決し、表示用の [Stop] を作る（id は 0）。 */
  private suspend fun toLiveStop(trackId: Long, d: DetectedStop): Stop {
    val placeId = findOrCreatePlace(d.latitude, d.longitude)
    if (placeResolutionDao.getByPlace(placeId) == null) {
      placeDao.getById(placeId)?.let { resolvePlace(it) }
    }
    val place = placeDao.getById(placeId)!!.toPlace()
    return Stop(id = 0, place = place, trackId = trackId, arrivalTime = d.arrivalTime, departureTime = d.departureTime)
  }

  /** place を Google で名前解決し、結果を place_resolutions に記録する（手動命名は上書きしない）。 */
  private suspend fun resolvePlace(place: PlaceEntity) {
    when (val outcome = placesNameResolver.resolve(place.latitude, place.longitude)) {
      is PlacesNameResolver.Outcome.Found -> {
        if (place.name == null) {
          placeDao.updateNameAndAddress(place.id, outcome.name, outcome.address, Date())
        }
        placeResolutionDao.upsert(PlaceResolutionEntity(place.id, Date(), outcome.googlePlaceId))
      }

      PlacesNameResolver.Outcome.NoMatch ->
        placeResolutionDao.upsert(PlaceResolutionEntity(place.id, Date(), null))

      PlacesNameResolver.Outcome.NotAttempted -> Unit // 行を作らず後でキャッチアップ
    }
  }

  /** 近く（[DEDUPE_RADIUS_METERS] 以内）に既存の場所があれば再利用、無ければ新規作成する。 */
  private suspend fun findOrCreatePlace(latitude: Double, longitude: Double): Long {
    val existing = placeDao.getAll().firstOrNull {
      distanceMeters(it.latitude, it.longitude, latitude, longitude) <= DEDUPE_RADIUS_METERS
    }
    if (existing != null) return existing.id
    return placeDao.insert(PlaceEntity(latitude = latitude, longitude = longitude))
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

  private fun SmoothedPointEntity.toGpsPoint(): GpsPoint = GpsPoint(
    id = sourcePointId ?: 0L,
    trackId = trackId,
    latitude = latitude,
    longitude = longitude,
    altitude = null,
    accuracy = 0f,
    speed = null,
    bearing = null,
    timestamp = timestamp,
    createdAt = createdAt,
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
