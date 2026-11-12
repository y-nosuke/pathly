package com.pathly.presentation.tracking

import android.Manifest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.ui.theme.PathlyAndroidTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var mockOnRequestPermission: () -> Unit
  private lateinit var mockViewModel: TrackingViewModel
  private lateinit var overlayViewModel: TrackOverlayViewModel
  private lateinit var uiStateFlow: MutableStateFlow<TrackingState>

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
  fun trackingScreen_hasPermissionNotTracking_showsStartFab() {
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
  fun trackingScreen_startFab_triggersViewModel() {
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
  fun trackingScreen_tracking_showsStopFabAndStatus() {
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
  fun trackingScreen_stopFab_triggersViewModel() {
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
    coEvery { mockViewModel.fetchPlaceResult("p1") } returns
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

    // 地図の上のボタンで検索窓を開く（検索のセッションを始める）。窓と重なる上端のボタンは隠れる。
    composeTestRule.onNodeWithContentDescription("検索して場所を登録").performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearch").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("検索して場所を登録").assertDoesNotExist()
    verify { mockViewModel.startPlaceSearch() }

    // 候補を選ぶと、検索窓を閉じて場所シートで登録へ進む。
    composeTestRule.onNodeWithTag("trackingPlaceSearchField").performTextInput("テスト")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("東京都千代田区").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("東京都千代田区").performClick()
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

    composeTestRule.onNodeWithContentDescription("検索して場所を登録").performClick()
    composeTestRule.onNodeWithContentDescription("検索を閉じる").performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearch").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("検索して場所を登録").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("記録開始").assertIsDisplayed()
  }

  @Test
  fun trackingScreen_jumpToRegion_selectingPredictionClosesSearch() {
    coEvery { mockViewModel.predictRegions(any(), any()) } returns
      listOf(PlacePrediction(placeId = "r1", primaryText = "札幌市", secondaryText = "北海道"))
    coEvery { mockViewModel.fetchRegion("r1") } returns GeoBounds(42.9, 141.2, 43.2, 141.5)

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

    // 地図の上のボタンで地名の検索窓を開く。窓と重なる上端のボタンは隠れる。
    composeTestRule.onNodeWithContentDescription("地域へ移動").performClick()
    composeTestRule.onNodeWithTag("trackingPlaceSearch").assertIsDisplayed()
    composeTestRule.onNodeWithText("地名で探して移動").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("地域へ移動").assertDoesNotExist()
    verify { mockViewModel.startPlaceSearch() }

    // 地名を選ぶと、その範囲を引いて窓を閉じる（場所シートは出さない＝登録はしない）。
    composeTestRule.onNodeWithTag("trackingPlaceSearchField").performTextInput("札幌")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("北海道").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("北海道").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("trackingPlaceSearch").fetchSemanticsNodes().isEmpty()
    }
    coVerify { mockViewModel.fetchRegion("r1") }
    composeTestRule.onNodeWithText("登録").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("地域へ移動").assertIsDisplayed()
  }
}
