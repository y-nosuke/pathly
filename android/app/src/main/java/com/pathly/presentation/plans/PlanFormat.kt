package com.pathly.presentation.plans

import androidx.compose.ui.graphics.Color
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanItem
import com.pathly.ui.theme.PathlyMapColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 計画の画面で使う文言と色（→ docs/specs/plans.md）。

private val MONTH_DAY = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE)
private val YEAR_MONTH_DAY = DateTimeFormatter.ofPattern("yyyy/M/d(E)", Locale.JAPANESE)

/** 「11/30(日)」。今年でなければ年を添える。 */
internal fun formatPlanDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = if (date.year == today.year) MONTH_DAY.format(date) else YEAR_MONTH_DAY.format(date)

/** 日数の言い方（1 日は「日帰り」）。 */
internal fun dayCountLabel(dayCount: Int): String = if (dayCount <= 1) "日帰り" else "${dayCount}日"

/**
 * 計画の日付と日数の要約。「11/30(日) 日帰り」「11/30(日)〜12/1(月) 2日」「日付なし・2日」。
 */
internal fun planDateSummary(plan: Plan, today: LocalDate = LocalDate.now()): String {
  val start = plan.startDate ?: return "日付なし・${dayCountLabel(plan.dayCount)}"
  if (plan.isDayTrip) return "${formatPlanDate(start, today)} 日帰り"
  return "${formatPlanDate(start, today)}〜${formatPlanDate(plan.endDate!!, today)} ${dayCountLabel(plan.dayCount)}"
}

/** 日の見出し・日のチップの文言（「1日目 11/30(日)」。日付なしなら「1日目」）。 */
internal fun dayLabel(plan: Plan, day: Int, today: LocalDate = LocalDate.now()): String = plan.dateOf(day)?.let { "${day}日目 ${formatPlanDate(it, today)}" } ?: "${day}日目"

/** [day] 日目の色（8 色を順に使う）。 */
internal fun planDayColor(day: Int): Color = PathlyMapColors.PlanDays[(day - 1).mod(PathlyMapColors.PlanDays.size)]

/** 「10:00」。 */
internal fun formatMinutesOfDay(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

/** 滞在の目安の言い方（「30分」「1時間」「1.5時間」「半日」）。 */
internal fun stayLabel(minutes: Int): String = when {
  minutes == HALF_DAY_MINUTES -> "半日"
  minutes < 60 -> "${minutes}分"
  minutes % 60 == 0 -> "${minutes / 60}時間"
  minutes % 30 == 0 -> "${minutes / 60}.5時間"
  else -> "${minutes / 60}時間${minutes % 60}分"
}

/** 行の 2 行目（「10:00 着・1時間」）。予定時刻も滞在の目安も無ければ null。 */
internal fun itemTimeLine(item: PlanItem): String? {
  val parts = listOfNotNull(
    item.plannedTime?.let { "${formatMinutesOfDay(it)} 着" },
    item.stayMinutes?.let { stayLabel(it) },
  )
  return parts.joinToString("・").ifEmpty { null }
}

/** 滞在の目安の選択肢（分。null は「なし」）。 */
internal val StayChoices: List<Int?> = listOf(null, 30, 60, 90, 120, 180, HALF_DAY_MINUTES)

internal const val HALF_DAY_MINUTES = 240

/** 新しく作った計画の名前。 */
internal const val NEW_PLAN_TITLE = "新しい計画"
