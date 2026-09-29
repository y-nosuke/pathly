package com.pathly.presentation.places

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PlacesStateTest {

  private val cafe = item(id = 1, googlePlaceId = "gp-1", code = "cafe")
  private val park = item(id = 2, googlePlaceId = "gp-2", code = "park", wishlisted = true)
  private val cafe2 = item(id = 3, googlePlaceId = "gp-3", code = "coffee_shop", wishlisted = true)
  private val uncategorized = item(id = 4, googlePlaceId = null, code = null)
  private val items = listOf(cafe, park, cafe2, uncategorized)

  @Test
  fun categoryFilter_keepsPlacesInAnySelectedCategory() {
    val state = PlacesState(items = items, categoryFilter = setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.UNCATEGORIZED))

    assertEquals(setOf(1L, 3L, 4L), state.visibleItems.map { it.place.id }.toSet())
    assertFalse(state.noFilter)
  }

  @Test
  fun categoryFilter_isAndWithOtherAxes() {
    val state = PlacesState(
      items = items,
      wishlistFilter = WishlistFilter.WISHLISTED,
      categoryFilter = setOf(PlaceCategoryFacet.CAFE),
    )

    assertEquals(listOf(3L), state.visibleItems.map { it.place.id })
  }

  @Test
  fun emptyCategoryFilter_keepsEverything() {
    val state = PlacesState(items = items)

    assertEquals(4, state.visibleItems.size)
    assertTrue(state.noFilter)
  }

  @Test
  fun categoryCounts_countAllPlacesRegardlessOfOtherFilters() {
    val state = PlacesState(items = items, wishlistFilter = WishlistFilter.WISHLISTED)

    assertEquals(
      mapOf(PlaceCategoryFacet.CAFE to 2, PlaceCategoryFacet.PARK to 1, PlaceCategoryFacet.UNCATEGORIZED to 1),
      state.categoryCounts,
    )
  }

  @Test
  fun listOptions_roundTripThroughSavedStateHandle() {
    val handle = SavedStateHandle()
    PlacesState(
      wishlistFilter = WishlistFilter.WISHLISTED,
      visitedFilter = VisitedFilter.UNVISITED,
      categoryFilter = setOf(PlaceCategoryFacet.PARK),
      sort = PlaceSort.NAME,
      sortDescending = true,
    ).saveListOptionsTo(handle)

    val restored = PlacesState().withListOptionsFrom(handle)

    assertEquals(WishlistFilter.WISHLISTED, restored.wishlistFilter)
    assertEquals(VisitedFilter.UNVISITED, restored.visitedFilter)
    assertEquals(setOf(PlaceCategoryFacet.PARK), restored.categoryFilter)
    assertEquals(PlaceSort.NAME, restored.sort)
    assertTrue(restored.sortDescending)
  }

  // 詳細から戻っただけ（条件が同じ）なら先頭へ戻さない。条件か先頭の場所が変わったら戻す。
  @Test
  fun scrollResetKey_changesOnlyWithConditionsOrTopItem() {
    val base = PlacesState(items = items)

    assertEquals(base.scrollResetKey, base.copy(isLoading = false, errorMessage = "x").scrollResetKey)
    assertNotEquals(base.scrollResetKey, base.copy(categoryFilter = setOf(PlaceCategoryFacet.CAFE)).scrollResetKey)
    assertNotEquals(base.scrollResetKey, base.copy(sortDescending = !base.sortDescending).scrollResetKey)
    assertNotEquals("先頭（登録が新しい id 4）が消えた", base.scrollResetKey, base.copy(items = items.filter { it.place.id != 4L }).scrollResetKey)
  }

  private fun item(id: Long, googlePlaceId: String?, code: String?, wishlisted: Boolean = false) = PlaceListItem(
    place = Place(
      id = id,
      name = "場所$id",
      latitude = 35.0,
      longitude = 139.0,
      note = null,
      googleName = null,
      googleAddress = null,
      category = code?.let { PlaceCategory(it, it) },
      googlePlaceId = googlePlaceId,
      createdAt = Date(id),
      updatedAt = Date(id),
    ),
    wishlistId = if (wishlisted) id else null,
    priority = null,
    markedVisitedAt = null,
    visitCount = 0,
  )
}
