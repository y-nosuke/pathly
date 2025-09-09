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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class GpsTrackDaoTest {

  @get:Rule
  val instantTaskExecutorRule = InstantTaskExecutorRule()

  private lateinit var database: PathlyDatabase
  private lateinit var gpsTrackDao: GpsTrackDao
  private lateinit var gpsPointDao: GpsPointDao

  @Before
  fun setup() {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      PathlyDatabase::class.java,
    )
      .allowMainThreadQueries()
      .build()

    gpsTrackDao = database.gpsTrackDao()
    gpsPointDao = database.gpsPointDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun insertTrackAndRetrieve() = runTest {
    // Given
    val startTime = Date()
    val track = GpsTrackEntity(
      startTime = startTime,
      endTime = null,
      isActive = true,
      createdAt = startTime,
      updatedAt = startTime,
    )

    // When
    val trackId = gpsTrackDao.insertTrack(track)
    val retrievedTrack = gpsTrackDao.getTrackById(trackId)

    // Then
    assertNotNull("トラックが取得できる", retrievedTrack)
    assertEquals("開始時刻が正しい", startTime.time, retrievedTrack!!.startTime.time)
    assertTrue("アクティブ状態が正しい", retrievedTrack.isActive)
    assertNull("終了時刻がnull", retrievedTrack.endTime)
  }

  @Test
  fun getAllTracksWithPoints_emptyDatabase() = runTest {
    // When
    val tracks = gpsTrackDao.getAllTracksWithPoints().first()

    // Then
    assertTrue("空のデータベースで空のリスト", tracks.isEmpty())
  }

  @Test
  fun getAllTracksWithPoints_withTracksAndPoints() = runTest {
    // Given
    val startTime1 = Date(System.currentTimeMillis() - 1000)
    val startTime2 = Date(System.currentTimeMillis())

    val track1 = GpsTrackEntity(
      startTime = startTime1,
      endTime = Date(),
      isActive = false,
      createdAt = startTime1,
      updatedAt = Date(),
    )

    val track2 = GpsTrackEntity(
      startTime = startTime2,
      endTime = null,
      isActive = true,
      createdAt = startTime2,
      updatedAt = startTime2,
    )

    val trackId1 = gpsTrackDao.insertTrack(track1)
    val trackId2 = gpsTrackDao.insertTrack(track2)

    // Add GPS points for each track
    val point1 = GpsPointEntity(
      trackId = trackId1,
      latitude = 35.6762,
      longitude = 139.6503,
      accuracy = 10f,
      timestamp = startTime1,
      createdAt = startTime1,
    )

    val point2 = GpsPointEntity(
      trackId = trackId1,
      latitude = 35.6896,
      longitude = 139.7006,
      accuracy = 8f,
      timestamp = Date(startTime1.time + 60000),
      createdAt = startTime1,
    )

    val point3 = GpsPointEntity(
      trackId = trackId2,
      latitude = 35.7000,
      longitude = 139.8000,
      accuracy = 12f,
      timestamp = startTime2,
      createdAt = startTime2,
    )

    gpsPointDao.insertPoint(point1)
    gpsPointDao.insertPoint(point2)
    gpsPointDao.insertPoint(point3)

    // When
    val tracksWithPoints = gpsTrackDao.getAllTracksWithPoints().first()

    // Then
    assertEquals("2つのトラックが取得される", 2, tracksWithPoints.size)

    // Track1の検証（新しい順なので2番目）
    val retrievedTrack1 = tracksWithPoints.find { it.track.id == trackId1 }
    assertNotNull("Track1が見つかる", retrievedTrack1)
    assertEquals("Track1のpoint数が正しい", 2, retrievedTrack1!!.points.size)
    assertFalse("Track1がinactive", retrievedTrack1.track.isActive)

    // Track2の検証（新しい順なので1番目）
    val retrievedTrack2 = tracksWithPoints.find { it.track.id == trackId2 }
    assertNotNull("Track2が見つかる", retrievedTrack2)
    assertEquals("Track2のpoint数が正しい", 1, retrievedTrack2!!.points.size)
    assertTrue("Track2がactive", retrievedTrack2.track.isActive)

    // 順序の確認（新しい順）
    assertEquals("最新のトラックが最初", trackId2, tracksWithPoints[0].track.id)
    assertEquals("古いトラックが2番目", trackId1, tracksWithPoints[1].track.id)
  }

  @Test
  fun getActiveTrack_noActiveTrack() = runTest {
    // Given - inactive trackのみ
    val track = GpsTrackEntity(
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      createdAt = Date(),
      updatedAt = Date(),
    )
    gpsTrackDao.insertTrack(track)

    // When
    val activeTrack = gpsTrackDao.getActiveTrack()

    // Then
    assertNull("アクティブなトラックなし", activeTrack)
  }

  @Test
  fun getActiveTrack_withActiveTrack() = runTest {
    // Given
    val inactiveTrack = GpsTrackEntity(
      startTime = Date(System.currentTimeMillis() - 2000),
      endTime = Date(),
      isActive = false,
      createdAt = Date(),
      updatedAt = Date(),
    )

    val activeTrack = GpsTrackEntity(
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date(),
    )

    gpsTrackDao.insertTrack(inactiveTrack)
    val activeTrackId = gpsTrackDao.insertTrack(activeTrack)

    // When
    val retrievedActiveTrack = gpsTrackDao.getActiveTrack()

    // Then
    assertNotNull("アクティブなトラックが取得される", retrievedActiveTrack)
    assertEquals("アクティブなトラックのIDが正しい", activeTrackId, retrievedActiveTrack!!.id)
    assertTrue("取得されたトラックがアクティブ", retrievedActiveTrack.isActive)
    assertNull("アクティブなトラックの終了時刻がnull", retrievedActiveTrack.endTime)
  }

  @Test
  fun deleteTrack_removesTrackAndCascadePoints() = runTest {
    // Given
    val track = GpsTrackEntity(
      startTime = Date(),
      endTime = null,
      isActive = false,
      createdAt = Date(),
      updatedAt = Date(),
    )

    val trackId = gpsTrackDao.insertTrack(track)

    val point = GpsPointEntity(
      trackId = trackId,
      latitude = 35.6762,
      longitude = 139.6503,
      accuracy = 10f,
      timestamp = Date(),
      createdAt = Date(),
    )

    gpsPointDao.insertPoint(point)

    // Verify track and point exist
    assertNotNull("削除前にトラックが存在", gpsTrackDao.getTrackById(trackId))
    assertEquals("削除前にポイントが存在", 1, gpsPointDao.getPointsByTrackIdSync(trackId).size)

    // When
    gpsTrackDao.deleteTrack(track.copy(id = trackId))

    // Then
    assertNull("トラックが削除される", gpsTrackDao.getTrackById(trackId))
    assertTrue(
      "関連ポイントも削除される（CASCADE）",
      gpsPointDao.getPointsByTrackIdSync(trackId).isEmpty(),
    )
  }

  @Test
  fun multipleActiveTracks_onlyOneReturned() = runTest {
    // Given - 複数のアクティブなトラック（通常は発生しないが、データ整合性テスト）
    val track1 = GpsTrackEntity(
      startTime = Date(System.currentTimeMillis() - 1000),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date(),
    )

    val track2 = GpsTrackEntity(
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date(),
    )

    gpsTrackDao.insertTrack(track1)
    val track2Id = gpsTrackDao.insertTrack(track2)

    // When
    val activeTrack = gpsTrackDao.getActiveTrack()

    // Then
    assertNotNull("アクティブなトラックが取得される", activeTrack)
    // 最新のアクティブなトラックが取得される（ORDER BY startTime DESC LIMIT 1）
    assertEquals("最新のアクティブなトラックが取得される", track2Id, activeTrack!!.id)
  }

  @Test
  fun flowUpdates_tracksChangesReactively() = runTest {
    // Given - 初期状態
    var tracksWithPoints = gpsTrackDao.getAllTracksWithPoints().first()
    assertTrue("初期状態は空", tracksWithPoints.isEmpty())

    // When - トラックを追加
    val track = GpsTrackEntity(
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date(),
    )
    gpsTrackDao.insertTrack(track)

    // Then - Flowが更新される
    tracksWithPoints = gpsTrackDao.getAllTracksWithPoints().first()
    assertEquals("トラックが追加された後、Flowが更新される", 1, tracksWithPoints.size)
  }
}
