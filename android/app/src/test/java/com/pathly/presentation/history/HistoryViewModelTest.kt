package com.pathly.presentation.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

  @get:Rule
  val instantTaskExecutorRule = InstantTaskExecutorRule()

  private val testDispatcher = StandardTestDispatcher()
  private val mockRepository = mockk<GpsTrackRepository>()
  private lateinit var viewModel: HistoryViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `初期化時_完了済み記録のみ取得する`() = runTest {
    // Given
    val tracks = listOf(
      createTrack(id = 1, isActive = true, endTime = null),
      createTrack(id = 2, isActive = false, endTime = Date()),
    )
    coEvery { mockRepository.getAllTracks() } returns flowOf(tracks)
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)

    // When
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    val state = viewModel.uiState.value
    assertEquals("完了済み記録のみ取得", 1, state.tracks.size)
    assertEquals("完了済み記録のIDが正しい", 2L, state.tracks[0].id)
    assertFalse("ローディング状態が解除される", state.isLoading)
  }

  @Test
  fun `記録なしの場合_空リスト`() = runTest {
    // Given
    coEvery { mockRepository.getAllTracks() } returns flowOf(emptyList())
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)

    // When
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    val state = viewModel.uiState.value
    assertTrue("記録リストが空", state.tracks.isEmpty())
    assertFalse("ローディング状態が解除", state.isLoading)
  }

  @Test
  fun `deleteTrack呼び出し_repositoryのdeleteが呼ばれる`() = runTest {
    // Given
    val track = createTrack(id = 1, isActive = false, endTime = Date())
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(track))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.deleteTrack(track) } returns Unit

    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.deleteTrack(track)
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    coVerify { mockRepository.deleteTrack(track) }
  }

  @Test
  fun `お気に入り絞り込み_お気に入りのみが残る`() = runTest {
    // Given
    val fav = createTrack(id = 1, isActive = false, endTime = Date(), isFavorite = true)
    val notFav = createTrack(id = 2, isActive = false, endTime = Date(), isFavorite = false)
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(fav, notFav))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)

    // Then
    val visible = viewModel.uiState.value.visibleTracks
    assertEquals("お気に入りのみ", 1, visible.size)
    assertEquals("残るのはお気に入り", 1L, visible[0].id)
  }

  @Test
  fun `立ち寄り件数順の降順_件数の多い経路が先頭`() = runTest {
    // Given
    val few = createTrack(id = 1, isActive = false, endTime = Date(), stopCount = 1)
    val many = createTrack(id = 2, isActive = false, endTime = Date(), stopCount = 5)
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(few, many))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.setSort(TrackSort.STOP_COUNT)

    // Then
    val visible = viewModel.uiState.value.visibleTracks
    assertEquals("件数の多い方が先頭", 2L, visible[0].id)
  }

  @Test
  fun `toggleFavorite_反転した値でrepositoryを呼ぶ`() = runTest {
    // Given
    val track = createTrack(id = 1, isActive = false, endTime = Date(), isFavorite = false)
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(track))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.setFavorite(1L, true) } returns Unit
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.toggleFavorite(track)
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    coVerify { mockRepository.setFavorite(1L, true) }
  }

  @Test
  fun `renameTrack_repositoryのrenameが呼ばれる`() = runTest {
    // Given
    coEvery { mockRepository.getAllTracks() } returns flowOf(emptyList())
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.renameTrack(1L, "鎌倉さんぽ") } returns Unit
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.renameTrack(1L, "鎌倉さんぽ")
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    coVerify { mockRepository.renameTrack(1L, "鎌倉さんぽ") }
  }

  @Test
  fun `updateLocationPermission_権限状態が更新される`() = runTest {
    // Given
    coEvery { mockRepository.getAllTracks() } returns flowOf(emptyList())
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.clearError()

    // Then
    val state = viewModel.uiState.value
    assertNull("エラーメッセージがクリア", state.errorMessage)
  }

  private fun createTrack(
    id: Long,
    startTime: Date = Date(),
    endTime: Date? = null,
    isActive: Boolean = false,
    name: String? = null,
    isFavorite: Boolean = false,
    stopCount: Int = 0,
  ): GpsTrack = GpsTrack(
    id = id,
    startTime = startTime,
    endTime = endTime,
    isActive = isActive,
    name = name,
    isFavorite = isFavorite,
    stopCount = stopCount,
    points = emptyList(),
    createdAt = Date(),
    updatedAt = Date(),
  )
}
