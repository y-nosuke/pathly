package com.pathly.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 記録画面の「経路」。地図に映っている範囲に入る過去の経路を探し、一覧から選んだものを色分けで地図に重ねる。
 *
 * 記録まわり（[TrackingViewModel]）とは関わりが無いので分けてある。範囲は押した時点のものを受け取り、
 * 地図を動かすたびには探さない（性能のため。探し直すにはもう一度押す）。
 */
@HiltViewModel
class TrackOverlayViewModel @Inject constructor(
  private val gpsTrackRepository: GpsTrackRepository,
  private val placeRepository: PlaceRepository,
) : ViewModel() {

  private val logger = Logger("TrackOverlayViewModel")

  // 線の点を読んで区間に分ける計算（補正点が無い古い経路では平滑化も走る）を主スレッドから外す。
  // テストでは差し替える。
  internal var computeDispatcher: CoroutineDispatcher = Dispatchers.Default

  private val _uiState = MutableStateFlow(TrackOverlayState())
  val uiState: StateFlow<TrackOverlayState> = _uiState.asStateFlow()

  private var searchJob: Job? = null

  // 選んだ経路（色・上限・線の読み込み・寄せる合図）は履歴の地図表示と共有する。
  private val selection = TrackOverlaySelection(
    state = _uiState,
    selectedOf = { it.selected },
    withSelected = { s, selected -> s.copy(selected = selected) },
    withFocus = { s, focus -> s.copy(focusRequest = focus) },
    gpsTrackRepository = gpsTrackRepository,
    placeRepository = placeRepository,
    scope = viewModelScope,
    computeDispatcher = { computeDispatcher },
  )

  /** [bounds] に入る経路を探して一覧を出す。前の検索が終わっていなければ取り消す。 */
  fun search(bounds: GeoBounds) {
    searchJob?.cancel()
    _uiState.update { it.copy(isSheetOpen = true, isSearching = true, errorMessage = null) }
    searchJob = viewModelScope.launch {
      try {
        val ids = gpsTrackRepository.getFinishedTrackIdsInBounds(bounds)
        // 一覧の行（名前・距離・お気に入り）は履歴一覧と同じものを使う。点は読まない。
        val all = gpsTrackRepository.getAllTracks().first()
        _uiState.update { it.copy(isSearching = false, inRange = all.filter { track -> track.id in ids }) }
        selection.syncWith(all)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        logger.e("Failed to search tracks in the map bounds", e)
        _uiState.update { it.copy(isSearching = false, errorMessage = "経路を探せませんでした") }
      }
    }
  }

  /** 経路を地図に重ねる／外す。上限まで選んでいれば足さない。 */
  fun toggle(track: GpsTrack) = selection.toggle(track)

  /** その経路の全体が見えるまで地図を寄せる（まだ重ねていなければ重ねてから。上限なら何もしない）。 */
  fun focus(track: GpsTrack) = selection.focus(track)

  /** 地図を寄せ終えた（または寄せられなかった）。 */
  fun consumeFocus() = selection.consumeFocus()

  /** 重ねている経路をすべて外す。 */
  fun clearAll() = selection.clearAll()

  /** 一覧を閉じる。重ねた線も消す（線だけが地図に残って、どこから外すか分からなくならないように）。 */
  fun closeSheet() {
    searchJob?.cancel()
    _uiState.value = TrackOverlayState()
  }
}
