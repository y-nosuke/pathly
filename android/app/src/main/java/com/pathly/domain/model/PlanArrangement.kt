package com.pathly.domain.model

/** 行の並び（何日目の何番目か）。並べ替え・日数の変更で行を動かすときの単位。 */
data class PlanSlot(val itemId: Long, val dayIndex: Int, val position: Int)

/**
 * 計画の行の並びを組み直す決まり（→ adr/0050）。保存は呼び出し側。
 */
object PlanArrangement {

  /**
   * 日数を [newDayCount] にしたときの並び。**消える日の行は、新しい最後の日の末尾へ、元の順（日・順番）で移す**。
   * 残る日の行は動かさない。返すのは、日か順番が変わる行だけ。
   */
  fun shrink(slots: List<PlanSlot>, newDayCount: Int): List<PlanSlot> {
    require(newDayCount >= 1) { "dayCount must be >= 1" }
    val overflow = slots.filter { it.dayIndex > newDayCount }.sortedWith(compareBy({ it.dayIndex }, { it.position }))
    if (overflow.isEmpty()) return emptyList()
    val lastDayEnd = slots.filter { it.dayIndex == newDayCount }.maxOfOrNull { it.position + 1 } ?: 0
    return overflow.mapIndexed { index, slot -> slot.copy(dayIndex = newDayCount, position = lastDayEnd + index) }
  }

  /** 日数を [newDayCount] にしたとき、消える日から移る行の数（確認の文言用）。 */
  fun movedByShrink(slots: List<PlanSlot>, newDayCount: Int): Int = slots.count { it.dayIndex > newDayCount }

  /**
   * 画面で並べ替えた結果（上から順の行の id と、その行が入った日）から、日ごとの順番を振り直す。
   */
  fun renumber(order: List<Pair<Long, Int>>): List<PlanSlot> {
    val counters = mutableMapOf<Int, Int>()
    return order.map { (itemId, day) ->
      val position = counters.getOrDefault(day, 0)
      counters[day] = position + 1
      PlanSlot(itemId, day, position)
    }
  }
}
