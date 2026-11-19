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
}

/**
 * 記録画面の地域の目印を持つ部品（[TrackingViewModel] が持つ）。保存はしない（Places の名前・範囲は保存しない
 * → ADR 0043）。ViewModel が残る間（タブの行き来・画面の回転）は残り、アプリが終わると消える。
 */
class RegionMarkerActions {
  private val _state = MutableStateFlow(RegionMarkersState())
  val state: StateFlow<RegionMarkersState> = _state.asStateFlow()

  /** 目印を出し（数は限らない）、その地域を最後に移した地域にする。もう出している地域なら数は増やさない。 */
  fun show(marker: RegionMarker) {
    _state.update { state ->
      if (state.find(marker.placeId) != null) {
        state.copy(focusedPlaceId = marker.placeId)
      } else {
        state.copy(markers = state.markers + marker, focusedPlaceId = marker.placeId)
      }
    }
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
}
