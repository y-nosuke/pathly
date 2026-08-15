package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCategoryGroupTest {

  @Test
  fun knownCodes_mapToTheirGroup() {
    assertEquals(PlaceCategoryGroup.FOOD, PlaceCategoryGroup.of("restaurant"))
    assertEquals(PlaceCategoryGroup.CAFE, PlaceCategoryGroup.of("cafe"))
    assertEquals(PlaceCategoryGroup.SHOPPING, PlaceCategoryGroup.of("convenience_store"))
    assertEquals(PlaceCategoryGroup.PARK, PlaceCategoryGroup.of("park"))
    assertEquals(PlaceCategoryGroup.CULTURE, PlaceCategoryGroup.of("museum"))
    assertEquals(PlaceCategoryGroup.ENTERTAINMENT, PlaceCategoryGroup.of("zoo"))
    assertEquals(PlaceCategoryGroup.TRANSIT, PlaceCategoryGroup.of("train_station"))
    assertEquals(PlaceCategoryGroup.LODGING, PlaceCategoryGroup.of("hotel"))
    assertEquals(PlaceCategoryGroup.SERVICE, PlaceCategoryGroup.of("hospital"))
  }

  // Google の型は増減するので、知らない型でも既定のピンに落とさず接尾辞で拾う。
  @Test
  fun unknownCodes_fallBackToSuffix() {
    assertEquals(PlaceCategoryGroup.FOOD, PlaceCategoryGroup.of("ramen_restaurant"))
    assertEquals(PlaceCategoryGroup.SHOPPING, PlaceCategoryGroup.of("shoe_store"))
    assertEquals(PlaceCategoryGroup.TRANSIT, PlaceCategoryGroup.of("monorail_station"))
    assertEquals(PlaceCategoryGroup.CULTURE, PlaceCategoryGroup.of("zen_temple"))
  }

  // 完全一致は接尾辞より優先する。coffee_shop は「店」ではなくカフェとして出したい。
  @Test
  fun exactMatchWins_overSuffix() {
    assertEquals(PlaceCategoryGroup.CAFE, PlaceCategoryGroup.of("coffee_shop"))
    assertEquals(PlaceCategoryGroup.CAFE, PlaceCategoryGroup.of("ice_cream_shop"))
    assertEquals(PlaceCategoryGroup.PARK, PlaceCategoryGroup.of("dog_park"))
  }

  @Test
  fun missingOrUnrecognizedCode_isOther() {
    assertEquals(PlaceCategoryGroup.OTHER, PlaceCategoryGroup.of(null as String?))
    assertEquals(PlaceCategoryGroup.OTHER, PlaceCategoryGroup.of(""))
    assertEquals(PlaceCategoryGroup.OTHER, PlaceCategoryGroup.of("   "))
    assertEquals(PlaceCategoryGroup.OTHER, PlaceCategoryGroup.of("point_of_interest"))
  }

  @Test
  fun lookupIsCaseInsensitive_andTrimmed() {
    assertEquals(PlaceCategoryGroup.FOOD, PlaceCategoryGroup.of("  Restaurant "))
  }

  @Test
  fun categoryOverload_readsTheCode_notTheDisplayName() {
    // 表示名はロケールで変わるので判定に使わない。code が正。
    assertEquals(PlaceCategoryGroup.CAFE, PlaceCategoryGroup.of(PlaceCategory("cafe", "カフェ")))
    assertEquals(PlaceCategoryGroup.OTHER, PlaceCategoryGroup.of(null as PlaceCategory?))
  }
}
