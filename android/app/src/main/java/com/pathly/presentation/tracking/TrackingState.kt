package com.pathly.presentation.tracking

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.presentation.places.PlaceDeleteUndo

data class TrackingState(
  val isTracking: Boolean = false,
  /** 停止を押してから保存（確定）が終わるまで。ローディングを出してバックを塞ぐ。 */
  val isFinalizing: Boolean = false,
  val hasLocationPermission: Boolean = false,
  val currentTrackId: Long? = null,
  val errorMessage: String? = null,
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
  // マップ上の POI から場所を登録した直後の一時メッセージ（表示後クリア）
  val placeRegisteredMessage: String? = null,
  // 場所を削除した直後の取り消し待ち（スナックバーで「取り消す」を出す）。場所一覧と同じ流儀。
  val deleteUndo: PlaceDeleteUndo = PlaceDeleteUndo(),
  // 空き地点の登録で近くに既存の場所が見つかったときの確認待ち（紐付け/新規をユーザーが選ぶ）。
  val nearbyRegisterPrompt: NearbyRegisterPrompt? = null,
  // 手動の立ち寄り追加で近くに既存の場所が見つかったときの確認待ち。
  val nearbyStopPrompt: NearbyStopPrompt? = null,
)

data class LocationInfo(
  val latitude: Double,
  val longitude: Double,
  val accuracy: Float,
  val timestamp: String,
)
