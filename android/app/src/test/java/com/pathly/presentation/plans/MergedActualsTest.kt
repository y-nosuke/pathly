package com.pathly.presentation.plans

import com.pathly.domain.model.Place
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.Stop
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class MergedActualsTest {

  private val zone = ZoneId.of("Asia/Tokyo")
  private val day1 = LocalDate.of(2026, 11, 30)
  private val day2 = day1.plusDays(1)

  private fun row(id: Long, day: Int, placeId: Long?) = PlanItem(
    id = id,
    planId = 1L,
    dayIndex = day,
    position = 0,
    place = placeId?.let { PlanPlace(it, "場所$it", 35.0, 135.0) },
    title = if (placeId == null) "メモ" else null,
    note = null,
    plannedTime = null,
    stayMinutes = null,
  )

  private fun stop(id: Long, placeId: Long, date: LocalDate, hour: Int): ActualStop {
    val at = Date.from(date.atTime(hour, 0).atZone(zone).toInstant())
    return ActualStop(
      Stop(id, Place(placeId, "場所$placeId", 35.0, 135.0, null, null, null, null, null, Date(0), Date(0)), 1L, at, at),
      onRecording = false,
      live = false,
      date = date,
    )
  }

  private val dayOf: (LocalDate?) -> Int? = { date -> date?.let { (it.toEpochDay() - day1.toEpochDay() + 1).toInt().takeIf { d -> d in 1..2 } } }

  @Test
  fun plannedVisits_attachToTheRow_andUnplannedOnes_goAfterTheLastEarlierVisitedRow() {
    // 1日目: 計画は A（10）→ メモ → B（20）。実際は A 10時 → X 11時（予定外）→ Y 8時（予定外・どれより前）。
    val items = listOf(row(1, 1, 10), row(2, 1, null), row(3, 1, 20), row(4, 2, 30)).associateBy { it.id }
    val slots = listOf(PlanSlotKey.Day(1), PlanSlotKey.Row(1), PlanSlotKey.Row(2), PlanSlotKey.Row(3), PlanSlotKey.Day(2), PlanSlotKey.Row(4))
    val stops = listOf(stop(101, 10, day1, 10), stop(102, 99, day1, 11), stop(103, 98, day1, 8))

    val merged = mergeActuals(slots, items, stops, dayOf)

    assertEquals(listOf(101L), merged.visitsByPlace.getValue(10).map { it.stop.id })
    assertEquals("A の後ろ", listOf(102L), merged.unplannedAfter.getValue(MergedActuals.afterRowKey(1)).map { it.stop.id })
    assertEquals("どの行より前なら日の頭", listOf(103L), merged.unplannedAfter.getValue(MergedActuals.dayStartKey(1)).map { it.stop.id })
  }

  @Test
  fun aPlannedPlaceVisitedOnAnotherDay_staysOnItsRow_andDoesNotAnchorThatDay() {
    // A は 1 日目の予定だが 2 日目に行った。2 日目の予定外 X は、A を手がかりにせず 2 日目の頭へ。
    val items = listOf(row(1, 1, 10), row(4, 2, 30)).associateBy { it.id }
    val slots = listOf(PlanSlotKey.Day(1), PlanSlotKey.Row(1), PlanSlotKey.Day(2), PlanSlotKey.Row(4))
    val stops = listOf(stop(101, 10, day2, 9), stop(102, 99, day2, 10))

    val merged = mergeActuals(slots, items, stops, dayOf)

    assertEquals(listOf(101L), merged.visitsByPlace.getValue(10).map { it.stop.id })
    assertEquals(listOf(102L), merged.unplannedAfter.getValue(MergedActuals.dayStartKey(2)).map { it.stop.id })
  }

  @Test
  fun unplannedStops_outsideThePlanDays_goToTheEnd() {
    val items = listOf(row(1, 1, 10)).associateBy { it.id }
    val slots = listOf(PlanSlotKey.Day(1), PlanSlotKey.Row(1))

    val merged = mergeActuals(slots, items, listOf(stop(102, 99, day1.minusDays(3), 10)), dayOf)

    assertEquals(listOf(102L), merged.outside.map { it.stop.id })
    assertEquals(emptyMap<String, List<ActualStop>>(), merged.unplannedAfter)
  }
}
