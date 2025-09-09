package com.pathly.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.GpsTrackWithPoints
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.util.EncryptionHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class GpsTrackRepositoryImplTest {

  @get:Rule
  val instantTaskExecutorRule = InstantTaskExecutorRule()

  private val testDispatcher = StandardTestDispatcher()
  private val mockGpsTrackDao = mockk<GpsTrackDao>(relaxed = true)
  private val mockGpsPointDao = mockk<GpsPointDao>(relaxed = true)
  private val mockEncryptionHelper = mockk<EncryptionHelper>(relaxed = true)

  private lateinit var repository: GpsTrackRepositoryImpl

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = GpsTrackRepositoryImpl(mockGpsTrackDao, mockGpsPointDao, mockEncryptionHelper)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `getAllTracks_空のリスト_空のFlowを返す`() = runTest {
    // Given
    coEvery { mockGpsTrackDao.getAllTracksWithPoints() } returns flowOf(emptyList())

    // When
    val result = repository.getAllTracks().first()

    // Then
    assertTrue("空のリストが返される", result.isEmpty())
  }

  @Test
  fun `getAllTracks_複数のトラック_変換されたGpsTrackリストを返す`() = runTest {
    // Given
    val trackEntity1 = createGpsTrackEntity(id = 1L, isActive = false)
    val trackEntity2 = createGpsTrackEntity(id = 2L, isActive = true)

    val pointEntities1 = listOf(
      createGpsPointEntity(id = 1L, trackId = 1L, latitude = 35.6762, longitude = 139.6503),
      createGpsPointEntity(id = 2L, trackId = 1L, latitude = 35.6896, longitude = 139.7006),
    )
    val pointEntities2 = listOf(
      createGpsPointEntity(id = 3L, trackId = 2L, latitude = 35.7000, longitude = 139.8000),
    )

    val tracksWithPoints = listOf(
      GpsTrackWithPoints(trackEntity1, pointEntities1),
      GpsTrackWithPoints(trackEntity2, pointEntities2),
    )

    coEvery { mockGpsTrackDao.getAllTracksWithPoints() } returns flowOf(tracksWithPoints)

    // When
    val result = repository.getAllTracks().first()

    // Then
    assertEquals("トラック数が正しい", 2, result.size)

    val track1 = result[0]
    assertEquals("Track1のIDが正しい", 1L, track1.id)
    assertEquals("Track1がinactive", false, track1.isActive)
    assertEquals("Track1のpoint数が正しい", 2, track1.points.size)

    val track2 = result[1]
    assertEquals("Track2のIDが正しい", 2L, track2.id)
    assertEquals("Track2がactive", true, track2.isActive)
    assertEquals("Track2のpoint数が正しい", 1, track2.points.size)
  }

  @Test
  fun `getTrackById_存在するトラック_変換されたGpsTrackを返す`() = runTest {
    // Given
    val trackId = 1L
    val trackEntity = createGpsTrackEntity(id = trackId, isActive = false)
    val pointEntities = listOf(
      createGpsPointEntity(
        id = 1L,
        trackId = trackId,
        latitude = 35.6762,
        longitude = 139.6503,
      ),
    )

    coEvery { mockGpsTrackDao.getTrackById(trackId) } returns trackEntity
    coEvery { mockGpsPointDao.getPointsByTrackIdSync(trackId) } returns pointEntities

    // When
    val result = repository.getTrackById(trackId)

    // Then
    assertNotNull("トラックが見つかる", result)
    assertEquals("IDが正しい", trackId, result!!.id)
    assertEquals("Point数が正しい", 1, result.points.size)
    assertEquals("Pointの緯度が正しい", 35.6762, result.points[0].latitude, 0.0001)
  }

  @Test
  fun `getTrackById_存在しないトラック_nullを返す`() = runTest {
    // Given
    val trackId = 999L
    coEvery { mockGpsTrackDao.getTrackById(trackId) } returns null

    // When
    val result = repository.getTrackById(trackId)

    // Then
    assertNull("nullが返される", result)
    coVerify(exactly = 0) { mockGpsPointDao.getPointsByTrackIdSync(any()) }
  }

  @Test
  fun `getActiveTrack_アクティブなトラックが存在_変換されたGpsTrackを返す`() = runTest {
    // Given
    val trackEntity = createGpsTrackEntity(id = 1L, isActive = true)
    val pointEntities = listOf(
      createGpsPointEntity(id = 1L, trackId = 1L, latitude = 35.6762, longitude = 139.6503),
    )

    coEvery { mockGpsTrackDao.getActiveTrack() } returns trackEntity
    coEvery { mockGpsPointDao.getPointsByTrackIdSync(1L) } returns pointEntities

    // When
    val result = repository.getActiveTrack()

    // Then
    assertNotNull("アクティブなトラックが見つかる", result)
    assertEquals("IDが正しい", 1L, result!!.id)
    assertTrue("アクティブ状態が正しい", result.isActive)
    assertEquals("Point数が正しい", 1, result.points.size)
  }

  @Test
  fun `getActiveTrack_アクティブなトラックが存在しない_nullを返す`() = runTest {
    // Given
    coEvery { mockGpsTrackDao.getActiveTrack() } returns null

    // When
    val result = repository.getActiveTrack()

    // Then
    assertNull("nullが返される", result)
    coVerify(exactly = 0) { mockGpsPointDao.getPointsByTrackIdSync(any()) }
  }

  @Test
  fun `deleteTrack_GpsTrackを渡す_Entityに変換してDaoを呼び出す`() = runTest {
    // Given
    val startTime = Date()
    val endTime = Date()
    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = emptyList(),
      createdAt = Date(),
      updatedAt = Date(),
    )

    coEvery { mockGpsTrackDao.deleteTrack(any()) } returns Unit

    // When
    repository.deleteTrack(track)

    // Then
    coVerify {
      mockGpsTrackDao.deleteTrack(
        match<GpsTrackEntity> { entity ->
          entity.id == 1L &&
            entity.startTime == startTime &&
            entity.endTime == endTime &&
            entity.isActive == false
        },
      )
    }
  }

  @Test
  fun `Entity変換_GpsTrackEntity_正しくGpsTrackに変換される`() = runTest {
    // Given
    val startTime = Date()
    val endTime = Date()
    val createdAt = Date()
    val updatedAt = Date()

    val trackEntity = GpsTrackEntity(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
    val points = listOf(
      GpsPoint(
        id = 1L,
        trackId = 1L,
        latitude = 35.6762,
        longitude = 139.6503,
        altitude = 100.0,
        accuracy = 10f,
        speed = 5f,
        bearing = 90f,
        timestamp = Date(),
        createdAt = Date(),
      ),
    )

    coEvery { mockGpsTrackDao.getTrackById(1L) } returns trackEntity
    coEvery { mockGpsPointDao.getPointsByTrackIdSync(1L) } returns listOf(
      createGpsPointEntity(id = 1L, trackId = 1L, latitude = 35.6762, longitude = 139.6503),
    )

    // When
    val result = repository.getTrackById(1L)

    // Then
    assertNotNull("変換結果が存在する", result)
    with(result!!) {
      assertEquals("IDが正しい", 1L, id)
      assertEquals("開始時刻が正しい", startTime, this.startTime)
      assertEquals("終了時刻が正しい", endTime, this.endTime)
      assertEquals("アクティブ状態が正しい", false, isActive)
      assertEquals("作成日時が正しい", createdAt, this.createdAt)
      assertEquals("更新日時が正しい", updatedAt, this.updatedAt)
      assertEquals("Point数が正しい", 1, this.points.size)
    }
  }

  private fun createGpsTrackEntity(
    id: Long,
    startTime: Date = Date(),
    endTime: Date? = null,
    isActive: Boolean = false,
    createdAt: Date = Date(),
    updatedAt: Date = Date(),
  ): GpsTrackEntity {
    return GpsTrackEntity(
      id = id,
      startTime = startTime,
      endTime = endTime,
      isActive = isActive,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
  }

  private fun createGpsPointEntity(
    id: Long,
    trackId: Long,
    latitude: Double,
    longitude: Double,
    altitude: Double? = null,
    accuracy: Float = 10f,
    speed: Float? = null,
    bearing: Float? = null,
    timestamp: Date = Date(),
    createdAt: Date = Date(),
  ): GpsPointEntity {
    return GpsPointEntity(
      id = id,
      trackId = trackId,
      latitude = latitude,
      longitude = longitude,
      altitude = altitude,
      accuracy = accuracy,
      speed = speed,
      bearing = bearing,
      timestamp = timestamp,
      createdAt = createdAt,
    )
  }
}
