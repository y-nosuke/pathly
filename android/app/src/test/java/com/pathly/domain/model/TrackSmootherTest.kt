package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TrackSmootherTest {

  private fun point(
    index: Int,
    lat: Double,
    lon: Double,
    accuracy: Float = 5f,
    timeSec: Long = index * 10L,
  ): GpsPoint = GpsPoint(
    id = index.toLong(),
    trackId = 1L,
    latitude = lat,
    longitude = lon,
    altitude = null,
    accuracy = accuracy,
    speed = null,
    bearing = null,
    timestamp = Date(timeSec * 1000L),
    createdAt = Date(0L),
  )

  @Test
  fun smooth_withFewerThanThreePoints_returnsInput() {
    val points = listOf(point(0, 35.0, 139.0), point(1, 35.001, 139.0))
    assertEquals(points, TrackSmoother.smooth(points))
  }

  @Test
  fun smooth_removesGpsJump() {
    val points = listOf(
      point(0, 35.0000, 139.0),
      point(1, 36.0000, 139.0), // 10秒で約111km→非現実的な速度→除外
      point(2, 35.0010, 139.0),
    )
    val result = TrackSmoother.smooth(points)
    assertTrue(result.none { it.latitude > 35.5 })
  }

  @Test
  fun smooth_keepsPointCountAndPreservesEndpoints() {
    val points = (0..6).map { i ->
      point(i, 35.0000 + i * 0.0002, 139.0) // 約22m間隔・良好な精度
    }
    val result = TrackSmoother.smooth(points)
    assertEquals(7, result.size)
    // 端点は平滑化でずらさない
    assertEquals(points.first().latitude, result.first().latitude, 0.0)
    assertEquals(points.last().latitude, result.last().latitude, 0.0)
  }

  @Test
  fun smooth_accuracyWeighting_pullsNoisyPointTowardAccurateNeighbors() {
    // 中央(index2)だけ精度が悪く東へ約90mずれた点。精度重みで良い近傍へ引き戻される。
    val points = listOf(
      point(0, 35.0000, 139.0000),
      point(1, 35.0002, 139.0000),
      point(2, 35.0004, 139.0010, accuracy = 100f),
      point(3, 35.0006, 139.0000),
      point(4, 35.0008, 139.0000),
    )
    val result = TrackSmoother.smooth(points)
    // 補正後の中央点の経度は、生(139.0010)よりも良い近傍(139.0000)へ寄る
    assertTrue(result[2].longitude < 139.0003)
  }

  @Test
  fun turningDegrees_straightLineIsZero() {
    val straight = (0..4).map { i -> point(i, 35.0 + i * 0.001, 139.0) }
    assertEquals(0.0, TrackSmoother.totalTurningDegrees(straight), 1.0)
  }
}
