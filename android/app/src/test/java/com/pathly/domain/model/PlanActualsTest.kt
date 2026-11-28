package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class PlanActualsTest {

  private val zone = ZoneId.of("Asia/Tokyo")
  private val nov30 = LocalDate.of(2026, 11, 30)

  private fun plan(id: Long, start: LocalDate?, days: Int = 1, title: String = "計画$id") = Plan(id, title, null, start, days, Date(0), Date(0))

  @Test
  fun covers_isTheDaysFromTheStart() {
    val trip = plan(1, nov30, days = 2)

    assertFalse(PlanActuals.covers(trip, nov30.minusDays(1)))
    assertTrue(PlanActuals.covers(trip, nov30))
    assertTrue(PlanActuals.covers(trip, nov30.plusDays(1)))
    assertFalse(PlanActuals.covers(trip, nov30.plusDays(2)))
    assertFalse("日付なしは入らない", PlanActuals.covers(plan(2, null), nov30))
  }

  @Test
  fun rangeMillis_isFromTheStartDayToTheDayAfterTheLast_inTheZone() {
    val range = PlanActuals.rangeMillis(plan(1, nov30, days = 2), zone)!!

    assertEquals(nov30.atStartOfDay(zone).toInstant().toEpochMilli(), range.first)
    assertEquals(nov30.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() - 1, range.last)
    assertNull(PlanActuals.rangeMillis(plan(2, null), zone))
  }

  @Test
  fun multiDayPlans_cannotShareDays_exceptTheLastAndFirstDay() {
    val kyoto = plan(1, nov30, days = 3, title = "京都") // 11/30〜12/2

    assertEquals("中の日が重なる", kyoto, PlanActuals.overlappingPlan(plan(2, nov30.plusDays(1), days = 2), listOf(kyoto)))
    assertEquals("すっぽり入る", kyoto, PlanActuals.overlappingPlan(plan(2, nov30.minusDays(1), days = 5), listOf(kyoto)))
    assertNull("最後の日に次が始まるのはよい", PlanActuals.overlappingPlan(plan(2, nov30.plusDays(2), days = 2), listOf(kyoto)))
    assertNull("開始日に前が終わるのはよい", PlanActuals.overlappingPlan(plan(2, nov30.minusDays(1), days = 2), listOf(kyoto)))
    assertNull("自分自身とは比べない", PlanActuals.overlappingPlan(kyoto.copy(dayCount = 4), listOf(kyoto)))
  }

  @Test
  fun dayTrips_mayOverlapAnything() {
    val kyoto = plan(1, nov30, days = 3)
    val arashiyama = plan(2, nov30.plusDays(1))

    assertNull("旅行の途中の日に日帰り", PlanActuals.overlappingPlan(arashiyama, listOf(kyoto)))
    assertNull("同じ日に日帰りが 2 つ", PlanActuals.overlappingPlan(plan(3, nov30.plusDays(1)), listOf(arashiyama)))
    assertNull("日帰りがあっても旅行は置ける", PlanActuals.overlappingPlan(plan(4, nov30, days = 2), listOf(arashiyama)))
    assertNull("日付なしは関係しない", PlanActuals.overlappingPlan(plan(5, null, days = 3), listOf(kyoto)))
  }

  @Test
  fun visited_arePlacesStoppedAtOnAnyLinkedTrack_whateverTheDay() {
    val visits = listOf(TrackVisit(1, 10), TrackVisit(2, 20), TrackVisit(3, 30))

    val visited = PlanActuals.visitedPlaceIds(setOf(1L, 2L), visits)

    assertEquals(setOf(10L, 20L), visited)
    assertEquals("同じ場所の 2 行はどちらも行った", 3, PlanActuals.visitedItemCount(listOf(10L, 10L, 20L, 40L), visited))
    assertEquals(emptySet<Long>(), PlanActuals.visitedPlaceIds(emptySet(), visits))
  }

  @Test
  fun recordingTrack_isTheActiveLinkedOne() {
    val tracks = listOf(TrackStart(1, Date(0), isActive = false), TrackStart(2, Date(1), isActive = true))

    assertEquals(2L, PlanActuals.recordingTrackId(setOf(1L, 2L), tracks))
    assertNull("結び付いていなければ記録中でない", PlanActuals.recordingTrackId(setOf(1L), tracks))
  }
}
