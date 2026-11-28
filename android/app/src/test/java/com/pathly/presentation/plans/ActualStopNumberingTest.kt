package com.pathly.presentation.plans

import com.pathly.domain.model.Place
import com.pathly.domain.model.Stop
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class ActualStopNumberingTest {

  private val zone = ZoneId.of("Asia/Tokyo")
  private val nov30 = LocalDate.of(2026, 11, 30)

  private fun at(day: LocalDate, hour: Int) = Date.from(day.atTime(hour, 0).atZone(zone).toInstant())

  private fun stop(id: Long, arrival: Date, trackId: Long = 1L) = ActualStop(
    Stop(id, Place(id, "場所$id", 35.0, 135.0, null, null, null, null, null, Date(0), Date(0)), trackId, arrival, arrival),
    onRecording = false,
    live = false,
  )

  @Test
  fun numbersAreByArrivalOrder_restartingEachDay_acrossTracks() {
    val numbered = listOf(
      stop(1, at(nov30, 15), trackId = 2), // 1 日目の午後（別の経路）
      stop(2, at(nov30, 10)), // 1 日目の午前
      stop(3, at(nov30.plusDays(1), 9)), // 2 日目
      stop(4, at(nov30, 12)),
    ).numberedByDay(zone).associateBy { it.stop.id }

    assertEquals(listOf(3, 1, 1, 2), listOf(1L, 2L, 3L, 4L).map { numbered.getValue(it).number })
    assertEquals(nov30.plusDays(1), numbered.getValue(3).date)
  }
}
