package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
  private val gpsTrackRepository: GpsTrackRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HistoryState())
  val uiState: StateFlow<HistoryState> = _uiState.asStateFlow()

  init {
    loadTracks()
    observeActiveTrack()
  }

  private fun loadTracks() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      try {
        gpsTrackRepository.getAllTracks().collect { tracks ->
          // 完了済み（記録中でなく終了時刻あり）だけを一覧に載せる。並べ替えは state 側で行う。
          val completedTracks = tracks.filter { !it.isActive && it.endTime != null }
          _uiState.update {
            it.copy(
              tracks = completedTracks,
              isLoading = false,
              errorMessage = null,
            )
          }
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoading = false,
            errorMessage = "データの読み込みに失敗しました: ${e.message}",
          )
        }
      }
    }
  }

  /** お気に入りの絞り込み（指定なし/お気に入り/お気に入り以外）。 */
  fun setFavoriteFilter(filter: TrackFavoriteFilter) {
    _uiState.update { it.copy(favoriteFilter = filter) }
  }

  /** 命名状況の絞り込み（指定なし/名前あり/未命名）。 */
  fun setNamedFilter(filter: TrackNamedFilter) {
    _uiState.update { it.copy(namedFilter = filter) }
  }

  /** 立ち寄りの有無の絞り込み（指定なし/あり/なし）。 */
  fun setStopFilter(filter: TrackStopFilter) {
    _uiState.update { it.copy(stopFilter = filter) }
  }

  /** 絞り込みを全解除する（3軸まとめて指定なしに戻す）。並べ替えは保持。 */
  fun clearFilters() {
    _uiState.update {
      it.copy(
        favoriteFilter = TrackFavoriteFilter.ANY,
        namedFilter = TrackNamedFilter.ANY,
        stopFilter = TrackStopFilter.ANY,
      )
    }
  }

  /** 並べ替え軸の変更。軸ごとの既定の向き（新しい/多い/長いが先）に合わせる。 */
  fun setSort(sort: TrackSort) {
    _uiState.update { it.copy(sort = sort, sortDescending = sort.defaultDescending) }
  }

  /** 並べ替えの昇順/降順を反転する。 */
  fun toggleSortDirection() {
    _uiState.update { it.copy(sortDescending = !it.sortDescending) }
  }

  /** お気に入り登録を切り替える。 */
  fun toggleFavorite(track: GpsTrack) {
    viewModelScope.launch {
      try {
        gpsTrackRepository.setFavorite(track.id, !track.isFavorite)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            errorMessage = "お気に入りの更新に失敗しました: ${e.message}",
          )
        }
      }
    }
  }

  /** 経路名を編集する（空なら未命名に戻す）。 */
  fun renameTrack(trackId: Long, name: String) {
    viewModelScope.launch {
      try {
        gpsTrackRepository.renameTrack(trackId, name)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            errorMessage = "名前の変更に失敗しました: ${e.message}",
          )
        }
      }
    }
  }

  fun deleteTrack(track: GpsTrack) {
    viewModelScope.launch {
      try {
        gpsTrackRepository.deleteTrack(track)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            errorMessage = "削除に失敗しました: ${e.message}",
          )
        }
      }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private fun observeActiveTrack() {
    viewModelScope.launch {
      gpsTrackRepository.getActiveTrackRealtime().collect { activeTrack ->
        _uiState.update { it.copy(activeTrack = activeTrack) }
      }
    }
  }
}
