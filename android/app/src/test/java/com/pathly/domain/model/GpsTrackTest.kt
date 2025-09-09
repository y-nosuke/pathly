package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import kotlin.math.abs

class GpsTrackTest {

  @Test
  fun `totalDistanceMeters_空の座標点リスト_0を返す`() {
    // Given
    val track = createGpsTrack(points = emptyList())

    // When
    val distance = track.totalDistanceMeters

    // Then
    assertEquals(0.0, distance, 0.001)
  }

  @Test
  fun `totalDistanceMeters_1つの座標点_0を返す`() {
    // Given
    val points = listOf(
      createGpsPoint(latitude = 35.6762, longitude = 139.6503), // 東京駅
    )
    val track = createGpsTrack(points = points)

    // When
    val distance = track.totalDistanceMeters

    // Then
    assertEquals(0.0, distance, 0.001)
  }

  @Test
  fun `totalDistanceMeters_2つの座標点_東京駅から新宿駅_約7km`() {
    // Given
    val points = listOf(
      createGpsPoint(latitude = 35.6762, longitude = 139.6503), // 東京駅
      createGpsPoint(latitude = 35.6896, longitude = 139.7006), // 新宿駅
    )
    val track = createGpsTrack(points = points)

    // When
    val distance = track.totalDistanceMeters

    // Then
    // 東京駅から新宿駅は約4-5km
    assertTrue(
      "計算された距離が3-6kmの範囲にある (実際: ${distance}m)",
      distance in 3000.0..6000.0,
    )
  }

  @Test
  fun `totalDistanceMeters_複数の座標点_距離が累積される`() {
    // Given - 直線上の3点（各点間100m）
    val points = listOf(
      createGpsPoint(latitude = 35.0000, longitude = 139.0000),
      createGpsPoint(latitude = 35.0009, longitude = 139.0000), // 約100m北
      createGpsPoint(latitude = 35.0018, longitude = 139.0000), // さらに約100m北
    )
    val track = createGpsTrack(points = points)

    // When
    val distance = track.totalDistanceMeters

    // Then
    // 2回の移動で合計約200m
    assertTrue("計算された距離が180-220mの範囲にある", distance in 180.0..220.0)
  }

  @Test
  fun `totalDistanceMeters_同じ座標点_0を返す`() {
    // Given
    val sameLocation = createGpsPoint(latitude = 35.6762, longitude = 139.6503)
    val points = listOf(sameLocation, sameLocation, sameLocation)
    val track = createGpsTrack(points = points)

    // When
    val distance = track.totalDistanceMeters

    // Then
    assertEquals(0.0, distance, 0.001)
  }

  @Test
  fun `totalDistanceMeters_Haversine公式の精度検証`() {
    // Given - 赤道上の1度の移動（約111km）
    val points = listOf(
      createGpsPoint(latitude = 0.0, longitude = 0.0),
      createGpsPoint(latitude = 0.0, longitude = 1.0),
    )
    val track = createGpsTrack(points = points)

    // When
    val distance = track.totalDistanceMeters

    // Then
    // 赤道上の1度は約111,320m
    val expectedDistance = 111_320.0
    val tolerance = 1000.0 // 1km の誤差許容
    assertTrue(
      "Haversine公式の計算精度が正しい (${distance}m, 期待値: ${expectedDistance}m)",
      abs(distance - expectedDistance) < tolerance,
    )
  }

  private fun createGpsTrack(
    id: Long = 1L,
    points: List<GpsPoint> = emptyList(),
    startTime: Date = Date(),
    endTime: Date? = null,
    isActive: Boolean = false,
    createdAt: Date = Date(),
    updatedAt: Date = Date(),
  ): GpsTrack {
    return GpsTrack(
      id = id,
      startTime = startTime,
      endTime = endTime,
      isActive = isActive,
      points = points,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
  }

  private fun createGpsPoint(
    id: Long = 1L,
    trackId: Long = 1L,
    latitude: Double,
    longitude: Double,
    altitude: Double? = null,
    accuracy: Float? = null,
    speed: Float? = null,
    bearing: Float? = null,
    timestamp: Date = Date(),
    createdAt: Date = Date(),
  ): GpsPoint {
    return GpsPoint(
      id = id,
      trackId = trackId,
      latitude = latitude,
      longitude = longitude,
      altitude = altitude,
      accuracy = accuracy ?: 10f,
      speed = speed,
      bearing = bearing,
      timestamp = timestamp,
      createdAt = createdAt,
    )
  }
}
