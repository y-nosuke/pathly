package com.pathly.presentation.places

import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class PlacesStatisticsTest {

  @Test
  fun countsPlacesVisitedAndWishes() {
    val stats = PlacesStatistics.of(
      listOf(
        // 立ち寄りの記録で訪問済み。
        item(id = 1, visitCount = 2),
        // 手で訪問済みにした。
        item(id = 2, markedVisited = true),
        // いま行きたい（まだ行っていない）。
        item(id = 3, wishlisted = true),
        // 行きたいで行った（行きたいは外れている）。
        item(id = 4, visitCount = 1, fulfilledWishCount = 1),
        // 行きたいで行った後に入れ直した。分母にも分子にも 1 つとして入る。
        item(id = 5, visitCount = 3, fulfilledWishCount = 2, wishlisted = true),
        // 何もない。
        item(id = 6),
      ),
    )

    assertEquals(PlacesStatistics(placeCount = 6, visitedCount = 4, wishedCount = 3, fulfilledCount = 2), stats)
    assertEquals(66, stats.fulfilledPercent)
    assertEquals("2 / 3（66%）", stats.fulfilledLabel())
  }

  // まだ残っているのに 100% と出さないよう切り捨てる。
  @Test
  fun percent_isFloored() {
    assertEquals(99, PlacesStatistics(placeCount = 200, visitedCount = 0, wishedCount = 200, fulfilledCount = 199).fulfilledPercent)
    assertEquals(100, PlacesStatistics(placeCount = 3, visitedCount = 3, wishedCount = 3, fulfilledCount = 3).fulfilledPercent)
  }

  // 行きたいにした場所が無ければ、割合は出さない（0 で割らない）。
  @Test
  fun noWishes_hasNoPercent() {
    val stats = PlacesStatistics.of(listOf(item(id = 1, visitCount = 1)))

    assertNull(stats.fulfilledPercent)
    assertEquals("0 / 0", stats.fulfilledLabel())
  }

  @Test
  fun empty_isAllZero() {
    assertEquals(PlacesStatistics(0, 0, 0, 0), PlacesStatistics.of(emptyList()))
  }

  // 数える範囲は一覧と同じ（絞り込み・名前の検索の後に残った場所）。
  @Test
  fun stateStatistics_countsOnlyVisibleItems() {
    val state = PlacesState(
      items = listOf(item(id = 1, visitCount = 1), item(id = 2, wishlisted = true), item(id = 3)),
      visitedFilter = VisitedFilter.UNVISITED,
    )

    assertEquals(PlacesStatistics(placeCount = 2, visitedCount = 0, wishedCount = 1, fulfilledCount = 0), state.statistics)
  }

  @Test
  fun statsText_addsTotalOnlyWhenFiltered() {
    val stats = PlacesStatistics(placeCount = 4, visitedCount = 3, wishedCount = 7, fulfilledCount = 2)
    assertEquals("4 か所 ・ 訪問 3 ・ 行きたいで行けた 2 / 7（28%）", placesStatsText(stats, totalCount = 4, isFiltered = false))
    assertEquals("4 / 12 か所 ・ 訪問 3 ・ 行きたいで行けた 2 / 7（28%）", placesStatsText(stats, totalCount = 12, isFiltered = true))
  }

  private fun item(
    id: Long,
    visitCount: Int = 0,
    markedVisited: Boolean = false,
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
      category = null,
      googlePlaceId = null,
      createdAt = Date(id),
      updatedAt = Date(id),
    ),
    wishlistId = if (wishlisted) id else null,
    priority = null,
    markedVisitedAt = if (markedVisited) Date(id) else null,
    visitCount = visitCount,
    fulfilledWishCount = fulfilledWishCount,
  )
}
