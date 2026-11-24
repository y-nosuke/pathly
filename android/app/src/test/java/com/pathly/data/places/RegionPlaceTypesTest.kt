package com.pathly.data.places

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionPlaceTypesTest {

  @Test
  fun regionTypes_areRegions() {
    assertTrue(isRegionPlaceTypes(listOf("locality", "political", "geocode")))
    assertTrue(isRegionPlaceTypes(listOf("administrative_area_level_1", "political")))
    assertTrue(isRegionPlaceTypes(listOf("country", "political")))
    assertTrue(isRegionPlaceTypes(listOf("postal_code")))
    assertTrue(isRegionPlaceTypes(listOf("sublocality_level_1", "sublocality", "political")))
  }

  @Test
  fun establishment_isNotRegionEvenWithRegionTypes() {
    assertFalse(isRegionPlaceTypes(listOf("cafe", "food", "establishment", "point_of_interest")))
    assertFalse(isRegionPlaceTypes(listOf("locality", "establishment")))
  }

  @Test
  fun neitherOrUnknown_isNotRegion() {
    assertFalse(isRegionPlaceTypes(emptyList()))
    assertFalse(isRegionPlaceTypes(listOf("route", "geocode")))
    assertFalse(isRegionPlaceTypes(listOf("political")))
  }
}
