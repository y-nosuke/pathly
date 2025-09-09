package com.pathly.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class GpsPointDaoTest {

  @get:Rule
  val instantTaskExecutorRule = InstantTaskExecutorRule()

  private lateinit var database: PathlyDatabase
  private lateinit var gpsTrackDao: GpsTrackDao
  private lateinit var gpsPointDao: GpsPointDao
  private var testTrackId: Long = 0

  @Before
  fun setup() = runTest {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      PathlyDatabase::class.java,
    )
      .allowMainThreadQueries()
      .build()

    gpsTrackDao = database.gpsTrackDao()
    gpsPointDao = database.gpsPointDao()

    // テスト用のトラックを作成
    val track = GpsTrackEntity(
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date(),
    )
    testTrackId = gpsTrackDao.insertTrack(track)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun insertPointAndRetrieve() = runTest {
    // Given
    val timestamp = Date()
    val point = GpsPointEntity(
      trackId = testTrackId,
      latitude = 35.6762,
      longitude = 139.6503,
      altitude = 100.5,
      accuracy = 10f,
      speed = 5.5f,
      bearing = 90f,
      timestamp = timestamp,
      createdAt = timestamp,
    )

    // When
    val pointId = gpsPointDao.insertPoint(point)
    val points = gpsPointDao.getPointsByTrackIdSync(testTrackId)

    // Then
    assertEquals("1つのポイントが取得される", 1, points.size)

    val retrievedPoint = points[0]
    assertEquals("ポイントIDが正しい", pointId, retrievedPoint.id)
    assertEquals("トラックIDが正しい", testTrackId, retrievedPoint.trackId)
    assertEquals("緯度が正しい", 35.6762, retrievedPoint.latitude, 0.0001)
    assertEquals("経度が正しい", 139.6503, retrievedPoint.longitude, 0.0001)
    assertEquals("高度が正しい", 100.5, retrievedPoint.altitude!!, 0.1)
    assertEquals("精度が正しい", 10f, retrievedPoint.accuracy, 0.1f)
    assertEquals("速度が正しい", 5.5f, retrievedPoint.speed!!, 0.1f)
    assertEquals("方位が正しい", 90f, retrievedPoint.bearing!!, 0.1f)
    assertEquals("タイムスタンプが正しい", timestamp.time, retrievedPoint.timestamp.time)
  }

  @Test
  fun insertPointWithNullOptionalFields() = runTest {
    // Given
    val point = GpsPointEntity(
      trackId = testTrackId,
      latitude = 35.7000,
      longitude = 139.8000,
      altitude = null,
      accuracy = 15f,
      speed = null,
      bearing = null,
      timestamp = Date(),
      createdAt = Date(),
    )

    // When
    gpsPointDao.insertPoint(point)
    val points = gpsPointDao.getPointsByTrackIdSync(testTrackId)

    // Then
    assertEquals("1つのポイントが取得される", 1, points.size)

    val retrievedPoint = points[0]
    assertEquals("緯度が正しい", 35.7000, retrievedPoint.latitude, 0.0001)
    assertEquals("経度が正しい", 139.8000, retrievedPoint.longitude, 0.0001)
    assertNull("高度がnull", retrievedPoint.altitude)
    assertEquals("精度が正しい", 15f, retrievedPoint.accuracy, 0.1f)
    assertNull("速度がnull", retrievedPoint.speed)
    assertNull("方位がnull", retrievedPoint.bearing)
  }

  @Test
  fun getPointsByTrackIdFlow_emptyTrack() = runTest {
    // When
    val points = gpsPointDao.getPointsByTrackId(testTrackId).first()

    // Then
    assertTrue("空のトラックで空のポイントリスト", points.isEmpty())
  }

  @Test
  fun getPointsByTrackIdFlow_multiplePoints() = runTest {
    // Given
    val baseTime = System.currentTimeMillis()
    val points = listOf(
      GpsPointEntity(
        trackId = testTrackId,
        latitude = 35.6762,
        longitude = 139.6503,
        accuracy = 10f,
        timestamp = Date(baseTime),
        createdAt = Date(),
      ),
      GpsPointEntity(
        trackId = testTrackId,
        latitude = 35.6800,
        longitude = 139.6600,
        accuracy = 8f,
        timestamp = Date(baseTime + 30000), // 30秒後
        createdAt = Date(),
      ),
      GpsPointEntity(
        trackId = testTrackId,
        latitude = 35.6850,
        longitude = 139.6700,
        accuracy = 12f,
        timestamp = Date(baseTime + 60000), // 60秒後
        createdAt = Date(),
      ),
    )

    // When
    points.forEach { gpsPointDao.insertPoint(it) }
    val retrievedPoints = gpsPointDao.getPointsByTrackId(testTrackId).first()

    // Then
    assertEquals("3つのポイントが取得される", 3, retrievedPoints.size)

    // 時系列順（timestamp ASC）で並んでいることを確認
    assertTrue(
      "1番目のポイントが最も古い",
      retrievedPoints[0].timestamp.time <= retrievedPoints[1].timestamp.time,
    )
    assertTrue(
      "2番目のポイントが中間",
      retrievedPoints[1].timestamp.time <= retrievedPoints[2].timestamp.time,
    )

    // 座標値の確認
    assertEquals("1番目のポイントの緯度", 35.6762, retrievedPoints[0].latitude, 0.0001)
    assertEquals("2番目のポイントの緯度", 35.6800, retrievedPoints[1].latitude, 0.0001)
    assertEquals("3番目のポイントの緯度", 35.6850, retrievedPoints[2].latitude, 0.0001)
  }

  @Test
  fun getPointsByTrackIdSync_nonExistentTrack() = runTest {
    // Given
    val nonExistentTrackId = 999L

    // When
    val points = gpsPointDao.getPointsByTrackIdSync(nonExistentTrackId)

    // Then
    assertTrue("存在しないトラックIDで空のリスト", points.isEmpty())
  }

  @Test
  fun multipleTracksWithPoints() = runTest {
    // Given - 2つのトラックを作成
    val track2 = GpsTrackEntity(
      startTime = Date(),
      endTime = null,
      isActive = false,
      createdAt = Date(),
      updatedAt = Date(),
    )
    val track2Id = gpsTrackDao.insertTrack(track2)

    // Track1にポイントを追加
    val track1Point = GpsPointEntity(
      trackId = testTrackId,
      latitude = 35.6762,
      longitude = 139.6503,
      accuracy = 10f,
      timestamp = Date(),
      createdAt = Date(),
    )

    // Track2にポイントを追加
    val track2Point = GpsPointEntity(
      trackId = track2Id,
      latitude = 35.7000,
      longitude = 139.8000,
      accuracy = 8f,
      timestamp = Date(),
      createdAt = Date(),
    )

    gpsPointDao.insertPoint(track1Point)
    gpsPointDao.insertPoint(track2Point)

    // When
    val track1Points = gpsPointDao.getPointsByTrackIdSync(testTrackId)
    val track2Points = gpsPointDao.getPointsByTrackIdSync(track2Id)

    // Then
    assertEquals("Track1のポイント数", 1, track1Points.size)
    assertEquals("Track2のポイント数", 1, track2Points.size)

    assertEquals("Track1のポイントのトラックID", testTrackId, track1Points[0].trackId)
    assertEquals("Track2のポイントのトラックID", track2Id, track2Points[0].trackId)

    assertEquals("Track1のポイントの緯度", 35.6762, track1Points[0].latitude, 0.0001)
    assertEquals("Track2のポイントの緯度", 35.7000, track2Points[0].latitude, 0.0001)
  }

  @Test
  fun flowUpdates_pointsChangeReactively() = runTest {
    // Given - 初期状態
    var points = gpsPointDao.getPointsByTrackId(testTrackId).first()
    assertTrue("初期状態は空", points.isEmpty())

    // When - ポイントを追加
    val point = GpsPointEntity(
      trackId = testTrackId,
      latitude = 35.6762,
      longitude = 139.6503,
      accuracy = 10f,
      timestamp = Date(),
      createdAt = Date(),
    )
    gpsPointDao.insertPoint(point)

    // Then - Flowが更新される
    points = gpsPointDao.getPointsByTrackId(testTrackId).first()
    assertEquals("ポイント追加後、Flowが更新される", 1, points.size)
  }

  @Test
  fun foreignKeyConstraint_invalidTrackId() = runTest {
    // Given - 存在しないトラックIDを参照するポイント
    val invalidPoint = GpsPointEntity(
      trackId = 999L, // 存在しないトラックID
      latitude = 35.6762,
      longitude = 139.6503,
      accuracy = 10f,
      timestamp = Date(),
      createdAt = Date(),
    )

    // When & Then - 外部キー制約によりエラーが発生する
    try {
      gpsPointDao.insertPoint(invalidPoint)
      fail("外部キー制約エラーが発生するはず")
    } catch (e: Exception) {
      assertTrue(
        "外部キー制約エラー",
        e.message?.contains("FOREIGN KEY constraint failed") == true ||
          e.message?.contains("foreign key") == true,
      )
    }
  }

  @Test
  fun insertBatchPoints_multiplePointsAtOnce() = runTest {
    // Given
    val baseTime = System.currentTimeMillis()
    val points = listOf(
      GpsPointEntity(
        trackId = testTrackId,
        latitude = 35.6762,
        longitude = 139.6503,
        accuracy = 10f,
        timestamp = Date(baseTime),
        createdAt = Date(),
      ),
      GpsPointEntity(
        trackId = testTrackId,
        latitude = 35.6800,
        longitude = 139.6600,
        accuracy = 8f,
        timestamp = Date(baseTime + 30000),
        createdAt = Date(),
      ),
      GpsPointEntity(
        trackId = testTrackId,
        latitude = 35.6850,
        longitude = 139.6700,
        accuracy = 12f,
        timestamp = Date(baseTime + 60000),
        createdAt = Date(),
      ),
    )

    // When
    points.forEach { gpsPointDao.insertPoint(it) }
    val retrievedPoints = gpsPointDao.getPointsByTrackIdSync(testTrackId)

    // Then
    assertEquals("バッチ挿入されたポイント数", 3, retrievedPoints.size)

    // すべてのポイントが正しく挿入されていることを確認
    points.forEachIndexed { index, originalPoint ->
      val retrievedPoint = retrievedPoints[index]
      assertEquals(
        "${index + 1}番目のポイントの緯度",
        originalPoint.latitude,
        retrievedPoint.latitude,
        0.0001,
      )
      assertEquals(
        "${index + 1}番目のポイントの経度",
        originalPoint.longitude,
        retrievedPoint.longitude,
        0.0001,
      )
      assertEquals(
        "${index + 1}番目のポイントの精度",
        originalPoint.accuracy,
        retrievedPoint.accuracy,
        0.1f,
      )
    }
  }
}
