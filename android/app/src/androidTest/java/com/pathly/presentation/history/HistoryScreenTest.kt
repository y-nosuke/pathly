package com.pathly.presentation.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.tracking.OverlayTrack
import com.pathly.ui.theme.PathlyAndroidTheme
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var mockOnTrackClick: (GpsTrack) -> Unit
  private lateinit var mockViewModel: HistoryViewModel
  private lateinit var uiStateFlow: MutableStateFlow<HistoryState>

  @Before
  fun setup() {
    mockOnTrackClick = mockk(relaxed = true)
    mockViewModel = mockk(relaxed = true)

    // 初期状態のUIState
    uiStateFlow = MutableStateFlow(
      HistoryState(
        tracks = emptyList(),
        isLoading = false,
        errorMessage = null,
      ),
    )

    // ViewModelのuiStateをモック
    io.mockk.every { mockViewModel.uiState } returns uiStateFlow
    io.mockk.every { mockViewModel.deleteTrack(any()) } returns Unit
    io.mockk.every { mockViewModel.clearError() } returns Unit
  }

  @Test
  fun historyScreen_initialState_showsTitle() {
    // Given & When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("外出履歴")
      .assertIsDisplayed()
  }

  @Test
  fun historyScreen_loading_showsProgressIndicator() {
    // Given
    uiStateFlow.value = HistoryState(
      tracks = emptyList(),
      isLoading = true,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    // CircularProgressIndicatorはcontentDescriptionがないので、存在確認が困難
    // 代わりにローディング状態でトラックリストが表示されないことを確認
    composeTestRule
      .onNodeWithText("記録がありません")
      .assertDoesNotExist()
  }

  @Test
  fun historyScreen_emptyTracks_showsEmptyMessage() {
    // Given
    uiStateFlow.value = HistoryState(
      tracks = emptyList(),
      isLoading = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("記録がありません")
      .assertIsDisplayed()
  }

  @Test
  fun historyScreen_withTracks_displaysTrackItems() {
    // Given
    val startTime = Date(System.currentTimeMillis() - 3600000) // 1時間前
    val endTime = Date()

    val tracks = listOf(
      GpsTrack(
        id = 1L,
        startTime = startTime,
        endTime = endTime,
        isActive = false,
        points = listOf(
          GpsPoint(
            id = 1L,
            trackId = 1L,
            latitude = 35.6762,
            longitude = 139.6503,
            altitude = null,
            accuracy = 10f,
            speed = null,
            bearing = null,
            timestamp = startTime,
            createdAt = startTime,
          ),
          GpsPoint(
            id = 2L,
            trackId = 1L,
            latitude = 35.6896,
            longitude = 139.7006,
            altitude = null,
            accuracy = 8f,
            speed = null,
            bearing = null,
            timestamp = endTime,
            createdAt = startTime,
          ),
        ),
        createdAt = startTime,
        updatedAt = endTime,
      ),
    )

    uiStateFlow.value = HistoryState(
      tracks = tracks,
      isLoading = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    // 日付フォーマット確認（正確な日付は環境依存なので部分一致）
    composeTestRule
      .onNodeWithText("開始:", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("終了:", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("移動距離:", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("立ち寄り:", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun historyScreen_trackItem_hasClickAction() {
    // Given
    val track = createSampleTrack()
    uiStateFlow.value = HistoryState(
      tracks = listOf(track),
      isLoading = false,
      errorMessage = null,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // When & Then - 名前編集・削除をまとめたオーバーフローメニュー（その他）がクリック可能。
    composeTestRule
      .onNodeWithContentDescription("その他")
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun historyScreen_deleteButton_triggersViewModel() {
    // Given
    val track = createSampleTrack()
    uiStateFlow.value = HistoryState(
      tracks = listOf(track),
      isLoading = false,
      errorMessage = null,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // When - オーバーフローメニューを開いて「削除」を選び、確認ダイアログで「削除する」を押す。
    composeTestRule
      .onNodeWithContentDescription("その他")
      .performClick()
    composeTestRule
      .onNodeWithText("削除")
      .performClick()
    composeTestRule
      .onNodeWithText("削除する")
      .performClick()

    // Then
    verify { mockViewModel.deleteTrack(track) }
  }

  @Test
  fun historyScreen_multipleTracks_displaysAllTracks() {
    // Given
    val tracks = listOf(
      createSampleTrack(id = 1L, pointsCount = 5, stopCount = 5),
      createSampleTrack(id = 2L, pointsCount = 3, stopCount = 3),
      createSampleTrack(id = 3L, pointsCount = 0, stopCount = 0),
    )

    uiStateFlow.value = HistoryState(
      tracks = tracks,
      isLoading = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("立ち寄り: 5件", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("立ち寄り: 3件", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("立ち寄り: 0件", substring = true)
      .assertIsDisplayed()

    // 各トラックにオーバーフローメニュー（その他）が1つずつ存在することを確認。
    composeTestRule
      .onAllNodesWithContentDescription("その他")
      .assertCountEquals(3)
  }

  @Test
  fun historyScreen_activeTrack_showsWithoutEndTime() {
    // Given - アクティブなトラック（endTimeがnull）
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = null, // アクティブなトラック
      isActive = true,
      points = createSamplePoints(2),
      createdAt = Date(),
      updatedAt = Date(),
    )

    uiStateFlow.value = HistoryState(
      tracks = listOf(track),
      isLoading = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("開始:", substring = true)
      .assertIsDisplayed()

    // 終了時刻は表示されない（endTimeがnullなので）
    composeTestRule
      .onNodeWithText("終了:", substring = true)
      .assertDoesNotExist()
  }

  @Test
  fun historyScreen_zeroDistanceTrack_showsZeroDistance() {
    // Given - 距離0のトラック（1点のみまたは同じ場所）
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = listOf(
        GpsPoint(
          id = 1L,
          trackId = 1L,
          latitude = 35.6762,
          longitude = 139.6503,
          altitude = null,
          accuracy = 10f,
          speed = null,
          bearing = null,
          timestamp = Date(),
          createdAt = Date(),
        ),
      ),
      createdAt = Date(),
      updatedAt = Date(),
    )

    uiStateFlow.value = HistoryState(
      tracks = listOf(track),
      isLoading = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("移動距離: 0.0km", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun historyScreen_stateChange_updatesUIReactively() {
    // Given - 初期状態は空
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(
          viewModel = mockViewModel,
          onTrackClick = mockOnTrackClick,
        )
      }
    }

    // Then - 空メッセージが表示される
    composeTestRule
      .onNodeWithText("記録がありません")
      .assertIsDisplayed()

    // When - トラックを追加
    val track = createSampleTrack()
    composeTestRule.runOnUiThread {
      uiStateFlow.value = HistoryState(
        tracks = listOf(track),
        isLoading = false,
        errorMessage = null,
      )
    }

    // Then - トラックアイテムが表示される
    composeTestRule
      .onNodeWithText("移動距離:", substring = true)
      .assertIsDisplayed()

    // 空メッセージは表示されない
    composeTestRule
      .onNodeWithText("記録がありません")
      .assertDoesNotExist()
  }

  @Test
  fun historyScreen_nameQuery_showsOnlyMatchingTracks() {
    uiStateFlow.value = HistoryState(
      tracks = listOf(
        createSampleTrack(id = 1L).copy(name = "秋の鎌倉さんぽ"),
        createSampleTrack(id = 2L).copy(name = "江ノ島"),
      ),
      nameQuery = "鎌倉",
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    composeTestRule.onNodeWithText("秋の鎌倉さんぽ").assertIsDisplayed()
    composeTestRule.onNodeWithText("江ノ島").assertDoesNotExist()
  }

  @Test
  fun historyScreen_nameQuery_typingAndClearingUpdatesQuery() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L).copy(name = "秋の鎌倉さんぽ")))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    composeTestRule.onNodeWithText("名前・日付で検索").performTextInput("鎌")
    verify { mockViewModel.setNameQuery("鎌") }

    // 検索語があるときだけ × が出て、押すと検索語を消す。
    composeTestRule.onNodeWithContentDescription("検索語を消す").assertDoesNotExist()
    uiStateFlow.value = uiStateFlow.value.copy(nameQuery = "鎌")
    composeTestRule.onNodeWithContentDescription("検索語を消す").performClick()
    verify { mockViewModel.setNameQuery("") }
  }

  @Test
  fun historyScreen_nameQueryWithNoMatch_showsNoMatchMessage() {
    uiStateFlow.value = HistoryState(
      tracks = listOf(createSampleTrack(id = 1L).copy(name = "秋の鎌倉さんぽ")),
      nameQuery = "箱根",
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    composeTestRule.onNodeWithText("条件に合う記録がありません").assertIsDisplayed()
    // 検索語は検索欄の × で消すので、絞り込みの欄の軸としては数えない（「すべて外す」も押せない）。
    composeTestRule.onNodeWithText("絞り込み").performClick()
    composeTestRule.onNodeWithText("0 件").assertIsDisplayed()
    composeTestRule.onNodeWithText("すべて外す").assertIsNotEnabled()
  }

  @Test
  fun historyScreen_activeFilterChip_removesThatAxis() {
    uiStateFlow.value = HistoryState(
      tracks = listOf(createSampleTrack(id = 1L)),
      favoriteFilter = TrackFavoriteFilter.FAVORITE,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    // 効いている条件の数がボタンに出て、チップの × でその軸だけ外す。
    composeTestRule.onNodeWithText("絞り込み 1").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("お気に入りの絞り込みを外す").performClick()
    verify { mockViewModel.setFavoriteFilter(TrackFavoriteFilter.ANY) }
  }

  @Test
  fun historyScreen_activeFilterChip_tapFlipsTheChoice() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)), favoriteFilter = TrackFavoriteFilter.FAVORITE)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    // チップ自体を押すと「お気に入り ⇄ お気に入り以外」を切り替える（欄は開かない）。
    composeTestRule.onNodeWithTag("activeFilter=お気に入り").performClick()
    verify { mockViewModel.setFavoriteFilter(TrackFavoriteFilter.NOT_FAVORITE) }
    composeTestRule.onNodeWithTag("listFilterPanel").assertDoesNotExist()
  }

  @Test
  fun historyScreen_filterPanel_selectsAChoiceAndClearsAll() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)), namedFilter = TrackNamedFilter.NAMED)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    composeTestRule.onNodeWithText("絞り込み 1").performClick()
    // 3 状態の軸は、並べたボタンから選ぶ。
    composeTestRule.onNodeWithTag("立ち寄り=立ち寄りなし").performClick()
    verify { mockViewModel.setStopFilter(TrackStopFilter.NO_STOPS) }
    composeTestRule.onNodeWithText("すべて外す").performClick()
    verify { mockViewModel.clearFilters() }
  }

  @Test
  fun historyScreen_resetListOptions_shownOnlyWhenNotInitial() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)), sortDescending = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    // 並べ替えの向きだけ変えていても出る。押すとまとめて戻す。
    composeTestRule.onNodeWithText("初期状態に戻す").performClick()
    verify { mockViewModel.resetListOptions() }
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)))
    composeTestRule.onNodeWithText("初期状態に戻す").assertDoesNotExist()
  }

  @Test
  fun historyScreen_filterPanel_closesFromBottomButton() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    // 片手でも届くよう、欄を開いている間は下にも閉じるボタンを出す。
    composeTestRule.onNodeWithText("絞り込み").performClick()
    composeTestRule.onNodeWithTag("listFilterPanel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filterPanelCloseButton").performClick()
    composeTestRule.onNodeWithTag("listFilterPanel").assertDoesNotExist()
    composeTestRule.onNodeWithTag("filterPanelCloseButton").assertDoesNotExist()
  }

  @Test
  fun historyScreen_filterPanelOpen_hidesActiveChipsAndResetsFromHeader() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)), favoriteFilter = TrackFavoriteFilter.FAVORITE)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick)
      }
    }

    composeTestRule.onNodeWithContentDescription("お気に入りの絞り込みを外す").assertIsDisplayed()
    composeTestRule.onNodeWithText("絞り込み 1").performClick()
    // 欄を開いている間はチップの行を出さない（欄で選ぶたびに欄がずれないように）。戻すのは欄の見出しから。
    composeTestRule.onNodeWithContentDescription("お気に入りの絞り込みを外す").assertDoesNotExist()
    composeTestRule.onNodeWithText("初期状態に戻す").performClick()
    verify { mockViewModel.resetListOptions() }
  }

  @Test
  fun historyScreen_mapToggle_switchesViewMode() {
    uiStateFlow.value = HistoryState(tracks = listOf(createSampleTrack(id = 1L)))
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick, mapContent = {})
      }
    }

    composeTestRule.onNodeWithContentDescription("地図で見る").performClick()
    verify { mockViewModel.setViewMode(HistoryViewMode.MAP) }
  }

  @Test
  fun historyScreen_mapView_listsFilteredTracks_selectsWithCheck_opensWithRowTap() {
    val first = createSampleTrack(id = 1L).copy(name = "鎌倉さんぽ")
    val second = createSampleTrack(id = 2L).copy(name = "横浜みなと")
    uiStateFlow.value = HistoryState(
      tracks = listOf(first, second),
      viewMode = HistoryViewMode.MAP,
      selected = listOf(OverlayTrack(track = first, colorIndex = 0)),
    )
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick, mapContent = {})
      }
    }

    // 地図表示: 下のシートに絞り込んだ経路の一覧。統計・並べ替えは出さない。
    composeTestRule.onNodeWithTag("historyMapSheet").assertIsDisplayed()
    composeTestRule.onNodeWithText("経路 2 件").assertIsDisplayed()
    composeTestRule.onNodeWithText("地図に重ねる経路を選ぶ（1 / 8 本）").assertIsDisplayed()
    composeTestRule.onNodeWithText("並べ替え: 日付順 ▾").assertDoesNotExist()

    // チェックで選ぶ（重ねる）。行のタップは経路詳細へ。
    composeTestRule.onNodeWithTag("historyMapCheck-2").performClick()
    verify { mockViewModel.toggleOverlay(second) }
    composeTestRule.onNodeWithText("横浜みなと").performClick()
    verify { mockOnTrackClick(second) }

    // 選んだ経路をすべて外す・すべて見る。
    composeTestRule.onNodeWithText("すべて外す").performClick()
    verify { mockViewModel.clearOverlays() }
    composeTestRule.onNodeWithContentDescription("選んだ経路をすべて見る").assertIsDisplayed()

    // 一覧へ戻る。
    composeTestRule.onNodeWithContentDescription("一覧で見る").performClick()
    verify { mockViewModel.setViewMode(HistoryViewMode.LIST) }
  }

  @Test
  fun historyScreen_mapView_selectedOutsideFilter_isListedSeparately() {
    val favorite = createSampleTrack(id = 1L).copy(name = "お気に入りの経路", isFavorite = true)
    val other = createSampleTrack(id = 2L).copy(name = "ほかの経路")
    uiStateFlow.value = HistoryState(
      tracks = listOf(favorite, other),
      favoriteFilter = TrackFavoriteFilter.FAVORITE,
      viewMode = HistoryViewMode.MAP,
      selected = listOf(OverlayTrack(track = other, colorIndex = 0)),
    )
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick, mapContent = {})
      }
    }

    composeTestRule.onNodeWithText("絞り込んだ経路 1 件").assertIsDisplayed()
    composeTestRule.onNodeWithText("絞り込みの外（重ねている経路）").assertIsDisplayed()
    composeTestRule.onNodeWithText("ほかの経路").assertIsDisplayed()
  }

  @Test
  fun historyScreen_mapView_collapsedBar_showsFilterSummary() {
    val favorite = createSampleTrack(id = 1L).copy(isFavorite = true)
    uiStateFlow.value = HistoryState(
      tracks = listOf(favorite, createSampleTrack(id = 2L)),
      favoriteFilter = TrackFavoriteFilter.FAVORITE,
      viewMode = HistoryViewMode.MAP,
      controlsCollapsed = true,
    )
    composeTestRule.setContent {
      PathlyAndroidTheme {
        HistoryScreen(viewModel = mockViewModel, onTrackClick = mockOnTrackClick, mapContent = {})
      }
    }

    composeTestRule.onNodeWithText("絞り込み 1・1 / 2 件").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("ひろげる").performClick()
    verify { mockViewModel.setControlsCollapsed(false) }
  }

  private fun createSampleTrack(
    id: Long = 1L,
    pointsCount: Int = 2,
    stopCount: Int = 0,
  ): GpsTrack = GpsTrack(
    id = id,
    startTime = Date(System.currentTimeMillis() - 3600000),
    endTime = Date(),
    isActive = false,
    stopCount = stopCount,
    points = createSamplePoints(pointsCount, trackId = id),
    createdAt = Date(),
    updatedAt = Date(),
  )

  private fun createSamplePoints(count: Int, trackId: Long = 1L): List<GpsPoint> = (1..count).map { index ->
    GpsPoint(
      id = index.toLong(),
      trackId = trackId,
      latitude = 35.6762 + (index * 0.001), // 少しずつ異なる位置
      longitude = 139.6503 + (index * 0.001),
      altitude = null,
      accuracy = 10f,
      speed = null,
      bearing = null,
      timestamp = Date(System.currentTimeMillis() - (3600000 - index * 1000)),
      createdAt = Date(),
    )
  }
}
