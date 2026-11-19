package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import com.pathly.util.DateFormatters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class HistoryListTextTest {

  private val start = Date(1_700_000_000_000L)

  private fun track(minutes: Long, meters: Double, name: String? = null, stopCount: Int = 0) = GpsTrack(
    id = minutes,
    startTime = start,
    endTime = Date(start.time + minutes * 60_000),
    isActive = false,
    name = name,
    stopCount = stopCount,
    storedDistanceMeters = meters,
    createdAt = start,
    updatedAt = start,
  )

  @Test
  fun statsText_notFiltered_showsCountDistanceAndDuration() {
    val tracks = listOf(track(minutes = 90, meters = 1_250.0), track(minutes = 45, meters = 3_000.0))
    assertEquals("2 回 ・ 4.3 km ・ 2 時間 15 分", historyStatsText(tracks, totalCount = 2, isFiltered = false))
  }

  // 絞り込み中は、絞った数字を全体と取り違えないよう全体の数を添える（一致していても絞り込み中であることは出す）。
  @Test
  fun statsText_filtered_showsShownAndTotalCounts() {
    val tracks = listOf(track(minutes = 30, meters = 500.0))
    assertEquals("1 / 4 回 ・ 0.5 km ・ 30 分", historyStatsText(tracks, totalCount = 4, isFiltered = true))
    assertEquals("1 / 1 回 ・ 0.5 km ・ 30 分", historyStatsText(tracks, totalCount = 1, isFiltered = true))
  }

  // 合計の距離は 100 km 以上で小数を落として 3 桁区切りにする（帯の数字を短く読みやすく）。
  @Test
  fun formatTotalKm_groupsLongDistances() {
    assertEquals("4.3 km", formatTotalKm(4_250.0))
    assertEquals("99.9 km", formatTotalKm(99_940.0))
    assertEquals("3,815 km", formatTotalKm(3_815_040.0))
  }

  @Test
  fun formatDuration_dropsZeroParts() {
    assertEquals("0 分", formatDuration(0))
    assertEquals("45 分", formatDuration(45))
    assertEquals("3 時間", formatDuration(180))
    assertEquals("41 時間 20 分", formatDuration(41 * 60 + 20))
  }

  // 名前があるときは日付を 2 行目に添え、名前が無いとき（日付が見出し）は省く。
  @Test
  fun rowSummaryParts_addsDateOnlyWhenNamed() {
    val time = "${DateFormatters.shortTime(start)}–${DateFormatters.shortTime(Date(start.time + 90 * 60_000))}"
    assertEquals(
      listOf("${DateFormatters.shortDate(start)} $time", "1.8 km", "立ち寄り 3"),
      trackRowSummaryParts(track(minutes = 90, meters = 1_800.0, name = "鎌倉", stopCount = 3)),
    )
    assertEquals(listOf(time, "1.8 km", "立ち寄り 0"), trackRowSummaryParts(track(minutes = 90, meters = 1_800.0)))
  }
}
