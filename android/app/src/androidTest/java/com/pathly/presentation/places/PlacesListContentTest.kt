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
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceRegion
import com.pathly.presentation.common.ListMapMode
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  // 追加のメニューで選んだ手段（"search" / "map"）。
  private var addedBy: String? = null

  @Test
  fun `切り替えで地図表示になり表示のメニューが消えて戻すと一覧に戻る`() {
    setContent(PlacesState(items = listOf(plain, wished)))
    composeTestRule.onNodeWithText("登録順 ・ 新しい順").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("地図で見る").performClick()

    composeTestRule.onNodeWithTag("listDisplayMenuButton").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所1").assertDoesNotExist()
    // 追加の丸は地図表示でも同じ位置に残る。
    composeTestRule.onNodeWithTag("placeAddButton").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("一覧で見る").performClick()

    composeTestRule.onNodeWithText("登録順 ・ 新しい順").assertIsDisplayed()
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
    setContent(PlacesState(items = listOf(plain, wished), viewMode = ListMapMode.MAP))

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
    setContent(PlacesState(items = listOf(plain, wished), viewMode = ListMapMode.MAP))
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
    setContent(PlacesState(items = emptyList(), viewMode = ListMapMode.MAP))

    composeTestRule.onNodeWithText("場所がありません", substring = true).assertIsDisplayed()
  }

  @Test
  fun `地図表示でたたむと検索と絞り込みが消えて帯に件数が出る`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = ListMapMode.MAP))
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
        viewMode = ListMapMode.MAP,
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
    setContent(
      PlacesState(items = listOf(plain, wished), wishlistFilter = WishlistFilter.WISHLISTED, sort = PlaceSort.NAME, sortDescending = false),
    )
    composeTestRule.onNodeWithText("名前順 ・ あ → ん").assertIsDisplayed()

    composeTestRule.onNodeWithText("初期状態に戻す").performClick()

    composeTestRule.onNodeWithText("登録順 ・ 新しい順").assertIsDisplayed()
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
    composeTestRule.onNodeWithText("登録順 ・ 新しい順").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 件").assertIsDisplayed()
    composeTestRule.onNodeWithText("初期状態に戻す").assertIsNotEnabled()

    // 閉じると、効いている条件のチップの行に戻る（初期状態なので何も出ない）。
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithText("初期状態に戻す").assertDoesNotExist()
  }

  @Test
  fun `一覧の先頭に統計が出て_絞り込むと数字が変わり全体の数が添えられる`() {
    setContent(PlacesState(items = listOf(plain, wished)))
    composeTestRule.onNodeWithContentDescription("場所 2 か所").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("訪問済み 0").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("行けた 0 / 1").assertIsDisplayed()

    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    // 絞り込みの欄を開いている間は出さない。
    composeTestRule.onNodeWithTag("listStatsBar").assertDoesNotExist()
    composeTestRule.onNodeWithTag("行きたい=行きたい").performClick()
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()

    composeTestRule.onNodeWithContentDescription("場所 1 / 2 か所").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("行けた 0 / 1").assertIsDisplayed()
  }

  // 統計の帯を押すとその場で統計のカードに広がり、見出しの行で帯に戻る。
  @Test
  fun `統計の帯を押すとカードに広がり_畳むと帯に戻る`() {
    setContent(PlacesState(items = listOf(plain, wished)))

    composeTestRule.onNodeWithTag("listStatsBar").performClick()
    composeTestRule.onNodeWithTag("listStatsCard").assertIsDisplayed()
    composeTestRule.onNodeWithText("いま行きたい").assertIsDisplayed()
    composeTestRule.onNodeWithText("行きたいで行けた").assertIsDisplayed()
    composeTestRule.onNodeWithText("0 / 1 か所").assertIsDisplayed()
    composeTestRule.onNodeWithText("業種の内訳").assertIsDisplayed()
    composeTestRule.onNodeWithText("未分類 2").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("統計を畳む").performClick()
    composeTestRule.onNodeWithTag("listStatsBar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("listStatsCard").assertDoesNotExist()
  }

  @Test
  fun `統計は地図表示と場所が無いときは出ない`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = ListMapMode.MAP))
    composeTestRule.onNodeWithTag("listStatsBar").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("一覧で見る").performClick()
    composeTestRule.onNodeWithTag("listStatsBar").assertIsDisplayed()
  }

  @Test
  fun `場所が1件も無いときは統計を出さない`() {
    setContent(PlacesState(items = emptyList()))
    composeTestRule.onNodeWithTag("listStatsBar").assertDoesNotExist()
  }

  @Test
  fun `表示のメニューで並べ替えの軸と向きを選ぶとボタンに出る`() {
    setContent(PlacesState(items = listOf(plain, wished)))

    composeTestRule.onNodeWithTag("listDisplayMenuButton").performClick()
    composeTestRule.onNodeWithText("古い順").performClick()
    composeTestRule.onNodeWithText("登録順 ・ 古い順").assertIsDisplayed()

    // 軸を変えると、向きはその軸の既定に戻る。
    composeTestRule.onNodeWithTag("listDisplayMenuButton").performClick()
    composeTestRule.onNodeWithText("訪問回数順").performClick()
    composeTestRule.onNodeWithText("訪問回数順 ・ 多い順").assertIsDisplayed()
  }

  @Test
  fun `右下の追加の丸からメニューで追加の手段を選ぶ`() {
    setContent(PlacesState(items = listOf(plain, wished)))

    composeTestRule.onNodeWithContentDescription("場所を追加").performClick()
    composeTestRule.onNodeWithText("検索して追加").assertIsDisplayed()
    composeTestRule.onNodeWithText("地図で選ぶ").performClick()

    assertEquals("map", addedBy)
  }

  @Test
  fun `地図表示の右下にすべての場所を見るボタンが出て_0件のときは出さない`() {
    setContent(PlacesState(items = listOf(plain, wished), viewMode = ListMapMode.MAP))
    composeTestRule.onNodeWithContentDescription("すべての場所を見る").assertIsDisplayed()

    // 絞り込みで 0 件にすると、寄せる先が無いので消える。
    composeTestRule.onNodeWithTag("filterPanelButton").performClick()
    composeTestRule.onNodeWithTag("行きたい=行きたい").performClick()
    composeTestRule.onNodeWithTag("訪問=訪問済み").performClick()
    composeTestRule.onNodeWithContentDescription("すべての場所を見る").assertDoesNotExist()
  }

  @Test
  fun `行に削除のボタンを出さない`() {
    setContent(PlacesState(items = listOf(plain, wished)))

    composeTestRule.onNodeWithText("場所1").assertIsDisplayed()
    // 押せないごみ箱（立ち寄りの記録がある場所）も含めて、削除に関わるボタンが無い。
    composeTestRule.onNodeWithContentDescription("削除", substring = true).assertDoesNotExist()
  }

  private val fuchu = item(id = 11, wishlisted = false, region = PlaceRegion.of("JP", "東京都", "府中市"))
  private val chofu = item(id = 12, wishlisted = true, region = PlaceRegion.of("JP", "東京都", "調布市"))
  private val sapporo = item(id = 13, wishlisted = false, region = PlaceRegion.of("JP", "北海道", "札幌市"))
  private val regional = listOf(fuchu, chofu, sapporo)

  @Test
  fun `地域で分けるとはじめは見出しだけで_押すと開閉できる`() {
    setContent(PlacesState(items = regional))
    composeTestRule.onNodeWithText("場所11").assertIsDisplayed()

    composeTestRule.onNodeWithTag("listDisplayMenuButton").performClick()
    composeTestRule.onNodeWithText("地域で分ける").performClick()

    // はじめはすべて閉じて、見出し（名前と件数）だけ。並びは北から。表示のボタンに分け方が添わる。
    composeTestRule.onNodeWithText("登録順 ・ 新しい順 ・ 地域").assertIsDisplayed()
    composeTestRule.onNodeWithTag("placeGroup:region:JP|東京都|").assertIsDisplayed()
    composeTestRule.onNodeWithText("2").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所11").assertDoesNotExist()
    composeTestRule.onNodeWithText("府中市").assertDoesNotExist()
    assertTrue(
      composeTestRule.onNodeWithText("北海道").fetchSemanticsNode().positionInRoot.y <
        composeTestRule.onNodeWithText("東京都").fetchSemanticsNode().positionInRoot.y,
    )

    // 都道府県を開くと市区町村の見出し、市区町村を開くと場所。
    composeTestRule.onNodeWithText("東京都").performClick()
    composeTestRule.onNodeWithText("府中市").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所11").assertDoesNotExist()
    composeTestRule.onNodeWithText("府中市").performClick()
    composeTestRule.onNodeWithText("場所11").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所12").assertDoesNotExist()

    // 閉じると中が消える。
    composeTestRule.onNodeWithText("東京都").performClick()
    composeTestRule.onNodeWithText("府中市").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所11").assertDoesNotExist()
  }

  @Test
  fun `分けないに戻すと今の一覧と同じになる`() {
    setContent(PlacesState(items = regional, grouping = PlaceGrouping.CATEGORY))
    composeTestRule.onNodeWithText("未分類").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所11").assertDoesNotExist()

    composeTestRule.onNodeWithTag("listDisplayMenuButton").performClick()
    composeTestRule.onNodeWithText("分けない").performClick()

    composeTestRule.onNodeWithText("登録順 ・ 新しい順").assertIsDisplayed()
    composeTestRule.onNodeWithText("未分類").assertDoesNotExist()
    composeTestRule.onNodeWithText("場所11").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所13").assertIsDisplayed()
  }

  @Test
  fun `名前の検索中は当たったまとまりを開いて見せ_消すと閉じた見出しに戻る`() {
    setContent(PlacesState(items = regional, grouping = PlaceGrouping.REGION))
    composeTestRule.onNodeWithText("場所12").assertDoesNotExist()

    // 検索は見出しのアイコンから開く（見出しの位置が窓になる）。
    composeTestRule.onNodeWithContentDescription("検索").performClick()
    composeTestRule.onNodeWithText("名前で検索").performTextInput("12")

    composeTestRule.onNodeWithText("東京都").assertIsDisplayed()
    composeTestRule.onNodeWithText("調布市").assertIsDisplayed()
    composeTestRule.onNodeWithText("場所12").assertIsDisplayed()
    // 当たらなかったまとまりは出さない。
    composeTestRule.onNodeWithText("北海道").assertDoesNotExist()
    composeTestRule.onNodeWithText("府中市").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("検索語を消す").performClick()

    composeTestRule.onNodeWithText("北海道").assertIsDisplayed()
    composeTestRule.onNodeWithText("調布市").assertDoesNotExist()
  }

  @Test
  fun `絞り込んだ結果を分けて見出しの件数も絞った数になる`() {
    setContent(PlacesState(items = regional, grouping = PlaceGrouping.REGION, wishlistFilter = WishlistFilter.WISHLISTED))

    composeTestRule.onNodeWithText("東京都").assertIsDisplayed()
    composeTestRule.onNodeWithText("1").assertIsDisplayed()
    composeTestRule.onNodeWithText("北海道").assertDoesNotExist()
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
    var statsExpanded by remember { mutableStateOf(false) }
    PlacesListContent(
      state = state,
      onAddByMap = { addedBy = "map" },
      onAddBySearch = { addedBy = "search" },
      onClearFilters = { state = state.copy(wishlistFilter = WishlistFilter.ANY, visitedFilter = VisitedFilter.ANY) },
      onResetListOptions = { state = PlacesState(items = state.items, viewMode = state.viewMode) },
      onNameQueryChange = { state = state.copy(nameQuery = it) },
      onWishlistFilterChange = { state = state.copy(wishlistFilter = it) },
      onVisitedFilterChange = { state = state.copy(visitedFilter = it) },
      onToggleCategoryFilter = {},
      onClearCategoryFilter = {},
      onToggleRegionFilter = {},
      onClearRegionFilter = {},
      onSortChange = { state = state.copy(sort = it, sortDescending = it.defaultDescending) },
      onSortDescendingChange = { state = state.copy(sortDescending = it) },
      onGroupingChange = { state = state.copy(grouping = it, openGroups = emptySet()) },
      onToggleGroup = { key -> state = state.copy(openGroups = if (key in state.openGroups) state.openGroups - key else state.openGroups + key) },
      onViewModeChange = { state = state.copy(viewMode = it) },
      onControlsCollapsedChange = { state = state.copy(controlsCollapsed = it) },
      onItemClick = {},
      onMapPlaceClick = {},
      onToggleWishlist = {},
      statsExpanded = statsExpanded,
      onStatsExpandedChange = { statsExpanded = it },
      // CI のエミュレータでは GMS の地図が落ちることがあるので、地図は描かない（ほかの画面のテストと同じ）。
      mapContent = {},
    )
  }

  private fun item(id: Long, wishlisted: Boolean, region: PlaceRegion = PlaceRegion.UNKNOWN) = PlaceListItem(
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
    region = region,
  )
}
