package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPlaceFilterTest {

  private val wishedCafe = RegisteredPlace(1L, "カフェ", 35.0, 139.0, isWishlisted = true, isVisited = false, categoryCode = "cafe", hasGoogleInfo = true)
  private val visitedPark = RegisteredPlace(2L, "公園", 35.0, 139.0, isWishlisted = false, isVisited = true, categoryCode = "park", hasGoogleInfo = true)
  private val pinOnly = RegisteredPlace(3L, null, 35.0, 139.0, hasGoogleInfo = false)
  private val unknownKind = RegisteredPlace(4L, "何か", 35.0, 139.0, categoryCode = null, hasGoogleInfo = true)
  private val places = listOf(wishedCafe, visitedPark, pinOnly, unknownKind)

  @Test
  fun `何も絞らなければ全部出す`() {
    val filter = MapPlaceFilter()

    assertTrue(filter.isEmpty)
    assertEquals(places, places.filter(filter::matches))
  }

  @Test
  fun `行きたい・訪問は3状態で絞る`() {
    assertEquals(listOf(wishedCafe), places.filter(MapPlaceFilter(wishlisted = true)::matches))
    assertEquals(listOf(visitedPark, pinOnly, unknownKind), places.filter(MapPlaceFilter(wishlisted = false)::matches))
    assertEquals(listOf(visitedPark), places.filter(MapPlaceFilter(visited = true)::matches))
    assertFalse(MapPlaceFilter(visited = false).isEmpty)
  }

  @Test
  fun `業種は選んだどれかに当たれば残す`() {
    val filter = MapPlaceFilter(categories = setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.PARK))

    assertEquals(listOf(wishedCafe, visitedPark), places.filter(filter::matches))
  }

  @Test
  fun `未分類とその他を一覧と同じに分ける`() {
    // Google の施設情報が無い場所は未分類、あるが業種が分からない場所はその他。
    assertEquals(listOf(pinOnly), places.filter(MapPlaceFilter(categories = setOf(PlaceCategoryFacet.UNCATEGORIZED))::matches))
    assertEquals(listOf(unknownKind), places.filter(MapPlaceFilter(categories = setOf(PlaceCategoryFacet.OTHER))::matches))
  }

  @Test
  fun `軸どうしはAND`() {
    val filter = MapPlaceFilter(wishlisted = true, categories = setOf(PlaceCategoryFacet.PARK))

    assertTrue(places.none(filter::matches))
  }

  @Test
  fun `業種の件数は行きたい・訪問に合う場所で数え0件の業種も残す`() {
    assertEquals(
      mapOf(PlaceCategoryFacet.CAFE to 1, PlaceCategoryFacet.PARK to 1, PlaceCategoryFacet.UNCATEGORIZED to 1, PlaceCategoryFacet.OTHER to 1),
      MapPlaceFilter().categoryCounts(places),
    )
    assertEquals(
      mapOf(PlaceCategoryFacet.CAFE to 1, PlaceCategoryFacet.PARK to 0, PlaceCategoryFacet.UNCATEGORIZED to 0, PlaceCategoryFacet.OTHER to 0),
      MapPlaceFilter(wishlisted = true).categoryCounts(places),
    )
    // 業種そのものの選択は件数に効かせない。
    assertEquals(MapPlaceFilter().categoryCounts(places), MapPlaceFilter(categories = setOf(PlaceCategoryFacet.CAFE)).categoryCounts(places))
  }

  @Test
  fun `チップは 指定なし→あり→なし→指定なし と回る`() {
    val none: Boolean? = null
    assertEquals(true, none.nextTriState())
    assertEquals(false, true.nextTriState())
    assertNull(false.nextTriState())
  }
}
