package com.pathly.domain.model

import java.util.Date

data class GpsPoint(
  val id: Long,
  val trackId: Long,
  val latitude: Double,
  val longitude: Double,
  val altitude: Double?,
  val accuracy: Float,
  val speed: Float?,
  val bearing: Float?,
  // Location 由来の付随情報（gps_points v9）。生点にのみ入る（補正後点では既定のまま）。
  val provider: String? = null,
  val verticalAccuracyMeters: Float? = null,
  val speedAccuracyMetersPerSecond: Float? = null,
  val bearingAccuracyDegrees: Float? = null,
  val mslAltitudeMeters: Double? = null,
  val mslAltitudeAccuracyMeters: Float? = null,
  val elapsedRealtimeNanos: Long = 0L,
  val isMock: Boolean = false,
  val extrasJson: String? = null,
  val timestamp: Date,
  val createdAt: Date,
)
