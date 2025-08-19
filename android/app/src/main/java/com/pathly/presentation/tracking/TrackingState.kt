package com.pathly.presentation.tracking

data class TrackingState(
  val isTracking: Boolean = false,
  val hasLocationPermission: Boolean = false,
  val currentTrackId: Long? = null,
  val errorMessage: String? = null
)