package com.pathly.presentation.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
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
  private val mockPlaceRepository = mockk<PlaceRepository>()
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
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

    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.setSort(TrackSort.STOP_COUNT)

    // Then
    val visible = viewModel.uiState.value.visibleTracks
    assertEquals("件数の多い方が先頭", 2L, visible[0].id)
  }

  @Test
  fun `業種絞り込み_選んだどれかに立ち寄った経路が残る`() = runTest {
    // Given
    val cafe = createTrack(id = 1, endTime = Date(), stopCategories = setOf(PlaceCategoryFacet.CAFE))
    val parkAndFood = createTrack(id = 2, endTime = Date(), stopCategories = setOf(PlaceCategoryFacet.PARK, PlaceCategoryFacet.FOOD))
    val shopping = createTrack(id = 3, endTime = Date(), stopCategories = setOf(PlaceCategoryFacet.SHOPPING))
    val noStops = createTrack(id = 4, endTime = Date())
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(cafe, parkAndFood, shopping, noStops))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.CAFE)
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.PARK)

    // Then
    val state = viewModel.uiState.value
    assertEquals("カフェか公園に寄った経路（OR）", listOf(1L, 2L), state.visibleTracks.map { it.id })
    assertFalse("絞り込み中", state.noFilter)
  }

  @Test
  fun `業種絞り込み_同じ業種をもう一度選ぶと外れる`() = runTest {
    // Given
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(createTrack(id = 1, endTime = Date())))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.CAFE)
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.CAFE)

    // Then
    assertTrue("指定なしに戻る", viewModel.uiState.value.categoryFilter.isEmpty())
    assertTrue("すべてが選択状態", viewModel.uiState.value.noFilter)
  }

  @Test
  fun `業種の件数_その業種に立ち寄った経路の数で立ち寄りの無い業種は出ない`() = runTest {
    // Given
    val tracks = listOf(
      createTrack(id = 1, endTime = Date(), stopCategories = setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.UNCATEGORIZED)),
      createTrack(id = 2, endTime = Date(), stopCategories = setOf(PlaceCategoryFacet.CAFE)),
      createTrack(id = 3, endTime = Date()),
    )
    coEvery { mockRepository.getAllTracks() } returns flowOf(tracks)
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)

    // When
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    assertEquals(
      mapOf(PlaceCategoryFacet.CAFE to 2, PlaceCategoryFacet.UNCATEGORIZED to 1),
      viewModel.uiState.value.categoryCounts,
    )
  }

  @Test
  fun `clearCategoryFilter_業種だけ外してほかの軸は残す`() = runTest {
    // Given
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(createTrack(id = 1, endTime = Date())))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.FOOD)
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.CAFE)

    // When
    viewModel.clearCategoryFilter()

    // Then
    val state = viewModel.uiState.value
    assertTrue("業種は指定なし", state.categoryFilter.isEmpty())
    assertEquals("お気に入りは残る", TrackFavoriteFilter.FAVORITE, state.favoriteFilter)
  }

  @Test
  fun `clearFilters_業種の絞り込みも解除する`() = runTest {
    // Given
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(createTrack(id = 1, endTime = Date())))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.toggleCategoryFilter(PlaceCategoryFacet.FOOD)

    // When
    viewModel.clearFilters()

    // Then
    assertTrue(viewModel.uiState.value.categoryFilter.isEmpty())
    assertEquals(1, viewModel.uiState.value.visibleTracks.size)
  }

  @Test
  fun `clearFilters_欄の軸は外し_名前の検索語は残す`() = runTest {
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(createTrack(id = 1, endTime = Date())))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)
    viewModel.setNameQuery("鎌倉")

    viewModel.clearFilters()

    assertEquals(TrackFavoriteFilter.ANY, viewModel.uiState.value.favoriteFilter)
    assertEquals("鎌倉", viewModel.uiState.value.nameQuery)
  }

  @Test
  fun `resetListOptions_絞り込み_検索_並べ替えをまとめて初期状態に戻す`() = runTest {
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(createTrack(id = 1, endTime = Date())))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)
    viewModel.setNameQuery("鎌倉")
    viewModel.setSort(TrackSort.DISTANCE)
    viewModel.toggleSortDirection()
    assertFalse(viewModel.uiState.value.isInitialListOptions)

    viewModel.resetListOptions()

    assertTrue(viewModel.uiState.value.isInitialListOptions)
    assertEquals(HistoryState().sort, viewModel.uiState.value.sort)
    assertEquals("", viewModel.uiState.value.nameQuery)
  }

  @Test
  fun `絞り込みと並べ替え_savedStateHandleに残り新しいViewModelで戻る`() = runTest {
    // Given: アプリが裏で落とされる前の ViewModel で条件を変える
    coEvery { mockRepository.getAllTracks() } returns flowOf(emptyList())
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    val handle = SavedStateHandle()
    val before = HistoryViewModel(mockRepository, mockPlaceRepository, handle)
    before.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)
    before.toggleCategoryFilter(PlaceCategoryFacet.CAFE)
    before.setSort(TrackSort.DISTANCE)
    before.toggleSortDirection()

    // When: 同じ handle から作り直す（プロセスが落ちて戻ったとき）
    val after = HistoryViewModel(mockRepository, mockPlaceRepository, handle)

    // Then
    val state = after.uiState.value
    assertEquals(TrackFavoriteFilter.FAVORITE, state.favoriteFilter)
    assertEquals(setOf(PlaceCategoryFacet.CAFE), state.categoryFilter)
    assertEquals(TrackSort.DISTANCE, state.sort)
    assertFalse("反転した向きも残る", state.sortDescending)
  }

  @Test
  fun `savedStateHandleが空_既定の絞り込みと並べ替え`() = runTest {
    coEvery { mockRepository.getAllTracks() } returns flowOf(emptyList())
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)

    val state = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle()).uiState.value

    assertTrue(state.noFilter)
    assertEquals(TrackSort.DATE, state.sort)
    assertTrue(state.sortDescending)
  }

  @Test
  fun `toggleFavorite_反転した値でrepositoryを呼ぶ`() = runTest {
    // Given
    val track = createTrack(id = 1, isActive = false, endTime = Date(), isFavorite = false)
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(track))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.setFavorite(1L, true) } returns Unit
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    viewModel.clearError()

    // Then
    val state = viewModel.uiState.value
    assertNull("エラーメッセージがクリア", state.errorMessage)
  }

  @Test
  fun `地図表示_選んだ経路は線を読み込み_絞り込みから外れても残る`() = runTest {
    // Given
    val tracks = listOf(
      createTrack(id = 1, endTime = Date(), isFavorite = true),
      createTrack(id = 2, endTime = Date()),
    )
    coEvery { mockRepository.getAllTracks() } returns flowOf(tracks)
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.getDisplaySegments(2L) } returns listOf(listOf(point(0), point(1)))
    coEvery { mockPlaceRepository.getStopsForTrack(2L) } returns flowOf(emptyList())
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
      .also { it.computeDispatcher = testDispatcher }
    testDispatcher.scheduler.advanceUntilIdle()

    // When: 地図表示で経路 2 を選び、お気に入りだけに絞る（経路 2 は外れる）
    viewModel.setViewMode(HistoryViewMode.MAP)
    viewModel.toggleOverlay(tracks[1])
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)

    // Then
    val state = viewModel.uiState.value
    assertEquals(HistoryViewMode.MAP, state.viewMode)
    assertEquals(listOf(1L), state.visibleTracks.map { it.id })
    assertEquals("絞り込みから外れても選びは残る", listOf(2L), state.selected.map { it.track.id })
    assertTrue("線を読み終えている", state.selected.single().isLoaded)
    assertEquals(0, state.selected.single().colorIndex)
  }

  @Test
  fun `地図表示_消された経路は選びから外れる`() = runTest {
    // Given
    val track = createTrack(id = 1, endTime = Date())
    val allTracks = kotlinx.coroutines.flow.MutableStateFlow(listOf(track))
    coEvery { mockRepository.getAllTracks() } returns allTracks
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.getDisplaySegments(1L) } returns listOf(listOf(point(0), point(1)))
    coEvery { mockPlaceRepository.getStopsForTrack(1L) } returns flowOf(emptyList())
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, SavedStateHandle())
      .also { it.computeDispatcher = testDispatcher }
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.toggleOverlay(track)
    testDispatcher.scheduler.advanceUntilIdle()

    // When
    allTracks.value = emptyList()
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    assertTrue(viewModel.uiState.value.selected.isEmpty())
  }

  @Test
  fun `見せ方_savedStateHandleに残り新しいViewModelで戻る`() = runTest {
    coEvery { mockRepository.getAllTracks() } returns flowOf(emptyList())
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    val handle = SavedStateHandle()
    HistoryViewModel(mockRepository, mockPlaceRepository, handle).apply {
      setViewMode(HistoryViewMode.MAP)
      setControlsCollapsed(true)
    }

    val state = HistoryViewModel(mockRepository, mockPlaceRepository, handle).uiState.value

    assertEquals(HistoryViewMode.MAP, state.viewMode)
    assertTrue(state.controlsCollapsed)
  }

  private fun point(i: Int) = com.pathly.domain.model.GpsPoint(
    id = i.toLong(),
    trackId = 1L,
    latitude = 35.68 + i * 0.001,
    longitude = 139.76,
    altitude = null,
    accuracy = 10f,
    speed = null,
    bearing = null,
    timestamp = Date(1_700_000_000_000L + i * 1000L),
    createdAt = Date(),
  )

  // 既定の並びは開始時刻の新しい順。Date() だと作るたびにミリ秒がずれて並びが実行ごとに変わるので、
  // 時刻を指定しない経路は同じ時刻にそろえ、渡した順のまま並ぶようにする。
  private val fixedStartTime = Date(1_700_000_000_000L)

  private fun createTrack(
    id: Long,
    startTime: Date = fixedStartTime,
    endTime: Date? = null,
    isActive: Boolean = false,
    name: String? = null,
    isFavorite: Boolean = false,
    stopCount: Int = 0,
    stopCategories: Set<PlaceCategoryFacet> = emptySet(),
  ): GpsTrack = GpsTrack(
    id = id,
    startTime = startTime,
    endTime = endTime,
    isActive = isActive,
    name = name,
    isFavorite = isFavorite,
    stopCount = stopCount,
    stopCategories = stopCategories,
    points = emptyList(),
    createdAt = Date(),
    updatedAt = Date(),
  )
}
