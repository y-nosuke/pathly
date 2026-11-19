package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

class HistoryStatisticsTest {

  private val zone = ZoneId.of("Asia/Tokyo")
  private val now = YearMonth.of(2026, 11)

  private fun track(id: Long, start: LocalDateTime, minutes: Long, meters: Double): GpsTrack {
    val startDate = Date.from(start.atZone(zone).toInstant())
    return GpsTrack(
      id = id,
      startTime = startDate,
      endTime = Date(startDate.time + minutes * 60_000),
      isActive = false,
      storedDistanceMeters = meters,
      createdAt = startDate,
      updatedAt = startDate,
    )
  }

  @Test
  fun sumsCountDistanceAndDuration() {
    val stats = HistoryStatistics.of(
      listOf(
        track(1, LocalDateTime.of(2026, 11, 1, 10, 0), minutes = 90, meters = 3_000.0),
        track(2, LocalDateTime.of(2026, 10, 5, 10, 0), minutes = 30, meters = 1_500.0),
      ),
      now = now,
      zone = zone,
    )

    assertEquals(2, stats.count)
    assertEquals(4_500.0, stats.distanceMeters, 0.0)
    assertEquals(120L, stats.minutes)
  }

  // 直近 12 か月（今月を含む）を古い月から並べ、経路の開始の月で数える。12 か月より前の経路は入れない。
  @Test
  fun monthly_coversLast12MonthsByStartMonth() {
    val stats = HistoryStatistics.of(
      listOf(
        track(1, LocalDateTime.of(2026, 11, 3, 10, 0), minutes = 10, meters = 1_000.0),
        track(2, LocalDateTime.of(2026, 11, 20, 10, 0), minutes = 10, meters = 2_000.0),
        // 月末の夜に始めて翌月にまたいでも、始めた月に入る。
        track(3, LocalDateTime.of(2026, 9, 30, 23, 30), minutes = 60, meters = 500.0),
        track(4, LocalDateTime.of(2025, 12, 1, 0, 0), minutes = 10, meters = 700.0),
        // 13 か月前。
        track(5, LocalDateTime.of(2025, 11, 30, 23, 0), minutes = 10, meters = 9_000.0),
      ),
      now = now,
      zone = zone,
    )

    assertEquals(12, stats.monthly.size)
    assertEquals(YearMonth.of(2025, 12), stats.monthly.first().month)
    assertEquals(now, stats.monthly.last().month)
    assertEquals(700.0, stats.monthly.first().meters, 0.0)
    assertEquals(500.0, stats.monthly.single { it.month == YearMonth.of(2026, 9) }.meters, 0.0)
    assertEquals(3_000.0, stats.thisMonthMeters, 0.0)
    assertEquals(3_000.0, stats.maxMonthMeters, 0.0)
  }

  // いちばん長い経路は距離で選び、同じなら新しいほう。
  @Test
  fun longest_isLongestDistanceThenNewest() {
    val older = track(1, LocalDateTime.of(2026, 1, 1, 10, 0), minutes = 10, meters = 5_000.0)
    val newer = track(2, LocalDateTime.of(2026, 2, 1, 10, 0), minutes = 10, meters = 5_000.0)
    val shorter = track(3, LocalDateTime.of(2026, 3, 1, 10, 0), minutes = 10, meters = 1_000.0)

    assertEquals(newer, HistoryStatistics.of(listOf(older, shorter, newer), now = now, zone = zone).longest)
  }

  @Test
  fun empty_hasNoLongestAndZeroMonths() {
    val stats = HistoryStatistics.of(emptyList(), now = now, zone = zone)

    assertEquals(0, stats.count)
    assertNull(stats.longest)
    assertEquals(12, stats.monthly.size)
    assertEquals(0.0, stats.maxMonthMeters, 0.0)
  }
}
