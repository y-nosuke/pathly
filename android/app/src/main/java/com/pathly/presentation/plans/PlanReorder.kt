package com.pathly.presentation.plans

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pathly.domain.model.PlanDetail

/** シートの並びの 1 つ。日の見出し（日帰りでは出さない）か行。 */
internal sealed interface PlanSlotKey {
  val key: String

  data class Day(val day: Int) : PlanSlotKey {
    override val key: String get() = "day:$day"
  }

  data class Row(val itemId: Long) : PlanSlotKey {
    override val key: String get() = "item:$itemId"
  }
}

/** [detail] の並び（日の見出しと行）。日帰りなら見出しを入れない。 */
internal fun slotsOf(detail: PlanDetail): List<PlanSlotKey> = buildList {
  val dayTrip = detail.plan.isDayTrip
  for (day in 1..detail.plan.dayCount) {
    if (!dayTrip) add(PlanSlotKey.Day(day))
    detail.itemsOn(day).forEach { add(PlanSlotKey.Row(it.id)) }
  }
  // 日数より後の日に残った行（ふつうは無い）も落とさない。
  detail.items.filter { it.dayIndex > detail.plan.dayCount }.forEach { add(PlanSlotKey.Row(it.id)) }
}

/** 並び（見出しと行）から、上から順の（行の id・その行が入った日）。見出しの無い日帰りは 1 日目。 */
internal fun orderOf(slots: List<PlanSlotKey>): List<Pair<Long, Int>> {
  var day = 1
  return slots.mapNotNull { slot ->
    when (slot) {
      is PlanSlotKey.Day -> {
        day = slot.day
        null
      }

      is PlanSlotKey.Row -> slot.itemId to day
    }
  }
}

/** [slots] の [from] 番目（行）を [to] 番目へ移した並び。見出しより上（いちばん上の見出しの前）へは出さない。 */
internal fun moveSlot(slots: List<PlanSlotKey>, from: Int, to: Int): List<PlanSlotKey> {
  val min = if (slots.firstOrNull() is PlanSlotKey.Day) 1 else 0
  val target = to.coerceIn(min, slots.lastIndex)
  if (from == target) return slots
  return slots.toMutableList().apply { add(target, removeAt(from)) }
}

/**
 * 計画の行を取っ手で掴んで並べ替える間の状態（→ adr/0051。動きは地名のチップ adr/0047 に揃える）。
 *
 * 掴んだ行を指に付けて動かし、ほかの行や日の見出しの中ほどを越えたら並び（[slots]）の中で入れ替える。
 * 見出しを越えればその日へ移る。並びは画面の中だけで、離したときに [orderOf] で保存の形にする。
 */
internal class PlanReorderState(private val listState: LazyListState) {
  /** 画面に出している並び。掴んでいない間は計画のとおり。 */
  var slots: List<PlanSlotKey> by mutableStateOf(emptyList())

  /** 掴んでいる行（掴んでいなければ null）。 */
  var dragging: Long? by mutableStateOf(null)
    private set

  /** 掴んだ行の、今の並びの位置からずらして描く量（px）。 */
  var offset by mutableFloatStateOf(0f)
    private set

  // 入れ替えた後、一覧が新しい並びで描き直されるまでは次の入れ替えをしない（二重に動かさない）。
  private var awaitingLayout: String? = null

  /** 計画が変わった（保存し終えた・別の端末の操作）。掴んでいない間だけ並びを入れ直す。 */
  fun sync(newSlots: List<PlanSlotKey>) {
    if (dragging == null) slots = newSlots
  }

  fun start(itemId: Long): Boolean {
    if (dragging != null) return false
    dragging = itemId
    offset = 0f
    awaitingLayout = null
    return true
  }

  /** 指の動きの分だけ動かし、越えた行・見出しと入れ替える。 */
  fun dragBy(dy: Float) {
    if (dragging == null) return
    offset += dy
    swapIfPassed()
  }

  /** 一覧を [consumed] だけスクロールした（端での送り）。指の下に行を留める。 */
  fun onScrolled(consumed: Float) {
    if (dragging == null) return
    offset += consumed
    swapIfPassed()
  }

  /** 掴んだ行が一覧の表示範囲のどこにあるか（中心の px）。見えていなければ null。 */
  fun draggedCenter(): Float? {
    val info = draggedInfo() ?: return null
    return info.offset + offset + info.size / 2f
  }

  /** 離した。並びが変わったなら保存の形（[orderOf]）を返す。 */
  fun drop(original: List<PlanSlotKey>): List<Pair<Long, Int>>? {
    if (dragging == null) return null
    dragging = null
    offset = 0f
    awaitingLayout = null
    return if (slots == original) null else orderOf(slots)
  }

  private fun draggedInfo(): LazyListItemInfo? {
    val id = dragging ?: return null
    val key = PlanSlotKey.Row(id).key
    return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
  }

  private fun swapIfPassed() {
    val id = dragging ?: return
    val dragged = draggedInfo() ?: return
    val draggedKey = PlanSlotKey.Row(id).key
    if (awaitingLayout != null) {
      if (awaitingLayout != layoutSignature()) awaitingLayout = null else return
    }
    val top = dragged.offset + offset
    val bottom = top + dragged.size
    val visible = listState.layoutInfo.visibleItemsInfo
    // 下へ動かすときは行の下端が、上へ動かすときは上端が、隣の行・見出しの中ほどを越えたら入れ替える。
    val below = visible.firstOrNull { it.index > dragged.index && it.isSlot() && it.key != draggedKey && bottom > it.offset + it.size / 2f }
      ?.takeIf { candidate -> visible.none { it.index in (dragged.index + 1) until candidate.index && it.isSlot() } }
    val above = visible.lastOrNull { it.index < dragged.index && it.isSlot() && top < it.offset + it.size / 2f }
      ?.takeIf { candidate -> visible.none { it.index in (candidate.index + 1) until dragged.index && it.isSlot() } }
    val target = below ?: above ?: return
    val from = slots.indexOfFirst { it.key == draggedKey }
    val to = slots.indexOfFirst { it.key == target.key }
    if (from < 0 || to < 0) return
    val moved = moveSlot(slots, from, to)
    if (moved == slots) return
    // 並びが変わると、行は入れ替えた相手の分だけ動いて描かれる。指の下に留めるため、その分を差し引く。
    val newOffset = if (target === below) target.offset + target.size - dragged.size else target.offset
    offset -= (newOffset - dragged.offset)
    awaitingLayout = layoutSignature()
    slots = moved
  }

  private fun layoutSignature(): String = listState.layoutInfo.visibleItemsInfo.joinToString(",") { "${it.key}@${it.index}" }

  private fun LazyListItemInfo.isSlot(): Boolean = (key as? String)?.let { it.startsWith("day:") || it.startsWith("item:") } == true
}
