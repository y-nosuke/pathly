package com.pathly.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.StopEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/** 場所の地域（places の列）を引いて書き足す問い合わせと、一覧への出方（→ adr/0029）。 */
@RunWith(AndroidJUnit4::class)
class PlaceRegionDaoTest {

  private lateinit var database: PathlyDatabase
  private lateinit var regionDao: PlaceRegionDao

  @Before
  fun setup() {
    database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PathlyDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    regionDao = database.placeRegionDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  private suspend fun place(lat: Double = 35.68, lng: Double = 139.76): Long = database.placeDao().insert(PlaceEntity(latitude = lat, longitude = lng))

  @Test
  fun newPlaces_areUnresolved_untilRegionIsWritten() = runTest {
    val a = place()
    val b = place(34.98, 135.76)

    assertEquals(listOf(a, b), regionDao.getUnresolved(0, 10).map { it.placeId })
    assertEquals(listOf(b), regionDao.getUnresolved(a, 10).map { it.placeId })
    assertEquals(2, regionDao.observeUnresolvedCount().first())

    regionDao.updateRegion(a, "JP", "東京都", "千代田区", Date())

    assertEquals(listOf(b), regionDao.getUnresolved(0, 10).map { it.placeId })
    assertEquals(1, regionDao.observeUnresolvedCount().first())
  }

  @Test
  fun unknownRegion_isStillResolved_soItIsNotLookedUpAgain() = runTest {
    val sea = place(30.0, 140.0)

    regionDao.updateRegion(sea, null, null, null, Date())

    assertEquals(emptyList<UnresolvedRegionRow>(), regionDao.getUnresolved(0, 10))
  }

  @Test
  fun writingRegion_doesNotTouchUpdatedAt() = runTest {
    val id = place()
    val before = database.placeDao().getById(id)!!.updatedAt

    regionDao.updateRegion(id, "JP", "東京都", "千代田区", Date(before.time + 60_000))

    val after = database.placeDao().getById(id)!!
    assertEquals(before, after.updatedAt)
    assertEquals("JP", after.countryCode)
    assertEquals("東京都", after.prefecture)
    assertEquals("千代田区", after.city)
  }

  @Test
  fun placeListAndStopRegions_carryTheRegion() = runTest {
    val tokyo = place()
    val unresolved = place(34.98, 135.76)
    regionDao.updateRegion(tokyo, "JP", "東京都", "千代田区", Date())
    val now = Date()
    val trackId = database.gpsTrackDao().insertTrack(
      GpsTrackEntity(startTime = now, endTime = now, isActive = false, createdAt = now, updatedAt = now),
    )
    database.stopDao().insert(StopEntity(placeId = tokyo, trackId = trackId, arrivalTime = now, departureTime = now))
    database.stopDao().insert(StopEntity(placeId = unresolved, trackId = trackId, arrivalTime = now, departureTime = now))

    val list = database.placeDao().getPlacesWithWishlist().first().associateBy { it.id }
    assertEquals("JP", list.getValue(tokyo).regionCountryCode)
    assertEquals("東京都", list.getValue(tokyo).regionPrefecture)
    assertEquals("千代田区", list.getValue(tokyo).regionCity)
    assertNull(list.getValue(unresolved).regionPrefecture)

    val stopRegions = database.stopDao().observeStopRegionsByTrack().first().map { Triple(it.countryCode, it.prefecture, it.city) }.toSet()
    assertEquals(setOf(Triple("JP", "東京都", "千代田区"), Triple(null, null, null)), stopRegions)
  }
}
