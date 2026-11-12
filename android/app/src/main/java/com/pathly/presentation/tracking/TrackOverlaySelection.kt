package com.pathly.presentation.tracking

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 地図に重ねる過去の経路の選び（色の割り当て・[TRACK_OVERLAY_MAX] 本までの上限・線と立ち寄りの読み込み・寄せる合図）。
 * 記録画面の「この範囲の経路」と履歴の地図表示で共有する（入口で選び方・見え方が変わらないように）。
 *
 * 選んだ経路と寄せる合図は、持ち主の画面の状態（[state]）の中に置く。[selectedOf]・[withSelected]・[withFocus] で
 * その場所を読み書きする。持ち主の状態をそのまま更新するので、選んだ直後に画面の状態へ反映される。
 */
internal class TrackOverlaySelection<S>(
  private val state: MutableStateFlow<S>,
  private val selectedOf: (S) -> List<OverlayTrack>,
  private val withSelected: (S, List<OverlayTrack>) -> S,
  private val withFocus: (S, TrackFocusRequest?) -> S,
  private val gpsTrackRepository: GpsTrackRepository,
  private val placeRepository: PlaceRepository,
  private val scope: CoroutineScope,
  // 線の点を読んで区間に分ける計算（補正点が無い古い経路では平滑化も走る）を主スレッドから外す。テストでは差し替える。
  private val computeDispatcher: () -> CoroutineDispatcher,
) {
  private var focusNonce = 0

  private val selected: List<OverlayTrack>
    get() = selectedOf(state.value)

  private fun updateSelected(transform: (List<OverlayTrack>) -> List<OverlayTrack>) {
    state.update { withSelected(it, transform(selectedOf(it))) }
  }

  /** 経路を地図に重ねる／外す。上限まで選んでいれば足さない。 */
  fun toggle(track: GpsTrack) {
    if (selected.any { it.track.id == track.id }) {
      updateSelected { list -> list.filterNot { it.track.id == track.id } }
      return
    }
    val color = nextFreeColorIndex(selected.map { it.colorIndex }) ?: return
    updateSelected { it + OverlayTrack(track, color) }
    load(track.id)
  }

  private fun load(trackId: Long) {
    scope.launch {
      val loaded = withContext(computeDispatcher()) { gpsTrackRepository.getDisplaySegments(trackId) }
      if (loaded == null) {
        // 読めなかった（消された）経路は選びから外す。
        updateSelected { list -> list.filterNot { it.track.id == trackId } }
        return@launch
      }
      val segments = loaded.filter { it.size >= 2 }
      val stops = placeRepository.getStopsForTrack(trackId).first()
      updateSelected { list ->
        list.map { if (it.track.id == trackId) it.copy(segments = segments, stops = stops, isLoaded = true) else it }
      }
    }
  }

  /**
   * その経路の全体が見えるまで地図を寄せる。まだ重ねていなければ重ねてから寄せる（上限なら何もしない）。
   * 実際に寄せるのは画面（線を読み終えてから）。
   */
  fun focus(track: GpsTrack) {
    if (selected.none { it.track.id == track.id }) {
      toggle(track)
      if (selected.none { it.track.id == track.id }) return
    }
    state.update { withFocus(it, TrackFocusRequest(track.id, ++focusNonce)) }
  }

  /** 地図を寄せ終えた（または寄せられなかった）。 */
  fun consumeFocus() {
    state.update { withFocus(it, null) }
  }

  /** 重ねている経路をすべて外す。 */
  fun clearAll() {
    updateSelected { emptyList() }
  }

  /** 今ある経路（[all]）に合わせる。消された経路は外し、名前・お気に入りは今の値に揃える。 */
  fun syncWith(all: List<GpsTrack>) {
    val byId = all.associateBy { it.id }
    updateSelected { list -> list.mapNotNull { o -> byId[o.track.id]?.let { o.copy(track = it) } } }
  }
}
