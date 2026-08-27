package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class StopDetectorTest {

  private fun point(
    index: Int,
    lat: Double,
    lon: Double,
    timeSec: Long,
  ): GpsPoint = GpsPoint(
    id = index.toLong(),
    trackId = 1L,
    latitude = lat,
    longitude = lon,
    altitude = null,
    accuracy = 5f,
    speed = null,
    bearing = null,
    timestamp = Date(timeSec * 1000L),
    createdAt = Date(0L),
  )

  @Test
  fun detect_stationaryOverThreshold_returnsOneStop() {
    // 50m圏内・4分滞在
    val points = listOf(
      point(0, 35.0000, 139.0000, 0),
      point(1, 35.0002, 139.0000, 60),
      point(2, 35.0000, 139.0002, 120),
      point(3, 35.0001, 139.0000, 180),
      point(4, 35.0002, 139.0001, 240),
    )
    val stops = StopDetector.detect(points)
    assertEquals(1, stops.size)
    assertEquals(4, stops[0].durationMinutes)
  }

  @Test
  fun detect_movingContinuously_returnsNoStop() {
    // 各点が約111m離れて移動 → 立ち寄りなし
    val points = (0..4).map { i -> point(i, 35.0 + i * 0.001, 139.0, i * 60L) }
    assertTrue(StopDetector.detect(points).isEmpty())
  }

  @Test
  fun detect_shortDwell_returnsNoStop() {
    // 50m圏内だが2分しか滞在していない → 立ち寄りではない
    val points = listOf(
      point(0, 35.0000, 139.0000, 0),
      point(1, 35.0001, 139.0000, 60),
      point(2, 35.0000, 139.0001, 120),
    )
    assertTrue(StopDetector.detect(points).isEmpty())
  }

  @Test
  fun detect_twoSeparateStops_returnsTwo() {
    val points = listOf(
      // 立ち寄りA
      point(0, 35.0000, 139.0000, 0),
      point(1, 35.0002, 139.0000, 60),
      point(2, 35.0000, 139.0002, 120),
      point(3, 35.0001, 139.0000, 180),
      point(4, 35.0002, 139.0001, 240),
      // 約1.1km移動して立ち寄りB
      point(5, 35.0100, 139.0000, 300),
      point(6, 35.0102, 139.0000, 360),
      point(7, 35.0100, 139.0002, 420),
      point(8, 35.0101, 139.0000, 480),
      point(9, 35.0102, 139.0001, 540),
    )
    assertEquals(2, StopDetector.detect(points).size)
  }

  @Test
  fun detect_gapBetweenTwoPoints_isNotDwell() {
    // 同じ場所の2点が10分空いている＝その間どこに居たかは分からない。滞在に数えない（→ adr/0022）。
    val points = listOf(
      point(0, 35.0000, 139.0000, 0),
      point(1, 35.0001, 139.0000, 600),
    )
    assertTrue(StopDetector.detect(points).isEmpty())
  }

  @Test
  fun detect_gapInsideCluster_doesNotMergeDwells() {
    // 同じ場所で2分 → アプリが20分止まる → 戻ってきて2分。
    // またいで数えると「24分の立ち寄り」になるが、どちらの滞在も3分に足りていない。
    val points = listOf(
      point(0, 35.0000, 139.0000, 0),
      point(1, 35.0001, 139.0000, 60),
      point(2, 35.0000, 139.0001, 120),
      point(3, 35.0000, 139.0000, 1320),
      point(4, 35.0001, 139.0000, 1380),
      point(5, 35.0000, 139.0001, 1440),
    )
    assertTrue(StopDetector.detect(points).isEmpty())
  }
}
