package com.pathly.presentation.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 地名のチップを長押しで掴んで並べ替える間の状態（→ ADR 0047）。チップの位置（縦の並びの中の上端と高さ）を覚えておき、
 * 掴んだチップを指に付けて動かす量（[offset]）から、離したときの行き先とほかのチップのよけ方を出す。
 * 並びは画面のものだけで、決まった順は [RegionMarkerActions.move] に渡す。
 */
internal class RegionChipDragState {
  /** 掴んでいるチップの地域（掴んでいなければ null）。 */
  var placeId by mutableStateOf<String?>(null)
    private set

  /** 掴んだチップを元の位置から縦に動かした量（px）。 */
  var offset by mutableFloatStateOf(0f)
    private set

  /** 縦に出している地名のチップの地域（上から）。描くたびに入れ直す。外した地域の位置は忘れる。 */
  var ids: List<String> = emptyList()
    set(value) {
      field = value
      slots.keys.retainAll(value.toSet())
    }

  private val slots = mutableMapOf<String, Slot>()
  private var foldedSlot: Slot? = null

  /** チップの位置（縦の並びの中の上端と高さ。px）を覚える。 */
  fun place(placeId: String, top: Float, height: Float) {
    slots[placeId] = Slot(top, height)
  }

  /** 「他 N 件」の位置を覚える。まとめが無いなら null。 */
  fun placeFolded(top: Float?, height: Float) {
    foldedSlot = top?.let { Slot(it, height) }
  }

  /** 掴む。別のチップを掴んでいる間（2 本目の指）は掴まない。 */
  fun start(placeId: String): Boolean {
    if (this.placeId != null) return false
    this.placeId = placeId
    offset = 0f
    return true
  }

  /** 指の動きの分だけ動かす。「すべて外す」の上へも、並びのいちばん下（まとめがあればまとめ）より下へも出さない。 */
  fun dragBy(dy: Float) {
    val tops = tops() ?: return
    val heights = heights() ?: return
    val from = ids.indexOf(placeId)
    val bottom = foldedSlot?.let { it.top + it.height } ?: (tops.last() + heights.last())
    offset = (offset + dy).coerceIn(tops.first() - tops[from], bottom - heights[from] - tops[from])
  }

  /** 今離したときの行き先（[regionChipDropTarget]）。掴んでいなければ null。 */
  fun target(): Int? {
    val tops = tops() ?: return null
    val heights = heights() ?: return null
    return regionChipDropTarget(tops, heights, ids.indexOf(placeId), offset, foldedSlot?.top)
  }

  /** 掴んだチップを「他 N 件」に重ねている（離すとまとめに入る）。 */
  fun overFolded(): Boolean = target() == ids.size

  /** 並びの [index] 番目のチップをよけさせる量（px）。[spacing] はチップの間。 */
  fun shiftOf(index: Int, spacing: Float): Float {
    val target = target() ?: return 0f
    val from = ids.indexOf(placeId)
    val pitch = (slots[placeId]?.height ?: return 0f) + spacing
    return regionChipShift(index, from, target, pitch)
  }

  /** 離した。並びが変わるなら（地域・行き先）を返す。行き先が並びの数ならまとめの先頭。 */
  fun drop(): Pair<String, Int>? {
    val id = placeId ?: return null
    val from = ids.indexOf(id)
    val target = target()
    placeId = null
    offset = 0f
    return if (from < 0 || target == null || target == from) null else id to target
  }

  /** 掴んでいるチップと、並びのすべてのチップの位置が分かっているときだけ上端を返す。 */
  private fun tops(): List<Float>? = positions()?.map { it.top }

  private fun heights(): List<Float>? = positions()?.map { it.height }

  private fun positions(): List<Slot>? {
    val id = placeId ?: return null
    if (id !in ids) return null
    return ids.map { slots[it] ?: return null }
  }

  private data class Slot(val top: Float, val height: Float)
}

/**
 * 掴んだチップ（[from] 番目を [offset] だけ動かした）を今離したときの行き先。チップの中心が、ほかのチップの中心を
 * いくつ越えたかで決める。[foldedTop]（「他 N 件」の上端）より下に中心が来たら、まとめに入れる（並びの数を返す）。
 */
internal fun regionChipDropTarget(tops: List<Float>, heights: List<Float>, from: Int, offset: Float, foldedTop: Float?): Int {
  val center = tops[from] + offset + heights[from] / 2
  if (foldedTop != null && center > foldedTop) return tops.size
  return tops.indices.count { it != from && tops[it] + heights[it] / 2 < center }
}

/**
 * 掴んだチップ（[from] 番目）の行き先が [target] のとき、[index] 番目のチップをよけさせる量。下へ動かせば間のチップが
 * 1 つ上へ、上へ動かせば 1 つ下へずれる。まとめに入れる（[target] が並びの数）ときは、下のチップが詰める。
 */
internal fun regionChipShift(index: Int, from: Int, target: Int, pitch: Float): Float = when {
  index == from -> 0f
  target > from && index in (from + 1)..target -> -pitch
  target < from && index in target until from -> pitch
  else -> 0f
}
