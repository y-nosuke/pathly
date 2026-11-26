package com.pathly.domain.model

import java.time.LocalDate
import java.util.Date

/**
 * 計画（→ docs/specs/plans.md・adr/0050）。
 *
 * 日は「1日目・2日目…」で持ち、[startDate] があれば日付はそこから求める。[startDate] が null なら「日付なし」。
 */
data class Plan(
  val id: Long,
  val title: String,
  val note: String?,
  val startDate: LocalDate?,
  /** 1 以上（1 は日帰り）。 */
  val dayCount: Int,
  val createdAt: Date,
  val updatedAt: Date,
) {
  val isDayTrip: Boolean get() = dayCount <= 1

  /** [day] 日目（1 から）の日付。日付なしなら null。 */
  fun dateOf(day: Int): LocalDate? = startDate?.plusDays((day - 1).toLong())

  /** 最後の日の日付。日付なしなら null。 */
  val endDate: LocalDate? get() = dateOf(dayCount)

  /** 一覧のどの区分に入るか（[today] は端末の今日）。 */
  fun groupOn(today: LocalDate): PlanGroup = when {
    startDate == null -> PlanGroup.UNDATED
    endDate!!.isBefore(today) -> PlanGroup.PAST
    else -> PlanGroup.UPCOMING
  }
}

/** 計画の一覧の区分（→ specs/plans.md の「計画の一覧」）。 */
enum class PlanGroup(val label: String) {
  UNDATED("日付なし"),
  UPCOMING("これから"),
  PAST("終わった"),
}

/** 計画の一覧の 1 行。 */
data class PlanSummary(
  val plan: Plan,
  /** 場所の行の数。 */
  val placeCount: Int,
)

/**
 * 計画の行。[place] があれば場所の行、null ならメモの行（文言は [title]）。
 * [plannedTime] は着く時刻（0 時からの分）、[stayMinutes] は滞在の目安（分）。どちらも任意。
 */
data class PlanItem(
  val id: Long,
  val planId: Long,
  val dayIndex: Int,
  val position: Int,
  val place: PlanPlace?,
  val title: String?,
  val note: String?,
  val plannedTime: Int?,
  val stayMinutes: Int?,
) {
  val isPlace: Boolean get() = place != null

  /** 行に出す名前（場所の行は場所の表示名、メモの行は文言）。 */
  val label: String get() = place?.name ?: title.orEmpty()
}

/** 場所の行が指す場所（名前は場所のもの。座標はアンカー → adr/0050）。 */
data class PlanPlace(
  val placeId: Long,
  val name: String,
  val latitude: Double,
  val longitude: Double,
)

/** 計画と、日ごと・順番どおりに並んだ行。 */
data class PlanDetail(
  val plan: Plan,
  val items: List<PlanItem>,
) {
  /** [day] 日目の行（順番どおり）。 */
  fun itemsOn(day: Int): List<PlanItem> = items.filter { it.dayIndex == day }

  /** 場所の行の日ごとの番号（1 から。メモの行は数えない）。行の id → 番号。 */
  val placeNumbers: Map<Long, Int> by lazy {
    buildMap {
      items.filter { it.isPlace }.groupBy { it.dayIndex }.values.forEach { dayItems ->
        dayItems.forEachIndexed { index, item -> put(item.id, index + 1) }
      }
    }
  }
}
