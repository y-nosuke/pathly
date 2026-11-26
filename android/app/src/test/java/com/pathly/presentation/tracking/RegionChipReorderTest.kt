package com.pathly.presentation.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionChipReorderTest {

  // 高さ 40・間 8 のチップが 3 つ（上端 0・48・96）。
  private val tops = listOf(0f, 48f, 96f)
  private val heights = listOf(40f, 40f, 40f)

  @Test
  fun dropTarget_swapsWhenTheEdgePassesTheNextCenter() {
    // 下へ: 下端（40 + offset）が次の中心（68・116）を越えたら入れ替わる。
    assertEquals(0, regionChipDropTarget(tops, heights, from = 0, offset = 0f, foldedTop = null))
    assertEquals(0, regionChipDropTarget(tops, heights, from = 0, offset = 27f, foldedTop = null))
    assertEquals(1, regionChipDropTarget(tops, heights, from = 0, offset = 29f, foldedTop = null))
    assertEquals(2, regionChipDropTarget(tops, heights, from = 0, offset = 77f, foldedTop = null))
    // 上へ: 上端（96 + offset）が上の中心（68・20）を越えたら入れ替わる。
    assertEquals(2, regionChipDropTarget(tops, heights, from = 2, offset = -27f, foldedTop = null))
    assertEquals(1, regionChipDropTarget(tops, heights, from = 2, offset = -29f, foldedTop = null))
    assertEquals(0, regionChipDropTarget(tops, heights, from = 2, offset = -97f, foldedTop = null))
  }

  @Test
  fun dragState_reachesTheLastWhenDroppedOnTheBottomChip() {
    // まとめが無いとき、いちばん下のチップまで動かす（そこで止まる）と、いちばん下になる。
    val drag = RegionChipDragState()
    drag.ids = listOf("a", "b", "c")
    listOf("a", "b", "c").forEachIndexed { i, id -> drag.place(id, tops[i], heights[i]) }
    drag.placeFolded(null, 0f)

    drag.start("a")
    drag.dragBy(500f)
    assertEquals(96f, drag.offset)
    assertEquals("a" to 2, drag.drop())
  }

  @Test
  fun dropTarget_intoFoldedWhenTheCenterIsBelowItsTop() {
    // 「他 N 件」の上端は 144。
    assertEquals(2, regionChipDropTarget(tops, heights, from = 0, offset = 100f, foldedTop = 144f))
    assertEquals(3, regionChipDropTarget(tops, heights, from = 0, offset = 125f, foldedTop = 144f))
  }

  @Test
  fun shift_makesRoomBetweenFromAndTarget() {
    // 下へ: 0 → 2 なら 1・2 が上へずれる。
    assertEquals(0f, regionChipShift(0, from = 0, target = 2, pitch = 48f))
    assertEquals(-48f, regionChipShift(1, from = 0, target = 2, pitch = 48f))
    assertEquals(-48f, regionChipShift(2, from = 0, target = 2, pitch = 48f))
    // 上へ: 2 → 0 なら 0・1 が下へずれる。
    assertEquals(48f, regionChipShift(0, from = 2, target = 0, pitch = 48f))
    assertEquals(48f, regionChipShift(1, from = 2, target = 0, pitch = 48f))
    // まとめへ（並びの数 3）: 下のチップが詰める。
    assertEquals(0f, regionChipShift(0, from = 1, target = 3, pitch = 48f))
    assertEquals(-48f, regionChipShift(2, from = 1, target = 3, pitch = 48f))
  }

  @Test
  fun dragState_clampsAndDrops() {
    val drag = RegionChipDragState()
    drag.ids = listOf("a", "b", "c")
    listOf("a", "b", "c").forEachIndexed { i, id -> drag.place(id, tops[i], heights[i]) }
    drag.placeFolded(144f, 40f)

    drag.start("b")
    drag.dragBy(-500f)
    // 先頭（a の上端）より上へは出ない。
    assertEquals(-48f, drag.offset)
    assertEquals(0, drag.target())
    assertEquals("b" to 0, drag.drop())
    assertNull(drag.placeId)

    drag.start("a")
    drag.dragBy(500f)
    // まとめの下端（184）より下へは出ない。
    assertEquals(144f, drag.offset)
    assertTrue(drag.overFolded())
    assertEquals("a" to 3, drag.drop())

    // 掴んでいる間、2 本目の指で別のチップを長押ししても掴み直さない。
    assertTrue(drag.start("c"))
    assertFalse(drag.start("a"))
    assertEquals("c", drag.placeId)
    drag.dragBy(5f)
    assertFalse(drag.overFolded())
    assertNull(drag.drop())
  }

  @Test
  fun dragState_forgetsRemovedRegions() {
    val drag = RegionChipDragState()
    drag.ids = listOf("a", "b")
    drag.place("a", 0f, 40f)
    drag.place("b", 48f, 40f)

    drag.ids = listOf("b")
    drag.start("a")
    // 外した地域は位置を忘れているので、行き先も出さない。
    assertNull(drag.target())
  }
}
