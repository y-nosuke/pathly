package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * 欠落（GPS が取れなかった時間帯）を、繋がっていない区間として扱えているかを検証する。
 * 繋いでしまうと、地図に通っていない直線が出て距離にも乗る（→ adr/0022）。
 */
class TrackSegmentsTest {

  private fun point(timeSec: Long, lat: Double = 35.0, lon: Double = 139.0): GpsPoint = GpsPoint(
    id = timeSec,
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
  fun `空なら区間も空`() {
    assertEquals(emptyList<List<GpsPoint>>(), TrackSegments.split(emptyList()))
  }

  @Test
  fun `途切れが無ければ1区間`() {
    val points = (0..5).map { point(it * 10L) }

    val segments = TrackSegments.split(points)

    assertEquals(1, segments.size)
    assertEquals(points, segments.single())
    assertFalse(TrackSegments.hasGap(points))
  }

  @Test
  fun `しきい値以上空いたら区切る`() {
    // 10秒間隔で3点 → 10分の空白 → また3点。
    val before = (0..2).map { point(it * 10L) }
    val after = (0..2).map { point(600L + it * 10L) }

    val segments = TrackSegments.split(before + after)

    assertEquals(2, segments.size)
    assertEquals(before, segments[0])
    assertEquals(after, segments[1])
    assertTrue(TrackSegments.hasGap(before + after))
  }

  @Test
  fun `1回や2回の取りこぼしでは区切らない`() {
    // 最長の記録間隔（60秒）を2回落としたくらいでは切らない。
    val points = listOf(point(0L), point(120L), point(130L))

    assertEquals(1, TrackSegments.split(points).size)
  }

  @Test
  fun `距離は途切れた区間をまたがない`() {
    // 同じ場所で3点 → 10分の空白 → 1km離れた場所で3点。
    val before = (0..2).map { point(it * 10L, lat = 35.0) }
    val after = (0..2).map { point(600L + it * 10L, lat = 35.01) }

    val bridged = TrackSmoother.totalDistanceMeters(before + after)
    val excluded = TrackSmoother.distanceExcludingGaps(before + after)

    // またいで足すと、記録が無い1km強がそのまま乗ってしまう。
    assertTrue("またぐと空白ぶんが乗る", bridged > 1000.0)
    assertTrue("またがなければほぼ0", excluded < 1.0)
  }

  @Test
  fun `平滑化は欠落をまたいで平均しない`() {
    // 空白の前後で大きく離れた点を、窓（5点）が混ぜてしまうと境目が引き寄せられる。
    val before = (0..4).map { point(it * 10L, lat = 35.0) }
    val after = (0..4).map { point(600L + it * 10L, lat = 36.0) }

    val segments = TrackSmoother.smoothSegments(before + after)

    assertEquals(2, segments.size)
    // 区間ごとに平滑化していれば、どの点も元の緯度から動かない（同じ場所の連なりなので）。
    assertTrue("前半が引きずられない", segments[0].all { it.latitude < 35.5 })
    assertTrue("後半が引きずられない", segments[1].all { it.latitude > 35.5 })
  }
}
