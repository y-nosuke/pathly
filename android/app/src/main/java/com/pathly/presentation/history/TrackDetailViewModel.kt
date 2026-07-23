package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import com.pathly.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 詳細画面の立ち寄り表示・編集。開いた経路の立ち寄りを確定（検出＋命名）し、
 * 保存済みの立ち寄りをリアクティブに配信する。
 */
@HiltViewModel
class TrackDetailViewModel @Inject constructor(
  private val placeRepository: PlaceRepository,
) : ViewModel() {

  private val _stops = MutableStateFlow<List<Stop>>(emptyList())
  val stops: StateFlow<List<Stop>> = _stops.asStateFlow()

  private var loadedTrackId: Long? = null
  private var collectJob: Job? = null

  fun load(track: GpsTrack) {
    if (loadedTrackId == track.id) return
    loadedTrackId = track.id

    // 検出・命名はバックグラウンドで進める（表示はすぐ購読を始める）。
    viewModelScope.launch { placeRepository.ensureStopsDetected(track) }

    collectJob?.cancel()
    collectJob = viewModelScope.launch {
      placeRepository.getStopsForTrack(track.id).collect { _stops.value = it }
    }
  }

  fun updatePlaceName(placeId: Long, name: String) {
    viewModelScope.launch { placeRepository.updatePlaceName(placeId, name) }
  }
}
