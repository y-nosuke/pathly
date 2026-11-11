package com.pathly.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.StopEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/** 地図の範囲に入る経路の問い合わせ（記録画面の「この範囲の経路」）。 */
@RunWith(AndroidJUnit4::class)
class TrackBoundsQueryTest {

  private lateinit var database: PathlyDatabase
  private lateinit var gpsTrackDao: GpsTrackDao

  @Before
  fun setup() {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      PathlyDatabase::class.java,
    )
      .allowMainThreadQueries()
      .build()
    gpsTrackDao = database.gpsTrackDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  private suspend fun track(active: Boolean = false, vararg points: Pair<Double, Double>): Long {
    val now = Date()
    val id = gpsTrackDao.insertTrack(
      GpsTrackEntity(startTime = now, endTime = if (active) null else now, isActive = active, createdAt = now, updatedAt = now),
    )
    points.forEach { (lat, lng) ->
      database.gpsPointDao().insertPoint(
        GpsPointEntity(trackId = id, latitude = lat, longitude = lng, accuracy = 5f, timestamp = now, createdAt = now),
      )
    }
    return id
  }

  private suspend fun stopAt(trackId: Long, latitude: Double, longitude: Double, google: Pair<Double, Double>? = null) {
    val placeId = database.placeDao().insert(PlaceEntity(latitude = latitude, longitude = longitude))
    google?.let { (lat, lng) ->
      database.googlePlaceDao().upsert(GooglePlaceEntity(placeId = placeId, googlePlaceId = "gp-$placeId", latitude = lat, longitude = lng))
    }
    database.stopDao().insert(StopEntity(placeId = placeId, trackId = trackId, arrivalTime = Date(), departureTime = Date()))
  }

  // 東京駅のあたり（35.67〜35.69, 139.75〜139.78）。
  private suspend fun inTokyo() = gpsTrackDao.getFinishedTrackIdsInBounds(35.67, 139.75, 35.69, 139.78).toSet()

  @Test
  fun trackWithPointInBounds_isFound() = runTest {
    val passed = track(points = arrayOf(35.60 to 139.70, 35.68 to 139.76, 35.75 to 139.80))
    val elsewhere = track(points = arrayOf(34.70 to 135.50, 34.71 to 135.51))

    assertEquals(setOf(passed), inTokyo())
    assertTrue(elsewhere !in inTokyo())
  }

  @Test
  fun trackWithStopInBounds_isFound_evenIfNoPointIsInside() = runTest {
    // 点は範囲の外だが、立ち寄った場所が範囲の中。
    val stopped = track(points = arrayOf(35.60 to 139.70))
    stopAt(stopped, 35.68, 139.76)

    assertEquals(setOf(stopped), inTokyo())
  }

  @Test
  fun stopUsesGoogleCoordinate_whenPresent() = runTest {
    // アンカーは範囲の外、施設の座標（地図のマーカーの位置）は範囲の中。
    val inside = track(points = arrayOf(35.60 to 139.70))
    stopAt(inside, 35.60, 139.70, google = 35.68 to 139.76)
    // アンカーは範囲の中でも、施設の座標が外なら外とみなす（マーカーが外にあるので）。
    val outside = track(points = arrayOf(35.60 to 139.70))
    stopAt(outside, 35.68, 139.76, google = 35.60 to 139.70)

    assertEquals(setOf(inside), inTokyo())
  }

  @Test
  fun activeTrack_isExcluded() = runTest {
    track(active = true, points = arrayOf(35.68 to 139.76))

    assertEquals(emptySet<Long>(), inTokyo())
  }

  @Test
  fun boundsCrossingAntimeridian_matchLongitudesOnBothSides() = runTest {
    val east = track(points = arrayOf(10.0 to 179.5))
    val west = track(points = arrayOf(10.0 to -179.5))
    val far = track(points = arrayOf(10.0 to 170.0))

    val found = gpsTrackDao.getFinishedTrackIdsInBounds(south = 9.0, west = 179.0, north = 11.0, east = -179.0).toSet()

    assertEquals(setOf(east, west), found)
    assertTrue(far !in found)
  }

  @Test
  fun pointLookup_usesLatitudeLongitudeIndex() {
    // 全点を読まないこと（索引で緯度の幅だけを引く）を、問い合わせの計画で確かめる。
    // 条件の形は GpsTrackDao.getFinishedTrackIdsInBounds の点の部分と同じ。
    val plan = buildString {
      database.openHelper.readableDatabase.query(
        "EXPLAIN QUERY PLAN SELECT trackId FROM gps_points " +
          "WHERE latitude BETWEEN ? AND ? AND " +
          "((? <= ? AND longitude BETWEEN ? AND ?) OR (? > ? AND (longitude >= ? OR longitude <= ?)))",
        arrayOf<Any>(35.67, 35.69, 139.75, 139.78, 139.75, 139.78, 139.75, 139.78, 139.75, 139.78),
      ).use { cursor ->
        while (cursor.moveToNext()) appendLine(cursor.getString(cursor.getColumnIndexOrThrow("detail")))
      }
    }
    assertTrue("索引を使っていない: $plan", plan.contains("index_gps_points_latitude_longitude"))
  }
}
