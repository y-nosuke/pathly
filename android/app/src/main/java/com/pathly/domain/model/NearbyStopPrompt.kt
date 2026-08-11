package com.pathly.domain.model

import java.util.Date

/**
 * 手動での立ち寄り追加における近接確認の保留状態。近くに既存の場所が見つかったので、
 * ユーザーが「紐付ける／新規で追加する」を選ぶまで追加を保留している状態を表す。
 *
 * [nearby] が見つかった既存の場所、残りはユーザーが選んだあとに適用する追加内容。
 * 以前は TrackingScreen（PendingManualAdd）と TrackDetailScreen（ProximityPrompt）に
 * 別々の private data class として定義されていた。
 */
data class NearbyStopPrompt(
  val nearby: RegisteredPlace,
  val latitude: Double,
  val longitude: Double,
  val arrivalTime: Date,
  val departureTime: Date,
  val name: String?,
)
