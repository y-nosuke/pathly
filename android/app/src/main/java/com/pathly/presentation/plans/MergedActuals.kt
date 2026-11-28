package com.pathly.presentation.plans

import com.pathly.domain.model.PlanItem
import java.time.LocalDate

/**
 * シートで、計画の行と実際の立ち寄りを日ごとに 1 つの並びにまとめたもの（→ docs/specs/plans.md の「実際も見る」）。
 *
 * - 計画の場所で立ち寄ったものは、その行に添える（[visitsByPlace]。別の日に行ったものも、その行に日付つきで添える）。
 * - 計画に無い立ち寄り（予定外）は、実際に行った順で、前後の計画の行の間に挟む（[unplannedAfter]）。
 *   挟む位置は「その日に、その立ち寄りより前に着いた計画の場所のうち最後の行」の後ろ。無ければ日の頭。
 * - 計画の日に入らない予定外の立ち寄り（計画の日付の外・日付の無い何日かの計画）は、末尾にまとめる（[outside]）。
 */
internal data class MergedActuals(
  /** 計画の場所の id → その場所への立ち寄り（着いた順）。 */
  val visitsByPlace: Map<Long, List<ActualStop>> = emptyMap(),
  /** 挟む位置の鍵（[afterRowKey]・[dayStartKey]）→ そこに挟む予定外の立ち寄り（着いた順）。 */
  val unplannedAfter: Map<String, List<ActualStop>> = emptyMap(),
  /** 計画の日に入らない予定外の立ち寄り（着いた順）。 */
  val outside: List<ActualStop> = emptyList(),
) {
  companion object {
    fun afterRowKey(itemId: Long): String = PlanSlotKey.Row(itemId).key

    fun dayStartKey(day: Int): String = "dayStart:$day"
  }
}

/**
 * [slots]（日の見出しと行の並び）と実際の立ち寄り [stops] をまとめる。[dayOf] は立ち寄りの日付が計画の何日目か
 * （日付の無い日帰りなら 1、計画の日に入らなければ null）。
 */
internal fun mergeActuals(
  slots: List<PlanSlotKey>,
  items: Map<Long, PlanItem>,
  stops: List<ActualStop>,
  dayOf: (LocalDate?) -> Int?,
): MergedActuals {
  if (stops.isEmpty()) return MergedActuals()
  val planPlaceIds = items.values.mapNotNull { it.place?.placeId }.toSet()
  val byArrival = stops.sortedWith(compareBy({ it.stop.arrivalTime }, { it.stop.id }))
  val visitsByPlace = byArrival.filter { it.stop.place.id in planPlaceIds }.groupBy { it.stop.place.id }
  // 日ごとの行（並びどおり）。見出しの無い日帰りは 1 日目。
  val rowsByDay = buildMap<Int, MutableList<PlanItem>> {
    var day = 1
    for (slot in slots) {
      when (slot) {
        is PlanSlotKey.Day -> day = slot.day
        is PlanSlotKey.Row -> items[slot.itemId]?.let { getOrPut(day) { mutableListOf() }.add(it) }
      }
    }
  }
  val unplannedAfter = mutableMapOf<String, MutableList<ActualStop>>()
  val outside = mutableListOf<ActualStop>()
  for (actual in byArrival.filter { it.stop.place.id !in planPlaceIds }) {
    val day = dayOf(actual.date)
    if (day == null) {
      outside += actual
      continue
    }
    // その日に、この立ち寄りより前に着いた計画の場所のうち、並びの最後の行の後ろに挟む。
    val anchor = rowsByDay[day].orEmpty().lastOrNull { row ->
      val placeId = row.place?.placeId ?: return@lastOrNull false
      visitsByPlace[placeId].orEmpty().any { dayOf(it.date) == day && !it.stop.arrivalTime.after(actual.stop.arrivalTime) }
    }
    val key = anchor?.let { MergedActuals.afterRowKey(it.id) } ?: MergedActuals.dayStartKey(day)
    unplannedAfter.getOrPut(key) { mutableListOf() } += actual
  }
  return MergedActuals(visitsByPlace, unplannedAfter, outside)
}
