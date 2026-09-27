package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class PlaceCategoryFacetTest {

  @Test
  fun withGoogleInfo_followsCategoryGroup() {
    assertEquals(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.of(hasGoogleInfo = true, categoryCode = "cafe"))
    assertEquals(PlaceCategoryFacet.FOOD, PlaceCategoryFacet.of(hasGoogleInfo = true, categoryCode = "ramen_restaurant"))
  }

  // 施設情報はあるが業種が無い・当たらない場所は「その他」。未分類とは分ける。
  @Test
  fun withGoogleInfoButNoKnownCategory_isOther() {
    assertEquals(PlaceCategoryFacet.OTHER, PlaceCategoryFacet.of(hasGoogleInfo = true, categoryCode = null))
    assertEquals(PlaceCategoryFacet.OTHER, PlaceCategoryFacet.of(hasGoogleInfo = true, categoryCode = "something_new"))
  }

  @Test
  fun withoutGoogleInfo_isUncategorized() {
    assertEquals(PlaceCategoryFacet.UNCATEGORIZED, PlaceCategoryFacet.of(hasGoogleInfo = false, categoryCode = null))
  }

  @Test
  fun ofPlace_usesGooglePlaceIdAsPresenceOfGoogleInfo() {
    assertEquals(PlaceCategoryFacet.UNCATEGORIZED, PlaceCategoryFacet.of(place(googlePlaceId = null, category = null)))
    assertEquals(PlaceCategoryFacet.OTHER, PlaceCategoryFacet.of(place(googlePlaceId = "gp", category = null)))
    assertEquals(PlaceCategoryFacet.PARK, PlaceCategoryFacet.of(place(googlePlaceId = "gp", category = PlaceCategory("park", "公園"))))
  }

  private fun place(googlePlaceId: String?, category: PlaceCategory?) = Place(
    id = 1,
    name = null,
    latitude = 35.0,
    longitude = 139.0,
    note = null,
    googleName = null,
    googleAddress = null,
    category = category,
    googlePlaceId = googlePlaceId,
    createdAt = Date(0),
    updatedAt = Date(0),
  )
}
