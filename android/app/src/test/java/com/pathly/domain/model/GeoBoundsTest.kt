package com.pathly.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoBoundsTest {

  @Test
  fun `範囲の中と境界は入り_外は入らない`() {
    val b = GeoBounds(south = 35.0, west = 139.0, north = 36.0, east = 140.0)

    assertTrue(b.contains(35.5, 139.5))
    assertTrue(b.contains(35.0, 140.0))
    assertFalse(b.contains(34.9, 139.5))
    assertFalse(b.contains(35.5, 140.1))
  }

  @Test
  fun `東経180度をまたぐ範囲は_両側の経度が入る`() {
    val b = GeoBounds(south = 9.0, west = 179.0, north = 11.0, east = -179.0)

    assertTrue(b.crossesAntimeridian)
    assertTrue(b.contains(10.0, 179.5))
    assertTrue(b.contains(10.0, -179.5))
    assertFalse(b.contains(10.0, 170.0))
  }
}
