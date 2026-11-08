package com.pathly.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.GpsTrack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class HistoryStateTest {

  private lateinit var savedZone: TimeZone

  @Before
  fun setUp() {
    savedZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(savedZone)
  }

  // 2026-11-08 12:00 JST（日曜）と 2026-11-07 12:00 JST（土曜）。
  private val kamakura = track(id = 1, name = "秋の鎌倉さんぽ", start = 1_794_106_800_000L, favorite = true)
  private val enoshima = track(id = 2, name = "江ノ島", start = 1_794_106_800_000L)
  private val unnamed = track(id = 3, name = null, start = 1_794_020_400_000L)
  private val tracks = listOf(kamakura, enoshima, unnamed)

  @Test
  fun `名前の検索_名前に含むものだけ残す`() {
    val state = HistoryState(tracks = tracks, nameQuery = "鎌倉")

    assertEquals(listOf(1L), state.visibleTracks.map { it.id })
    assertFalse(state.noFilter)
  }

  @Test
  fun `名前の検索_未命名は見出しの日付で探す`() {
    val state = HistoryState(tracks = tracks, nameQuery = "11/07")

    assertEquals(listOf(3L), state.visibleTracks.map { it.id })
  }

  @Test
  fun `名前の検索_名前がある経路は日付では当たらない`() {
    // 名前がある経路の見出しは名前なので、日付（副題）では探さない。
    val state = HistoryState(tracks = tracks, nameQuery = "11/08")

    assertTrue(state.visibleTracks.isEmpty())
  }

  @Test
  fun `名前の検索_ほかの絞り込みとはAND`() {
    // 「鎌倉」に当たるのはお気に入りの経路だけなので、お気に入り以外で絞ると残らない。
    val state = HistoryState(tracks = tracks, nameQuery = "鎌倉", favoriteFilter = TrackFavoriteFilter.NOT_FAVORITE)

    assertTrue(state.visibleTracks.isEmpty())
  }

  @Test
  fun `名前の検索_空白だけなら絞らない`() {
    val state = HistoryState(tracks = tracks, nameQuery = " ")

    assertEquals(3, state.visibleTracks.size)
    assertTrue(state.noFilter)
  }

  @Test
  fun `名前の検索語も保存して戻せる`() {
    val handle = SavedStateHandle()
    HistoryState(nameQuery = "鎌倉").saveListOptionsTo(handle)

    assertEquals("鎌倉", HistoryState().withListOptionsFrom(handle).nameQuery)
  }

  private fun track(id: Long, name: String?, start: Long, favorite: Boolean = false) = GpsTrack(
    id = id,
    startTime = Date(start),
    endTime = Date(start + 60_000),
    isActive = false,
    name = name,
    isFavorite = favorite,
    createdAt = Date(start),
    updatedAt = Date(start),
  )
}
