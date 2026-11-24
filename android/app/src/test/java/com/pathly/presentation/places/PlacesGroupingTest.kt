package com.pathly.presentation.places

import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceRegion
import com.pathly.presentation.common.ListGroupRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** 場所一覧のまとまり（地域・業種で分けて開閉する → specs/screens.md「5. 場所画面」）。 */
class PlacesGroupingTest {

  private fun item(id: Long, region: PlaceRegion, code: String? = null, wishlisted: Boolean = false, name: String = "場所$id") = PlaceListItem(
    place = Place(
      id = id,
      name = name,
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
    wishlistId = if (wishlisted) id else null,
    priority = null,
    markedVisitedAt = null,
    visitCount = 0,
    region = region,
  )

  private val fuchuCafe = item(1, PlaceRegion.of("JP", "東京都", "府中市"), code = "cafe", wishlisted = true)
  private val chofuPark = item(2, PlaceRegion.of("JP", "東京都", "調布市"), code = "park")
  private val fuchuPark = item(3, PlaceRegion.of("JP", "東京都", "府中市"), code = "park")
  private val tokyoNoCity = item(4, PlaceRegion.of("JP", "東京都", null))
  private val hokkaido = item(5, PlaceRegion.of("JP", "北海道", "札幌市"), code = "restaurant")
  private val unknown = item(6, PlaceRegion.UNKNOWN, code = "unknown_type")
  private val honolulu = item(7, PlaceRegion.of("US", "ハワイ州", "ホノルル"))
  private val items = listOf(fuchuCafe, chofuPark, fuchuPark, tokyoNoCity, hokkaido, unknown, honolulu)

  private fun List<PlaceGroup>.labels() = map { it.label }

  @Test
  fun region_ordersPrefecturesFromNorthThenOverseasThenUnknown() {
    val groups = groupPlaces(items, PlaceGrouping.REGION)

    assertEquals(listOf("北海道", "東京都", "アメリカ合衆国", "地域不明"), groups.labels())
    assertEquals(listOf(1, 4, 1, 1), groups.map { it.count })
  }

  @Test
  fun region_hasCitiesByNameAndPlacesWithoutCityDirectlyUnderPrefecture() {
    val tokyo = groupPlaces(items, PlaceGrouping.REGION).single { it.label == "東京都" }

    // 市区町村は名前順（件数の多さで動かない）。市区町村が分からない場所は都道府県の直下。
    assertEquals(listOf("調布市", "府中市"), tokyo.children.labels())
    assertEquals(listOf(1, 2), tokyo.children.map { it.count })
    assertEquals(listOf(4L), tokyo.items.map { it.place.id })
  }

  @Test
  fun region_keepsTheGivenOrderInsideAGroup() {
    // 並べ替えの結果（ここでは逆順）をまとまりの中でも保つ。
    val fuchu = groupPlaces(items.reversed(), PlaceGrouping.REGION)
      .single { it.label == "東京都" }.children.single { it.label == "府中市" }

    assertEquals(listOf(3L, 1L), fuchu.items.map { it.place.id })
  }

  @Test
  fun category_followsTheFilterOrderWithOtherAndUncategorizedLast() {
    val groups = groupPlaces(items, PlaceGrouping.CATEGORY)

    assertEquals(listOf("飲食", "カフェ", "公園", "その他", "未分類"), groups.labels())
    assertEquals(listOf(1, 1, 2, 1, 2), groups.map { it.count })
  }

  @Test
  fun none_makesNoGroupsAndListsPlacesAsTheyAre() {
    val state = PlacesState(items = items, grouping = PlaceGrouping.NONE)

    assertTrue(groupPlaces(items, PlaceGrouping.NONE).isEmpty())
    assertEquals(state.visibleItems.map { it.place.id }, state.listRows.map { (it as ListGroupRow.Item).item.place.id })
  }

  @Test
  fun rows_showOnlyHeadersWhenAllClosed() {
    val rows = PlacesState(items = items, grouping = PlaceGrouping.REGION).listRows

    assertTrue(rows.all { it is ListGroupRow.Header && !it.expanded && it.depth == 0 })
    assertEquals(4, rows.size)
  }

  @Test
  fun rows_showChildrenThenDirectPlacesOfAnOpenGroup() {
    val tokyoKey = groupPlaces(items, PlaceGrouping.REGION).single { it.label == "東京都" }.key
    val fuchuKey = groupPlaces(items, PlaceGrouping.REGION).single { it.label == "東京都" }.children.single { it.label == "府中市" }.key
    val rows = PlacesState(items = items, grouping = PlaceGrouping.REGION, openGroups = setOf(tokyoKey, fuchuKey)).listRows

    val described = rows.map { row ->
      when (row) {
        is ListGroupRow.Header -> "${row.depth}:${row.group.label}"
        is ListGroupRow.Item -> "${row.depth}:#${row.item.place.id}"
      }
    }
    // 既定の並べ替え（登録の新しい順）で、府中市の中は 3 → 1。
    assertEquals(
      listOf("0:北海道", "0:東京都", "1:調布市", "1:府中市", "2:#3", "2:#1", "1:#4", "0:アメリカ合衆国", "0:地域不明"),
      described,
    )
  }

  @Test
  fun rows_countOnlyPlacesLeftByFiltersAndDropEmptyGroups() {
    val state = PlacesState(items = items, grouping = PlaceGrouping.REGION, wishlistFilter = WishlistFilter.WISHLISTED)

    val headers = state.listRows.filterIsInstance<ListGroupRow.Header<PlaceListItem>>()
    assertEquals(listOf("東京都" to 1), headers.map { it.group.label to it.group.count })
  }

  @Test
  fun rows_openEveryGroupWhileSearchingByNameWithoutChangingRememberedOnes() {
    val searching = PlacesState(items = items, grouping = PlaceGrouping.REGION, nameQuery = "場所3")

    val described = searching.listRows.map { row ->
      when (row) {
        is ListGroupRow.Header -> "${row.group.label}:${row.expanded}"
        is ListGroupRow.Item -> "#${row.item.place.id}"
      }
    }
    assertEquals(listOf("東京都:true", "府中市:true", "#3"), described)
    // 検索語を消すと、覚えている開閉（ここでは全部閉じている）に戻る。
    assertTrue(searching.copy(nameQuery = "").listRows.all { it is ListGroupRow.Header && !it.expanded })
  }

  @Test
  fun groupingChangeScrollsToTopButOpeningAGroupDoesNot() {
    val base = PlacesState(items = items)

    assertTrue(base.scrollResetKey != base.copy(grouping = PlaceGrouping.REGION).scrollResetKey)
    assertEquals(
      base.copy(grouping = PlaceGrouping.REGION).scrollResetKey,
      base.copy(grouping = PlaceGrouping.REGION, openGroups = setOf("x")).scrollResetKey,
    )
  }

  @Test
  fun resetIsNotNeededJustForGrouping() {
    // 分け方は見せ方の設定なので「初期状態に戻す」を出す理由にしない。
    assertTrue(PlacesState(items = items, grouping = PlaceGrouping.CATEGORY).isInitialListOptions)
  }
}
