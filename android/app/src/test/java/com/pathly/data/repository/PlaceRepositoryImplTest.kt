package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class PlaceRepositoryImplTest {

  private val placeDao = mockk<PlaceDao>(relaxed = true)
  private val stopDao = mockk<StopDao>(relaxed = true)
  private val resolver = mockk<PlacesNameResolver>(relaxed = true)
  private val repository = PlaceRepositoryImpl(placeDao, stopDao, resolver)

  private fun point(lat: Double, lon: Double, timeSec: Long) = GpsPoint(
    id = 0,
    trackId = 1L,
    latitude = lat,
    longitude = lon,
    altitude = null,
    accuracy = 5f,
    speed = null,
    bearing = null,
    timestamp = Date(timeSec * 1000L),
    createdAt = Date(0L),
  )

  // 50m圏内・4分滞在 → 立ち寄り1件
  private fun stationaryTrack() = GpsTrack(
    id = 1L,
    startTime = Date(0L),
    endTime = Date(240_000L),
    isActive = false,
    points = listOf(
      point(35.0000, 139.0000, 0),
      point(35.0002, 139.0000, 60),
      point(35.0000, 139.0002, 120),
      point(35.0001, 139.0000, 180),
      point(35.0002, 139.0001, 240),
    ),
    createdAt = Date(0L),
    updatedAt = Date(0L),
  )

  @Test
  fun ensureStopsDetected_whenNoStops_createsPlaceAndStopThenResolvesName() = runTest {
    coEvery { stopDao.countByTrack(1L) } returns 0
    coEvery { placeDao.getAll() } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getUnnamedPlacesForTrack(1L) } returns listOf(
      PlaceEntity(id = 10L, latitude = 35.0001, longitude = 139.0001),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Result("カフェ", "住所")

    repository.ensureStopsDetected(stationaryTrack())

    val stopSlot = slot<StopEntity>()
    coVerify { placeDao.insert(any()) }
    coVerify { stopDao.insert(capture(stopSlot)) }
    assertEquals(10L, stopSlot.captured.placeId)
    assertEquals(1L, stopSlot.captured.trackId)
    coVerify { placeDao.updateNameAndAddress(10L, "カフェ", "住所", any()) }
  }

  @Test
  fun ensureStopsDetected_whenStopsExist_skipsDetectionAndNaming() = runTest {
    coEvery { stopDao.countByTrack(1L) } returns 2

    repository.ensureStopsDetected(stationaryTrack())

    // 保存済みなら検出も命名も再実行しない（開き直しで再評価・再課金しない）。
    coVerify(exactly = 0) { stopDao.insert(any()) }
    coVerify(exactly = 0) { placeDao.insert(any()) }
    coVerify(exactly = 0) { placeDao.getUnnamedPlacesForTrack(any()) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun ensureStopsDetected_reusesNearbyExistingPlace() = runTest {
    coEvery { stopDao.countByTrack(1L) } returns 0
    // 立ち寄り重心のすぐ近くに既存の場所 → 再利用（新規作成しない）
    coEvery { placeDao.getAll() } returns listOf(
      PlaceEntity(id = 7L, latitude = 35.0001, longitude = 139.0001),
    )
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getUnnamedPlacesForTrack(1L) } returns emptyList()

    repository.ensureStopsDetected(stationaryTrack())

    val stopSlot = slot<StopEntity>()
    coVerify(exactly = 0) { placeDao.insert(any()) }
    coVerify { stopDao.insert(capture(stopSlot)) }
    assertEquals(7L, stopSlot.captured.placeId)
  }

  @Test
  fun resolveMissingNames_namesUnnamedPlaces() = runTest {
    coEvery { placeDao.getUnnamedPlacesForTrack(1L) } returns listOf(
      PlaceEntity(id = 5L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Result("名前", "住所")

    repository.resolveMissingNames(1L)

    coVerify { placeDao.updateNameAndAddress(5L, "名前", "住所", any()) }
  }

  @Test
  fun resolveMissingNames_whenPlacesReturnsNull_keepsUnnamed() = runTest {
    coEvery { placeDao.getUnnamedPlacesForTrack(1L) } returns listOf(
      PlaceEntity(id = 5L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns null

    repository.resolveMissingNames(1L)

    coVerify(exactly = 0) { placeDao.updateNameAndAddress(any(), any(), any(), any()) }
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
