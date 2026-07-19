package com.pathly.presentation.tracking

import android.Manifest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.ui.theme.PathlyAndroidTheme
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
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("記録停止")
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
}
