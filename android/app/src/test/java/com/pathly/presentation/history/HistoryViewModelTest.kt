package com.pathly.presentation.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.GroupOpenState
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.TrackGrouping
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.SettingsRepository
import com.pathly.presentation.common.ListGroupRow
import com.pathly.presentation.common.ListMapMode
import com.pathly.util.DateFormatters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
  private val mockSettings = mockk<SettingsRepository>(relaxed = true)
  private val deletionQueue = TrackDeletionQueue(mockRepository).also { it.scope = TestScope(testDispatcher) }
  private lateinit var viewModel: HistoryViewModel
  private val groupingFlow = MutableStateFlow(TrackGrouping.NONE)
  private val groupOpenFlow = MutableStateFlow(GroupOpenState())

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    every { mockPlaceRepository.observeRegisteredPlaces() } returns flowOf(emptyList())
    every { mockSettings.historyGrouping } returns groupingFlow
    every { mockSettings.historyGroupOpenState } returns groupOpenFlow
    every { mockSettings.setHistoryGrouping(any()) } answers { groupingFlow.value = firstArg() }
    every { mockSettings.setHistoryGroupOpenState(any()) } answers { groupOpenFlow.value = firstArg() }
  }

  // 年月で分けたとき、見出しを押すと開閉が入れ替わり、設定に覚える（既定で開いている最新の月は閉じる）。
  @Test
  fun `年月で分けると見出しの開閉を設定に覚える`() = runTest {
    val nov = createTrack(id = 1, startTime = Date(1_763_600_000_000L), endTime = Date(1_763_603_600_000L))
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(nov))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
    viewModel.setGrouping(TrackGrouping.MONTH)
    testDispatcher.scheduler.advanceUntilIdle()

    val header = viewModel.uiState.value.listRows.filterIsInstance<ListGroupRow.Header<GpsTrack>>().last()
    assertTrue(header.expanded)
    viewModel.toggleGroup(header.group.key)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(setOf(header.group.key), groupOpenFlow.value.closed)
    assertFalse(viewModel.uiState.value.isGroupOpen(header.group.key))

    // 名前の検索中は押しても開閉しない（すべて開いて見せている）。
    viewModel.setNameQuery("2025")
    viewModel.toggleGroup(header.group.key)
    assertEquals(setOf(header.group.key), groupOpenFlow.value.closed)
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    val state = viewModel.uiState.value
    assertTrue("記録リストが空", state.tracks.isEmpty())
    assertFalse("ローディング状態が解除", state.isLoading)
  }

  // 経路詳細で消す予定にした経路は、取り消しを待っている間も一覧から隠す。取り消せば戻り、確定すれば DB から消す。
  @Test
  fun `消す予定の経路_一覧から隠し_取り消すと戻る_確定するとdeleteが呼ばれる`() = runTest {
    // Given
    val track = createTrack(id = 1, isActive = false, endTime = Date())
    val other = createTrack(id = 2, isActive = false, endTime = Date())
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(track, other))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    coEvery { mockRepository.deleteTrack(track) } returns Unit

    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()

    // When: 消す予定にする
    deletionQueue.request(track)
    testDispatcher.scheduler.advanceUntilIdle()
    // Then: 隠れる（まだ消していない）
    assertEquals(listOf(2L), viewModel.uiState.value.tracks.map { it.id })
    assertEquals(DateFormatters.shortDate(track.startTime), deletionQueue.pending.value?.label)
    coVerify(exactly = 0) { mockRepository.deleteTrack(any()) }

    // When: 取り消す
    deletionQueue.undo(track.id)
    testDispatcher.scheduler.advanceUntilIdle()
    // Then: 戻る
    assertEquals(listOf(1L, 2L), viewModel.uiState.value.tracks.map { it.id })

    // When: もう一度消す予定にして、確定する
    deletionQueue.request(track)
    deletionQueue.commit(track.id)
    testDispatcher.scheduler.advanceUntilIdle()
    // Then
    coVerify(exactly = 1) { mockRepository.deleteTrack(track) }
    assertNull(deletionQueue.pending.value)
  }

  // 同じ経路を別の画面から開き直して消しても、待っている削除はそのまま（確定させて取り消せなくしない）。
  @Test
  fun `同じ経路を二度消す予定にしても_前の待ちは確定しない`() = runTest {
    val track = createTrack(id = 1, isActive = false, endTime = Date())

    deletionQueue.request(track)
    deletionQueue.request(track)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { mockRepository.deleteTrack(any()) }
    deletionQueue.undo(track.id)
    assertTrue(deletionQueue.hiddenIds.value.isEmpty())
  }

  // 消すのに失敗しても落ちず、一覧に戻す。
  @Test
  fun `消すのに失敗_落ちずに一覧へ戻す`() = runTest {
    val track = createTrack(id = 1, isActive = false, endTime = Date())
    coEvery { mockRepository.deleteTrack(any()) } throws IllegalStateException("disk full")

    deletionQueue.request(track)
    deletionQueue.commit(track.id)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(deletionQueue.hiddenIds.value.isEmpty())
    assertNull(deletionQueue.pending.value)
  }

  // 待っている削除があるうちに別の経路を消す予定にすると、前のものは今消す（スナックバーは最新の 1 件だけ）。
  @Test
  fun `消す予定が重なる_前の削除はその時点で確定する`() = runTest {
    val first = createTrack(id = 1, isActive = false, endTime = Date())
    val second = createTrack(id = 2, isActive = false, endTime = Date())
    coEvery { mockRepository.deleteTrack(any()) } returns Unit

    deletionQueue.request(first)
    deletionQueue.request(second)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { mockRepository.deleteTrack(first) }
    coVerify(exactly = 0) { mockRepository.deleteTrack(second) }
    assertEquals(2L, deletionQueue.pending.value?.track?.id)
    assertEquals(setOf(2L), deletionQueue.hiddenIds.value)
  }

  @Test
  fun `お気に入り絞り込み_お気に入りのみが残る`() = runTest {
    // Given
    val fav = createTrack(id = 1, isActive = false, endTime = Date(), isFavorite = true)
    val notFav = createTrack(id = 2, isActive = false, endTime = Date(), isFavorite = false)
    coEvery { mockRepository.getAllTracks() } returns flowOf(listOf(fav, notFav))
    coEvery { mockRepository.getActiveTrackRealtime() } returns flowOf(null)
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    val before = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, handle)
    before.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)
    before.toggleCategoryFilter(PlaceCategoryFacet.CAFE)
    before.setSort(TrackSort.DISTANCE)
    before.toggleSortDirection()

    // When: 同じ handle から作り直す（プロセスが落ちて戻ったとき）
    val after = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, handle)

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

    val state = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle()).uiState.value

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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
      .also { it.computeDispatcher = testDispatcher }
    testDispatcher.scheduler.advanceUntilIdle()

    // When: 地図表示で経路 2 を選び、お気に入りだけに絞る（経路 2 は外れる）
    viewModel.setViewMode(ListMapMode.MAP)
    viewModel.toggleOverlay(tracks[1])
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.setFavoriteFilter(TrackFavoriteFilter.FAVORITE)

    // Then
    val state = viewModel.uiState.value
    assertEquals(ListMapMode.MAP, state.viewMode)
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
    viewModel = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, SavedStateHandle())
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
    HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, handle).apply {
      setViewMode(ListMapMode.MAP)
      setControlsCollapsed(true)
    }

    val state = HistoryViewModel(mockRepository, mockPlaceRepository, mockSettings, deletionQueue, handle).uiState.value

    assertEquals(ListMapMode.MAP, state.viewMode)
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
