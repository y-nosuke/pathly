package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack

data class HistoryState(
  val tracks: List<GpsTrack> = emptyList(),
  val activeTrack: GpsTrack? = null,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
)
