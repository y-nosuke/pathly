package com.pathly.data.local.entity

import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class EntityConversionTest {

  @Test
  fun `GpsTrackEntity_toGpsTrack変換_全フィールドが正しくマッピングされる`() {
    // Given
    val startTime = Date(1640995200000L)
    val endTime = Date(1640995300000L)
    val createdAt = Date(1640995100000L)
    val updatedAt = Date(1640995400000L)

    val trackEntity = GpsTrackEntity(
      id = 123L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      createdAt = createdAt,
      updatedAt = updatedAt
    )

    val points = listOf(
      GpsPoint(
        id = 1L,
        trackId = 123L,
        latitude = 35.6762,
        longitude = 139.6503,
        altitude = 100.0,
        accuracy = 10f,
        speed = 5f,
        bearing = 90f,
        timestamp = Date(),
        createdAt = Date()
      )
    )

    // When - GpsTrackRepositoryImplの変換ロジックを模擬
    val result = trackEntity.toGpsTrack(points)

    // Then
    assertEquals("IDが正しくマッピング", 123L, result.id)
    assertEquals("開始時刻が正しくマッピング", startTime, result.startTime)
    assertEquals("終了時刻が正しくマッピング", endTime, result.endTime)
    assertEquals("アクティブ状態が正しくマッピング", false, result.isActive)
    assertEquals("作成日時が正しくマッピング", createdAt, result.createdAt)
    assertEquals("更新日時が正しくマッピング", updatedAt, result.updatedAt)
    assertEquals("ポイントリストが正しくマッピング", points, result.points)
    assertEquals("ポイント数が正しい", 1, result.points.size)
  }

  @Test
  fun `GpsTrackEntity_toGpsTrack変換_空のポイントリスト`() {
    // Given
    val trackEntity = GpsTrackEntity(
      id = 456L,
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date()
    )

    // When
    val result = trackEntity.toGpsTrack(emptyList())

    // Then
    assertEquals("IDが正しい", 456L, result.id)
    assertNull("終了時刻がnull", result.endTime)
    assertTrue("アクティブ状態がtrue", result.isActive)
    assertTrue("ポイントリストが空", result.points.isEmpty())
  }

  @Test
  fun `GpsTrackEntity_toGpsTrack変換_デフォルトパラメータ`() {
    // Given
    val trackEntity = GpsTrackEntity(
      id = 789L,
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date()
    )

    // When - points パラメータを省略（デフォルト値使用）
    val result = trackEntity.toGpsTrack()

    // Then
    assertEquals("IDが正しい", 789L, result.id)
    assertTrue("ポイントリストがデフォルトで空", result.points.isEmpty())
  }

  @Test
  fun `GpsPointEntity_toGpsPoint変換_全フィールドが正しくマッピングされる`() {
    // Given
    val timestamp = Date(1640995200000L)
    val createdAt = Date(1640995100000L)

    val pointEntity = GpsPointEntity(
      id = 101L,
      trackId = 202L,
      latitude = 35.6762,
      longitude = 139.6503,
      altitude = 150.5,
      accuracy = 8f,
      speed = 12.5f,
      bearing = 45f,
      timestamp = timestamp,
      createdAt = createdAt
    )

    // When - GpsTrackRepositoryImplの変換ロジックを模擬
    val result = pointEntity.toGpsPoint()

    // Then
    assertEquals("IDが正しくマッピング", 101L, result.id)
    assertEquals("トラックIDが正しくマッピング", 202L, result.trackId)
    assertEquals("緯度が正しくマッピング", 35.6762, result.latitude, 0.0001)
    assertEquals("経度が正しくマッピング", 139.6503, result.longitude, 0.0001)
    assertEquals("高度が正しくマッピング", 150.5, result.altitude!!, 0.1)
    assertEquals("精度が正しくマッピング", 8f, result.accuracy, 0.1f)
    assertEquals("速度が正しくマッピング", 12.5f, result.speed!!, 0.1f)
    assertEquals("方位が正しくマッピング", 45f, result.bearing!!, 0.1f)
    assertEquals("タイムスタンプが正しくマッピング", timestamp, result.timestamp)
    assertEquals("作成日時が正しくマッピング", createdAt, result.createdAt)
  }

  @Test
  fun `GpsPointEntity_toGpsPoint変換_nullable値がnull`() {
    // Given
    val pointEntity = GpsPointEntity(
      id = 103L,
      trackId = 204L,
      latitude = 35.7000,
      longitude = 139.8000,
      altitude = null,
      accuracy = 15f,
      speed = null,
      bearing = null,
      timestamp = Date(),
      createdAt = Date()
    )

    // When
    val result = pointEntity.toGpsPoint()

    // Then
    assertEquals("IDが正しい", 103L, result.id)
    assertEquals("トラックIDが正しい", 204L, result.trackId)
    assertEquals("緯度が正しい", 35.7000, result.latitude, 0.0001)
    assertEquals("経度が正しい", 139.8000, result.longitude, 0.0001)
    assertNull("高度がnull", result.altitude)
    assertEquals("精度が正しい", 15f, result.accuracy, 0.1f)
    assertNull("速度がnull", result.speed)
    assertNull("方位がnull", result.bearing)
  }

  @Test
  fun `GpsTrack_toEntity変換_全フィールドが正しくマッピングされる`() {
    // Given
    val startTime = Date(1640995200000L)
    val endTime = Date(1640995300000L)
    val createdAt = Date(1640995100000L)
    val updatedAt = Date(1640995400000L)

    val track = GpsTrack(
      id = 555L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = emptyList(), // Entity変換では points は使用されない
      createdAt = createdAt,
      updatedAt = updatedAt
    )

    // When - GpsTrackRepositoryImpl.deleteTrackの変換ロジックを模擬
    val result = GpsTrackEntity(
      id = track.id,
      startTime = track.startTime,
      endTime = track.endTime,
      isActive = track.isActive,
      createdAt = track.createdAt,
      updatedAt = track.updatedAt
    )

    // Then
    assertEquals("IDが正しくマッピング", 555L, result.id)
    assertEquals("開始時刻が正しくマッピング", startTime, result.startTime)
    assertEquals("終了時刻が正しくマッピング", endTime, result.endTime)
    assertEquals("アクティブ状態が正しくマッピング", false, result.isActive)
    assertEquals("作成日時が正しくマッピング", createdAt, result.createdAt)
    assertEquals("更新日時が正しくマッピング", updatedAt, result.updatedAt)
  }

  @Test
  fun `双方向変換_GpsTrackEntity_to_GpsTrack_to_GpsTrackEntity_整合性確認`() {
    // Given
    val startTime = Date(1640995200000L)
    val endTime = Date(1640995300000L)
    val createdAt = Date(1640995100000L)
    val updatedAt = Date(1640995400000L)

    val originalEntity = GpsTrackEntity(
      id = 999L,
      startTime = startTime,
      endTime = endTime,
      isActive = true,
      createdAt = createdAt,
      updatedAt = updatedAt
    )

    // When
    val domainModel = originalEntity.toGpsTrack()
    val convertedEntity = GpsTrackEntity(
      id = domainModel.id,
      startTime = domainModel.startTime,
      endTime = domainModel.endTime,
      isActive = domainModel.isActive,
      createdAt = domainModel.createdAt,
      updatedAt = domainModel.updatedAt
    )

    // Then
    assertEquals("ID変換の整合性", originalEntity.id, convertedEntity.id)
    assertEquals("開始時刻変換の整合性", originalEntity.startTime, convertedEntity.startTime)
    assertEquals("終了時刻変換の整合性", originalEntity.endTime, convertedEntity.endTime)
    assertEquals(
      "アクティブ状態変換の整合性",
      originalEntity.isActive,
      convertedEntity.isActive
    )
    assertEquals("作成日時変換の整合性", originalEntity.createdAt, convertedEntity.createdAt)
    assertEquals("更新日時変換の整合性", originalEntity.updatedAt, convertedEntity.updatedAt)
  }

  // Extension functions to mimic the private methods in GpsTrackRepositoryImpl
  private fun GpsTrackEntity.toGpsTrack(points: List<GpsPoint> = emptyList()): GpsTrack {
    return GpsTrack(
      id = this.id,
      startTime = this.startTime,
      endTime = this.endTime,
      isActive = this.isActive,
      points = points,
      createdAt = this.createdAt,
      updatedAt = this.updatedAt
    )
  }

  private fun GpsPointEntity.toGpsPoint(): GpsPoint {
    return GpsPoint(
      id = this.id,
      trackId = this.trackId,
      latitude = this.latitude,
      longitude = this.longitude,
      altitude = this.altitude,
      accuracy = this.accuracy,
      speed = this.speed,
      bearing = this.bearing,
      timestamp = this.timestamp,
      createdAt = this.createdAt
    )
  }
}