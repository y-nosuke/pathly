package com.pathly.presentation.tracking

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pathly.domain.repository.GpsTrackRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<GpsTrackRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Android Framework のモック設定
        mockkStatic("androidx.core.content.ContextCompat")

        // Application context の設定
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.packageName } returns "com.pathly"

        // 権限チェックをモック
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                any<Context>(),
                any<String>()
            )
        } returns PackageManager.PERMISSION_GRANTED
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateLocationPermission_権限false設定`() = runTest {
        // Given
        coEvery { mockRepository.getActiveTrack() } returns null
        val viewModel = TrackingViewModel(mockApplication, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.updateLocationPermission(false)

        // Then
        val state = viewModel.uiState.value
        assertFalse("権限状態がfalse", state.hasLocationPermission)
    }

    @Test
    fun `updateLocationPermission_権限true設定`() = runTest {
        // Given
        coEvery { mockRepository.getActiveTrack() } returns null
        val viewModel = TrackingViewModel(mockApplication, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.updateLocationPermission(true)

        // Then
        val state = viewModel.uiState.value
        assertTrue("権限状態がtrue", state.hasLocationPermission)
    }

    @Test
    fun `clearError_エラーメッセージクリア`() = runTest {
        // Given
        coEvery { mockRepository.getActiveTrack() } returns null
        val viewModel = TrackingViewModel(mockApplication, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearError()

        // Then
        val state = viewModel.uiState.value
        assertNull("エラーメッセージがクリア", state.errorMessage)
    }
}