package com.pathly.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** 経路の始まり（記録中かを見るのに使う。点は持たない）。 */
data class TrackStart(
  val trackId: Long,
  val startTime: Date,
  /** 記録中か。 */
  val isActive: Boolean,
)

/** 経路の立ち寄り先（その経路でその場所に立ち寄った）。 */
data class TrackVisit(
  val trackId: Long,
  val placeId: Long,
)

/** 経路を計画に結び付けた方法（→ docs/specs/plans.md の「結び付いた経路」）。 */
enum class PlanTrackSource {
  /** 記録を始めたときに、今日の計画から選んだ。計画の日付を変えて範囲から外れたら外れる。 */
  START,

  /** 計画の詳細の「経路を結び付ける」で選んだ。日付を変えても残る。 */
  MANUAL,
}

/** 計画に結び付いた経路（1 つの経路が結び付く計画は 1 つだけ → adr/0050）。 */
data class PlanTrackLink(
  val trackId: Long,
  val planId: Long,
  val source: PlanTrackSource,
)

/**
 * 計画と実績の決まり（→ docs/specs/plans.md の「計画と実績」・adr/0050）。
 * 結び付きは保存する（`plan_tracks`）が、「行った」は保存せず、結び付いた経路の立ち寄りから読むたびに求める。
 */
object PlanActuals {

  /** [date] が計画の日付の範囲に入るか（日付なしなら入らない）。 */
  fun covers(plan: Plan, date: LocalDate): Boolean {
    val start = plan.startDate ?: return false
    return !date.isBefore(start) && !date.isAfter(plan.endDate!!)
  }

  /**
   * 計画の日付の範囲を、端末の時刻帯の時刻（ミリ秒）で。開始日の 0:00 から、最後の日の翌日の 0:00 の手前まで。日付なしなら null。
   */
  fun rangeMillis(plan: Plan, zone: ZoneId = ZoneId.systemDefault()): LongRange? {
    val start = plan.startDate ?: return null
    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = start.plusDays(plan.dayCount.coerceAtLeast(1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    return from until until
  }

  /**
   * [plan] と日が重なる計画（[others] のうち最初のもの。無ければ null）。
   *
   * 何日かの計画どうしは日を重ねられない。ただし、ある計画の最後の日と別の計画の開始日が同じ日なのはよい。
   * 日帰りの計画はどの計画とも重なってよい。日付なしは関係しない（→ docs/specs/plans.md の「計画の日付の重なり」）。
   */
  fun overlappingPlan(plan: Plan, others: List<Plan>): Plan? {
    if (!plan.isMultiDay) return null
    val start = plan.startDate!!
    val end = plan.endDate!!
    return others.firstOrNull { other ->
      other.id != plan.id && other.isMultiDay && start.isBefore(other.endDate!!) && other.startDate!!.isBefore(end)
    }
  }

  private val Plan.isMultiDay: Boolean get() = startDate != null && dayCount > 1

  /** 結び付いた経路のどれかで立ち寄った場所。 */
  fun visitedPlaceIds(linked: Set<Long>, visits: List<TrackVisit>): Set<Long> = if (linked.isEmpty()) {
    emptySet()
  } else {
    visits.filter { it.trackId in linked }.mapTo(HashSet()) { it.placeId }
  }

  /** 行った場所の行の数（同じ場所が 2 行あれば 2 と数える。メモの行は数えない）。 */
  fun visitedItemCount(placeIdsOfItems: List<Long>, visited: Set<Long>): Int = placeIdsOfItems.count { it in visited }

  /** 結び付いた経路の中に記録中のものがあれば、その経路の id。 */
  fun recordingTrackId(linked: Set<Long>, tracks: List<TrackStart>): Long? = tracks.firstOrNull { it.isActive && it.trackId in linked }?.trackId
}
