package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceRegion
import com.pathly.domain.model.RegionKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/** 経路一覧の地域の絞り込み（立ち寄った場所の地域で見る → adr/0029）。 */
class HistoryStateRegionTest {

  private fun track(id: Long, regions: Set<PlaceRegion>, favorite: Boolean = false) = GpsTrack(
    id = id,
    startTime = Date(id * 1000),
    endTime = Date(id * 1000 + 60_000),
    isActive = false,
    isFavorite = favorite,
    stopCount = regions.size,
    createdAt = Date(id),
    updatedAt = Date(id),
    stopRegions = regions,
  )

  private val tokyoKyoto = track(1, setOf(PlaceRegion("東京都", "渋谷区"), PlaceRegion("京都府", "京都市")))
  private val tokyo = track(2, setOf(PlaceRegion("東京都", "新宿区")), favorite = true)
  private val noStops = track(3, emptySet())
  private val tracks = listOf(tokyoKyoto, tokyo, noStops)

  @Test
  fun regionFilter_keepsTracksThatStoppedInAnySelectedRegion() {
    val state = HistoryState(tracks = tracks, regionFilter = setOf(RegionKey("京都府")))

    assertEquals(listOf(1L), state.visibleTracks.map { it.id })
  }

  @Test
  fun tracksWithoutStops_areInUnknownRegion() {
    val state = HistoryState(tracks = tracks, regionFilter = setOf(RegionKey.UNKNOWN))

    assertEquals(listOf(3L), state.visibleTracks.map { it.id })
  }

  @Test
  fun regionCounts_followOtherAxes() {
    val state = HistoryState(tracks = tracks, favoriteFilter = TrackFavoriteFilter.FAVORITE)

    val counts = state.regionCounts.associate { it.prefecture to it.count }
    assertEquals(mapOf("東京都" to 1, "京都府" to 0, null to 0), counts)
  }
}
