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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 詳細画面の立ち寄り表示・編集と、補正後軌跡の表示・再補正。開いた経路の立ち寄りを
 * 確定（検出＋命名）し、保存済みの立ち寄りをリアクティブに配信する。
 */
@HiltViewModel
class TrackDetailViewModel @Inject constructor(
  private val placeRepository: PlaceRepository,
  private val gpsTrackRepository: GpsTrackRepository,
) : ViewModel() {

  private val _stops = MutableStateFlow<List<Stop>>(emptyList())
  val stops: StateFlow<List<Stop>> = _stops.asStateFlow()

  // 保存済みの補正後点列を反映したトラック。再補正すると更新される。
  private val _displayTrack = MutableStateFlow<GpsTrack?>(null)
  val displayTrack: StateFlow<GpsTrack?> = _displayTrack.asStateFlow()

  private var loadedTrackId: Long? = null
  private var collectJob: Job? = null

  fun load(track: GpsTrack) {
    if (loadedTrackId == track.id) return
    loadedTrackId = track.id

    // 検出・命名はバックグラウンドで進める（表示はすぐ購読を始める）。
    viewModelScope.launch { placeRepository.ensureStopsDetected(track) }

    // 保存済みの補正後点列を読み込んで表示に反映する。
    viewModelScope.launch { _displayTrack.value = gpsTrackRepository.getTrackById(track.id) }

    collectJob?.cancel()
    collectJob = viewModelScope.launch {
      placeRepository.getStopsForTrack(track.id).collect { _stops.value = it }
    }
  }

  /** 軌跡を生データから作り直して保存し直す（アルゴリズム修正の反映・記録中失敗の保険）。 */
  fun recomputeSmoothing() {
    val trackId = loadedTrackId ?: return
    viewModelScope.launch {
      gpsTrackRepository.recomputeSmoothed(trackId)
      _displayTrack.value = gpsTrackRepository.getTrackById(trackId)
    }
  }

  fun updatePlaceName(placeId: Long, name: String) {
    viewModelScope.launch { placeRepository.updatePlaceName(placeId, name) }
  }

  /** 未命名の場所を Places で取り直す（オフラインで命名できなかった分の手動再実行）。 */
  fun retryNaming() {
    val trackId = loadedTrackId ?: return
    viewModelScope.launch { placeRepository.resolveMissingNames(trackId) }
  }
}
