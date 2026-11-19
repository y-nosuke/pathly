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
