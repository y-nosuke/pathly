package com.pathly.presentation.tracking

import com.pathly.domain.model.GpsTrack

data class TrackingState(
  val isTracking: Boolean = false,
  val hasLocationPermission: Boolean = false,
  val currentTrackId: Long? = null,
  val errorMessage: String? = null,
  val currentLocation: LocationInfo? = null,
  val locationCount: Int = 0,
  val currentTrack: GpsTrack? = null,
)

data class LocationInfo(
  val latitude: Double,
  val longitude: Double,
  val accuracy: Float,
  val timestamp: String,
)
