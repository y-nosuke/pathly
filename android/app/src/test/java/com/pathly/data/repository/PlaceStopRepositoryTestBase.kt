package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceCategoryDao
import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceMergeDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.PlanDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.VisitedPlaceDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.dao.WishlistFulfillmentDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Before
import java.util.Date

/**
 * 場所と立ち寄りの 3 つの窓口のテストの土台（→ adr/0049）。DAO はモックで、3 つの実装は
 * 実際と同じく 1 つの [PlaceStopSession]・[PlaceOperations] を共有する（窓口をまたぐ操作も確かめられる）。
 */
abstract class PlaceStopRepositoryTestBase {

  protected val placeDao = mockk<PlaceDao>(relaxed = true)
  protected val stopDao = mockk<StopDao>(relaxed = true)
  protected val smoothedPointDao = mockk<SmoothedPointDao>(relaxed = true)
  protected val gpsPointDao = mockk<GpsPointDao>(relaxed = true)
  protected val placeResolutionDao = mockk<PlaceResolutionDao>(relaxed = true)
  protected val googlePlaceDao = mockk<GooglePlaceDao>(relaxed = true)
  protected val googlePlaceCategoryDao = mockk<GooglePlaceCategoryDao>(relaxed = true)
  protected val wishlistDao = mockk<WishlistDao>(relaxed = true)
  protected val visitedPlaceDao = mockk<VisitedPlaceDao>(relaxed = true)
  protected val wishlistFulfillmentDao = mockk<WishlistFulfillmentDao>(relaxed = true)
  protected val placeMergeDao = mockk<PlaceMergeDao>(relaxed = true)
  protected val planDao = mockk<PlanDao>(relaxed = true)
  protected val resolver = mockk<PlacesNameResolver>(relaxed = true)

  @Before
  fun stubNullableLongs() {
    // relaxed モックは nullable な Long にも 0L を返す。実 DAO は「持っている place が無ければ null」
    // なので、既定を null にしておく（要るテストだけ上書きする）。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId(any()) } returns null
  }

  protected val session = PlaceStopSession()

  protected val placeOperations = PlaceOperations(
    placeDao,
    stopDao,
    placeResolutionDao,
    googlePlaceDao,
    googlePlaceCategoryDao,
    wishlistDao,
    visitedPlaceDao,
    wishlistFulfillmentDao,
    planDao,
    resolver,
    session,
  )

  protected val liveStops = LiveStopRepositoryImpl(
    placeDao,
    stopDao,
    smoothedPointDao,
    gpsPointDao,
    placeResolutionDao,
    placeOperations,
    session,
  )

  protected val stops = StopRepositoryImpl(
    placeDao,
    stopDao,
    smoothedPointDao,
    placeResolutionDao,
    googlePlaceDao,
    resolver,
    placeOperations,
    session,
  )

  protected val places = PlaceRepositoryImpl(
    placeDao,
    placeResolutionDao,
    placeMergeDao,
    resolver,
    placeOperations,
    session,
  )

  protected fun gp(lat: Double, lon: Double) = GpsPointEntity(
    trackId = 1L,
    latitude = lat,
    longitude = lon,
    accuracy = 5f,
    timestamp = Date(),
  )

  protected fun sp(lat: Double, lon: Double, timeSec: Long, seq: Int) = SmoothedPointEntity(
    trackId = 1L,
    seq = seq,
    latitude = lat,
    longitude = lon,
    timestamp = Date(timeSec * 1000L),
  )

  // 50m圏内に4分滞在 → 遠くへ移動（離脱）。確定した立ち寄り1件。
  protected fun finishedVisitPoints() = listOf(
    sp(35.0000, 139.0000, 0, 0),
    sp(35.0001, 139.0000, 60, 1),
    sp(35.0000, 139.0001, 120, 2),
    sp(35.0001, 139.0001, 180, 3),
    sp(35.0000, 139.0000, 240, 4),
    sp(35.0100, 139.0100, 300, 5), // 約1.4km 離れる＝離脱
  )

  // 50m圏内に4分滞在したまま（末尾まで滞在中）。確定していない＝立ち寄り中。
  protected fun dwellingPoints() = finishedVisitPoints().dropLast(1)

  // クラスタA [0-240s]（過去・確定できる）→ 約1.4km移動 → クラスタB [300-540s]（末尾まで滞在中）。
  // 記録中は A が境界以前、B が境界より後（滞在中）になる。
  protected fun twoClustersDwellingAtTail() = listOf(
    sp(35.0000, 139.0000, 0, 0),
    sp(35.0001, 139.0000, 60, 1),
    sp(35.0000, 139.0001, 120, 2),
    sp(35.0001, 139.0001, 180, 3),
    sp(35.0000, 139.0000, 240, 4),
    sp(35.0100, 139.0100, 300, 5), // 遠くへ移動＝Aを離脱、Bの起点
    sp(35.0101, 139.0100, 360, 6),
    sp(35.0100, 139.0101, 420, 7),
    sp(35.0101, 139.0101, 480, 8),
    sp(35.0100, 139.0100, 540, 9),
  )

  /** 挿入した place を覚えて返す簡易ストア（今ここを立ち寄りのテスト用）。 */
  protected fun storePlaces(): MutableList<PlaceEntity> {
    val stored = mutableListOf<PlaceEntity>()
    var nextId = 1L
    coEvery { placeDao.insert(any()) } answers {
      val id = nextId++
      stored += firstArg<PlaceEntity>().copy(id = id)
      id
    }
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.getById(any()) } answers { stored.find { place -> place.id == firstArg<Long>() } }
    coEvery { googlePlaceDao.getWithCategoryByPlace(any()) } returns null
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted
    return stored
  }

  protected fun gpAt(lat: Double, lon: Double, timeSec: Long) = gp(lat, lon).copy(timestamp = Date(timeSec * 1000L))

  protected suspend fun startHere(arrivalSec: Long, name: String? = "売店") = liveStops.startLiveStop(
    trackId = 1L,
    anchorLatitude = 35.0,
    anchorLongitude = 139.0,
    arrivalTime = Date(arrivalSec * 1000L),
    latitude = 35.0,
    longitude = 139.0,
    name = name,
    googlePlaceId = null,
    googleName = null,
  )

  protected fun stopEntity(id: Long, placeId: Long) = StopEntity(
    id = id,
    placeId = placeId,
    trackId = 1L,
    arrivalTime = Date(0L),
    departureTime = Date(60_000L),
  )

  // --- 滞在期間の編集・立ち寄りの手動統合（adr/0024）---

  protected fun stopEntity(id: Long, placeId: Long, arrivalMillis: Long, departureMillis: Long, note: String? = null) = StopEntity(
    id = id,
    placeId = placeId,
    trackId = 1L,
    arrivalTime = Date(arrivalMillis),
    departureTime = Date(departureMillis),
    note = note,
  )
}
