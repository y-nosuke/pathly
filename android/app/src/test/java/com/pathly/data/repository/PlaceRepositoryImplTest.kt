package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceCategoryDao
import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.VisitedPlaceDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.NamedPlaceRow
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.RegisteredPlaceRow
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.DetectedStop
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.StopCandidate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class PlaceRepositoryImplTest {

  private val placeDao = mockk<PlaceDao>(relaxed = true)
  private val stopDao = mockk<StopDao>(relaxed = true)
  private val smoothedPointDao = mockk<SmoothedPointDao>(relaxed = true)
  private val gpsPointDao = mockk<GpsPointDao>(relaxed = true)
  private val placeResolutionDao = mockk<PlaceResolutionDao>(relaxed = true)
  private val googlePlaceDao = mockk<GooglePlaceDao>(relaxed = true)
  private val googlePlaceCategoryDao = mockk<GooglePlaceCategoryDao>(relaxed = true)
  private val wishlistDao = mockk<WishlistDao>(relaxed = true)
  private val visitedPlaceDao = mockk<VisitedPlaceDao>(relaxed = true)
  private val resolver = mockk<PlacesNameResolver>(relaxed = true)

  @Before
  fun stubNullableLongs() {
    // relaxed モックは nullable な Long にも 0L を返す。実 DAO は「持っている place が無ければ null」
    // なので、既定を null にしておく（要るテストだけ上書きする）。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId(any()) } returns null
  }

  private val repository = PlaceRepositoryImpl(
    placeDao,
    stopDao,
    smoothedPointDao,
    gpsPointDao,
    placeResolutionDao,
    googlePlaceDao,
    googlePlaceCategoryDao,
    wishlistDao,
    visitedPlaceDao,
    resolver,
  )

  private fun gp(lat: Double, lon: Double) = GpsPointEntity(
    trackId = 1L,
    latitude = lat,
    longitude = lon,
    accuracy = 5f,
    timestamp = Date(),
  )

  private fun sp(lat: Double, lon: Double, timeSec: Long, seq: Int) = SmoothedPointEntity(
    trackId = 1L,
    seq = seq,
    latitude = lat,
    longitude = lon,
    timestamp = Date(timeSec * 1000L),
  )

  // 50m圏内に4分滞在 → 遠くへ移動（離脱）。確定した立ち寄り1件。
  private fun finishedVisitPoints() = listOf(
    sp(35.0000, 139.0000, 0, 0),
    sp(35.0001, 139.0000, 60, 1),
    sp(35.0000, 139.0001, 120, 2),
    sp(35.0001, 139.0001, 180, 3),
    sp(35.0000, 139.0000, 240, 4),
    sp(35.0100, 139.0100, 300, 5), // 約1.4km 離れる＝離脱
  )

  // 50m圏内に4分滞在したまま（末尾まで滞在中）。確定していない＝立ち寄り中。
  private fun dwellingPoints() = finishedVisitPoints().dropLast(1)

  // クラスタA [0-240s]（過去・確定できる）→ 約1.4km移動 → クラスタB [300-540s]（末尾まで滞在中）。
  // 記録中は A が境界以前、B が境界より後（滞在中）になる。
  private fun twoClustersDwellingAtTail() = listOf(
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

  @Test
  fun updateStops_finalizedVisit_persistsStopAndResolvesName() = runTest {
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns listOf(
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("カフェ", "住所", PlaceCategory("cafe", "カフェ・喫茶"), "gp-1", 35.5, 139.5)

    repository.updateStopsForTrack(1L, isFinal = false)

    val stopSlot = slot<StopEntity>()
    coVerify { stopDao.insert(capture(stopSlot)) }
    assertEquals(10L, stopSlot.captured.placeId)
    assertEquals(1L, stopSlot.captured.trackId)
    // Google 由来の名前・住所は google_places に入れる（places.name はユーザー名専用）。
    coVerify {
      googlePlaceDao.upsert(
        match { it.placeId == 10L && it.googlePlaceId == "gp-1" && it.name == "カフェ" && it.address == "住所" },
      )
    }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) }
    // 離脱済みなので「立ち寄り中」は無い。
    assertNull(repository.currentStop.value)
  }

  @Test
  fun updateStops_dwelling_setsCurrentStopWithoutPersistingStop() = runTest {
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns dwellingPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 20L
    coEvery { placeDao.getById(20L) } returns
      PlaceEntity(id = 20L, name = "カフェ", latitude = 35.0, longitude = 139.0)
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { placeResolutionDao.getByPlace(20L) } returns null
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("カフェ", "住所", PlaceCategory("cafe", "カフェ・喫茶"), "gp-2", 35.5, 139.5)
    // 生の現在地はまだ立ち寄りの中心にある＝滞在中。
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gp(35.0, 139.0)

    repository.updateStopsForTrack(1L, isFinal = false)

    // 滞在中は stop を保存しない。place は先行確定し「立ち寄り中」に出る。
    coVerify(exactly = 0) { stopDao.insert(any()) }
    val current = repository.currentStop.value
    assertNotNull(current)
    assertEquals(20L, current!!.place.id)
    coVerify { googlePlaceDao.upsert(match { it.placeId == 20L && it.googlePlaceId == "gp-2" }) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 20L }) }
  }

  /**
   * 施設の代表点が重心から 30m 超（ここでは約 40m）離れていても、滞在中に place が増えないこと。
   *
   * 以前は解決のたびに places の座標を施設の座標へ書き換えていたため、同定の鍵（30m）が動いて
   * 「自分で作った place を次のティックで見つけられない」状態になり、位置バッチごとに place が
   * 増え続けていた（→ adr/0023）。DAO をモックで固定していた既存テストではこれを捕まえられない
   * ので、挿入が近傍検索に反映される簡易ストアで検証する。
   */
  @Test
  fun updateStops_dwelling_doesNotDuplicatePlace_whenFacilityCoordinateIsFarFromAnchor() = runTest {
    val stored = mutableListOf<PlaceEntity>()
    val resolved = mutableSetOf<Long>()
    var nextId = 1L
    coEvery { placeDao.insert(any()) } answers {
      val id = nextId++
      stored += firstArg<PlaceEntity>().copy(id = id)
      id
    }
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } answers {
      val minLatitude = firstArg<Double>()
      val maxLatitude = secondArg<Double>()
      val minLongitude = thirdArg<Double>()
      val maxLongitude = arg<Double>(3)
      stored.filter {
        it.latitude in minLatitude..maxLatitude && it.longitude in minLongitude..maxLongitude
      }
    }
    coEvery { placeDao.getById(any()) } answers { stored.find { place -> place.id == firstArg<Long>() } }
    coEvery { placeResolutionDao.getByPlace(any()) } answers {
      firstArg<Long>().takeIf { it in resolved }?.let { PlaceResolutionEntity(it, Date()) }
    }
    coEvery { placeResolutionDao.upsert(any()) } answers { resolved += firstArg<PlaceResolutionEntity>().placeId }
    coEvery { googlePlaceDao.getWithCategoryByPlace(any()) } returns null
    // この施設を持つ place はまだ無い＝統合は起きない。relaxed のままだと 0L が返り、
    // 「既に同じ施設の place がある」と誤判定される（→ 統合されて upsert が走らない）。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId(any()) } returns null
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns dwellingPoints()
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gp(35.0, 139.0)
    // 重心は約 (35.00004, 139.00004)。施設の代表点はそこから約 40m 北＝同定半径 30m の外。
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-9", 35.0004, 139.00004)

    repeat(5) { repository.updateStopsForTrack(1L, isFinal = false) }

    assertEquals("滞在中に何度呼ばれても place は1件", 1, stored.size)
    // アンカーは検出した重心のまま。施設の座標は google_places に入る（表示用）。
    assertEquals(35.00004, stored.single().latitude, 1e-7)
    coVerify {
      googlePlaceDao.upsert(match { it.googlePlaceId == "gp-9" && it.latitude == 35.0004 })
    }
    // 解決済みの place は二度と叩かない（place 1件1回・adr/0014）。
    coVerify(exactly = 1) { resolver.resolve(any(), any()) }
  }

  /**
   * 滞在中に先行確保した place を、確定した立ち寄りがそのまま使うこと（確保は滞在につき1回）。
   *
   * クラスタは点を吸収して重心が動くので、確定時に確保し直すと別の place ができ、
   * 先行確保した方が誰からも参照されない置き去りになる（→ adr/0023）。
   */
  @Test
  fun updateStops_dwellThenFinalize_reusesPlaceReservedForThatDwell() = runTest {
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
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gp(35.0, 139.0)
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted
    // 1回目は滞在中（末尾まで同じ場所）、2回目は離脱して同じクラスタが確定する。
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returnsMany
      listOf(dwellingPoints(), finishedVisitPoints())

    repository.updateStopsForTrack(1L, isFinal = false)
    repository.updateStopsForTrack(1L, isFinal = false)

    assertEquals("滞在中と確定で place を作り直さない", 1, stored.size)
    val stopSlot = slot<StopEntity>()
    coVerify { stopDao.insert(capture(stopSlot)) }
    assertEquals("確定した立ち寄りは先行確保した place を指す", stored.single().id, stopSlot.captured.placeId)
  }

  // 補正後の末尾はまだ滞在中でも、生の現在地が立ち寄りの半径外に出ていれば「立ち寄り中」表示は
  // 即クリアする（補正の確定ラグや 50m を抜けきる分を待たない）。表示のみで保存には影響しない。
  @Test
  fun updateStops_whenLiveLocationLeftRadius_clearsCurrentStopDespiteLaggingSmoothed() = runTest {
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns dwellingPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 20L
    coEvery { placeDao.getById(20L) } returns
      PlaceEntity(id = 20L, name = "カフェ", latitude = 35.0, longitude = 139.0)
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { placeResolutionDao.getByPlace(20L) } returns null
    // 生の現在地はクラスタ中心(≈35.0,139.0)から約1.4km離れている＝離脱済み。
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gp(35.0100, 139.0100)

    repository.updateStopsForTrack(1L, isFinal = false)

    // 滞在中は保存しないが、生の現在地が離れているので表示も出さない。
    coVerify(exactly = 0) { stopDao.insert(any()) }
    assertNull(repository.currentStop.value)
  }

  @Test
  fun updateStops_finalizesDwellingWhenIsFinal() = runTest {
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns dwellingPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 30L
    coEvery { stopDao.insert(any()) } returns 300L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    repository.updateStopsForTrack(1L, isFinal = true)

    // 記録終了なら末尾の滞在も確定して保存する。
    coVerify { stopDao.insert(any()) }
    assertNull(repository.currentStop.value)
  }

  // 記録中にユーザーが立ち寄りを削除しても、次の検出で復活しないこと（退行防止）。
  // 検出は境界（最後の確定 departure）以降の点だけを見るため、削除した立ち寄りの区間は
  // スライスに入らず再検出されない。getByTrackAfter は境界を忠実に反映させて検証する。
  @Test
  fun updateStops_afterUserDeletesStopMidRecording_doesNotResurrectIt() = runTest {
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } answers {
      val afterMillis = secondArg<Long>()
      finishedVisitPoints().filter { it.timestamp.time > afterMillis }
    }
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 40L
    coEvery { stopDao.insert(any()) } returns 400L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    // パス1: 確定した立ち寄りを1件保存する（境界が末尾 departure まで進む）。
    repository.updateStopsForTrack(1L, isFinal = false)
    coVerify(exactly = 1) { stopDao.insert(any()) }

    // ユーザーが立ち寄りを削除 → 境界は下がらないので、以降の検出スライスに立ち寄りの区間は
    // 入らず、再挿入されないこと。
    repository.updateStopsForTrack(1L, isFinal = false)
    coVerify(exactly = 1) { stopDao.insert(any()) }
  }

  // 記録中の再解析は「境界（確定済みの過去）以前」だけを候補にする。滞在中・末尾はライブ検出に
  // 任せて重複を防ぎつつ、記録中に誤って消した過去の立ち寄りは候補に出せること。
  @Test
  fun detectMissingStops_whileRecording_capsAtBoundary_offersPastExcludesTail() = runTest {
    val points = twoClustersDwellingAtTail()
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } answers {
      val afterMillis = secondArg<Long>()
      points.filter { it.timestamp.time > afterMillis }
    }
    coEvery { smoothedPointDao.getByTrack(1L) } returns points
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 50L
    coEvery { placeDao.getById(50L) } returns
      PlaceEntity(id = 50L, latitude = 35.01, longitude = 139.01)
    coEvery { stopDao.insert(any()) } returns 500L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { placeResolutionDao.getByPlace(50L) } returns null
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted
    // 生の現在地はクラスタB（滞在中）の中心付近にある＝滞在中。
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gp(35.0100, 139.0100)

    // パス1: ライブ検出でクラスタA（0-240s）を確定し、境界を A.departure(240s) まで進める
    // （末尾のクラスタB は滞在中で未確定＝currentStop）。
    repository.updateStopsForTrack(1L, isFinal = false)
    assertNotNull(repository.currentStop.value)

    // ユーザーが記録中に A を削除した想定（getByTrack は空）。再解析すると…
    val candidates = repository.detectMissingStops(1L)

    // 境界(240s)以前の A だけが候補に出る。滞在中の末尾 B（departure 540s）は出ない。
    assertEquals(1, candidates.size)
    assertEquals(Date(0L), candidates[0].detected.arrivalTime)
    assertEquals(Date(240_000L), candidates[0].detected.departureTime)
  }

  @Test
  fun detectMissingStops_noExisting_returnsAllDetected() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted

    val result = repository.detectMissingStops(1L)

    // 一覧が空なので検出した立ち寄りがそのまま候補になる。永続化はしない。
    assertEquals(1, result.size)
    coVerify(exactly = 0) { stopDao.insert(any()) }
  }

  @Test
  fun detectMissingStops_addsDisplayName_fromPlaces() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("カフェ", "住所", PlaceCategory("cafe", "カフェ・喫茶"), "gp-1", 35.5, 139.5)

    val result = repository.detectMissingStops(1L)

    // 候補には追加前の判断用に表示名が付く（永続化はしない）。
    assertEquals("カフェ", result.single().name)
    assertEquals("gp-1", result.single().googlePlaceId)
    coVerify(exactly = 0) { stopDao.insert(any()) }
    coVerify(exactly = 0) { placeDao.insert(any()) }
  }

  @Test
  fun detectMissingStops_reusesNearbyNamedPlace_withoutCallingPlaces() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    // 候補座標のすぐ近くに命名済みの place がある → それを再利用し Places は叩かない。
    coEvery { placeDao.getNamedPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      NamedPlaceRow(
        id = 7L,
        name = "自宅",
        latitude = 35.0,
        longitude = 139.0,
        googlePlaceId = null,
        googleName = null,
        googleAddress = null,
        googleLatitude = null,
        googleLongitude = null,
        categoryCode = null,
        categoryDisplayName = null,
      ),
    )

    val result = repository.detectMissingStops(1L)

    assertEquals("自宅", result.single().name)
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun detectMissingStops_overlappingExisting_isExcluded() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns listOf(
      StopEntity(id = 1L, placeId = 10L, trackId = 1L, arrivalTime = Date(0), departureTime = Date(240_000)),
    )

    val result = repository.detectMissingStops(1L)

    // 既存の立ち寄りと時間帯が重なる候補は「一覧に有る」として除外される。
    assertTrue(result.isEmpty())
  }

  @Test
  fun detectMissingStops_nonOverlappingExisting_isKept() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns listOf(
      StopEntity(id = 1L, placeId = 10L, trackId = 1L, arrivalTime = Date(10_000_000), departureTime = Date(10_100_000)),
    )
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted

    val result = repository.detectMissingStops(1L)

    // 時間帯が重ならない既存があっても、取りこぼした候補は残す。
    assertEquals(1, result.size)
  }

  @Test
  fun addStops_persistsSelectedAndBakesInName() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    // 検出時に名前を焼き込むので、命名のための Places 呼び出しは不要。
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    val candidate = StopCandidate(
      detected = DetectedStop(35.0, 139.0, Date(0), Date(240_000), pointCount = 5),
      name = "カフェ",
      address = "住所",
      googlePlaceId = "gp-1",
    )
    val added = repository.addStops(1L, listOf(candidate))

    assertEquals(1, added)
    coVerify { stopDao.insert(match { it.trackId == 1L && it.placeId == 10L }) }
    // 検出時に引いた Google データは google_places に焼き込む（places.name はユーザー名専用）。
    coVerify {
      googlePlaceDao.upsert(
        match { it.placeId == 10L && it.googlePlaceId == "gp-1" && it.name == "カフェ" && it.address == "住所" },
      )
    }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun addStops_empty_addsNothing() = runTest {
    val added = repository.addStops(1L, emptyList())

    assertEquals(0, added)
    coVerify(exactly = 0) { stopDao.insert(any()) }
  }

  @Test
  fun addManualStop_typedName_setsNameAndMarksResolved() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList() // findOrCreatePlace で新規作成
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null

    val id = repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "手動カフェ", null)

    assertEquals(100L, id)
    coVerify { stopDao.insert(match { it.trackId == 1L && it.placeId == 10L }) }
    // 手入力の名前はユーザー名として未命名の place に焼き込む。googlePlaceId は無いので google_places は作らない。
    coVerify { placeDao.updateName(10L, "手動カフェ", any()) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    // 手動追加は「ユーザーが決めた」印として解決ログを残す（自動命名で上書きさせない）。
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun addManualStop_poi_bakesInNameAndGooglePlaceId() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    // その googlePlaceId の place はまだ無い → ID 同定で新規作成される。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null

    repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "スタバ", "gp-9")

    // 手入力名はユーザー名（places.name）。POI 由来の googlePlaceId は google_places に控える。
    coVerify { placeDao.updateName(10L, "スタバ", any()) }
    coVerify { googlePlaceDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-9" }) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun addManualStop_poiName_goesToGooglePlacesNotPlaceName() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null

    // POI の名前をそのまま使った（自分では入力していない）ケース。
    repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), null, "gp-9", googleName = "スタバ")

    // Google が付けた名前は google_places.name へ。places.name（自分で付けた名前）は空のまま。
    coVerify { googlePlaceDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-9" && it.name == "スタバ" }) }
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) }
  }

  @Test
  fun addManualStop_ownNameOnFacility_keepsBothNamesAndTheLink() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null

    // 「スタバだけど自分は休憩と呼ぶ」。列が分かれているので両立する。
    repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "休憩", "gp-9", googleName = "スタバ")

    coVerify { placeDao.updateName(10L, "休憩", any()) }
    // 名前を変えても施設との紐付けは外さない（カテゴリ・住所・施設ページが残る）。
    coVerify { googlePlaceDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-9" && it.name == "スタバ" }) }
  }

  @Test
  fun addManualStop_poiAlreadyRegistered_reusesPlaceByGoogleId() = runTest {
    // 既に同じ googlePlaceId の place(77) がある → 施設の同一性で再利用し、新規作成しない。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns 77L
    coEvery { placeDao.getById(77L) } returns
      PlaceEntity(id = 77L, name = "スタバ", latitude = 35.0, longitude = 139.0, source = "USER")
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeResolutionDao.getByPlace(77L) } returns PlaceResolutionEntity(77L, Date(0))

    repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "スタバ", "gp-9")

    coVerify { stopDao.insert(match { it.placeId == 77L }) }
    coVerify(exactly = 0) { placeDao.insert(any()) } // 座標で新規作成しない（ID同定で再利用）
  }

  @Test
  fun addManualStop_noName_leavesUnnamedButMarksResolved() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null

    repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), null, null)

    // 名前無しの手動追加は place を未命名のまま残すが、自動命名で近くの別施設名に化けないよう
    // 解決ログは残す（未命名のまま維持。あとで手動「場所を取得」で命名可）。
    coVerify { stopDao.insert(any()) }
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
  }

  @Test
  fun addManualStop_reusesNearbyPlace_doesNotOverwriteExistingName() = runTest {
    // すぐ近く（同座標）に命名済みの place がある → 再利用し、名前は上書きしない。
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns listOf(
      PlaceEntity(id = 7L, name = "自宅", latitude = 35.0, longitude = 139.0),
    )
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(7L) } returns PlaceEntity(id = 7L, name = "自宅", latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(7L) } returns null

    repository.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "別名", null)

    coVerify { stopDao.insert(match { it.placeId == 7L }) }
    coVerify(exactly = 0) { placeDao.insert(any()) } // 新規作成しない
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) } // 命名済みは守る
  }

  @Test
  fun resolveUnresolvedNames_noMatch_recordsNullRow() = runTest {
    coEvery { placeDao.getPlacesWithoutGoogleIdForTrack(1L) } returns listOf(
      PlaceEntity(id = 5L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NoMatch

    repository.resolveUnresolvedNames(1L)

    coVerify { placeResolutionDao.upsert(match { it.placeId == 5L }) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun resolveUnresolvedNames_offline_recordsNoRow() = runTest {
    coEvery { placeDao.getPlacesWithoutGoogleIdForTrack(1L) } returns listOf(
      PlaceEntity(id = 6L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted

    repository.resolveUnresolvedNames(1L)

    // 未実施は行を作らない（後でキャッチアップ）。
    coVerify(exactly = 0) { placeResolutionDao.upsert(any()) }
  }

  private fun stopEntity(id: Long, placeId: Long) = StopEntity(
    id = id,
    placeId = placeId,
    trackId = 1L,
    arrivalTime = Date(0L),
    departureTime = Date(60_000L),
  )

  @Test
  fun reassignStopPlace_withChosenPoi_repointsStopAndRecyclesOldDetected() = runTest {
    // stop 100 は元 place 10（検出由来・孤立）。選んだ POI(gp-9) の新 place(88) へ付け替える。
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null
    coEvery { placeDao.insert(any()) } returns 88L
    coEvery { googlePlaceDao.getByPlace(88L) } returns null
    coEvery { placeResolutionDao.getByPlace(88L) } returns null
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0

    val chosen = PlaceSearchResult("gp-9", "日高屋", "住所", PlaceCategory("chinese_restaurant", "中華料理"), 35.02, 139.02)
    repository.reassignStopPlace(100L, chosen, null)

    coVerify { stopDao.updatePlace(100L, 88L) } // この訪問だけ付け替え
    coVerify { googlePlaceDao.upsert(match { it.placeId == 88L && it.googlePlaceId == "gp-9" }) }
    coVerify { placeDao.deleteById(10L) } // 孤立した検出由来の元 place を回収
  }

  @Test
  fun reassignStopPlace_withCustomName_createsNewUserPlace() = runTest {
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")
    coEvery { placeDao.insert(any()) } returns 88L
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0

    repository.reassignStopPlace(100L, null, "日高屋")

    // 手入力名は新しい USER 場所として作る（座標同定せず分離）。
    coVerify { placeDao.insert(match { it.name == "日高屋" && it.source == "USER" }) }
    coVerify { stopDao.updatePlace(100L, 88L) }
    coVerify { placeDao.deleteById(10L) }
  }

  @Test
  fun deleteStops_orphanedDetectedPlaceIsDeleted_sharedPlaceIsKept() = runTest {
    // stop 100 -> place 10（検出由来・他に訪問なし＝孤立）, stop 200 -> place 20（他にも訪問が残る）
    coEvery { stopDao.getByIds(listOf(100L, 200L)) } returns listOf(stopEntity(100L, 10L), stopEntity(200L, 20L))
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { stopDao.countByPlace(20L) } returns 1
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")

    val result = repository.deleteStops(listOf(100L, 200L))

    coVerify { stopDao.deleteByIds(listOf(100L, 200L)) }
    coVerify { placeDao.deleteById(10L) } // 検出由来で孤立した場所ごと削除
    coVerify(exactly = 0) { placeDao.deleteById(20L) } // 他訪問が残る場所は保持
    assertEquals(2, result.stopsDeleted)
    assertEquals(1, result.placesDeleted)
    assertEquals(1, result.placesKept)
  }

  @Test
  fun deleteStops_orphanedUserPlace_isKept() = runTest {
    // ユーザーが明示的に登録した場所（USER）は、参照ゼロで孤立しても自動では消さない。
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, name = "手動の場所", latitude = 35.0, longitude = 139.0, source = "USER")

    val result = repository.deleteStops(listOf(100L))

    coVerify(exactly = 0) { placeDao.deleteById(10L) } // USER 由来は保持
    assertEquals(0, result.placesDeleted)
    assertEquals(1, result.placesKept)
  }

  @Test
  fun deleteStops_placeOnWishlist_isKeptEvenWithoutRemainingStops() = runTest {
    // stop 100 -> place 10。stop は無くなるが行きたい登録があるので場所は残す。
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 1

    val result = repository.deleteStops(listOf(100L))

    coVerify { stopDao.deleteByIds(listOf(100L)) }
    coVerify(exactly = 0) { placeDao.deleteById(10L) } // wishlist を巻き込まない
    assertEquals(0, result.placesDeleted)
    assertEquals(1, result.placesKept)
  }

  @Test
  fun deleteStops_emptyList_isNoOp() = runTest {
    val result = repository.deleteStops(emptyList())

    coVerify(exactly = 0) { stopDao.deleteByIds(any()) }
    assertEquals(0, result.stopsDeleted)
  }

  @Test
  fun undoLastStopChange_afterDeletion_restoresStopsAndOrphanedPlace() = runTest {
    // 孤立 place を回収する削除 → 取り消しで place・Google データ・解決ログ・stop を元IDのまま復元。
    val stop = stopEntity(100L, 10L)
    val place = PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")
    val resolution = PlaceResolutionEntity(10L, Date(0L))
    val google = GooglePlaceEntity(10L, "gp-1", "カフェ", "住所")
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stop)
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0
    coEvery { placeDao.getById(10L) } returns place
    coEvery { placeResolutionDao.getByPlace(10L) } returns resolution
    coEvery { googlePlaceDao.getByPlace(10L) } returns google

    repository.deleteStops(listOf(100L))
    val undone = repository.undoLastStopChange()

    assertTrue(undone)
    coVerify { placeDao.insert(place) }
    coVerify { googlePlaceDao.upsert(google) }
    coVerify { placeResolutionDao.upsert(resolution) }
    coVerify { stopDao.insert(stop) }
  }

  @Test
  fun undoLastStopChange_withoutPriorChange_returnsFalse() = runTest {
    assertFalse(repository.undoLastStopChange())
  }

  // --- 滞在期間の編集・立ち寄りの手動統合（adr/0024）---

  private fun stopEntity(id: Long, placeId: Long, arrivalMillis: Long, departureMillis: Long, note: String? = null) = StopEntity(
    id = id,
    placeId = placeId,
    trackId = 1L,
    arrivalTime = Date(arrivalMillis),
    departureTime = Date(departureMillis),
    note = note,
  )

  @Test
  fun updateStopDuration_writesGivenRange() = runTest {
    repository.updateStopDuration(100L, Date(1_000L), Date(5_000L))

    coVerify { stopDao.updateDuration(100L, Date(1_000L), Date(5_000L)) }
  }

  @Test
  fun updateStopDuration_arrivalNotBeforeDeparture_isIgnored() = runTest {
    // 到着＝出発（滞在0分）と逆転は押し間違いでしか作れないので書かない。
    repository.updateStopDuration(100L, Date(5_000L), Date(5_000L))
    repository.updateStopDuration(100L, Date(5_000L), Date(1_000L))

    coVerify(exactly = 0) { stopDao.updateDuration(any(), any(), any()) }
  }

  @Test
  fun mergeStops_samePlace_keepsEarliestAndCoversWholeRange() = runTest {
    // 到着が最も早い 100 を残し、期間は 100 の到着〜101 の出発まで広げる。101 は消す。
    val first = stopEntity(100L, 10L, 0L, 60_000L)
    val second = stopEntity(101L, 10L, 120_000L, 300_000L)
    coEvery { stopDao.getByIds(listOf(100L, 101L)) } returns listOf(second, first)

    val result = repository.mergeStops(listOf(100L, 101L))

    assertEquals(100L, result?.survivingStopId)
    assertEquals(2, result?.mergedCount)
    coVerify { stopDao.deleteByIds(listOf(101L)) }
    coVerify { stopDao.update(first.copy(arrivalTime = Date(0L), departureTime = Date(300_000L))) }
  }

  @Test
  fun mergeStops_concatenatesNotesInArrivalOrder() = runTest {
    val first = stopEntity(100L, 10L, 0L, 60_000L, note = "入口")
    val second = stopEntity(101L, 10L, 120_000L, 300_000L, note = "  ゾウ舎  ")
    val third = stopEntity(102L, 10L, 400_000L, 500_000L, note = "   ")
    coEvery { stopDao.getByIds(listOf(100L, 101L, 102L)) } returns listOf(third, second, first)

    repository.mergeStops(listOf(100L, 101L, 102L))

    // 空白だけのメモは落とし、残りを到着順に連結する。
    coVerify {
      stopDao.update(
        first.copy(arrivalTime = Date(0L), departureTime = Date(500_000L), note = "入口\nゾウ舎"),
      )
    }
  }

  @Test
  fun mergeStops_differentPlaces_isRefused() = runTest {
    // 場所が混ざるとどれが正か決められないので、まとめずに null を返す。
    coEvery { stopDao.getByIds(listOf(100L, 101L)) } returns listOf(
      stopEntity(100L, 10L, 0L, 60_000L),
      stopEntity(101L, 11L, 120_000L, 300_000L),
    )

    assertNull(repository.mergeStops(listOf(100L, 101L)))

    coVerify(exactly = 0) { stopDao.deleteByIds(any()) }
    coVerify(exactly = 0) { stopDao.update(any()) }
  }

  @Test
  fun mergeStops_singleStop_isNoOp() = runTest {
    assertNull(repository.mergeStops(listOf(100L)))

    coVerify(exactly = 0) { stopDao.getByIds(any()) }
    coVerify(exactly = 0) { stopDao.deleteByIds(any()) }
  }

  @Test
  fun undoLastStopChange_afterMerge_restoresAbsorbedAndRevertsSurvivor() = runTest {
    val first = stopEntity(100L, 10L, 0L, 60_000L)
    val second = stopEntity(101L, 10L, 120_000L, 300_000L)
    coEvery { stopDao.getByIds(listOf(100L, 101L)) } returns listOf(first, second)

    repository.mergeStops(listOf(100L, 101L))
    val undone = repository.undoLastStopChange()

    assertTrue(undone)
    coVerify { stopDao.insert(second) } // 吸収した側は元IDのまま戻す
    coVerify { stopDao.update(first) } // 残した側は変更前の期間・メモへ戻す
    // 場所は全員同じで残った1件が参照し続けるので、回収も復元も起きない。
    coVerify(exactly = 0) { placeDao.insert(any()) }
  }

  @Test
  fun updatePlaceName_blankBecomesNull() = runTest {
    repository.updatePlaceName(5L, "   ")
    coVerify { placeDao.updateName(5L, null, any()) }
  }

  @Test
  fun updatePlaceName_trimsWhitespace() = runTest {
    repository.updatePlaceName(5L, "  カフェ  ")
    coVerify { placeDao.updateName(5L, "カフェ", any()) }
  }

  @Test
  fun updateStopNote_trimsWhitespace() = runTest {
    repository.updateStopNote(100L, "  限定パフェが美味しかった  ")
    coVerify { stopDao.updateNote(100L, "限定パフェが美味しかった") }
  }

  @Test
  fun updateStopNote_blankBecomesNull() = runTest {
    repository.updateStopNote(100L, "   ")
    coVerify { stopDao.updateNote(100L, null) }
  }

  @Test
  fun resolveAllUnresolvedNames_propagatesFailure_soCallerCanRetry() = runTest {
    // ここで例外を握り潰すと、WorkManager は「成功」と受け取って再試行しない。
    // オンライン復帰後のキャッチアップが目的なので、失敗は呼び出し元まで伝える必要がある。
    coEvery { placeDao.getUnresolvedPlaces() } throws IllegalStateException("db down")

    val thrown = runCatching { repository.resolveAllUnresolvedNames() }.exceptionOrNull()

    assertTrue("呼び出し元へ例外が伝わる（実際は $thrown）", thrown is IllegalStateException)
  }

  @Test
  fun resolveAllUnresolvedNames_whenResolverFails_propagates() = runTest {
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 30L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } throws IllegalStateException("network down")

    val thrown = runCatching { repository.resolveAllUnresolvedNames() }.exceptionOrNull()

    assertTrue("解決中の失敗も伝わる（実際は $thrown）", thrown is IllegalStateException)
  }

  @Test
  fun resolveAllUnresolvedNames_resolvesEachUnresolvedPlaceAndKeepsAnchor() = runTest {
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 30L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(35.0, 139.0) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", PlaceCategory("shinto_shrine", "神社"), "gp-30", 35.7, 139.7)

    repository.resolveAllUnresolvedNames()

    // 施設の座標は google_places に入れる（表示用）。places のアンカーは動かさない（adr/0023）。
    coVerify {
      googlePlaceDao.upsert(
        match {
          it.placeId == 30L &&
            it.googlePlaceId == "gp-30" &&
            it.name == "神社" &&
            it.latitude == 35.7 &&
            it.longitude == 139.7
        },
      )
    }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 30L }) }
  }

  @Test
  fun resolve_whenSameFacilityAlreadyHasPlace_mergesUntouchedDetectedPlace() = runTest {
    // 座標では見つけられなかったが、解決したら既存 place と同じ施設だった → そちらへ寄せる。
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 30L, latitude = 35.0, longitude = 139.0, source = PlaceSource.DETECTED.name),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L
    coEvery { wishlistDao.countByPlace(30L) } returns 0
    coEvery { visitedPlaceDao.countByPlace(30L) } returns 0

    repository.resolveAllUnresolvedNames()

    // 参照を移してから消す。新しい行に Google データは書かない（寄せ先が既に持っている）。
    coVerify { stopDao.repointPlace(30L, 7L) }
    coVerify { placeDao.deleteById(30L) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun resolve_whenUserPlaceHitsSameFacility_keepsItSeparate() = runTest {
    // 地図で指して作った place（USER）は、中身が空でもまとめない。指したこと自体が
    // 「ここを別の場所として残したい」という意思表示だから（→ adr/0025）。
    val place = PlaceEntity(id = 31L, latitude = 35.0, longitude = 139.0, source = PlaceSource.USER.name)
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(place)
    coEvery { placeDao.getById(31L) } returns place
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L

    repository.resolveAllUnresolvedNames()

    coVerify(exactly = 0) { stopDao.repointPlace(any(), any()) }
    coVerify(exactly = 0) { placeDao.deleteById(any()) }
    // 別の場所として残すが、同じ施設を2つに持たせられないので施設情報は付かない。
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 31L }) }
  }

  @Test
  fun resolve_whenPlaceWasTouchedByUser_doesNotMerge() = runTest {
    // 自分で名前を付けた place は、同じ施設に解決されても吸収しない（誤解決で融合させない）。
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 31L, name = "いつもの神社", latitude = 35.0, longitude = 139.0, source = PlaceSource.DETECTED.name),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L

    repository.resolveAllUnresolvedNames()

    coVerify(exactly = 0) { stopDao.repointPlace(any(), any()) }
    coVerify(exactly = 0) { placeDao.deleteById(any()) }
    // 寄せられないうえに同じ施設を2つ持たせるわけにもいかないので、施設情報は付けない（→ adr/0025）。
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    // 同じ問い合わせを繰り返さないよう、解決記録は残す。
    coVerify { placeResolutionDao.upsert(match { it.placeId == 31L }) }
  }

  @Test
  fun findNearbyPlace_returnsNearestWithinDetectionRadius() = runTest {
    coEvery { placeDao.getRegisteredPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      RegisteredPlaceRow(1L, "同じ地点", 35.0, 139.0, 0, 0, null, null),
      RegisteredPlaceRow(2L, "約1km先", 35.01, 139.0, 0, 0, null, null),
    )

    val near = repository.findNearbyPlace(35.0, 139.0)

    assertEquals(1L, near?.placeId)
  }

  @Test
  fun findNearbyPlace_noneWithinRadius_returnsNull() = runTest {
    coEvery { placeDao.getRegisteredPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      RegisteredPlaceRow(2L, "約1km先", 35.01, 139.0, 0, 0, null, null),
    )

    assertNull(repository.findNearbyPlace(35.0, 139.0))
  }

  @Test
  fun addManualStop_forceNewPlace_insertsNewPlaceWithoutCoordinateDedup() = runTest {
    coEvery { placeDao.insert(any()) } returns 42L
    coEvery { stopDao.insert(any()) } returns 100L

    repository.addManualStop(1L, 35.0, 139.0, Date(), Date(), null, null, forceNewPlace = true)

    // 座標同定（近傍30mの再利用）を通らず、新しい place を挿入する。
    coVerify { placeDao.insert(match { it.latitude == 35.0 && it.longitude == 139.0 }) }
    coVerify(exactly = 0) { placeDao.getInBounds(any(), any(), any(), any()) }
    coVerify { stopDao.insert(match { it.placeId == 42L }) }
  }
}
