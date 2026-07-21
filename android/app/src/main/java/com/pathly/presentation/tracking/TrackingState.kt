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
  // アプリ更新やクラッシュで中断され、再開/完了の確認待ちになっているトラック
  val interruptedTrack: GpsTrack? = null,
  // 電池の最適化を無効化済みか（バックグラウンド記録の安定性に影響）。既定はtrueで案内を出さない
  val isIgnoringBatteryOptimizations: Boolean = true,
)

data class LocationInfo(
  val latitude: Double,
  val longitude: Double,
  val accuracy: Float,
  val timestamp: String,
)
