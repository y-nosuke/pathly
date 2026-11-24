package com.pathly.presentation.tracking

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.presentation.history.SameFacilityReview
import com.pathly.presentation.places.PlaceDeleteUndo
import com.pathly.presentation.places.PlaceEditActions
import com.pathly.presentation.stops.ManualStopActions
import com.pathly.presentation.stops.StopEditActions
import com.pathly.ui.theme.PathlyAndroidTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class TrackingScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var mockOnRequestPermission: () -> Unit
  private lateinit var mockViewModel: TrackingViewModel
  private lateinit var overlayViewModel: TrackOverlayViewModel
  private lateinit var uiStateFlow: MutableStateFlow<TrackingState>
  private lateinit var placeEdit: PlaceEditActions
  private lateinit var regionMarkers: RegionMarkerActions
  private lateinit var stopEdit: StopEditActions
  private lateinit var manualStop: ManualStopActions
  private lateinit var unresolvedCountFlow: MutableStateFlow<Int>

  @Before
  fun setup() {
    // 全画面マップの isMyLocationEnabled が権限を要求するため、事前に付与しておく
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    instrumentation.uiAutomation.grantRuntimePermission(
      packageName,
      Manifest.permission.ACCESS_FINE_LOCATION,
    )
    instrumentation.uiAutomation.grantRuntimePermission(
      packageName,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    mockOnRequestPermission = mockk(relaxed = true)
    mockViewModel = mockk(relaxed = true)
    overlayViewModel = TrackOverlayViewModel(mockk(relaxed = true), mockk(relaxed = true))

    uiStateFlow = MutableStateFlow(
      TrackingState(
        hasLocationPermission = true,
        isTracking = false,
        errorMessage = null,
      ),
    )

    io.mockk.every { mockViewModel.uiState } returns uiStateFlow
    io.mockk.every { mockViewModel.checkLocationPermission() } returns Unit
    io.mockk.every { mockViewModel.startTracking() } returns Unit
    io.mockk.every { mockViewModel.stopTracking() } returns Unit
    // 地図の「登録済みの場所」の絞り込み（ボタン列が毎回受け取るので、本物の値を返す）。
    io.mockk.every { mockViewModel.mapPlaceFilter } returns MutableStateFlow(MapPlaceFilter())
    // 場所の編集・立ち寄りの手動追加の部品。確認待ち・取り消しは出していない状態にする。
    placeEdit = mockk(relaxed = true)
    io.mockk.every { placeEdit.nearbyRegisterPrompt } returns MutableStateFlow(null)
    io.mockk.every { placeEdit.deleteUndo } returns MutableStateFlow(PlaceDeleteUndo())
    io.mockk.every { mockViewModel.placeEdit } returns placeEdit
    manualStop = mockk(relaxed = true)
    io.mockk.every { manualStop.nearbyStopPrompt } returns MutableStateFlow(null)
    io.mockk.every { mockViewModel.manualStop } returns manualStop
    // 記録中の経路の立ち寄りを直す部品。場所の未取得・同じ施設の確認待ちは無い状態にする（テストごとに変える）。
    stopEdit = mockk(relaxed = true)
    unresolvedCountFlow = MutableStateFlow(0)
    io.mockk.every { stopEdit.unresolvedCount } returns unresolvedCountFlow
    io.mockk.every { stopEdit.sameFacilityPending } returns MutableStateFlow(emptyList())
    io.mockk.every { stopEdit.sameFacilityDialogOpen } returns MutableStateFlow(false)
    io.mockk.every { stopEdit.sameFacilityReview(any(), any()) } returns SameFacilityReview()
    io.mockk.every { mockViewModel.stopEdit } returns stopEdit
    // 地域の目印は本物（チップの増減・選び直し・上限を画面で確かめるため）。
    regionMarkers = RegionMarkerActions()
    io.mockk.every { mockViewModel.regionMarkers } returns regionMarkers
  }

  @Test
  fun trackingScreen_noPermission_showsPermissionRequest() {
    uiStateFlow.value = TrackingState(hasLocationPermission = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText("位置情報の権限が必要です")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("GPS記録機能を使用するため、位置情報へのアクセスを許可してください。")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("位置情報を許可")
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun trackingScreen_permissionButton_triggersCallback() {
    uiStateFlow.value = TrackingState(hasLocationPermission = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText("位置情報を許可")
      .performClick()

    verify { mockOnRequestPermission() }
  }

  @Test
  fun trackingScreen_hasPermissionNotTracking_showsStartButton() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("記録開始")
      .assertIsDisplayed()
      .assertHasClickAction()

    // 権限オーバーレイは表示されない
    composeTestRule
      .onNodeWithText("位置情報を許可")
      .assertDoesNotExist()
  }

  @Test
  fun trackingScreen_startButton_triggersViewModel() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("記録開始")
      .performClick()

    verify { mockViewModel.startTracking() }
  }

  @Test
  fun trackingScreen_tracking_showsStopButtonAndStatus() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("記録停止")
      .assertIsDisplayed()
      .assertHasClickAction()

    composeTestRule
      .onNodeWithText("記録中", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("移動距離")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("地点数")
      .assertIsDisplayed()
  }

  @Test
  fun trackingScreen_stopButton_triggersViewModel() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 停止は誤爆防止の確認ダイアログを挟むので、FAB だけでは停止しない。
    composeTestRule
      .onNodeWithContentDescription("記録停止")
      .performClick()
    composeTestRule
      .onNodeWithText("停止する")
      .performClick()

    verify { mockViewModel.stopTracking() }
  }

  @Test
  fun trackingScreen_withError_showsErrorMessage() {
    val errorMessage = "GPS信号を受信できません"
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = false,
      errorMessage = errorMessage,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText(errorMessage)
      .assertIsDisplayed()

    // エラーがあっても記録ボタンは表示される
    composeTestRule
      .onNodeWithContentDescription("記録開始")
      .assertIsDisplayed()
  }

  @Test
  fun trackingScreen_stateChanges_updatesReactively() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("記録開始")
      .assertIsDisplayed()

    composeTestRule.runOnUiThread {
      uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)
    }

    composeTestRule
      .onNodeWithContentDescription("記録停止")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithContentDescription("記録開始")
      .assertDoesNotExist()
  }

  @Test
  fun trackingScreen_permissionGranted_hidesPermissionOverlay() {
    uiStateFlow.value = TrackingState(hasLocationPermission = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText("位置情報を許可")
      .assertIsDisplayed()

    composeTestRule.runOnUiThread {
      uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = false)
    }

    composeTestRule
      .onNodeWithContentDescription("記録開始")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("位置情報を許可")
      .assertDoesNotExist()
  }

  @Test
  fun trackingScreen_searchAndRegister_selectingPredictionOpensPlaceSheet() {
    coEvery { mockViewModel.predictPlacesNear(any(), any()) } returns
      listOf(PlacePrediction(placeId = "p1", primaryText = "テスト喫茶", secondaryText = "東京都千代田区"))
    coEvery { placeEdit.fetchPlaceResult("p1") } returns
      PlaceSearchResult(
        googlePlaceId = "p1",
        name = "テスト喫茶",
        address = "東京都千代田区",
        category = null,
        latitude = 35.68,
        longitude = 139.76,
      )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 地図の上の入口で検索窓を開く（検索のセッションを始める）。窓と重なる上端の入口は隠れる。
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearch").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).assertDoesNotExist()
    verify { mockViewModel.startPlaceSearch() }

    // 施設の候補は「登録」の 1 行だけ（地域へ移動は出ない）。選ぶと、検索窓を閉じて場所シートで登録へ進む。
    composeTestRule.onNodeWithTag("trackingPlaceSearchField").performTextInput("テスト")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("東京都千代田区").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(MOVE_ROW).assertDoesNotExist()
    composeTestRule.onNodeWithTag(REGISTER_ROW).assertIsDisplayed().assert(hasText("登録"))
    composeTestRule.onNodeWithTag(REGISTER_ROW).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("trackingPlaceSearch").fetchSemanticsNodes().isEmpty()
    }
    // 場所シート（POI タップと同じ）が出る。登録のボタンはシートの下のほうにあるので、在ることだけを見る。
    composeTestRule.onNodeWithText("登録").assertExists()
  }

  @Test
  fun trackingScreen_searchAndRegister_closeReturnsToMap() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).performClick()
    composeTestRule.onNodeWithContentDescription("検索を閉じる").performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearch").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("記録開始").assertIsDisplayed()
  }

  @Test
  fun trackingScreen_jumpToRegion_selectingPredictionClosesSearch() {
    stubRegions()
    setTrackingContent()

    // 検索窓を開く（施設と地域の共通の窓）。
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearch").assertIsDisplayed()
    verify { mockViewModel.startPlaceSearch() }

    // 「地域へ移動」の行を選ぶと、その範囲を引いて窓を閉じる（場所シートは出さない＝登録はしない）。
    selectRegion(query = "札幌", openSearch = false)
    coVerify { mockViewModel.fetchRegion("r1") }
    coVerify(exactly = 0) { placeEdit.fetchPlaceResult(any()) }
    composeTestRule.onNodeWithText("登録").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).assertIsDisplayed()

    // 目印がある間は地名のチップを出す。地名を押しても目印は残る（地図を寄せ直すだけ）。
    composeTestRule.onNodeWithTag("regionMarkerChip").assertIsDisplayed()
    composeTestRule.onNodeWithText("札幌市").performClick()
    composeTestRule.onNodeWithTag("regionMarkerChip").assertIsDisplayed()

    // 検索窓を開いている間は、ほかの上端の操作と同じく隠す。
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).performClick()
    composeTestRule.onNodeWithTag("regionMarkerChip").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("検索を閉じる").performClick()
    composeTestRule.onNodeWithTag("regionMarkerChip").assertIsDisplayed()

    // 記録中も、上の下で記録中のピルと並んで出る（記録中の操作は下のシート）。
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)
    composeTestRule.onNodeWithContentDescription("地図で指して追加").assertIsDisplayed()
    composeTestRule.onNodeWithTag("regionMarkerChip").assertIsDisplayed()

    // × で目印ごとチップが消える。
    composeTestRule.onNodeWithContentDescription("札幌市の目印を外す").performClick()
    composeTestRule.onNodeWithTag("regionMarkerChip").assertDoesNotExist()
  }

  @Test
  fun trackingScreen_search_regionCandidateOffersMoveAndRegister() {
    stubRegions()
    coEvery { placeEdit.fetchPlaceResult("r2") } returns
      PlaceSearchResult(
        googlePlaceId = "r2",
        name = "北九州市",
        address = "福岡県北九州市",
        category = null,
        latitude = 33.88,
        longitude = 130.88,
      )
    setTrackingContent()

    // 地域の候補は「地域へ移動」と「場所として登録」の 2 行になる。
    composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearchField").performTextInput("北九州")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(MOVE_ROW).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("北九州市").assertCountEquals(2)
    composeTestRule.onNodeWithTag(MOVE_ROW).assert(hasText("地域へ移動"))
    composeTestRule.onNodeWithTag(REGISTER_ROW).assert(hasText("場所として登録"))

    // 「場所として登録」を選ぶと、地域へは移さずに場所シートで登録へ進む。
    composeTestRule.onNodeWithTag(REGISTER_ROW).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("trackingPlaceSearch").fetchSemanticsNodes().isEmpty()
    }
    coVerify { placeEdit.fetchPlaceResult("r2") }
    coVerify(exactly = 0) { mockViewModel.fetchRegion(any()) }
    composeTestRule.onNodeWithTag("regionMarkerChip").assertDoesNotExist()
    composeTestRule.onNodeWithText("登録").assertExists()
  }

  @Test
  fun trackingScreen_jumpToRegion_multipleChipsSwitchAndRemoveOneByOne() {
    stubRegions()
    setTrackingContent()

    // 2 つ目の地域を選ぶとチップが増え、最後に移した地域のチップが選ばれた見た目になる。2 つ以上で「すべて外す」が出る。
    selectRegion(query = "札幌")
    composeTestRule.onNodeWithText("すべて外す").assertDoesNotExist()
    selectRegion(query = "北九州")
    composeTestRule.onAllNodesWithTag("regionMarkerChip").assertCountEquals(2)
    composeTestRule.onNodeWithText("すべて外す").assertIsDisplayed()
    chip("北九州市").assertIsSelected()
    chip("札幌市").assertIsNotSelected()

    // チップの地名のタップで、その地域へ行き来する。
    composeTestRule.onNodeWithText("札幌市").performClick()
    chip("札幌市").assertIsSelected()
    chip("北九州市").assertIsNotSelected()

    // 出している地域を選び直しても増えず、範囲も引き直さない（その地域へ移るだけ）。
    selectRegion(query = "北九州")
    composeTestRule.onAllNodesWithTag("regionMarkerChip").assertCountEquals(2)
    chip("北九州市").assertIsSelected()
    coVerify(exactly = 1) { mockViewModel.fetchRegion("r2") }

    // × でその地域だけを外す。1 つになると「すべて外す」は消える。
    composeTestRule.onNodeWithContentDescription("北九州市の目印を外す").performClick()
    composeTestRule.onAllNodesWithTag("regionMarkerChip").assertCountEquals(1)
    composeTestRule.onNodeWithText("札幌市").assertIsDisplayed()
    composeTestRule.onNodeWithText("すべて外す").assertDoesNotExist()
  }

  @Test
  fun trackingScreen_jumpToRegion_sixthAndLaterFoldIntoOneChip() {
    stubRegions()
    // 縦に出す数まで出しておく。
    repeat(VISIBLE_REGION_CHIPS) { i ->
      regionMarkers.show(RegionMarker("p$i", "地域$i", 35.0, 139.0, GeoBounds(34.9, 138.9, 35.1, 139.1)))
    }
    setTrackingContent()
    composeTestRule.onAllNodesWithTag("regionMarkerChip").assertCountEquals(VISIBLE_REGION_CHIPS)
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").assertDoesNotExist()

    // 6 つ目を選ぶと、縦のチップは増えず「他 1 件」にまとまる。今見ている地域がその中なので、まとめを塗りつぶす。
    selectRegion(query = "札幌")
    assertEquals(VISIBLE_REGION_CHIPS + 1, regionMarkers.state.value.markers.size)
    composeTestRule.onAllNodesWithTag("regionMarkerChip").assertCountEquals(VISIBLE_REGION_CHIPS)
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").assertIsDisplayed().assertIsSelected()
    composeTestRule.onNodeWithText("他 1 件").assertIsDisplayed()

    // 縦のチップへ移ると、まとめの塗りつぶしは外れる。
    composeTestRule.onNodeWithText("地域0").performClick()
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").assertIsNotSelected()

    // まとめのメニューから選ぶと、その地域へ移り、まとめを塗りつぶす。
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").performClick()
    composeTestRule.onNodeWithText("札幌市").performClick()
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").assertIsSelected()
    assertEquals("r1", regionMarkers.state.value.focusedPlaceId)

    // メニューの × でその地域を外すと、まとめごと消える。
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").performClick()
    composeTestRule.onNodeWithContentDescription("札幌市の目印を外す").performClick()
    composeTestRule.onNodeWithTag("regionMarkerFoldedChip").assertDoesNotExist()
    assertEquals(VISIBLE_REGION_CHIPS, regionMarkers.state.value.markers.size)

    // 「すべて外す」（縦の並びのいちばん上）でまとめて外す。
    composeTestRule.onNodeWithText("すべて外す").performClick()
    composeTestRule.onNodeWithTag("regionMarkerChip").assertDoesNotExist()
    composeTestRule.onNodeWithText("すべて外す").assertDoesNotExist()
  }

  /** 地名の候補（札幌・北九州。地域）と、その範囲を返すようにする。 */
  private fun stubRegions() {
    val regionPredictions: (String) -> List<PlacePrediction> = { query ->
      when {
        query.startsWith("札幌") -> listOf(PlacePrediction(placeId = "r1", primaryText = "札幌市", secondaryText = "北海道", isRegion = true))
        query.startsWith("北九州") -> listOf(PlacePrediction(placeId = "r2", primaryText = "北九州市", secondaryText = "福岡県", isRegion = true))
        else -> emptyList()
      }
    }
    coEvery { mockViewModel.predictPlacesNear(any(), any()) } answers { regionPredictions(firstArg()) }
    coEvery { mockViewModel.predictRegions(any(), any()) } answers { regionPredictions(firstArg()) }
    coEvery { mockViewModel.fetchRegion("r1") } returns
      MapRegion(bounds = GeoBounds(42.9, 141.2, 43.2, 141.5), latitude = 43.06, longitude = 141.35)
    coEvery { mockViewModel.fetchRegion("r2") } returns
      MapRegion(bounds = GeoBounds(33.7, 130.6, 34.0, 131.0), latitude = 33.88, longitude = 130.88)
  }

  private fun setTrackingContent() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }
  }

  /** 検索窓で [query] を打ち、地域の候補の「地域へ移動」の行を選んで窓が閉じるまで待つ。 */
  private fun selectRegion(query: String, openSearch: Boolean = true) {
    if (openSearch) composeTestRule.onNodeWithContentDescription(SEARCH_ENTRY).performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearchField").performTextInput(query)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(MOVE_ROW).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(MOVE_ROW).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("trackingPlaceSearch").fetchSemanticsNodes().isEmpty()
    }
  }

  /** 地名 [name] の地域のチップ。 */
  private fun chip(name: String) = composeTestRule.onNode(hasTestTag("regionMarkerChip") and hasAnyDescendant(hasText(name)))

  @Test
  fun trackingScreen_recordSheet_hideAlsoHidesStopAndRestoreBringsItBack() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 記録中のシートに情報と脇役の操作が並ぶ。
    composeTestRule.onNodeWithContentDescription("今ここを立ち寄り").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("地図で指して追加").assertIsDisplayed()

    // 停止はシートの中にある。畳むと一緒に隠れ、戻すボタンだけが残る（地図の上に丸は無い）。
    composeTestRule.onNodeWithContentDescription("シートを畳む").performClick()
    composeTestRule.onNodeWithContentDescription("記録停止").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("▲ 記録中", substring = true).performClick()
    composeTestRule.onNodeWithContentDescription("記録停止").assertIsDisplayed()
  }

  @Test
  fun trackingScreen_recordSheet_startAndStopAreInsideSheet() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = false)
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 停止中は、シートの中の「記録を始める」で始める。
    composeTestRule.onNode(hasContentDescription("記録開始") and hasAnyAncestor(hasTestTag("trackingRecordSheet")))
      .assertIsDisplayed()
      .performClick()
    verify { mockViewModel.startTracking() }

    // 記録中は、シートの情報の段の停止。押すと確認ダイアログが出る（すぐには止めない）。
    uiStateFlow.value = uiStateFlow.value.copy(isTracking = true)
    composeTestRule.onNode(hasContentDescription("記録停止") and hasAnyAncestor(hasTestTag("trackingRecordSheet")))
      .assertIsDisplayed()
      .performClick()
    verify(exactly = 0) { mockViewModel.stopTracking() }
  }

  @Test
  fun trackingScreen_recordSheet_listsStopsNewestFirst() {
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = true,
      stops = listOf(stop(id = 1L, name = "動物園", minutesFromStart = 0), stop(id = 2L, name = "売店", minutesFromStart = 60)),
      currentStop = stop(id = 0L, name = "カフェ", minutesFromStart = 120),
    )
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 立ち寄り中も数に入れ、新しい順（立ち寄り中 → 2 → 1）に並ぶ。
    composeTestRule.onNodeWithText("立ち寄り 3件（新しい順）").assertIsDisplayed()
    // ハーフでは新しい 2 件ほどしか見えないので、広げて全部を出す。
    composeTestRule.onNode(
      SemanticsMatcher("シートを広げる") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "シートを広げる" },
    ).performClick()
    // 立ち寄り中の名前は上の段（● 立ち寄り中）にも出るので、一覧の中の行を指す。
    fun rowTop(name: String) = composeTestRule
      .onNode(hasText(name, substring = true) and hasAnyAncestor(hasTestTag("recordingStopList")))
      .fetchSemanticsNode().boundsInRoot.top
    val current = rowTop("カフェ")
    val second = rowTop("売店")
    val first = rowTop("動物園")
    assert(current < second && second < first) { "新しい順になっていない: $current, $second, $first" }
    composeTestRule.onNodeWithText("滞在中", substring = true).assertIsDisplayed()
  }

  @Test
  fun trackingScreen_recordSheet_staysHiddenWhenFirstStopArrives() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 畳んだあとに最初の立ち寄りが来て、シートの高さが変わっても、勝手に開かない。
    composeTestRule.onNodeWithContentDescription("シートを畳む").performClick()
    composeTestRule.waitForIdle()
    uiStateFlow.value = uiStateFlow.value.copy(stops = listOf(stop(id = 1L, name = "動物園", minutesFromStart = 0)))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithContentDescription("記録停止").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("▲ 記録中", substring = true).assertIsDisplayed()
  }

  @Test
  fun trackingScreen_recordSheet_saysNoStopsYet() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isTracking = true)
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule.onNodeWithText("まだ立ち寄りはありません").assertIsDisplayed()
  }

  @Test
  fun trackingScreen_recordSheet_editsStopsLikeTrackDetail() {
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = true,
      stops = listOf(stop(id = 1L, name = "動物園", minutesFromStart = 0)),
      currentStop = stop(id = 0L, name = "カフェ", minutesFromStart = 60),
      currentLocation = LocationInfo(latitude = 35.0, longitude = 139.0, accuracy = 5f, timestamp = "10:00"),
      currentTrack = GpsTrack(
        id = 1L,
        startTime = Date(1_700_000_000_000L),
        endTime = null,
        isActive = true,
        createdAt = Date(0L),
        updatedAt = Date(0L),
      ),
    )
    unresolvedCountFlow.value = 2
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 未命名の経路は「名前を付ける」。押すと経路詳細と同じ名前のダイアログ。
    composeTestRule.onNodeWithText("名前を付ける").performClick()
    composeTestRule.onNodeWithText("経路の名前").assertIsDisplayed()
    composeTestRule.onNodeWithText("キャンセル").performClick()

    // 未取得の場所があれば「場所を取得」の帯。
    composeTestRule.onNodeWithText("場所を取得（未取得 2件）").performClick()
    verify { stopEdit.resolveNames() }

    // 立ち寄り中は「今ここ」を押せない（今いる立ち寄りは行の ⋮ から変える）。
    composeTestRule.onNodeWithContentDescription("今ここを立ち寄り").assertIsNotEnabled()

    // 立ち寄り中の行（先頭）にも ⋮ が出る。中身は保存前でもできることだけで、削除の代わりに「立ち寄り中をやめる」。
    composeTestRule.onAllNodesWithContentDescription("操作").assertCountEquals(2)
    composeTestRule.onAllNodesWithContentDescription("操作")[0].performClick()
    composeTestRule.onNodeWithText("立ち寄り中をやめる").performClick()
    verify { stopEdit.dismissLiveStop() }

    // 確定した立ち寄りの行は経路詳細と同じ。削除はスナックバーの「取り消す」つき。
    composeTestRule.onAllNodesWithContentDescription("操作")[1].performClick()
    composeTestRule.onNodeWithText("削除").performClick()
    verify { stopEdit.deleteStops(listOf(1L)) }
    composeTestRule.onNodeWithText("取り消す").assertIsDisplayed()
  }

  @Test
  fun trackingScreen_addHere_startsStopRightAway() {
    coEvery { placeEdit.nearbyPois(any(), any()) } returns emptyList()
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = true,
      currentLocation = LocationInfo(latitude = 35.0, longitude = 139.0, accuracy = 5f, timestamp = "10:00"),
    )
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    // 「今ここ」は区間を選ばず、その場で立ち寄り中にする（終わった立ち寄りは足さない）。
    composeTestRule.onNodeWithContentDescription("今ここを立ち寄り").performClick()
    composeTestRule.onNodeWithText("今から立ち寄り中にします", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("立ち寄り中にする").performClick()
    verify { manualStop.startStopHere(35.0, 139.0, any()) }
    verify(exactly = 0) { manualStop.addManualStopWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any()) }
  }

  /** 記録中の経路の立ち寄り（[minutesFromStart] 分後に着いて 30 分いた）。 */
  private fun stop(id: Long, name: String, minutesFromStart: Int): Stop {
    val arrival = Date(1_700_000_000_000L + minutesFromStart * 60_000L)
    return Stop(
      id = id,
      place = Place(
        id = id + 100L,
        name = name,
        latitude = 35.0,
        longitude = 139.0,
        note = null,
        googleName = null,
        googleAddress = null,
        category = null,
        googlePlaceId = null,
        createdAt = Date(0L),
        updatedAt = Date(0L),
      ),
      trackId = 1L,
      arrivalTime = arrival,
      departureTime = Date(arrival.time + 30 * 60_000L),
    )
  }

  @Test
  fun trackingScreen_layers_containRegisteredPlacesAndRoutesInRange() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").performClick()
    composeTestRule.onNodeWithTag("mapLayersPanel").assertIsDisplayed()
    composeTestRule.onNodeWithText("登録済みの場所").assertIsDisplayed()
    composeTestRule.onNodeWithText("この範囲の経路").assertIsDisplayed()

    composeTestRule.onNodeWithText("登録済みの場所").performClick()
    verify { mockViewModel.toggleShowRegisteredPlaces() }
  }

  @Test
  fun trackingScreen_batteryBanner_opensSettings() {
    uiStateFlow.value = TrackingState(hasLocationPermission = true, isIgnoringBatteryOptimizations = false)
    val onOpenSettings = mockk<() -> Unit>(relaxed = true)
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          onOpenSettings = onOpenSettings,
          viewModel = mockViewModel,
          trackOverlayViewModel = overlayViewModel,
          mapContent = {},
        )
      }
    }

    composeTestRule.onNodeWithText("電池の最適化で記録が止まることがあります").performClick()
    verify { onOpenSettings() }
  }
}

/** 記録画面の上の検索の入口（施設と地域の共通の窓を開く）。 */
private const val SEARCH_ENTRY = "場所・地域を検索"

/** 検索窓の候補の「地域へ移動」の行。 */
private const val MOVE_ROW = "mapSearchCandidate_MoveToRegion"

/** 検索窓の候補の「登録」「場所として登録」の行。 */
private const val REGISTER_ROW = "mapSearchCandidate_Register"
