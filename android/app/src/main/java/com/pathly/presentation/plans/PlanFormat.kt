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

/** 滞在の目安の言い方（「30分」「1時間」「1時間30分」）。 */
internal fun stayLabel(minutes: Int): String = when {
  minutes < 60 -> "${minutes}分"
  minutes % 60 == 0 -> "${minutes / 60}時間"
  else -> "${minutes / 60}時間${minutes % 60}分"
}

/** 「10:00」。24 時を超えたら翌日の時刻に「翌」を付ける（「翌1:00」）。 */
internal fun formatClock(minutes: Int): String = if (minutes >= DAY_MINUTES) "翌" + formatMinutesOfDay(minutes % DAY_MINUTES) else formatMinutesOfDay(minutes)

/**
 * 行の 2 行目。「10:00 着 – 11:30 発・1時間30分」（出発は着く時刻 ＋ 滞在の目安）。
 * 着く時刻が無ければ「1時間30分」だけ。どちらも無ければ null。
 */
internal fun itemTimeLine(item: PlanItem): String? {
  val arrival = item.plannedTime?.let { "${formatClock(it)} 着" }
  val departure = item.plannedDeparture?.let { " – ${formatClock(it)} 発" }.orEmpty()
  val parts = listOfNotNull(arrival?.plus(departure), item.stayMinutes?.let { stayLabel(it) })
  return parts.joinToString("・").ifEmpty { null }
}

private const val DAY_MINUTES = 24 * 60

/** 新しく作った計画の名前。 */
internal const val NEW_PLAN_TITLE = "新しい計画"
