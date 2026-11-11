package com.pathly.presentation.places

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * 場所タブの一覧表示と地図表示の切り替え。絞り込みを共有したまま切り替えられることを確かめる。
 * 地図の中（マーカー・範囲）は画面のテストでは見にくいので、ここでは扱わない。
 */
@RunWith(AndroidJUnit4::class)
class PlacesListContentTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val plain = item(id = 1, wishlisted = false)
  private val wished = item(id = 2, wishlisted = true)

  @Test
  fun `切り替えで地図表示になり並べ替えが消えて戻すと一覧に戻る`() {
    setContent(PlacesState(items = listOf(plain, wished)))
    composeTestRule.onNodeWithText("並べ替え", substring = true).assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("地図で見る").performClick()

    composeTestRule.onNodeWithText("並べ替え", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("一覧で見る").performClick()

    composeTestRule.onNodeWithText("並べ替え", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
  }

  @Test
  fun `絞り込みは地図表示を挟んでも残る`() {
    setContent(PlacesState(items = listOf(plain, wished)))

    // 「行きたい」チップで行きたいの場所だけにする。
    // 行の「行きたい」の表示と重なるので、先に並ぶチップを押す。
    composeTestRule.onAllNodesWithText("行きたい").onFirst().performClick()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("地図で見る").performClick()
    composeTestRule.onNodeWithContentDescription("一覧で見る").performClick()

    composeTestRule.onNodeWithText("場所2").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()
  }

  @Test
  fun `地図表示でバックを押すと一覧に戻る`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = PlacesViewMode.MAP))

    Espresso.pressBack()

    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("地図で見る").assertIsDisplayed()
  }

  @Test
  fun `地図表示でも0件の文言が出る`() {
    setContent(PlacesState(items = emptyList(), viewMode = PlacesViewMode.MAP))

    composeTestRule.onNodeWithText("場所がありません", substring = true).assertIsDisplayed()
  }

  @Test
  fun `地図表示でたたむと検索と絞り込みが消えて帯に件数が出る`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = PlacesViewMode.MAP))
    composeTestRule.onNodeWithText("すべて").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("たたむ").performClick()

    composeTestRule.onNodeWithText("すべて").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所 2 件").assertIsDisplayed()
    // 一覧へ戻る切り替えは、たたんでも残る。
    composeTestRule.onNodeWithContentDescription("一覧で見る").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("ひろげる").performClick()

    composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
  }

  @Test
  fun `たたんだ帯に絞り込み中であることと残った件数が出る`() {
    setContent(
      PlacesState(
        items = listOf(plain, wished),
        wishlistFilter = WishlistFilter.WISHLISTED,
        viewMode = PlacesViewMode.MAP,
        controlsCollapsed = true,
      ),
    )

    composeTestRule.onNodeWithText("絞り込み 1・1 / 2 件").assertIsDisplayed()
  }

  @Test
  fun `一覧表示にはたたむボタンが無い`() {
    setContent(PlacesState(items = listOf(plain, wished), controlsCollapsed = true))

    composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("たたむ").assertDoesNotExist()
  }

  // ViewModel の代わりに、状態をこの場で持って操作に応じて書き換える。
  private fun setContent(initial: PlacesState) {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        Host(initial)
      }
    }
  }

  @Composable
  private fun Host(initial: PlacesState) {
    var state by remember { mutableStateOf(initial) }
    PlacesListContent(
      state = state,
      onAddByMap = {},
      onAddBySearch = {},
      onClearFilters = { state = state.copy(wishlistFilter = WishlistFilter.ANY, visitedFilter = VisitedFilter.ANY) },
      onNameQueryChange = { state = state.copy(nameQuery = it) },
      onWishlistFilterChange = { state = state.copy(wishlistFilter = it) },
      onVisitedFilterChange = { state = state.copy(visitedFilter = it) },
      onToggleCategoryFilter = {},
      onClearCategoryFilter = {},
      onToggleRegionFilter = {},
      onClearRegionFilter = {},
      onSortChange = {},
      onToggleSortDirection = {},
      onViewModeChange = { state = state.copy(viewMode = it) },
      onControlsCollapsedChange = { state = state.copy(controlsCollapsed = it) },
      onItemClick = {},
      onMapPlaceClick = {},
      onToggleWishlist = {},
      onDeleteRequest = {},
      // CI のエミュレータでは GMS の地図が落ちることがあるので、地図は描かない（ほかの画面のテストと同じ）。
      mapContent = {},
    )
  }

  private fun item(id: Long, wishlisted: Boolean) = PlaceListItem(
    place = Place(
      id = id,
      name = "場所$id",
      latitude = 35.0 + id * 0.01,
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
    markedVisitedAt = null,
    visitCount = 0,
  )
}
