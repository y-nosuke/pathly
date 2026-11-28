package com.pathly.domain.model

import java.time.LocalDate

// 計画の編集（保存する前の下書きの上で行う → docs/specs/plans.md の「保存と下書き」）。
// どれも新しい PlanDetail を返す。行の並び（日・順番）はいつも振り直してそろえる。

/** 新しい計画（名前だけ・日付なし・日帰り・行なし）。 */
fun newPlanDetail(title: String, now: java.util.Date = java.util.Date()): PlanDetail = PlanDetail(Plan(0, title, null, null, 1, now, now), emptyList())

/** 名前とメモを変える。 */
fun PlanDetail.withTitleAndNote(title: String, note: String?): PlanDetail = copy(plan = plan.copy(title = title, note = note?.ifBlank { null }))

/**
 * 開始日（null は日付なし）と日数を変える。日数を減らしたら、消える日の行は新しい最後の日の末尾へ移す（→ adr/0050）。
 */
fun PlanDetail.withDates(startDate: LocalDate?, dayCount: Int): PlanDetail {
  val days = dayCount.coerceAtLeast(1)
  val moved = PlanArrangement.shrink(items.map { PlanSlot(it.id, it.dayIndex, it.position) }, days).associateBy { it.itemId }
  val newItems = items.map { item -> moved[item.id]?.let { item.copy(dayIndex = it.dayIndex, position = it.position) } ?: item }
  return copy(plan = plan.copy(startDate = startDate, dayCount = days), items = newItems).normalized()
}

/** 場所の行を [day] 日目の末尾に足す。[newId] は足す行に振る仮の id を返す（保存するまで使う）。 */
fun PlanDetail.withPlacesAdded(day: Int, places: List<PlanPlace>, newId: () -> Long): PlanDetail {
  val added = places.map { PlanItem(newId(), plan.id, day, Int.MAX_VALUE, it, null, null, null, null) }
  return copy(items = items + added).normalized()
}

/** 予定の行を [day] 日目の末尾に足す。 */
fun PlanDetail.withMemoAdded(day: Int, title: String, plannedTime: Int?, note: String?, id: Long): PlanDetail = copy(items = items + PlanItem(id, plan.id, day, Int.MAX_VALUE, null, title, note?.ifBlank { null }, plannedTime, null)).normalized()

/** 行の予定時刻・滞在の目安・メモ（予定の行は内容も）を変える。場所の行の名前は場所のもの。 */
fun PlanDetail.withItemUpdated(itemId: Long, title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?): PlanDetail = copy(
  items = items.map { item ->
    if (item.id != itemId) {
      item
    } else {
      item.copy(
        title = if (item.isPlace) null else (title?.ifBlank { null } ?: item.title),
        plannedTime = plannedTime,
        stayMinutes = if (item.isPlace) stayMinutes else null,
        note = note?.ifBlank { null },
      )
    }
  },
)

/** 行を [day] 日目の末尾へ移す。 */
fun PlanDetail.withItemMovedToDay(itemId: Long, day: Int): PlanDetail {
  val item = items.firstOrNull { it.id == itemId } ?: return this
  if (item.dayIndex == day) return this
  return copy(items = items.map { if (it.id == itemId) it.copy(dayIndex = day, position = Int.MAX_VALUE) else it }).normalized()
}

/** 並べ替えた結果（上から順の行の id と、その行が入った日）にする。並びに無い行はそのまま後ろに残す。 */
fun PlanDetail.withOrder(order: List<Pair<Long, Int>>): PlanDetail {
  val slots = PlanArrangement.renumber(order).associateBy { it.itemId }
  return copy(items = items.map { item -> slots[item.id]?.let { item.copy(dayIndex = it.dayIndex, position = it.position) } ?: item }).normalized()
}

/** 外した行と、元の並び（取り消しで同じ所へ戻すため）。 */
data class RemovedPlanItem(val item: PlanItem, val index: Int)

/** 行を外す。外した行を返す（無ければ null）。 */
fun PlanDetail.withItemRemoved(itemId: Long): Pair<PlanDetail, RemovedPlanItem?> {
  val index = items.indexOfFirst { it.id == itemId }
  if (index < 0) return this to null
  return copy(items = items.filterNot { it.id == itemId }).normalized() to RemovedPlanItem(items[index], index)
}

/** 外した行を元の所へ戻す（その日が無くなっていれば最後の日の末尾）。 */
fun PlanDetail.withItemRestored(removed: RemovedPlanItem): PlanDetail {
  if (items.any { it.id == removed.item.id }) return this
  val item = removed.item.copy(dayIndex = removed.item.dayIndex.coerceAtMost(plan.dayCount))
  val list = items.toMutableList().apply { add(removed.index.coerceIn(0, size), item) }
  return copy(items = list).renumberedInListOrder()
}

/**
 * [day] 日目を消す（日の見出しの ×）。その日の行は前の日の末尾へ移し（1 日目なら次の日の先頭）、後ろの日を 1 日ずつ詰める。
 * 日帰り（1 日だけ）は消せない。
 */
fun PlanDetail.withDayRemoved(day: Int): PlanDetail {
  if (plan.dayCount <= 1 || day !in 1..plan.dayCount) return this
  val ordered = normalized().items
  val target = if (day == 1) 1 else day - 1
  // 1 日目を消すときは、行を 2 日目（詰めると新しい 1 日目）の先頭へ。
  val result = mutableListOf<PlanItem>()
  val removedDayItems = ordered.filter { it.dayIndex == day }
  for (d in 1..plan.dayCount) {
    if (d == day) continue
    val newDay = if (d > day) d - 1 else d
    val dayItems = ordered.filter { it.dayIndex == d }.map { it.copy(dayIndex = newDay) }
    if (day == 1 && d == 2) result += removedDayItems.map { it.copy(dayIndex = 1) }
    result += dayItems
    if (day != 1 && d == target) result += removedDayItems.map { it.copy(dayIndex = newDay) }
  }
  return copy(plan = plan.copy(dayCount = plan.dayCount - 1), items = result).renumberedInListOrder()
}

/** 日・順番で並べ直し、日ごとの順番を 0 から振り直す。 */
fun PlanDetail.normalized(): PlanDetail = copy(items = items.sortedWith(compareBy({ it.dayIndex }, { it.position }))).renumberedInListOrder()

/** 今のリストの並びのまま、日ごとの順番を 0 から振り直す（日ごとにまとまっていなければ日でまとめる）。 */
private fun PlanDetail.renumberedInListOrder(): PlanDetail {
  val stable = items.withIndex().sortedWith(compareBy({ it.value.dayIndex }, { it.index })).map { it.value }
  val counters = mutableMapOf<Int, Int>()
  return copy(
    items = stable.map { item ->
      val position = counters.getOrDefault(item.dayIndex, 0)
      counters[item.dayIndex] = position + 1
      item.copy(position = position)
    },
  )
}

/** 中身が同じか（名前・メモ・日付・日数・行の並びと中身。行の id と日時は見ない）。保存や下書きが要るかの判定に使う。 */
fun PlanDetail.sameContentAs(other: PlanDetail): Boolean = plan.title == other.plan.title &&
  plan.note == other.plan.note &&
  plan.startDate == other.plan.startDate &&
  plan.dayCount == other.plan.dayCount &&
  items.map { it.contentKey() } == other.items.map { it.contentKey() }

private fun PlanItem.contentKey() = listOf(dayIndex, position, place?.placeId, title, note, plannedTime, stayMinutes)
