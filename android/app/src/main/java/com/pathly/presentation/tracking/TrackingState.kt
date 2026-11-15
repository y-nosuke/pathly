package com.pathly.presentation.tracking

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop

data class TrackingState(
  val isTracking: Boolean = false,
  /** 停止を押してから保存（確定）が終わるまで。ローディングを出してバックを塞ぐ。 */
  val isFinalizing: Boolean = false,
  val hasLocationPermission: Boolean = false,
  val currentTrackId: Long? = null,
  val errorMessage: String? = null,
  // 端末の位置情報が OFF で開始できなかった。「位置情報の設定を開く」付きの案内を出し、
  // 設定から戻って ON になっていれば下ろす（errorMessage とは別に持つ）。
  val isLocationDisabled: Boolean = false,
  val currentLocation: LocationInfo? = null,
  val locationCount: Int = 0,
  val currentTrack: GpsTrack? = null,
  // 記録中の「立ち寄り中」（3分超で place 先行確定・メモリ保持）。離れたら null。
  val currentStop: Stop? = null,
  // 記録中トラックの確定済み立ち寄り（地図にマーカー表示するため。詳細画面と同じ見た目）。
  val stops: List<Stop> = emptyList(),
  // 「登録済みの場所」を地図に出すか（記録画面の画面別トグル）と、その全place。
  val showRegisteredPlaces: Boolean = false,
  val registeredPlaces: List<RegisteredPlace> = emptyList(),
  // アプリ更新やクラッシュで中断され、再開/完了の確認待ちになっているトラック
  val interruptedTrack: GpsTrack? = null,
  // 電池の最適化を無効化済みか（バックグラウンド記録の安定性に影響）。既定はtrueで案内を出さない
  val isIgnoringBatteryOptimizations: Boolean = true,
  // 場所の登録・立ち寄りの追加などの直後の一時メッセージ（表示後クリア）。場所の確認待ち・削除の取り消しは
  // 部品（PlaceEditActions・ManualStopActions）が持つ。
  val placeRegisteredMessage: String? = null,
)

data class LocationInfo(
  val latitude: Double,
  val longitude: Double,
  val accuracy: Float,
  val timestamp: String,
)
