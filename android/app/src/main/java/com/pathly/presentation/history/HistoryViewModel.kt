package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
  private val gpsTrackRepository: GpsTrackRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow(HistoryState())
  val uiState: StateFlow<HistoryState> = _uiState.asStateFlow()

  init {
    loadTracks()
  }

  private fun loadTracks() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true)
      try {
        gpsTrackRepository.getAllTracks().collect { tracks ->
          val completedTracks = tracks.filter { !it.isActive && it.endTime != null }
            .sortedByDescending { it.startTime }
          _uiState.value = _uiState.value.copy(
            tracks = completedTracks,
            isLoading = false,
            errorMessage = null
          )
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          errorMessage = "データの読み込みに失敗しました: ${e.message}"
        )
      }
    }
  }

  fun deleteTrack(track: GpsTrack) {
    viewModelScope.launch {
      try {
        gpsTrackRepository.deleteTrack(track)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          errorMessage = "削除に失敗しました: ${e.message}"
        )
      }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }
}