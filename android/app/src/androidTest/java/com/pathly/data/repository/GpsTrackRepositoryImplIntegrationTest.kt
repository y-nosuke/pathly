package com.pathly.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.domain.model.GpsTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.Date

@RunWith(AndroidJUnit4::class)
class GpsTrackRepositoryImplIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PathlyDatabase
    private lateinit var repository: GpsTrackRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PathlyDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        repository = GpsTrackRepositoryImpl(
            database.gpsTrackDao(),
            database.gpsPointDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getAllTracks_emptyDatabase_returnsEmptyFlow() = runTest {
        // When
        val tracks = repository.getAllTracks().first()

        // Then
        assertTrue("空のデータベースで空のFlow", tracks.isEmpty())
    }

    @Test
    fun getAllTracks_withTracksAndPoints_returnsTracksWithDistanceCalculation() = runTest {
        // Given - データベースに直接データを挿入
        val startTime1 = Date(System.currentTimeMillis() - 2000)
        val startTime2 = Date(System.currentTimeMillis())

        // Track1 (完了済み)
        val track1 = GpsTrackEntity(
            startTime = startTime1,
            endTime = Date(startTime1.time + 300000), // 5分後
            isActive = false,
            createdAt = startTime1,
            updatedAt = Date()
        )

        // Track2 (進行中)
        val track2 = GpsTrackEntity(
            startTime = startTime2,
            endTime = null,
            isActive = true,
            createdAt = startTime2,
            updatedAt = startTime2
        )

        val track1Id = database.gpsTrackDao().insertTrack(track1)
        val track2Id = database.gpsTrackDao().insertTrack(track2)

        // Track1のポイント（東京駅→新宿駅）
        val track1Points = listOf(
            GpsPointEntity(
                trackId = track1Id,
                latitude = 35.6762,
                longitude = 139.6503, // 東京駅
                accuracy = 10f,
                timestamp = startTime1,
                createdAt = startTime1
            ),
            GpsPointEntity(
                trackId = track1Id,
                latitude = 35.6896,
                longitude = 139.7006, // 新宿駅
                accuracy = 8f,
                timestamp = Date(startTime1.time + 120000),
                createdAt = startTime1
            )
        )

        // Track2のポイント（1点のみ）
        val track2Point = GpsPointEntity(
            trackId = track2Id,
            latitude = 35.7000,
            longitude = 139.8000,
            accuracy = 12f,
            timestamp = startTime2,
            createdAt = startTime2
        )

        track1Points.forEach { database.gpsPointDao().insertPoint(it) }
        database.gpsPointDao().insertPoint(track2Point)

        // When
        val tracks = repository.getAllTracks().first()

        // Then
        assertEquals("2つのトラックが取得される", 2, tracks.size)

        // 新しい順で並んでいることを確認
        assertEquals("最新のトラックが最初", track2Id, tracks[0].id)
        assertEquals("古いトラックが2番目", track1Id, tracks[1].id)

        // Track1の検証（距離計算含む）
        val retrievedTrack1 = tracks[1]
        assertEquals("Track1のpoint数", 2, retrievedTrack1.points.size)
        assertFalse("Track1がinactive", retrievedTrack1.isActive)
        assertTrue("Track1の距離が計算される（>0）", retrievedTrack1.totalDistanceMeters > 0)
        // 東京駅→新宿駅の距離は約3-6km
        assertTrue("Track1の距離が現実的な範囲", 
            retrievedTrack1.totalDistanceMeters in 3000.0..6000.0)

        // Track2の検証
        val retrievedTrack2 = tracks[0]
        assertEquals("Track2のpoint数", 1, retrievedTrack2.points.size)
        assertTrue("Track2がactive", retrievedTrack2.isActive)
        assertEquals("Track2の距離（1点のみ）", 0.0, retrievedTrack2.totalDistanceMeters, 0.001)
    }

    @Test
    fun getTrackById_existingTrack_returnsTrackWithPoints() = runTest {
        // Given
        val startTime = Date()
        val track = GpsTrackEntity(
            startTime = startTime,
            endTime = null,
            isActive = true,
            createdAt = startTime,
            updatedAt = startTime
        )

        val trackId = database.gpsTrackDao().insertTrack(track)

        val points = listOf(
            GpsPointEntity(
                trackId = trackId,
                latitude = 35.6762,
                longitude = 139.6503,
                altitude = 100.0,
                accuracy = 10f,
                speed = 5f,
                bearing = 90f,
                timestamp = startTime,
                createdAt = startTime
            ),
            GpsPointEntity(
                trackId = trackId,
                latitude = 35.6800,
                longitude = 139.6600,
                accuracy = 8f,
                timestamp = Date(startTime.time + 60000),
                createdAt = startTime
            )
        )

        points.forEach { database.gpsPointDao().insertPoint(it) }

        // When
        val retrievedTrack = repository.getTrackById(trackId)

        // Then
        assertNotNull("トラックが取得される", retrievedTrack)
        assertEquals("トラックIDが正しい", trackId, retrievedTrack!!.id)
        assertEquals("ポイント数が正しい", 2, retrievedTrack.points.size)
        assertTrue("距離が計算される", retrievedTrack.totalDistanceMeters > 0)

        // ポイントの詳細確認
        val firstPoint = retrievedTrack.points[0]
        assertEquals("1番目のポイントの緯度", 35.6762, firstPoint.latitude, 0.0001)
        assertEquals("1番目のポイントの高度", 100.0, firstPoint.altitude!!, 0.1)
        assertEquals("1番目のポイントの速度", 5f, firstPoint.speed!!, 0.1f)
        assertEquals("1番目のポイントの方位", 90f, firstPoint.bearing!!, 0.1f)
    }

    @Test
    fun getTrackById_nonExistentTrack_returnsNull() = runTest {
        // When
        val track = repository.getTrackById(999L)

        // Then
        assertNull("存在しないトラックでnull", track)
    }

    @Test
    fun getActiveTrack_noActiveTrack_returnsNull() = runTest {
        // Given - 非アクティブなトラックのみ
        val track = GpsTrackEntity(
            startTime = Date(),
            endTime = Date(),
            isActive = false,
            createdAt = Date(),
            updatedAt = Date()
        )
        database.gpsTrackDao().insertTrack(track)

        // When
        val activeTrack = repository.getActiveTrack()

        // Then
        assertNull("アクティブなトラックなし", activeTrack)
    }

    @Test
    fun getActiveTrack_withActiveTrack_returnsActiveTrackWithPoints() = runTest {
        // Given
        val activeTrack = GpsTrackEntity(
            startTime = Date(),
            endTime = null,
            isActive = true,
            createdAt = Date(),
            updatedAt = Date()
        )

        val trackId = database.gpsTrackDao().insertTrack(activeTrack)

        val point = GpsPointEntity(
            trackId = trackId,
            latitude = 35.6762,
            longitude = 139.6503,
            accuracy = 10f,
            timestamp = Date(),
            createdAt = Date()
        )

        database.gpsPointDao().insertPoint(point)

        // When
        val retrievedActiveTrack = repository.getActiveTrack()

        // Then
        assertNotNull("アクティブなトラックが取得される", retrievedActiveTrack)
        assertEquals("アクティブなトラックのID", trackId, retrievedActiveTrack!!.id)
        assertTrue("取得されたトラックがアクティブ", retrievedActiveTrack.isActive)
        assertEquals("ポイント数が正しい", 1, retrievedActiveTrack.points.size)
        assertEquals("ポイントの緯度が正しい", 35.6762, retrievedActiveTrack.points[0].latitude, 0.0001)
    }

    @Test
    fun deleteTrack_existingTrack_removesTrackAndPoints() = runTest {
        // Given
        val startTime = Date()
        val track = GpsTrackEntity(
            startTime = startTime,
            endTime = null,
            isActive = false,
            createdAt = startTime,
            updatedAt = startTime
        )

        val trackId = database.gpsTrackDao().insertTrack(track)

        val point = GpsPointEntity(
            trackId = trackId,
            latitude = 35.6762,
            longitude = 139.6503,
            accuracy = 10f,
            timestamp = startTime,
            createdAt = startTime
        )

        database.gpsPointDao().insertPoint(point)

        // Create GpsTrack domain object
        val domainTrack = GpsTrack(
            id = trackId,
            startTime = startTime,
            endTime = null,
            isActive = false,
            points = emptyList(), // deleteTrackでは使用されない
            createdAt = startTime,
            updatedAt = startTime
        )

        // Verify existence before deletion
        assertNotNull("削除前にトラックが存在", database.gpsTrackDao().getTrackById(trackId))
        assertEquals("削除前にポイントが存在", 1, 
            database.gpsPointDao().getPointsByTrackIdSync(trackId).size)

        // When
        repository.deleteTrack(domainTrack)

        // Then
        assertNull("トラックが削除される", database.gpsTrackDao().getTrackById(trackId))
        assertTrue("関連ポイントも削除される（CASCADE）", 
            database.gpsPointDao().getPointsByTrackIdSync(trackId).isEmpty())

        // Repository経由でも確認
        val tracks = repository.getAllTracks().first()
        assertTrue("Repository経由でもトラックが見つからない", tracks.isEmpty())
    }

    @Test
    fun endToEndScenario_createTrackAddPointsRetrieve() = runTest {
        // Given - 実際のアプリのユースケースをシミュレート
        val startTime = Date()

        // 1. 新しいトラックを作成
        val newTrack = GpsTrackEntity(
            startTime = startTime,
            endTime = null,
            isActive = true,
            createdAt = startTime,
            updatedAt = startTime
        )

        val trackId = database.gpsTrackDao().insertTrack(newTrack)

        // 2. GPS記録のシミュレート（30秒間隔で3点）
        val gpsPoints = listOf(
            GpsPointEntity(
                trackId = trackId,
                latitude = 35.6762,
                longitude = 139.6503,
                altitude = 10.0,
                accuracy = 15f,
                speed = 0f,
                bearing = null,
                timestamp = startTime,
                createdAt = startTime
            ),
            GpsPointEntity(
                trackId = trackId,
                latitude = 35.6770,
                longitude = 139.6510,
                altitude = 12.0,
                accuracy = 10f,
                speed = 2.5f,
                bearing = 45f,
                timestamp = Date(startTime.time + 30000),
                createdAt = startTime
            ),
            GpsPointEntity(
                trackId = trackId,
                latitude = 35.6780,
                longitude = 139.6520,
                altitude = 15.0,
                accuracy = 8f,
                speed = 3.0f,
                bearing = 60f,
                timestamp = Date(startTime.time + 60000),
                createdAt = startTime
            )
        )

        gpsPoints.forEach { database.gpsPointDao().insertPoint(it) }

        // 3. 記録を終了
        val endTime = Date(startTime.time + 90000)
        val completedTrack = newTrack.copy(
            id = trackId,
            endTime = endTime,
            isActive = false,
            updatedAt = endTime
        )

        database.gpsTrackDao().updateTrack(completedTrack)

        // When - Repository経由でデータを取得
        val allTracks = repository.getAllTracks().first()
        val specificTrack = repository.getTrackById(trackId)
        val activeTrack = repository.getActiveTrack()

        // Then
        assertEquals("全トラック数", 1, allTracks.size)
        
        val track = allTracks[0]
        assertEquals("トラックIDが正しい", trackId, track.id)
        assertEquals("開始時刻が正しい", startTime.time, track.startTime.time)
        assertEquals("終了時刻が正しい", endTime.time, track.endTime!!.time)
        assertFalse("トラックが非アクティブ", track.isActive)
        assertEquals("ポイント数が正しい", 3, track.points.size)
        
        // 距離計算の確認
        assertTrue("距離が計算されている", track.totalDistanceMeters > 0)
        assertTrue("計算された距離が現実的", track.totalDistanceMeters < 1000) // 1km以下のはず
        
        // 個別取得の確認
        assertEquals("個別取得でも同じトラック", trackId, specificTrack!!.id)
        assertEquals("ポイント数も同じ", 3, specificTrack.points.size)
        
        // アクティブトラックの確認
        assertNull("完了済みトラックはアクティブトラック検索で見つからない", activeTrack)
        
        // ポイントの時系列順序確認
        val points = track.points
        assertTrue("1番目 <= 2番目のタイムスタンプ", 
            points[0].timestamp.time <= points[1].timestamp.time)
        assertTrue("2番目 <= 3番目のタイムスタンプ", 
            points[1].timestamp.time <= points[2].timestamp.time)
        
        // GPSデータの詳細確認
        assertEquals("1番目のポイントの速度", 0f, points[0].speed!!, 0.1f)
        assertNull("1番目のポイントの方位はnull", points[0].bearing)
        assertEquals("2番目のポイントの方位", 45f, points[1].bearing!!, 0.1f)
        assertEquals("3番目のポイントの高度", 15.0, points[2].altitude!!, 0.1)
    }
}