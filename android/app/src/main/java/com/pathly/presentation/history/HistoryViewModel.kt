package com.pathly.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.RegionKey
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.presentation.common.toggled
import com.pathly.presentation.tracking.TrackOverlaySelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
  private val gpsTrackRepository: GpsTrackRepository,
  private val placeRepository: PlaceRepository,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  // 絞り込み・並べ替えはアプリが裏で落とされても戻せるよう savedStateHandle から始める。
  private val _uiState = MutableStateFlow(HistoryState().withListOptionsFrom(savedStateHandle))
  val uiState: StateFlow<HistoryState> = _uiState.asStateFlow()

  // 線の点を読む計算を主スレッドから外す。テストでは差し替える。
  internal var computeDispatcher: CoroutineDispatcher = Dispatchers.Default

  // 地図表示で選んで重ねる経路（記録画面の「この範囲の経路」と同じ仕組み）。ViewModel が生きている間
  // （経路詳細へ行って戻る・タブを切り替える）は保ち、アプリを閉じると消える。
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
          // 地図に重ねている経路も、消された経路は外し、名前・お気に入りを今の値に揃える。
          selection.syncWith(completedTracks)
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
    updateListOptions { it.copy(favoriteFilter = filter) }
  }

  /** 命名状況の絞り込み（指定なし/名前あり/未命名）。 */
  fun setNamedFilter(filter: TrackNamedFilter) {
    updateListOptions { it.copy(namedFilter = filter) }
  }

  /** 立ち寄りの有無の絞り込み（指定なし/あり/なし）。 */
  fun setStopFilter(filter: TrackStopFilter) {
    updateListOptions { it.copy(stopFilter = filter) }
  }

  /** 業種の絞り込みで、その業種を選ぶ／外す（複数選べる）。 */
  fun toggleCategoryFilter(facet: PlaceCategoryFacet) {
    updateListOptions { it.copy(categoryFilter = it.categoryFilter.toggled(facet)) }
  }

  /** 業種の絞り込みだけをまとめて外す（ほかの軸はそのまま）。 */
  fun clearCategoryFilter() {
    updateListOptions { it.copy(categoryFilter = emptySet()) }
  }

  /** 地域の絞り込みで、その都道府県・市区町村を選ぶ／外す（複数選べる）。 */
  fun toggleRegionFilter(region: RegionKey) {
    updateListOptions { it.copy(regionFilter = it.regionFilter.toggled(region)) }
  }

  /** 地域の絞り込みだけをまとめて外す（ほかの軸はそのまま）。 */
  fun clearRegionFilter() {
    updateListOptions { it.copy(regionFilter = emptySet()) }
  }

  /** 名前の検索語を変える（入力のたびに一覧を絞る）。 */
  fun setNameQuery(query: String) {
    updateListOptions { it.copy(nameQuery = query) }
  }

  /**
   * 絞り込みの欄の 5 軸を全解除する。名前の検索語は検索欄の × で消すので残す。並べ替えも保持。
   */
  fun clearFilters() {
    updateListOptions {
      it.copy(
        favoriteFilter = TrackFavoriteFilter.ANY,
        namedFilter = TrackNamedFilter.ANY,
        stopFilter = TrackStopFilter.ANY,
        categoryFilter = emptySet(),
        regionFilter = emptySet(),
      )
    }
  }

  /** 絞り込み・名前の検索・並べ替え（軸と向き）をまとめて初期状態に戻す。 */
  fun resetListOptions() {
    val initial = HistoryState()
    updateListOptions {
      it.copy(
        favoriteFilter = initial.favoriteFilter,
        namedFilter = initial.namedFilter,
        stopFilter = initial.stopFilter,
        categoryFilter = initial.categoryFilter,
        regionFilter = initial.regionFilter,
        nameQuery = initial.nameQuery,
        sort = initial.sort,
        sortDescending = initial.sortDescending,
      )
    }
  }

  /** 並べ替え軸の変更。軸ごとの既定の向き（新しい/多い/長いが先）に合わせる。 */
  fun setSort(sort: TrackSort) {
    updateListOptions { it.copy(sort = sort, sortDescending = sort.defaultDescending) }
  }

  /** 並べ替えの昇順/降順を反転する。 */
  fun toggleSortDirection() {
    updateListOptions { it.copy(sortDescending = !it.sortDescending) }
  }

  /** 一覧で見るか地図で見るかを切り替える（絞り込みはそのまま）。 */
  fun setViewMode(mode: HistoryViewMode) {
    updateListOptions { it.copy(viewMode = mode) }
  }

  /** 地図表示で、地図に重ねた見出し・検索欄・絞り込みをたたむ／ひろげる。 */
  fun setControlsCollapsed(collapsed: Boolean) {
    updateListOptions { it.copy(controlsCollapsed = collapsed) }
  }

  /** 地図表示で、経路を地図に重ねる／外す（同時に 8 本まで）。 */
  fun toggleOverlay(track: GpsTrack) = selection.toggle(track)

  /** 地図表示で、その経路の全体が見えるまで地図を寄せる（まだ重ねていなければ重ねてから）。 */
  fun focusOverlay(track: GpsTrack) = selection.focus(track)

  /** 地図を寄せ終えた（または寄せられなかった）。 */
  fun consumeFocus() = selection.consumeFocus()

  /** 地図に重ねている経路をすべて外す。 */
  fun clearOverlays() = selection.clearAll()

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

  /** 絞り込み・並べ替えを変えて、savedStateHandle にも残す。 */
  private fun updateListOptions(transform: (HistoryState) -> HistoryState) {
    _uiState.update(transform)
    _uiState.value.saveListOptionsTo(savedStateHandle)
  }
}
