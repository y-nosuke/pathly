package com.pathly.presentation.tracking

import com.pathly.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionMarkerActionsTest {

  private fun marker(id: String) = RegionMarker(id, "地域$id", 35.0, 139.0, GeoBounds(34.9, 138.9, 35.1, 139.1))

  @Test
  fun show_addsInOrderAndFocusesTheLatest() {
    val actions = RegionMarkerActions()

    assertTrue(actions.show(marker("a")))
    assertTrue(actions.show(marker("b")))

    assertEquals(listOf("a", "b"), actions.state.value.markers.map { it.placeId })
    assertEquals("b", actions.state.value.focusedPlaceId)
  }

  @Test
  fun show_sameRegionDoesNotAddButFocuses() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    assertTrue(actions.show(marker("a")))

    assertEquals(listOf("a", "b"), actions.state.value.markers.map { it.placeId })
    assertEquals("a", actions.state.value.focusedPlaceId)
  }

  @Test
  fun show_overLimitIsRejectedWithoutDroppingTheOldest() {
    val actions = RegionMarkerActions()
    repeat(RegionMarkerActions.MAX_MARKERS) { actions.show(marker("$it")) }
    val before = actions.state.value

    assertFalse(actions.state.value.canShow("new"))
    assertFalse(actions.show(marker("new")))

    assertEquals(before, actions.state.value)
    // 出している地域は上限でも選び直せる。
    assertTrue(actions.state.value.canShow("0"))
    assertTrue(actions.show(marker("0")))
    assertEquals("0", actions.state.value.focusedPlaceId)
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
  fun clear_removesEverything() {
    val actions = RegionMarkerActions()
    actions.show(marker("a"))
    actions.show(marker("b"))

    actions.clear()

    assertEquals(RegionMarkersState(), actions.state.value)
  }
}
