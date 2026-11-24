package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.GroupOpenState
import com.pathly.domain.model.TrackGrouping
import com.pathly.presentation.common.ListGroup
import com.pathly.presentation.common.ListGroupRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/** 履歴一覧の年月のまとまり（年 → 月で分けて開閉する → specs/screens.md「2. 履歴画面」）。 */
class HistoryGroupingTest {

  private val zone = ZoneId.systemDefault()

  private fun track(id: Long, start: LocalDateTime, meters: Double = 1000.0, name: String? = null) = GpsTrack(
    id = id,
    startTime = Date.from(start.atZone(zone).toInstant()),
    endTime = Date.from(start.plusHours(1).atZone(zone).toInstant()),
    isActive = false,
    name = name,
    storedDistanceMeters = meters,
    createdAt = Date(),
    updatedAt = Date(),
  )

  // 月の境目: 10/31 23:59 は 10 月、11/1 0:00 は 11 月（開始の日の年月で数える）。年の境目も同じ。
  private val oct31 = track(1, LocalDateTime.of(2026, 10, 31, 23, 59), meters = 3000.0)
  private val nov1 = track(2, LocalDateTime.of(2026, 11, 1, 0, 0), meters = 5000.0)
  private val nov20 = track(3, LocalDateTime.of(2026, 11, 20, 9, 0), meters = 2000.0, name = "鎌倉")
  private val dec31 = track(4, LocalDateTime.of(2025, 12, 31, 23, 30))
  private val jan1 = track(5, LocalDateTime.of(2026, 1, 1, 0, 10))
  private val tracks = listOf(nov20, nov1, oct31, jan1, dec31)

  private fun List<ListGroup<GpsTrack>>.describe(): List<String> = flatMap { year ->
    listOf(year.label + ":" + year.count) + year.children.map { "  " + it.label + ":" + it.items.map { t -> t.id } }
  }

  @Test
  fun groupsByStartDateIntoYearThenMonth_acrossMonthAndYearBoundaries() {
    val groups = groupTracksByMonth(tracks, newestFirst = true, zone = zone)

    assertEquals(
      listOf("2026年:4", "  11月:[3, 2]", "  10月:[1]", "  1月:[5]", "2025年:1", "  12月:[4]"),
      groups.describe(),
    )
  }

  @Test
  fun oldestFirstReversesYearsAndMonthsButKeepsTheOrderInside() {
    val groups = groupTracksByMonth(tracks, newestFirst = false, zone = zone)

    // まとまりの中は渡した並び（ここでは 3 → 2）のまま。
    assertEquals(
      listOf("2025年:1", "  12月:[4]", "2026年:4", "  1月:[5]", "  10月:[1]", "  11月:[3, 2]"),
      groups.describe(),
    )
  }

  @Test
  fun defaultOpensOnlyTheNewestYearAndMonth() {
    assertEquals(setOf("year:2026", "month:2026-11"), defaultOpenMonthKeys(tracks, zone))
    assertTrue(defaultOpenMonthKeys(emptyList(), zone).isEmpty())
  }

  @Test
  fun groupSummary_countsAndSumsDistanceOfEveryTrackInside() {
    val year = groupTracksByMonth(tracks, newestFirst = true, zone = zone).first()

    assertEquals("2 回 ・ 7.0 km", trackGroupSummary(year.children.first()))
    assertEquals("4 回 ・ 11.0 km", trackGroupSummary(year))
  }

  private fun HistoryState.describeRows(): List<String> = listRows.map { row ->
    when (row) {
      is ListGroupRow.Header -> "${row.depth}:${row.group.label}:${if (row.expanded) "open" else "closed"}"
      is ListGroupRow.Item -> "${row.depth}:#${row.item.id}"
    }
  }

  @Test
  fun rows_notGrouped_areJustTheTracks() {
    val state = HistoryState(tracks = tracks)

    assertEquals(state.visibleTracks.map { it.id }, state.listRows.map { (it as ListGroupRow.Item).item.id })
  }

  @Test
  fun rows_openTheNewestYearAndMonthByDefault() {
    val state = HistoryState(tracks = tracks, grouping = TrackGrouping.MONTH)

    assertEquals(
      listOf("0:2026年:open", "1:11月:open", "2:#3", "2:#2", "1:10月:closed", "1:1月:closed", "0:2025年:closed"),
      state.describeRows(),
    )
  }

  @Test
  fun rows_followWhatWasToggled_evenAfterTheDefaultMoves() {
    // 11 月を閉じ、2025 年を開いた。
    val open = GroupOpenState().toggled("month:2026-11", currentlyOpen = true).toggled("year:2025", currentlyOpen = false)
    val state = HistoryState(tracks = tracks, grouping = TrackGrouping.MONTH, groupOpenState = open)
    assertEquals(
      listOf("0:2026年:open", "1:11月:closed", "1:10月:closed", "1:1月:closed", "0:2025年:open", "1:12月:closed"),
      state.describeRows(),
    )

    // 12 月の記録ができると、既定は 12 月に移る。押して閉じた 11 月は閉じたまま。
    val dec = track(6, LocalDateTime.of(2026, 12, 2, 10, 0))
    val later = state.copy(tracks = listOf(dec) + tracks)
    assertTrue(later.isGroupOpen("month:2026-12"))
    assertFalse(later.isGroupOpen("month:2026-11"))
  }

  @Test
  fun rows_monthOrderFollowsDateDirection_butIsNewestFirstForOtherAxes() {
    val oldest = HistoryState(tracks = tracks, grouping = TrackGrouping.MONTH, sortDescending = false)
    assertEquals("2025年", (oldest.listRows.first() as ListGroupRow.Header).group.label)

    val byDistanceShortest = HistoryState(tracks = tracks, grouping = TrackGrouping.MONTH, sort = TrackSort.DISTANCE, sortDescending = false)
    val rows = byDistanceShortest.describeRows()
    assertEquals("0:2026年:open", rows.first())
    // 11 月の中は距離の短い順（2 km の #3 → 5 km の #2）。
    assertEquals(listOf("2:#3", "2:#2"), rows.filter { it.startsWith("2:") })
  }

  @Test
  fun rows_countOnlyTracksLeftByFiltersAndDropEmptyGroups() {
    val state = HistoryState(tracks = tracks, grouping = TrackGrouping.MONTH, nameQuery = "鎌倉")

    // 名前の検索中は残ったまとまりをすべて開いて見せる。
    assertEquals(listOf("0:2026年:open", "1:11月:open", "2:#3"), state.describeRows())
    assertEquals(1, (state.listRows.first() as ListGroupRow.Header).group.count)
  }
}
