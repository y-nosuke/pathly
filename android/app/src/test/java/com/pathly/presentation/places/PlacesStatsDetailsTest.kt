package com.pathly.presentation.places

import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceListItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class PlacesStatsDetailsTest {

  @Test
  fun countsWishlistedNow() {
    val details = PlacesStatsDetails.of(
      listOf(
        item(id = 1, wishlisted = true),
        // 行きたいで行って外れたものは、いま行きたいに入れない。
        item(id = 2, fulfilledWishCount = 1),
        item(id = 3),
      ),
    )

    assertEquals(1, details.wishlistedCount)
  }

  // 多い順（同じなら絞り込みの欄の並び）。4 業種までは全部を出す。
  @Test
  fun categories_upTo4_areAllShown() {
    val details = PlacesStatsDetails.of(
      listOf(
        item(1, "park"),
        item(2, "cafe"),
        item(3, "cafe"),
        item(4, "restaurant"),
        // Google の施設情報が無い場所は未分類。
        item(5, null),
      ),
    )

    assertEquals(
      listOf(
        CategoryShare(PlaceCategoryFacet.CAFE, 2),
        CategoryShare(PlaceCategoryFacet.FOOD, 1),
        CategoryShare(PlaceCategoryFacet.PARK, 1),
        CategoryShare(PlaceCategoryFacet.UNCATEGORIZED, 1),
      ),
      details.categories,
    )
  }

  // 5 業種以上なら、上位 3 業種と「ほかの業種」にまとめる。
  @Test
  fun categories_over4_groupRestAsOthers() {
    val details = PlacesStatsDetails.of(
      listOf(
        item(1, "cafe"),
        item(2, "cafe"),
        item(3, "cafe"),
        item(4, "park"),
        item(5, "park"),
        item(6, "restaurant"),
        item(7, "lodging"),
        item(8, null),
      ),
    )

    assertEquals(
      listOf(
        CategoryShare(PlaceCategoryFacet.CAFE, 3),
        CategoryShare(PlaceCategoryFacet.PARK, 2),
        CategoryShare(PlaceCategoryFacet.FOOD, 1),
        CategoryShare(null, 2),
      ),
      details.categories,
    )
    assertEquals("ほかの業種", details.categories.last().label)
  }

  @Test
  fun empty_hasNoCategories() {
    assertEquals(PlacesStatsDetails(wishlistedCount = 0, categories = emptyList()), PlacesStatsDetails.of(emptyList()))
  }

  private fun item(
    id: Long,
    categoryCode: String? = null,
    wishlisted: Boolean = false,
    fulfilledWishCount: Int = 0,
  ) = PlaceListItem(
    place = Place(
      id = id,
      name = "場所$id",
      latitude = 35.0,
      longitude = 139.0,
      note = null,
      googleName = null,
      googleAddress = null,
      category = categoryCode?.let { PlaceCategory(it) },
      googlePlaceId = categoryCode?.let { "g$id" },
      createdAt = Date(id),
      updatedAt = Date(id),
    ),
    wishlistId = if (wishlisted) id else null,
    priority = null,
    markedVisitedAt = null,
    visitCount = 0,
    fulfilledWishCount = fulfilledWishCount,
  )
}
