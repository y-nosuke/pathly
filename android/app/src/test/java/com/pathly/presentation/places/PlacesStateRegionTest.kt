package com.pathly.presentation.places

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceRegion
import com.pathly.domain.model.RegionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Date

/** 場所一覧の地域の絞り込み（→ adr/0029）。 */
class PlacesStateRegionTest {

  private fun item(id: Long, region: PlaceRegion, code: String? = null) = PlaceListItem(
    place = Place(
      id = id,
      name = "場所$id",
      latitude = 35.0,
      longitude = 139.0,
      note = null,
      googleName = null,
      googleAddress = null,
      category = code?.let { PlaceCategory(it, it) },
      googlePlaceId = code?.let { "gp-$id" },
      createdAt = Date(id),
      updatedAt = Date(id),
    ),
    wishlistId = null,
    priority = null,
    markedVisitedAt = null,
    visitCount = 0,
    region = region,
  )

  private val shibuyaCafe = item(1, PlaceRegion("東京都", "渋谷区"), code = "cafe")
  private val shinjukuPark = item(2, PlaceRegion("東京都", "新宿区"), code = "park")
  private val kyotoCafe = item(3, PlaceRegion("京都府", "京都市"), code = "cafe")
  private val unknown = item(4, PlaceRegion.UNKNOWN)
  private val items = listOf(shibuyaCafe, shinjukuPark, kyotoCafe, unknown)

  @Test
  fun regionFilter_keepsPlacesInAnySelectedRegion() {
    val state = PlacesState(items = items, regionFilter = setOf(RegionKey("京都府"), RegionKey("東京都", "渋谷区")))

    assertEquals(setOf(1L, 3L), state.visibleItems.map { it.place.id }.toSet())
    assertFalse(state.noFilter)
    assertEquals(1, state.activeFilterCount)
  }

  @Test
  fun regionFilter_unknownKeepsPlacesWithoutRegion() {
    val state = PlacesState(items = items, regionFilter = setOf(RegionKey.UNKNOWN))

    assertEquals(listOf(4L), state.visibleItems.map { it.place.id })
  }

  @Test
  fun regionCounts_followOtherAxesButNotRegion() {
    // 業種でカフェに絞ると、地域の件数はカフェだけで数える。地域の選択は件数に効かせない。
    val state = PlacesState(
      items = items,
      categoryFilter = setOf(PlaceCategoryFacet.CAFE),
      regionFilter = setOf(RegionKey("京都府")),
    )

    val counts = state.regionCounts.associate { it.prefecture to it.count }
    assertEquals(mapOf("東京都" to 1, "京都府" to 1, null to 0), counts)
    assertEquals(listOf(3L), state.visibleItems.map { it.place.id })
  }

  @Test
  fun categoryCounts_followRegionFilter() {
    val state = PlacesState(items = items, regionFilter = setOf(RegionKey("東京都")))

    assertEquals(1, state.categoryCounts[PlaceCategoryFacet.CAFE])
    assertEquals(1, state.categoryCounts[PlaceCategoryFacet.PARK])
  }

  @Test
  fun regionFilter_roundTripsThroughSavedStateHandle() {
    val handle = SavedStateHandle()
    val selected = setOf(RegionKey("東京都"), RegionKey("東京都", "渋谷区"), RegionKey.UNKNOWN)

    PlacesState(regionFilter = selected).saveListOptionsTo(handle)

    assertEquals(selected, PlacesState().withListOptionsFrom(handle).regionFilter)
  }
}
