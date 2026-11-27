package com.pathly.presentation.plans

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanFormatTest {

  private fun at(days: Int, hour: Int, minute: Int = 0): Int = days * 24 * 60 + hour * 60 + minute

  @Test
  fun formatClock_sameDay_hasNoPrefix() {
    assertEquals("0:00", formatClock(at(0, 0)))
    assertEquals("23:59", formatClock(at(0, 23, 59)))
  }

  @Test
  fun formatClock_nextDays_namesHowManyDaysLater() {
    assertEquals("翌0:00", formatClock(at(1, 0)))
    assertEquals("翌1:30", formatClock(at(1, 1, 30)))
    assertEquals("翌々10:00", formatClock(at(2, 10)))
    assertEquals("3日後10:00", formatClock(at(3, 10)))
    assertEquals("4日後2:05", formatClock(at(4, 2, 5)))
  }
}
