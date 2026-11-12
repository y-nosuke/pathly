package com.pathly.presentation.places

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

    // 絞り込みの欄で「行きたい」を選び、行きたいの場所だけにする。
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithTag("行きたい=行きたい").performClick()
    composeTestRule.onNodeWithText("1 件").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
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
  fun `絞り込みの欄は下の閉じるボタンと戻るで閉じる`() {
    setContent(PlacesState(items = listOf(plain, wished)))
    composeTestRule.onNodeWithTag("filterPanelCloseButton").assertDoesNotExist()

    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithTag("filterPanelCloseButton").performClick()
    composeTestRule.onNodeWithTag("listFilterPanel").assertDoesNotExist()
    composeTestRule.onNodeWithTag("filterPanelCloseButton").assertDoesNotExist()

    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    Espresso.pressBack()
    composeTestRule.onNodeWithTag("listFilterPanel").assertDoesNotExist()
  }

  @Test
  fun `地図表示で欄を開いているときの戻るは欄だけを閉じる`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = PlacesViewMode.MAP))
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()

    Espresso.pressBack()

    // 1 回目は欄を閉じるだけで、地図表示のまま。2 回目で一覧に戻る。
    composeTestRule.onNodeWithTag("listFilterPanel").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()
    Espresso.pressBack()
    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
  }

  @Test
  fun `地図表示でも0件の文言が出る`() {
    setContent(PlacesState(items = emptyList(), viewMode = PlacesViewMode.MAP))

    composeTestRule.onNodeWithText("場所がありません", substring = true).assertIsDisplayed()
  }

  @Test
  fun `地図表示でたたむと検索と絞り込みが消えて帯に件数が出る`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = PlacesViewMode.MAP))
    composeTestRule.onNodeWithTag("filterPanelButton").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("たたむ").performClick()

    composeTestRule.onNodeWithTag("filterPanelButton").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所 2 件").assertIsDisplayed()
    // 一覧へ戻る切り替えは、たたんでも残る。
    composeTestRule.onNodeWithContentDescription("一覧で見る").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("ひろげる").performClick()

    composeTestRule.onNodeWithTag("filterPanelButton").assertIsDisplayed()
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

    composeTestRule.onNodeWithTag("filterPanelButton").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("たたむ").assertDoesNotExist()
  }

  @Test
  fun `効いている条件がチップで出て_×で外せる`() {
    setContent(PlacesState(items = listOf(plain, wished), wishlistFilter = WishlistFilter.WISHLISTED))
    composeTestRule.onNodeWithText("絞り込み 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("行きたいの絞り込みを外す").performClick()

    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("行きたいの絞り込みを外す").assertDoesNotExist()
    composeTestRule.onNodeWithText("絞り込み").assertIsDisplayed()
  }

  @Test
  fun `3状態の軸のチップを押すと〇〇と〇〇以外が切り替わる`() {
    setContent(PlacesState(items = listOf(plain, wished), wishlistFilter = WishlistFilter.WISHLISTED))
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()

    composeTestRule.onNodeWithTag("activeFilter=行きたい").performClick()

    // 欄は開かず、行きたい以外に切り替わる。もう一度押すと戻る。
    composeTestRule.onNodeWithTag("listFilterPanel").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("activeFilter=行きたい以外").performClick()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()
    composeTestRule.onNodeWithText("絞り込み 1").assertIsDisplayed()
  }

  @Test
  fun `欄のすべて外すで軸をまとめて外せる`() {
    setContent(
      PlacesState(items = listOf(plain, wished), wishlistFilter = WishlistFilter.WISHLISTED, visitedFilter = VisitedFilter.UNVISITED),
    )
    composeTestRule.onNodeWithText("絞り込み 2").performClick()

    composeTestRule.onNodeWithText("すべて外す").performClick()

    composeTestRule.onNodeWithText("2 件").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所2").assertIsDisplayed()
  }

  @Test
  fun `初期状態に戻すで絞り込みと並べ替えをまとめて戻す`() {
    setContent(PlacesState(items = listOf(plain, wished), wishlistFilter = WishlistFilter.WISHLISTED, sort = PlaceSort.NAME))
    composeTestRule.onNodeWithText("並べ替え: 名前順", substring = true).assertIsDisplayed()

    composeTestRule.onNodeWithText("初期状態に戻す").performClick()

    composeTestRule.onNodeWithText("並べ替え: 登録順", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
    composeTestRule.onNodeWithText("初期状態に戻す").assertDoesNotExist()
  }

  @Test
  fun `初期状態では初期状態に戻すを出さない`() {
    setContent(PlacesState(items = listOf(plain, wished)))

    composeTestRule.onNodeWithText("初期状態に戻す").assertDoesNotExist()
    // 欄の見出しには出しておき、押せなくするだけ（出たり消えたりしないように）。
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithText("初期状態に戻す").assertIsNotEnabled()
  }

  @Test
  fun `欄を開いている間は効いている条件のチップを出さず_欄の見出しから初期状態に戻せる`() {
    setContent(PlacesState(items = listOf(plain, wished), wishlistFilter = WishlistFilter.WISHLISTED, sort = PlaceSort.NAME))
    composeTestRule.onNodeWithContentDescription("行きたいの絞り込みを外す").assertIsDisplayed()

    composeTestRule.onNodeWithTag("filterPanelButton").performClick()

    // 欄で選ぶたびにチップの行が出たり消えたりして欄がずれないよう、開いている間は出さない。
    composeTestRule.onNodeWithContentDescription("行きたいの絞り込みを外す").assertDoesNotExist()
    composeTestRule.onNodeWithText("初期状態に戻す").assertIsEnabled().performClick()
    composeTestRule.onNodeWithText("並べ替え: 登録順", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("2 件").assertIsDisplayed()
    composeTestRule.onNodeWithText("初期状態に戻す").assertIsNotEnabled()

    // 閉じると、効いている条件のチップの行に戻る（初期状態なので何も出ない）。
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithText("初期状態に戻す").assertDoesNotExist()
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
      onResetListOptions = { state = PlacesState(items = state.items, viewMode = state.viewMode) },
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
