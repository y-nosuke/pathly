package com.pathly.data.repository

import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.PlaceCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class LiveStopRepositoryImplTest : PlaceStopRepositoryTestBase() {

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

    liveStops.updateStopsForTrack(1L, isFinal = false)

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
    assertNull(liveStops.currentStop.value)
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

    liveStops.updateStopsForTrack(1L, isFinal = false)

    // 滞在中は stop を保存しない。place は先行確定し「立ち寄り中」に出る。
    coVerify(exactly = 0) { stopDao.insert(any()) }
    val current = liveStops.currentStop.value
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

    repeat(5) { liveStops.updateStopsForTrack(1L, isFinal = false) }

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

    liveStops.updateStopsForTrack(1L, isFinal = false)
    liveStops.updateStopsForTrack(1L, isFinal = false)

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

    liveStops.updateStopsForTrack(1L, isFinal = false)

    // 滞在中は保存しないが、生の現在地が離れているので表示も出さない。
    coVerify(exactly = 0) { stopDao.insert(any()) }
    assertNull(liveStops.currentStop.value)
  }

  // 「今ここを立ち寄り」: 押したらすぐ立ち寄り中になり、3 分待たない。保存はまだしない。
  @Test
  fun startLiveStop_showsCurrentStopAtOnce_withoutPersisting() = runTest {
    val stored = storePlaces()

    startHere(arrivalSec = 30)

    val current = liveStops.currentStop.value
    assertNotNull(current)
    assertEquals(stored.single().id, current!!.place.id)
    assertEquals(Date(30_000L), current.arrivalTime)
    coVerify(exactly = 0) { stopDao.insert(any()) }
  }

  // 手で始めた立ち寄り中のあいだは、検出の立ち寄り中を出さず、検出用の place も確保しない。
  // 離れて 1 分たったら、押した時刻〜最後に半径の中にいた時刻で確定し、同じ滞在を検出した立ち寄りは保存しない。
  @Test
  fun startLiveStop_thenLeave_persistsOnceFromPressTime_andSkipsDetectedDuplicate() = runTest {
    val stored = storePlaces()
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returnsMany
      listOf(dwellingPoints(), finishedVisitPoints(), finishedVisitPoints())
    coEvery { gpsPointDao.getLatestPoint(1L) } returnsMany
      listOf(gpAt(35.0, 139.0, 240), gpAt(35.01, 139.01, 300), gpAt(35.01, 139.01, 360))

    startHere(arrivalSec = 30)
    liveStops.updateStopsForTrack(1L, isFinal = false) // まだ中にいる
    assertEquals("検出の立ち寄り中に place を作らない", 1, stored.size)
    assertEquals(stored.single().id, liveStops.currentStop.value?.place?.id)

    liveStops.updateStopsForTrack(1L, isFinal = false) // 外に出た（まだ確定しない）
    coVerify(exactly = 0) { stopDao.insert(any()) }
    liveStops.updateStopsForTrack(1L, isFinal = false) // 外にいるのが 1 分続いた

    val stopSlot = slot<StopEntity>()
    coVerify(exactly = 1) { stopDao.insert(capture(stopSlot)) }
    assertEquals(stored.single().id, stopSlot.captured.placeId)
    assertEquals(Date(30_000L), stopSlot.captured.arrivalTime)
    assertEquals(Date(240_000L), stopSlot.captured.departureTime)
    assertNull(liveStops.currentStop.value)
  }

  // 押してすぐ（3 分未満）離れても、自分で押したので立ち寄りとして残す。
  @Test
  fun startLiveStop_leftWithinMinutes_isStillKept() = runTest {
    storePlaces()
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns emptyList()
    coEvery { gpsPointDao.getLatestPoint(1L) } returnsMany listOf(gpAt(35.01, 139.01, 60), gpAt(35.01, 139.01, 120))

    startHere(arrivalSec = 30)
    liveStops.updateStopsForTrack(1L, isFinal = false)
    liveStops.updateStopsForTrack(1L, isFinal = false)

    val stopSlot = slot<StopEntity>()
    coVerify(exactly = 1) { stopDao.insert(capture(stopSlot)) }
    assertEquals(Date(30_000L), stopSlot.captured.departureTime)
  }

  // 屋内の GPS の飛びで 1 点だけ外に出ても、すぐ中に戻れば確定しない。
  @Test
  fun startLiveStop_singleJumpOutside_doesNotFinalize() = runTest {
    storePlaces()
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns emptyList()
    coEvery { gpsPointDao.getLatestPoint(1L) } returnsMany
      listOf(gpAt(35.01, 139.01, 60), gpAt(35.0, 139.0, 90), gpAt(35.01, 139.01, 130))

    startHere(arrivalSec = 30)
    repeat(3) { liveStops.updateStopsForTrack(1L, isFinal = false) }

    coVerify(exactly = 0) { stopDao.insert(any()) }
    assertEquals(Date(90_000L), liveStops.currentStop.value?.departureTime)
  }

  // 選び直したら、前にこの立ち寄り中のために作った場所は（誰も使っていなければ）片付ける。
  @Test
  fun startLiveStop_repick_removesPlaceMadeForIt() = runTest {
    val stored = storePlaces()
    coEvery { stopDao.countByPlace(any()) } returns 0
    coEvery { wishlistDao.countByPlace(any()) } returns 0
    startHere(arrivalSec = 30, name = "売店")
    val first = stored.single().id
    liveStops.startLiveStop(1L, 35.0, 139.0, Date(200_000L), 35.001, 139.0, "カフェ", null, null)

    coVerify { placeDao.deleteById(first) }
  }

  // 名前も施設も選ばなかったときは、解決ログを書かない（あとで自動で名前が付く）。
  @Test
  fun startLiveStop_withoutNameOrFacility_leavesPlaceForAutoNaming() = runTest {
    storePlaces()
    startHere(arrivalSec = 30, name = null)

    coVerify(exactly = 0) { placeResolutionDao.upsert(any()) }
  }

  // 立ち寄り中に付けたメモ・直した到着は、確定した立ち寄りに入る。
  @Test
  fun liveStop_noteAndArrival_goIntoPersistedStop() = runTest {
    storePlaces()
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns emptyList()
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gpAt(35.0, 139.0, 500)

    startHere(arrivalSec = 30)
    liveStops.updateLiveStopNote(1L, "  ケーキがおいしい  ")
    liveStops.updateLiveStopArrival(1L, Date(10_000L))
    assertEquals("ケーキがおいしい", liveStops.currentStop.value?.note)
    liveStops.updateStopsForTrack(1L, isFinal = true)

    val stopSlot = slot<StopEntity>()
    coVerify(exactly = 1) { stopDao.insert(capture(stopSlot)) }
    assertEquals("ケーキがおいしい", stopSlot.captured.note)
    assertEquals(Date(10_000L), stopSlot.captured.arrivalTime)
  }

  // 立ち寄り中の場所の名前を変えたら、次の位置の更新を待たずに表示が変わる。
  @Test
  fun updatePlaceName_ofLiveStopPlace_refreshesCurrentStopAtOnce() = runTest {
    val stored = storePlaces()
    coEvery { placeDao.updateName(any(), any(), any()) } answers {
      val id = firstArg<Long>()
      val index = stored.indexOfFirst { it.id == id }
      stored[index] = stored[index].copy(name = secondArg())
    }
    startHere(arrivalSec = 30, name = "売店")

    places.updatePlaceName(stored.single().id, "カフェ")

    assertEquals("カフェ", liveStops.currentStop.value?.place?.name)
  }

  // 検出の立ち寄り中をやめると、表示が消え、離れても記録を止めても保存しない。
  @Test
  fun dismissLiveStop_detected_hidesAndNeverPersists() = runTest {
    storePlaces()
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns dwellingPoints()
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gpAt(35.0, 139.0, 240)
    liveStops.updateStopsForTrack(1L, isFinal = false)
    assertNotNull(liveStops.currentStop.value)

    liveStops.dismissLiveStop(1L)
    assertNull(liveStops.currentStop.value)
    liveStops.updateStopsForTrack(1L, isFinal = false)
    assertNull("やめたあとは出し直さない", liveStops.currentStop.value)
    liveStops.updateStopsForTrack(1L, isFinal = true)

    coVerify(exactly = 0) { stopDao.insert(any()) }
  }

  // 記録を止めたときに立ち寄り中なら、その時刻（最後の点）で確定する。
  @Test
  fun startLiveStop_finalizedWhenRecordingStops() = runTest {
    storePlaces()
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns emptyList()
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gpAt(35.0, 139.0, 500)

    startHere(arrivalSec = 30)
    liveStops.updateStopsForTrack(1L, isFinal = true)

    val stopSlot = slot<StopEntity>()
    coVerify(exactly = 1) { stopDao.insert(capture(stopSlot)) }
    assertEquals(Date(500_000L), stopSlot.captured.departureTime)
    assertNull(liveStops.currentStop.value)
  }

  // すでに立ち寄り中のときに押すと、到着はそのままに場所だけ選び直す。
  @Test
  fun startLiveStop_whileAlreadyStopping_keepsArrivalAndChangesPlace() = runTest {
    val stored = storePlaces()
    startHere(arrivalSec = 30, name = "売店")
    // 2 回目は別の座標の施設を選んだ（30m より離れているので別の場所になる）。
    liveStops.startLiveStop(
      trackId = 1L,
      anchorLatitude = 35.0,
      anchorLongitude = 139.0,
      arrivalTime = Date(200_000L),
      latitude = 35.001,
      longitude = 139.0,
      name = "カフェ",
      googlePlaceId = null,
      googleName = null,
    )

    val current = liveStops.currentStop.value
    assertEquals(2, stored.size)
    assertEquals(stored.last().id, current?.place?.id)
    assertEquals(Date(30_000L), current?.arrivalTime)
  }

  @Test
  fun updateStops_finalizesDwellingWhenIsFinal() = runTest {
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } returns dwellingPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 30L
    coEvery { stopDao.insert(any()) } returns 300L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    liveStops.updateStopsForTrack(1L, isFinal = true)

    // 記録終了なら末尾の滞在も確定して保存する。
    coVerify { stopDao.insert(any()) }
    assertNull(liveStops.currentStop.value)
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
    liveStops.updateStopsForTrack(1L, isFinal = false)
    coVerify(exactly = 1) { stopDao.insert(any()) }

    // ユーザーが立ち寄りを削除 → 境界は下がらないので、以降の検出スライスに立ち寄りの区間は
    // 入らず、再挿入されないこと。
    liveStops.updateStopsForTrack(1L, isFinal = false)
    coVerify(exactly = 1) { stopDao.insert(any()) }
  }
}
