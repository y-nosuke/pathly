package com.pathly.presentation.tracking

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    mockOnRequestPermission = mockk(relaxed = true)
    mockViewModel = mockk(relaxed = true)

    // 初期状態のUIState
    uiStateFlow = MutableStateFlow(
      TrackingState(
        hasLocationPermission = true,
        isTracking = false,
        errorMessage = null,
      ),
    )

    // ViewModelのuiStateをモック
    io.mockk.every { mockViewModel.uiState } returns uiStateFlow
    io.mockk.every { mockViewModel.checkLocationPermission() } returns Unit
    io.mockk.every { mockViewModel.startTracking() } returns Unit
    io.mockk.every { mockViewModel.stopTracking() } returns Unit
  }

  @Test
  fun trackingScreen_initialState_showsTitle() {
    // Given & When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("Pathly - GPS記録")
      .assertIsDisplayed()
  }

  @Test
  fun trackingScreen_noPermission_showsPermissionRequest() {
    // Given
    uiStateFlow.value = TrackingState(
      hasLocationPermission = false,
      isTracking = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then
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
    // Given
    uiStateFlow.value = TrackingState(
      hasLocationPermission = false,
      isTracking = false,
      errorMessage = null,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // When
    composeTestRule
      .onNodeWithText("位置情報を許可")
      .performClick()

    // Then
    verify { mockOnRequestPermission() }
  }

  @Test
  fun trackingScreen_hasPermissionNotTracking_showsStartButton() {
    // Given
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = false,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("お出掛けの記録を開始しましょう")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("記録開始")
      .assertIsDisplayed()
      .assertHasClickAction()

    composeTestRule
      .onNodeWithText("ボタンを押すと、30秒間隔でGPS位置を記録します")
      .assertIsDisplayed()
  }

  @Test
  fun trackingScreen_startTrackingButton_triggersViewModel() {
    // Given
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = false,
      errorMessage = null,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // When
    composeTestRule
      .onNodeWithText("記録開始")
      .performClick()

    // Then
    verify { mockViewModel.startTracking() }
  }

  @Test
  fun trackingScreen_tracking_showsActiveContent() {
    // Given
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = true,
      errorMessage = null,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("📍 記録中")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("GPS位置を記録しています")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("記録停止")
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun trackingScreen_stopTrackingButton_triggersViewModel() {
    // Given
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = true,
      errorMessage = null,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // When
    composeTestRule
      .onNodeWithText("記録停止")
      .performClick()

    // Then
    verify { mockViewModel.stopTracking() }
  }

  @Test
  fun trackingScreen_withError_showsErrorMessage() {
    // Given
    val errorMessage = "GPS信号を受信できません"
    uiStateFlow.value = TrackingState(
      hasLocationPermission = true,
      isTracking = false,
      errorMessage = errorMessage,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText(errorMessage)
      .assertIsDisplayed()

    // エラーがあっても通常のコンテンツも表示される
    composeTestRule
      .onNodeWithText("記録開始")
      .assertIsDisplayed()
  }

  @Test
  fun trackingScreen_errorWithPermission_showsBothErrorAndPermissionContent() {
    // Given
    val errorMessage = "位置情報サービスが無効です"
    uiStateFlow.value = TrackingState(
      hasLocationPermission = false,
      isTracking = false,
      errorMessage = errorMessage,
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText(errorMessage)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("位置情報の権限が必要です")
      .assertIsDisplayed()
  }

  @Test
  fun trackingScreen_stateChanges_updatesUIReactively() {
    // Given - 初期状態は記録停止中
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then - 記録開始ボタンが表示される
    composeTestRule
      .onNodeWithText("記録開始")
      .assertIsDisplayed()

    // When - 状態を記録中に変更
    composeTestRule.runOnUiThread {
      uiStateFlow.value = TrackingState(
        hasLocationPermission = true,
        isTracking = true,
        errorMessage = null,
      )
    }

    // Then - 記録停止ボタンが表示される
    composeTestRule
      .onNodeWithText("記録停止")
      .assertIsDisplayed()

    // 記録開始ボタンは表示されない
    composeTestRule
      .onNodeWithText("記録開始")
      .assertDoesNotExist()
  }

  @Test
  fun trackingScreen_permissionGranted_hidesPermissionContent() {
    // Given - 初期状態は権限なし
    uiStateFlow.value = TrackingState(
      hasLocationPermission = false,
      isTracking = false,
      errorMessage = null,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackingScreen(
          onRequestPermission = mockOnRequestPermission,
          viewModel = mockViewModel,
        )
      }
    }

    // Then - 権限リクエストが表示される
    composeTestRule
      .onNodeWithText("位置情報を許可")
      .assertIsDisplayed()

    // When - 権限が付与される
    composeTestRule.runOnUiThread {
      uiStateFlow.value = TrackingState(
        hasLocationPermission = true,
        isTracking = false,
        errorMessage = null,
      )
    }

    // Then - 記録開始画面が表示される
    composeTestRule
      .onNodeWithText("記録開始")
      .assertIsDisplayed()

    // 権限リクエストは表示されない
    composeTestRule
      .onNodeWithText("位置情報を許可")
      .assertDoesNotExist()
  }
}
