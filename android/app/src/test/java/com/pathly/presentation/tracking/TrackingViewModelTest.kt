package com.pathly.presentation.tracking

import com.pathly.data.settings.SettingsRepository
import com.pathly.data.tracking.TrackingController
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import com.pathly.domain.usecase.PlaceEditUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

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
  private val mockPlaceEditUseCase = mockk<PlaceEditUseCase>(relaxed = true)
  private val mockAddManualStopUseCase = mockk<AddManualStopUseCase>(relaxed = true)
  private val mockSettingsRepository = mockk<SettingsRepository>(relaxed = true).also {
    every { it.showRegisteredPlaces(any()) } returns MutableStateFlow(false)
  }

  /** 予期せぬ切断をテストから発火させるための入口。 */
  private val disconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val mockController = mockk<TrackingController>(relaxed = true).also {
    every { it.currentLocation } returns MutableStateFlow(null)
    every { it.locationCount } returns MutableStateFlow(0)
    every { it.isTracking } returns MutableStateFlow(false)
    every { it.unexpectedDisconnect } returns disconnects
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
    mockPlaceEditUseCase,
    mockAddManualStopUseCase,
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

  private fun activeTrack(id: Long = 1L) = GpsTrack(
    id = id,
    startTime = Date(),
    endTime = null,
    isActive = true,
    createdAt = Date(),
    updatedAt = Date(),
  )

  @Test
  fun `予期せぬ切断_記録中のトラックを閉じない`() = runTest {
    val viewModel = createViewModel()
    coEvery { mockRepository.getActiveTrack() } returns activeTrack()

    disconnects.tryEmit(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    // 閉じてしまうと START_STICKY による自己回復（再起動したサービスが続きを記録する）が
    // 成立しなくなるため、finishTrack は呼ばない。
    coVerify(exactly = 0) { mockRepository.finishTrack(any(), any()) }
    assertNotNull("突き合わせが走ること", viewModel.uiState.value)
  }

  @Test
  fun `予期せぬ切断_サービスが復帰していれば記録中に戻る`() = runTest {
    val viewModel = createViewModel()
    coEvery { mockRepository.getActiveTrack() } returns activeTrack(id = 7L)
    every { mockController.reattach() } returns true

    disconnects.tryEmit(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("記録中に戻る", state.isTracking)
    assertEquals(7L, state.currentTrackId)
    assertNull("中断ダイアログは出さない", state.interruptedTrack)
  }

  @Test
  fun `予期せぬ切断_復帰しなければ中断として扱う`() = runTest {
    val viewModel = createViewModel()
    val track = activeTrack(id = 9L)
    coEvery { mockRepository.getActiveTrack() } returns track
    every { mockController.reattach() } returns false

    disconnects.tryEmit(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse("記録中ではない", state.isTracking)
    assertEquals("再開/完了をユーザーに選ばせる", track, state.interruptedTrack)
  }

  // ---- 近接確認（もとは Composable 側の分岐だった） ----

  @Test
  fun `登録_近くに既存があれば確認待ちにする`() = runTest {
    val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)
    coEvery {
      mockPlaceEditUseCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any())
    } returns PlaceEditUseCase.RegisterResult.NearbyFound(nearby)
    val viewModel = createViewModel()

    viewModel.registerPlaceWithNearbyCheck(35.0, 139.0, "新しい場所", false, Priority.MEDIUM, null, null)
    testDispatcher.scheduler.advanceUntilIdle()

    val prompt = viewModel.uiState.value.nearbyRegisterPrompt
    assertEquals(nearby, prompt?.nearby)
    assertEquals("新しい場所", prompt?.name)
  }

  @Test
  fun `登録_近接確認で新規を選べば確認を閉じて強制的に新規登録する`() = runTest {
    val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)
    coEvery {
      mockPlaceEditUseCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any())
    } returns PlaceEditUseCase.RegisterResult.NearbyFound(nearby)
    coEvery {
      mockPlaceEditUseCase.register(any(), any(), any(), any(), any(), any(), any(), any())
    } returns PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false)
    val viewModel = createViewModel()
    viewModel.registerPlaceWithNearbyCheck(35.0, 139.0, "新しい場所", true, Priority.HIGH, "メモ", null)
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.confirmNearbyNew()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull("確認は閉じる", viewModel.uiState.value.nearbyRegisterPrompt)
    // 座標同定せず必ず新しい場所を作る。
    coVerify { mockPlaceEditUseCase.register(35.0, 139.0, "新しい場所", true, Priority.HIGH, "メモ", null, true) }
  }

  @Test
  fun `登録_近接確認で紐付けを選べば既存の場所へ反映する`() = runTest {
    val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)
    coEvery {
      mockPlaceEditUseCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any())
    } returns PlaceEditUseCase.RegisterResult.NearbyFound(nearby)
    val viewModel = createViewModel()
    viewModel.registerPlaceWithNearbyCheck(35.0, 139.0, null, true, Priority.LOW, "メモ", null)
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.confirmNearbyLink()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull("確認は閉じる", viewModel.uiState.value.nearbyRegisterPrompt)
    coVerify { mockPlaceEditUseCase.linkToExisting(5L, true, Priority.LOW, "メモ") }
  }

  @Test
  fun `登録_確認が不要なら即座に登録して結果を通知する`() = runTest {
    coEvery {
      mockPlaceEditUseCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any())
    } returns PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false)
    val viewModel = createViewModel()

    viewModel.registerPlaceWithNearbyCheck(35.0, 139.0, "カフェ", false, Priority.MEDIUM, null, "gp-1")
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull("確認は出さない", state.nearbyRegisterPrompt)
    assertEquals("「カフェ」を登録しました", state.placeRegisteredMessage)
  }
}
