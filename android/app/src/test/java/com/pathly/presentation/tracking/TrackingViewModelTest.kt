package com.pathly.presentation.tracking

import com.pathly.data.settings.SettingsRepository
import com.pathly.data.tracking.TrackingController
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TrackingViewModel は TrackingController 越しにしか Android framework を触らないので、
 * Service・Context・権限チェックをモックせずに素の JVM テストで検証できる。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val mockRepository = mockk<GpsTrackRepository>(relaxed = true)
  private val mockPlaceRepository = mockk<PlaceRepository>(relaxed = true).also {
    every { it.currentStop } returns MutableStateFlow(null)
    every { it.observeRegisteredPlaces() } returns MutableStateFlow(emptyList())
  }
  private val mockWishlistRepository = mockk<WishlistRepository>(relaxed = true)
  private val mockSettingsRepository = mockk<SettingsRepository>(relaxed = true).also {
    every { it.showRegisteredPlaces(any()) } returns MutableStateFlow(false)
  }
  private val mockController = mockk<TrackingController>(relaxed = true).also {
    every { it.currentLocation } returns MutableStateFlow(null)
    every { it.locationCount } returns MutableStateFlow(0)
    every { it.isTracking } returns MutableStateFlow(false)
    every { it.unexpectedDisconnect } returns MutableSharedFlow()
  }

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    coEvery { mockRepository.getActiveTrack() } returns null
    every { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): TrackingViewModel = TrackingViewModel(
    mockController,
    mockRepository,
    mockPlaceRepository,
    mockWishlistRepository,
    mockSettingsRepository,
  ).also { testDispatcher.scheduler.advanceUntilIdle() }

  @Test
  fun `updateLocationPermission_権限false設定`() = runTest {
    val viewModel = createViewModel()

    viewModel.updateLocationPermission(false)

    assertFalse("権限状態がfalse", viewModel.uiState.value.hasLocationPermission)
  }

  @Test
  fun `updateLocationPermission_権限true設定`() = runTest {
    val viewModel = createViewModel()

    viewModel.updateLocationPermission(true)

    assertTrue("権限状態がtrue", viewModel.uiState.value.hasLocationPermission)
  }

  @Test
  fun `clearError_エラーメッセージクリア`() = runTest {
    val viewModel = createViewModel()

    viewModel.clearError()

    assertNull("エラーメッセージがクリア", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `startTracking_開始できれば記録中になる`() = runTest {
    every { mockController.start() } returns null
    val viewModel = createViewModel()

    viewModel.startTracking()

    val state = viewModel.uiState.value
    assertTrue("記録中になる", state.isTracking)
    assertNull("エラーは出ない", state.errorMessage)
  }

  @Test
  fun `startTracking_権限が無ければ記録中にせずエラーを出す`() = runTest {
    every { mockController.start() } returns TrackingController.StartFailure.MISSING_PERMISSION
    val viewModel = createViewModel()

    viewModel.startTracking()

    val state = viewModel.uiState.value
    // 楽観的に記録中にすると「記録中なのに何も記録されない」状態になるため false のまま
    assertFalse("記録中にはならない", state.isTracking)
    assertEquals("位置情報の権限が必要です", state.errorMessage)
  }

  @Test
  fun `startTracking_位置情報がオフなら記録中にせずエラーを出す`() = runTest {
    every { mockController.start() } returns TrackingController.StartFailure.LOCATION_DISABLED
    val viewModel = createViewModel()

    viewModel.startTracking()

    val state = viewModel.uiState.value
    assertFalse("記録中にはならない", state.isTracking)
    assertEquals("端末の位置情報がオフです。設定でオンにしてください", state.errorMessage)
  }

  @Test
  fun `stopTracking_コントローラへ停止を伝え状態を戻す`() = runTest {
    every { mockController.start() } returns null
    val viewModel = createViewModel()
    viewModel.startTracking()

    viewModel.stopTracking()

    verify { mockController.stop() }
    val state = viewModel.uiState.value
    assertFalse("記録中でなくなる", state.isTracking)
    assertNull("現在地は消える", state.currentLocation)
  }
}
