package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Date

class PlanEditsTest {

  private fun place(id: Long) = PlanPlace(id, "場所$id", 35.0, 135.0)

  private fun item(id: Long, day: Int, position: Int, placeId: Long? = id) = PlanItem(
    id = id,
    planId = 1,
    dayIndex = day,
    position = position,
    place = placeId?.let { place(it) },
    title = if (placeId == null) "メモ$id" else null,
    note = null,
    plannedTime = null,
    stayMinutes = null,
  )

  private fun plan(days: Int, vararg items: PlanItem) = PlanDetail(Plan(1, "計画", null, null, days, Date(0), Date(0)), items.toList())

  private fun PlanDetail.layout(): List<Pair<Long, Int>> = items.map { it.id to it.dayIndex }

  @Test
  fun withDates_shrinkingMovesRowsOfRemovedDaysToTheEndOfTheLastDay() {
    val detail = plan(3, item(1, 1, 0), item(2, 2, 0), item(3, 3, 0), item(4, 3, 1))

    val shrunk = detail.withDates(LocalDate.of(2026, 11, 30), 2)

    assertEquals(2, shrunk.plan.dayCount)
    assertEquals(LocalDate.of(2026, 11, 30), shrunk.plan.startDate)
    assertEquals(listOf(1L to 1, 2L to 2, 3L to 2, 4L to 2), shrunk.layout())
    assertEquals(listOf(0, 0, 1, 2), shrunk.items.map { it.position })
  }

  @Test
  fun withDayRemoved_movesTheRowsToTheEndOfThePreviousDay_andShiftsLaterDays() {
    val detail = plan(3, item(1, 1, 0), item(2, 2, 0), item(3, 2, 1), item(4, 3, 0))

    val removed = detail.withDayRemoved(2)

    assertEquals(2, removed.plan.dayCount)
    assertEquals(listOf(1L to 1, 2L to 1, 3L to 1, 4L to 2), removed.layout())
  }

  @Test
  fun withDayRemoved_firstDay_movesTheRowsToTheStartOfTheNextDay() {
    val detail = plan(3, item(1, 1, 0), item(2, 2, 0), item(3, 3, 0))

    val removed = detail.withDayRemoved(1)

    assertEquals(2, removed.plan.dayCount)
    assertEquals(listOf(1L to 1, 2L to 1, 3L to 2), removed.layout())
  }

  @Test
  fun withDayRemoved_dayTrip_isNotRemoved() {
    val detail = plan(1, item(1, 1, 0))

    assertEquals(detail, detail.withDayRemoved(1))
  }

  @Test
  fun withPlacesAdded_andMemo_goToTheEndOfTheDay() {
    var next = -1L
    val detail = plan(2, item(1, 1, 0), item(2, 2, 0))
      .withPlacesAdded(1, listOf(place(10), place(11))) { next-- }
      .withMemoAdded(1, "昼食", 720, " ", id = -9)

    assertEquals(listOf(1L to 1, -1L to 1, -2L to 1, -9L to 1, 2L to 2), detail.layout())
    val memo = detail.items.first { it.id == -9L }
    assertEquals(720, memo.plannedTime)
    assertEquals("空のメモは持たない", null, memo.note)
  }

  @Test
  fun withOrder_movesRowsAcrossDays() {
    val detail = plan(2, item(1, 1, 0), item(2, 1, 1), item(3, 2, 0))

    val reordered = detail.withOrder(listOf(1L to 1, 3L to 1, 2L to 2))

    assertEquals(listOf(1L to 1, 3L to 1, 2L to 2), reordered.layout())
  }

  @Test
  fun removeAndRestore_bringsTheRowBackToTheSamePlace() {
    val detail = plan(1, item(1, 1, 0), item(2, 1, 1), item(3, 1, 2))

    val (removedDetail, removed) = detail.withItemRemoved(2)
    assertNotNull(removed)
    assertEquals(listOf(1L, 3L), removedDetail.items.map { it.id })

    val restored = removedDetail.withItemRestored(removed!!)
    assertEquals(listOf(1L, 2L, 3L), restored.items.map { it.id })
  }

  @Test
  fun withItemUpdated_keepsPlaceRowsNamedByThePlace_andDepartureIsArrivalPlusStay() {
    val detail = plan(1, item(1, 1, 0), item(2, 1, 1, placeId = null))
      .withItemUpdated(1, title = "無視される", plannedTime = 600, stayMinutes = 90, note = null)
      .withItemUpdated(2, title = "夕食", plannedTime = 1080, stayMinutes = 30, note = "予約")

    val (placeRow, memoRow) = detail.items
    assertEquals("場所1", placeRow.label)
    assertEquals(690, placeRow.plannedDeparture)
    assertEquals("夕食", memoRow.label)
    assertEquals("メモの行は滞在の目安を持たない", null, memoRow.stayMinutes)
  }

  @Test
  fun sameContentAs_ignoresIdsButNotOrder() {
    val a = plan(1, item(1, 1, 0), item(2, 1, 1))
    val sameButOtherIds = plan(1, item(1, 1, 0).copy(id = -5), item(2, 1, 1).copy(id = -6))
    val reordered = a.withOrder(listOf(2L to 1, 1L to 1))

    assertTrue(a.sameContentAs(sameButOtherIds))
    assertFalse(a.sameContentAs(reordered))
    assertFalse(a.sameContentAs(a.withTitleAndNote("別の名前", null)))
  }
}
