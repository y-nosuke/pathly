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
  fun smooth_removesLowAccuracyPoint() {
    val points = listOf(
      point(0, 35.0000, 139.0),
      point(1, 35.0010, 139.0, accuracy = 50f), // 精度が悪い→除外
      point(2, 35.0020, 139.0),
    )
    val result = TrackSmoother.smooth(points)
    assertTrue(result.none { it.accuracy > TrackSmoother.MAX_ACCURACY_METERS })
    assertEquals(2, result.size)
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
    assertEquals(2, result.size)
  }

  @Test
  fun smooth_thinsOutTinyMovements() {
    val points = listOf(
      point(0, 35.0000, 139.0),
      point(1, 35.0000001, 139.0), // 直前とほぼ同じ位置→間引き
      point(2, 35.0010, 139.0),
    )
    val result = TrackSmoother.smooth(points)
    assertEquals(2, result.size)
  }

  @Test
  fun smooth_keepsValidPointsAndPreservesEndpoints() {
    val points = (0..4).map { i ->
      point(i, 35.0000 + i * 0.0002, 139.0) // 約22m間隔・良好な精度
    }
    val result = TrackSmoother.smooth(points)
    assertEquals(5, result.size)
    // 端点は平滑化でずらさない
    assertEquals(points.first().latitude, result.first().latitude, 0.0)
    assertEquals(points.last().latitude, result.last().latitude, 0.0)
  }
}
