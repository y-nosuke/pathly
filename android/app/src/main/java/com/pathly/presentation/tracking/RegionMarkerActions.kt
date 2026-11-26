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

  /** 選んだ地域をすべて見る範囲へ寄せた（どれか 1 つを見ているのではないので、塗りつぶしを外す）。 */
  fun unfocus() {
    _state.update { it.copy(focusedPlaceId = null) }
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

  /**
   * その地域を並びの [toIndex] 番目へ移す（チップのドラッグの並べ替え・まとめの「上に出す」→ ADR 0047）。
   * 先に抜いてから入れるので、[toIndex] は移した後の位置。範囲の外は端に寄せる。塗りつぶしは変えない。
   */
  fun move(placeId: String, toIndex: Int) {
    _state.update { state ->
      val from = state.markers.indexOfFirst { it.placeId == placeId }
      if (from < 0) return@update state
      val rest = state.markers.toMutableList()
      val marker = rest.removeAt(from)
      val to = toIndex.coerceIn(0, rest.size)
      if (to == from) return@update state
      rest.add(to, marker)
      state.copy(markers = rest)
    }
  }

  /** その地域を並びの最後（「他 N 件」のまとめの最後）へ入れる。代わりに、まとめの先頭が縦のチップに上がる（ADR 0047）。 */
  fun fold(placeId: String) {
    move(placeId, Int.MAX_VALUE)
  }

  /**
   * まとめの中の地域を並びの [slot] 番目（縦のチップのいちばん下）に出し、そこにいた地域をまとめの最後へ入れる
   * （まとめのメニューの「上に出す」→ ADR 0047）。
   */
  fun bringUp(placeId: String, slot: Int) {
    _state.update { state ->
      val from = state.markers.indexOfFirst { it.placeId == placeId }
      if (from <= slot || slot !in state.markers.indices) return@update state
      val rest = state.markers.toMutableList()
      val pushedOut = rest[slot]
      rest.add(slot, rest.removeAt(from))
      rest.remove(pushedOut)
      rest.add(pushedOut)
      state.copy(markers = rest)
    }
  }

  /** すべて外す。 */
  fun clear() {
    _state.value = RegionMarkersState()
  }
}
