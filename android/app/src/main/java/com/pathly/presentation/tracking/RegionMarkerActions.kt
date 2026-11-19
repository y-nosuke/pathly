package com.pathly.presentation.tracking

import com.pathly.domain.model.GeoBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 「地域へ移動」で移した地域の目印（中心のピンと名前）。[placeId] で同じ地域を見分け、
 * [bounds] はチップ・目印から寄せ直すときの範囲（選んだときと同じ）。名前は選んだ候補の文字。
 */
data class RegionMarker(
  val placeId: String,
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val bounds: GeoBounds,
)

/** 出している地域の目印（選んだ順）と、最後に地図を移した地域（[focusedPlaceId]。チップを塗りつぶす）。 */
data class RegionMarkersState(
  val markers: List<RegionMarker> = emptyList(),
  val focusedPlaceId: String? = null,
) {
  /** その地域の目印をもう出しているなら、それを返す（選び直しは数を増やさず、取得もしない）。 */
  fun find(placeId: String): RegionMarker? = markers.firstOrNull { it.placeId == placeId }

  /** その地域を選べるか（もう出している地域か、上限に達していない）。 */
  fun canShow(placeId: String): Boolean = find(placeId) != null || markers.size < RegionMarkerActions.MAX_MARKERS
}

/**
 * 記録画面の地域の目印を持つ部品（[TrackingViewModel] が持つ）。保存はしない（Places の名前・範囲は保存しない
 * → ADR 0043）。ViewModel が残る間（タブの行き来・画面の回転）は残り、アプリが終わると消える。
 */
class RegionMarkerActions {
  private val _state = MutableStateFlow(RegionMarkersState())
  val state: StateFlow<RegionMarkersState> = _state.asStateFlow()

  /**
   * 目印を出し、その地域を最後に移した地域にする。もう出している地域なら数は増やさない。
   * 上限に達していて新しい地域なら何もせず false（窓に出せない旨を出すのは呼び出し側）。
   */
  fun show(marker: RegionMarker): Boolean {
    var shown = false
    _state.update { state ->
      when {
        state.find(marker.placeId) != null -> {
          shown = true
          state.copy(focusedPlaceId = marker.placeId)
        }

        state.markers.size < MAX_MARKERS -> {
          shown = true
          state.copy(markers = state.markers + marker, focusedPlaceId = marker.placeId)
        }

        else -> state
      }
    }
    return shown
  }

  /** チップ・目印のタップでその地域へ寄せ直した。 */
  fun focus(placeId: String) {
    _state.update { state -> if (state.find(placeId) != null) state.copy(focusedPlaceId = placeId) else state }
  }

  /** その地域の目印とチップを外す（地図は動かさない）。 */
  fun remove(placeId: String) {
    _state.update { state ->
      state.copy(
        markers = state.markers.filterNot { it.placeId == placeId },
        focusedPlaceId = state.focusedPlaceId.takeUnless { it == placeId },
      )
    }
  }

  /** すべて外す。 */
  fun clear() {
    _state.value = RegionMarkersState()
  }

  companion object {
    /** 同時に出せる地域の目印の数。 */
    const val MAX_MARKERS = 5

    /** 上限で新しい地域を選んだときに窓に出す文言。 */
    const val LIMIT_MESSAGE = "地域の目印は ${MAX_MARKERS} つまでです。チップの × で外してから選んでください"
  }
}
