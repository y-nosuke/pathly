package com.pathly.presentation.tracking

import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import io.mockk.coEvery
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
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class TrackOverlayViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val repository = mockk<GpsTrackRepository>()
  private lateinit var viewModel: TrackOverlayViewModel

  private val bounds = GeoBounds(south = 35.67, west = 139.75, north = 35.69, east = 139.78)

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = TrackOverlayViewModel(repository).also { it.computeDispatcher = testDispatcher }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun track(id: Long, name: String? = null) = GpsTrack(
    id = id,
    startTime = Date(id * 1000),
    endTime = Date(id * 1000 + 500),
    isActive = false,
    name = name,
    createdAt = Date(),
    updatedAt = Date(),
  )

  private fun point(i: Int) = GpsPoint(
    id = i.toLong(),
    trackId = 1,
    latitude = 35.68 + i * 0.0001,
    longitude = 139.76,
    altitude = null,
    accuracy = 5f,
    speed = null,
    bearing = null,
    timestamp = Date(1000L + i * 1000L),
    createdAt = Date(),
  )

  @Test
  fun `探すと_範囲に入る経路だけを新しい順のまま一覧に出す`() = runTest {
    coEvery { repository.getFinishedTrackIdsInBounds(bounds) } returns setOf(3L, 1L)
    coEvery { repository.getAllTracks() } returns flowOf(listOf(track(3), track(2), track(1)))

    viewModel.search(bounds)
    assertTrue("探している間はシートを出して読み込み中", viewModel.uiState.value.isSheetOpen && viewModel.uiState.value.isSearching)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isSearching)
    assertEquals(listOf(3L, 1L), state.inRange.map { it.id })
  }

  @Test
  fun `選ぶと_空いている色を割り当てて線を読み込む`() = runTest {
    val points = (0 until 5).map(::point)
    coEvery { repository.getDisplaySegments(1L) } returns listOf(points)
    coEvery { repository.getDisplaySegments(2L) } returns listOf(points)

    viewModel.toggle(track(1))
    viewModel.toggle(track(2))
    testDispatcher.scheduler.advanceUntilIdle()

    val selected = viewModel.uiState.value.selected
    assertEquals(listOf(0, 1), selected.map { it.colorIndex })
    assertTrue("線の点を読み込む", selected.all { it.segments.isNotEmpty() })
  }

  @Test
  fun `外すと_その色が空き_次に選んだ経路が使う`() = runTest {
    coEvery { repository.getDisplaySegments(any()) } returns listOf(listOf(point(0), point(1)))

    viewModel.toggle(track(1))
    viewModel.toggle(track(2))
    viewModel.toggle(track(1)) // 外す
    viewModel.toggle(track(3))
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(2L, 3L), state.selected.map { it.track.id })
    assertEquals(0, state.colorIndexOf(3L))
    assertEquals(1, state.colorIndexOf(2L))
  }

  @Test
  fun `上限まで選ぶと_それ以上は足さない`() = runTest {
    coEvery { repository.getDisplaySegments(any()) } returns listOf(listOf(point(0), point(1)))

    (1L..(TRACK_OVERLAY_MAX + 1).toLong()).forEach { viewModel.toggle(track(it)) }
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(TRACK_OVERLAY_MAX, state.selected.size)
    assertFalse(state.canSelectMore)
    assertNull(state.colorIndexOf((TRACK_OVERLAY_MAX + 1).toLong()))
  }

  @Test
  fun `探し直すと_消された経路は選びから外れ_範囲の外の経路は残る`() = runTest {
    coEvery { repository.getDisplaySegments(any()) } returns listOf(listOf(point(0), point(1)))
    viewModel.toggle(track(1))
    viewModel.toggle(track(2))
    testDispatcher.scheduler.advanceUntilIdle()

    // 経路 2 は消された。経路 1 は残っているが、今回の範囲には入らない。
    coEvery { repository.getFinishedTrackIdsInBounds(bounds) } returns setOf(5L)
    coEvery { repository.getAllTracks() } returns flowOf(listOf(track(5), track(1, name = "名前を付けた")))
    viewModel.search(bounds)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(1L), state.selected.map { it.track.id })
    assertEquals("名前は今の値に揃える", "名前を付けた", state.selected.single().track.name)
    assertEquals(listOf(1L), state.selectedOutOfRange.map { it.id })
  }

  @Test
  fun `読めなかった経路は選びから外す`() = runTest {
    coEvery { repository.getDisplaySegments(1L) } returns null

    viewModel.toggle(track(1))
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.selected.isEmpty())
  }

  @Test
  fun `一覧を閉じても_重ねた線は残る`() = runTest {
    coEvery { repository.getDisplaySegments(any()) } returns listOf(listOf(point(0), point(1)))
    viewModel.toggle(track(1))
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.closeSheet()

    val state = viewModel.uiState.value
    assertFalse(state.isSheetOpen)
    assertEquals(1, state.selected.size)
  }

  @Test
  fun `探すのに失敗したら_一覧に知らせる`() = runTest {
    coEvery { repository.getFinishedTrackIdsInBounds(bounds) } throws IllegalStateException("db")

    viewModel.search(bounds)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isSearching)
    assertEquals("経路を探せませんでした", state.errorMessage)
  }
}
