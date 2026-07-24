package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 詳細画面の立ち寄り表示・編集と、補正後軌跡の表示・再解析。
 * 検出・命名は自動では行わず（開いても検出しない）、再解析／場所を取得ボタンで明示的に行う。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackDetailViewModel @Inject constructor(
  private val placeRepository: PlaceRepository,
  private val gpsTrackRepository: GpsTrackRepository,
) : ViewModel() {

  private val _stops = MutableStateFlow<List<Stop>>(emptyList())
  val stops: StateFlow<List<Stop>> = _stops.asStateFlow()

  // 保存済みの補正後点列を反映したトラック。再解析すると更新される。
  private val _displayTrack = MutableStateFlow<GpsTrack?>(null)
  val displayTrack: StateFlow<GpsTrack?> = _displayTrack.asStateFlow()

  // 削除失敗などの一時メッセージ（表示したらクリアする）。
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  private val loadedTrackId = MutableStateFlow<Long?>(null)

  /** 未取得（googlePlaceId 無し）の place 件数。「場所を取得」ボタンの表示に使う。 */
  val unresolvedCount: StateFlow<Int> = loadedTrackId
    .flatMapLatest { id -> if (id == null) flowOf(0) else placeRepository.unresolvedCountForTrack(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  private var collectJob: Job? = null

  fun load(track: GpsTrack) {
    if (loadedTrackId.value == track.id) return
    loadedTrackId.value = track.id

    // 開いても検出はしない。保存済みの立ち寄りと補正後軌跡を表示するだけ。
    viewModelScope.launch { _displayTrack.value = gpsTrackRepository.getTrackById(track.id) }

    collectJob?.cancel()
    collectJob = viewModelScope.launch {
      placeRepository.getStopsForTrack(track.id).collect { _stops.value = it }
    }
  }

  fun updatePlaceName(placeId: Long, name: String) {
    viewModelScope.launch { placeRepository.updatePlaceName(placeId, name) }
  }

  /** 未取得の場所を Places で取り直す（手動・googlePlaceId 無しが対象）。 */
  fun resolveNames() {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch { placeRepository.resolveUnresolvedNames(trackId) }
  }

  /** 立ち寄り（訪問）1件を削除する。場所は残す。 */
  fun deleteStop(stopId: Long) {
    viewModelScope.launch { placeRepository.deleteStop(stopId) }
  }

  /** 場所ごと削除する。他経路に訪問が残っていれば削除せずメッセージを出す。 */
  fun deletePlace(placeId: Long, trackId: Long) {
    viewModelScope.launch {
      if (!placeRepository.deletePlace(placeId, trackId)) {
        _message.value = "他の経路にも訪問があるため、場所ごとは削除できません（この訪問だけ削除できます）"
      }
    }
  }

  fun clearMessage() {
    _message.value = null
  }

  /** 再解析: 軌跡を再補正 → 立ち寄りを検出し直し → 命名する。 */
  fun reanalyze() {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      gpsTrackRepository.recomputeSmoothed(trackId)
      placeRepository.redetectStops(trackId)
      _displayTrack.value = gpsTrackRepository.getTrackById(trackId)
    }
  }
}
