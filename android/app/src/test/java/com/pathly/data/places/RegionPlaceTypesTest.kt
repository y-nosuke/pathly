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

  @Test
  fun regionLevelPicksPrefectureOrCountryForTheTopAndAnythingButCountryForCities() {
    // 「東京都」で探して先に出た府中市は、都道府県の段には合わない。
    assertTrue(matchesRegionLevel(listOf("administrative_area_level_1", "political"), topLevel = true))
    assertTrue(matchesRegionLevel(listOf("country", "political"), topLevel = true))
    assertFalse(matchesRegionLevel(listOf("locality", "geocode", "political"), topLevel = true))
    assertTrue(matchesRegionLevel(listOf("locality", "geocode", "political"), topLevel = false))
    // ソウルのように都市が州の段の所もあるので、都市では州の段も受ける。
    assertTrue(matchesRegionLevel(listOf("administrative_area_level_1", "political"), topLevel = false))
    assertFalse(matchesRegionLevel(listOf("country", "political"), topLevel = false))
  }
}
