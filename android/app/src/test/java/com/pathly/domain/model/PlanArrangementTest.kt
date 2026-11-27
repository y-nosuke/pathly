package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Date

class PlanArrangementTest {

  @Test
  fun shrink_movesRowsOfRemovedDaysToTheEndOfTheNewLastDay_inDayThenPositionOrder() {
    val slots = listOf(
      PlanSlot(1, 1, 0),
      PlanSlot(2, 2, 0),
      PlanSlot(3, 2, 1),
      PlanSlot(4, 3, 1),
      PlanSlot(5, 3, 0),
      PlanSlot(6, 4, 0),
    )

    val moved = PlanArrangement.shrink(slots, 2)

    // 3 日目（順番 0 → 1）、4 日目の順に、2 日目の末尾（2 から）へ。
    assertEquals(
      listOf(PlanSlot(5, 2, 2), PlanSlot(4, 2, 3), PlanSlot(6, 2, 4)),
      moved,
    )
  }

  @Test
  fun shrink_toAnEmptyLastDay_startsFromZero() {
    val moved = PlanArrangement.shrink(listOf(PlanSlot(1, 1, 0), PlanSlot(2, 3, 0)), 2)

    assertEquals(listOf(PlanSlot(2, 2, 0)), moved)
  }

  @Test
  fun shrink_toDayTrip_putsEverythingOnDayOne() {
    val moved = PlanArrangement.shrink(listOf(PlanSlot(1, 2, 0), PlanSlot(2, 1, 5)), 1)

    assertEquals(listOf(PlanSlot(1, 1, 6)), moved)
  }

  @Test
  fun shrink_withoutRemovedRows_changesNothing() {
    assertTrue(PlanArrangement.shrink(listOf(PlanSlot(1, 1, 0), PlanSlot(2, 2, 0)), 2).isEmpty())
    assertTrue("増やすときも動かない", PlanArrangement.shrink(listOf(PlanSlot(1, 2, 0)), 5).isEmpty())
  }

  @Test
  fun movedByShrink_countsRowsOnRemovedDays() {
    val slots = listOf(PlanSlot(1, 1, 0), PlanSlot(2, 2, 0), PlanSlot(3, 3, 0))

    assertEquals(2, PlanArrangement.movedByShrink(slots, 1))
    assertEquals(0, PlanArrangement.movedByShrink(slots, 3))
  }

  @Test
  fun renumber_numbersEachDayFromZero_inTheGivenOrder() {
    val slots = PlanArrangement.renumber(listOf(10L to 1, 11L to 2, 12L to 1, 13L to 2))

    assertEquals(
      listOf(PlanSlot(10, 1, 0), PlanSlot(11, 2, 0), PlanSlot(12, 1, 1), PlanSlot(13, 2, 1)),
      slots,
    )
  }

  @Test
  fun groupOn_splitsUndatedUpcomingAndPast_byTheLastDay() {
    val today = LocalDate.of(2026, 11, 30)
    fun plan(start: LocalDate?, days: Int) = Plan(1, "計画", null, start, days, Date(0), Date(0))

    assertEquals(PlanGroup.UNDATED, plan(null, 3).groupOn(today))
    assertEquals(PlanGroup.UPCOMING, plan(today, 1).groupOn(today))
    assertEquals("最後の日が今日なら、これから", PlanGroup.UPCOMING, plan(today.minusDays(1), 2).groupOn(today))
    assertEquals(PlanGroup.PAST, plan(today.minusDays(2), 2).groupOn(today))
    assertNull(plan(null, 2).dateOf(1))
    assertEquals(LocalDate.of(2026, 12, 1), plan(today, 2).dateOf(2))
  }

  @Test
  fun placeNumbers_countOnlyPlaceRows_fromOneForEachDay() {
    fun item(id: Long, day: Int, place: Boolean) = PlanItem(
      id = id,
      planId = 1,
      dayIndex = day,
      position = id.toInt(),
      place = if (place) PlanPlace(id, "場所$id", 35.0, 135.0) else null,
      title = if (place) null else "メモ",
      note = null,
      plannedTime = null,
      stayMinutes = null,
    )
    val detail = PlanDetail(
      Plan(1, "計画", null, null, 2, Date(0), Date(0)),
      listOf(item(1, 1, true), item(2, 1, false), item(3, 1, true), item(4, 2, true)),
    )

    assertEquals(mapOf(1L to 1, 3L to 2, 4L to 1), detail.placeNumbers)
  }
}
