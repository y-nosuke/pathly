package com.pathly.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
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
import kotlinx.coroutines.withContext
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
) : ViewModel() {

  private val logger = Logger("TrackOverlayViewModel")

  // 線の点を読んで区間に分ける計算（補正点が無い古い経路では平滑化も走る）を主スレッドから外す。
  // テストでは差し替える。
  internal var computeDispatcher: CoroutineDispatcher = Dispatchers.Default

  private val _uiState = MutableStateFlow(TrackOverlayState())
  val uiState: StateFlow<TrackOverlayState> = _uiState.asStateFlow()

  private var searchJob: Job? = null

  /** [bounds] に入る経路を探して一覧を出す。前の検索が終わっていなければ取り消す。 */
  fun search(bounds: GeoBounds) {
    searchJob?.cancel()
    _uiState.update { it.copy(isSheetOpen = true, isSearching = true, errorMessage = null) }
    searchJob = viewModelScope.launch {
      try {
        val ids = gpsTrackRepository.getFinishedTrackIdsInBounds(bounds)
        // 一覧の行（名前・距離・お気に入り）は履歴一覧と同じものを使う。点は読まない。
        val all = gpsTrackRepository.getAllTracks().first()
        val byId = all.associateBy { it.id }
        _uiState.update { state ->
          state.copy(
            isSearching = false,
            inRange = all.filter { it.id in ids },
            // 消された経路は外し、名前・お気に入りは今の値に揃える。
            selected = state.selected.mapNotNull { o -> byId[o.track.id]?.let { o.copy(track = it) } },
          )
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        logger.e("Failed to search tracks in the map bounds", e)
        _uiState.update { it.copy(isSearching = false, errorMessage = "経路を探せませんでした") }
      }
    }
  }

  /** 経路を地図に重ねる／外す。上限まで選んでいれば足さない。 */
  fun toggle(track: GpsTrack) {
    val state = _uiState.value
    if (track.id in state.selectedIds) {
      _uiState.update { s -> s.copy(selected = s.selected.filterNot { it.track.id == track.id }) }
      return
    }
    val color = nextFreeColorIndex(state.selected.map { it.colorIndex }) ?: return
    _uiState.update { s -> s.copy(selected = s.selected + OverlayTrack(track, color)) }
    loadSegments(track.id)
  }

  private fun loadSegments(trackId: Long) {
    viewModelScope.launch {
      val loaded = withContext(computeDispatcher) { gpsTrackRepository.getDisplaySegments(trackId) }
      if (loaded == null) {
        // 読めなかった（消された）経路は選びから外す。
        _uiState.update { s -> s.copy(selected = s.selected.filterNot { it.track.id == trackId }) }
        return@launch
      }
      val segments = loaded.filter { it.size >= 2 }
      _uiState.update { s ->
        s.copy(selected = s.selected.map { if (it.track.id == trackId) it.copy(segments = segments) else it })
      }
    }
  }

  /** 重ねている経路をすべて外す。 */
  fun clearAll() {
    _uiState.update { it.copy(selected = emptyList()) }
  }

  /** 一覧を閉じる。重ねた線は残す。 */
  fun closeSheet() {
    searchJob?.cancel()
    _uiState.update { it.copy(isSheetOpen = false, isSearching = false) }
  }
}
