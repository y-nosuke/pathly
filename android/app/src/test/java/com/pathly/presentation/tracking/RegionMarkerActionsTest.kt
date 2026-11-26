package com.pathly.presentation.tracking

import com.pathly.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionMarkerActionsTest {

  private fun marker(id: String) = RegionMarker(id, "地域$id", 35.0, 139.0, GeoBounds(34.9, 138.9, 35.1, 139.1))

  @Test
  fun show_addsInOrderAndFocusesTheLatest() {
    val actions = RegionMarkerActions()

    actions.show(marker("a"))
    actions.show(marker("b"))

    assertEquals(listOf("a", "b"), actions.state.value.markers.map { it.placeId })
    assertEquals("b", actions.state.value.focusedPlaceId)
  }

  @Test
  fun show_sameRegionDoesNotAddButFocuses() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    actions.show(marker("a"))

    assertEquals(listOf("a", "b"), actions.state.value.markers.map { it.placeId })
    assertEquals("a", actions.state.value.focusedPlaceId)
  }

  @Test
  fun show_hasNoLimit() {
    val actions = RegionMarkerActions()

    repeat(12) { actions.show(marker("$it")) }

    assertEquals(12, actions.state.value.markers.size)
    assertEquals("11", actions.state.value.focusedPlaceId)
  }

  @Test
  fun focus_onlyKnownRegion() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    actions.focus("a")
    assertEquals("a", actions.state.value.focusedPlaceId)
    actions.focus("unknown")
    assertEquals("a", actions.state.value.focusedPlaceId)
  }

  @Test
  fun unfocus_keepsMarkersAndClearsFocus() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    actions.unfocus()

    assertEquals(listOf("a", "b"), actions.state.value.markers.map { it.placeId })
    assertNull(actions.state.value.focusedPlaceId)
  }

  @Test
  fun remove_dropsOnlyThatRegionAndClearsFocusIfItWasFocused() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    actions.remove("a")
    assertEquals(listOf("b"), actions.state.value.markers.map { it.placeId })
    assertEquals("b", actions.state.value.focusedPlaceId)

    actions.remove("b")
    assertTrue(actions.state.value.markers.isEmpty())
    assertNull(actions.state.value.focusedPlaceId)
  }

  @Test
  fun move_insertsAtTheIndexAfterTakingOutAndKeepsFocus() {
    val actions = RegionMarkerActions()
    listOf("a", "b", "c", "d").forEach { actions.show(marker(it)) }

    actions.move("a", 2)
    assertEquals(listOf("b", "c", "a", "d"), actions.state.value.markers.map { it.placeId })
    actions.move("d", 0)
    assertEquals(listOf("d", "b", "c", "a"), actions.state.value.markers.map { it.placeId })
    assertEquals("d", actions.state.value.focusedPlaceId)
  }

  @Test
  fun foldAndBringUp_goToTheEndOfTheFolded() {
    val actions = RegionMarkerActions()
    (1..7).forEach { actions.show(marker("$it")) }

    // 2 つ目をまとめに入れると、まとめの最後に入り、まとめの先頭だった 6 が 5 つ目に上がる。
    actions.fold("2")
    assertEquals(listOf("1", "3", "4", "5", "6", "7", "2"), actions.state.value.markers.map { it.placeId })
    // まとめの 2 を上に出す（5 つ目）と、5 つ目だった 6 がまとめの最後へ。
    actions.bringUp("2", VISIBLE_REGION_CHIPS - 1)
    assertEquals(listOf("1", "3", "4", "5", "2", "7", "6"), actions.state.value.markers.map { it.placeId })
    // まとめに無い地域は上に出さない。
    actions.bringUp("1", VISIBLE_REGION_CHIPS - 1)
    assertEquals(listOf("1", "3", "4", "5", "2", "7", "6"), actions.state.value.markers.map { it.placeId })
  }

  @Test
  fun move_clampsAndIgnoresUnknown() {
    val actions = RegionMarkerActions()
    listOf("a", "b").forEach { actions.show(marker(it)) }
    val before = actions.state.value

    actions.move("unknown", 0)
    assertEquals(before, actions.state.value)
    actions.move("a", 99)
    assertEquals(listOf("b", "a"), actions.state.value.markers.map { it.placeId })
  }

  @Test
  fun clear_removesEverything() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    actions.clear()

    assertEquals(RegionMarkersState(), actions.state.value)
  }
}
