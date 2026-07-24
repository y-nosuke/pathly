package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class PlaceRepositoryImplTest {

  private val placeDao = mockk<PlaceDao>(relaxed = true)
  private val stopDao = mockk<StopDao>(relaxed = true)
  private val smoothedPointDao = mockk<SmoothedPointDao>(relaxed = true)
  private val placeResolutionDao = mockk<PlaceResolutionDao>(relaxed = true)
  private val resolver = mockk<PlacesNameResolver>(relaxed = true)
  private val repository = PlaceRepositoryImpl(
    placeDao,
    stopDao,
    smoothedPointDao,
    placeResolutionDao,
    resolver,
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

  @Test
  fun updateStops_finalizedVisit_persistsStopAndResolvesName() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.countByTrack(1L) } returns 0
    coEvery { placeDao.getAll() } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns listOf(
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("カフェ", "住所", "gp-1")

    repository.updateStopsForTrack(1L, isFinal = false)

    val stopSlot = slot<StopEntity>()
    coVerify { stopDao.insert(capture(stopSlot)) }
    assertEquals(10L, stopSlot.captured.placeId)
    assertEquals(1L, stopSlot.captured.trackId)
    coVerify { placeDao.updateNameAndAddress(10L, "カフェ", "住所", any()) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-1" }) }
    // 離脱済みなので「立ち寄り中」は無い。
    assertNull(repository.currentStop.value)
  }

  @Test
  fun updateStops_dwelling_setsCurrentStopWithoutPersistingStop() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns dwellingPoints()
    coEvery { stopDao.countByTrack(1L) } returns 0
    coEvery { placeDao.getAll() } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 20L
    coEvery { placeDao.getById(20L) } returns
      PlaceEntity(id = 20L, name = "カフェ", latitude = 35.0, longitude = 139.0)
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { placeResolutionDao.getByPlace(20L) } returns null
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("カフェ", "住所", "gp-2")

    repository.updateStopsForTrack(1L, isFinal = false)

    // 滞在中は stop を保存しない。place は先行確定し「立ち寄り中」に出る。
    coVerify(exactly = 0) { stopDao.insert(any()) }
    val current = repository.currentStop.value
    assertNotNull(current)
    assertEquals(20L, current!!.place.id)
    coVerify { placeResolutionDao.upsert(match { it.placeId == 20L && it.googlePlaceId == "gp-2" }) }
  }

  @Test
  fun updateStops_finalizesDwellingWhenIsFinal() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns dwellingPoints()
    coEvery { stopDao.countByTrack(1L) } returns 0
    coEvery { placeDao.getAll() } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 30L
    coEvery { stopDao.insert(any()) } returns 300L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    repository.updateStopsForTrack(1L, isFinal = true)

    // 記録終了なら末尾の滞在も確定して保存する。
    coVerify { stopDao.insert(any()) }
    assertNull(repository.currentStop.value)
  }

  @Test
  fun redetectStops_deletesThenDetects() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.countByTrack(1L) } returns 0
    coEvery { placeDao.getAll() } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    repository.redetectStops(1L)

    coVerify { stopDao.deleteByTrack(1L) }
    coVerify { stopDao.insert(any()) }
  }

  @Test
  fun resolveUnresolvedNames_noMatch_recordsNullRow() = runTest {
    coEvery { placeDao.getPlacesWithoutGoogleIdForTrack(1L) } returns listOf(
      PlaceEntity(id = 5L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NoMatch

    repository.resolveUnresolvedNames(1L)

    coVerify { placeResolutionDao.upsert(match { it.placeId == 5L && it.googlePlaceId == null }) }
    coVerify(exactly = 0) { placeDao.updateNameAndAddress(any(), any(), any(), any()) }
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
}
