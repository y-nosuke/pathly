package com.pathly.domain.model

/**
 * 近接確認の保留状態。近くに既存の場所が見つかったので、ユーザーが「紐付ける／新規で登録する」を
 * 選ぶまで登録を保留している状態を表す。
 *
 * [nearby] が見つかった既存の場所、残りはユーザーが選んだあとに適用する登録内容。
 * 以前は同じものが TrackingScreen と TrackDetailScreen にそれぞれ private data class として
 * 定義され、分岐も Composable 側にあった。
 */
data class NearbyRegisterPrompt(
  val nearby: RegisteredPlace,
  val latitude: Double,
  val longitude: Double,
  val name: String?,
  val wishlist: Boolean,
  val priority: Priority,
  val memo: String?,
)
