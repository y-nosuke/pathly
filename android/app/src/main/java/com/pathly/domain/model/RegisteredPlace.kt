package com.pathly.domain.model

import java.util.Locale

/**
 * 地図に「登録済みの場所」マーカーとして出す最小情報。USER・DETECTED どちらの place も対象。
 * 立ち寄り（stops）とは別に、その地点に既知の場所があることを見せ、手動追加の重複を防ぐ導線に使う。
 */
data class RegisteredPlace(
  val placeId: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
) {
  /** 表示名。名前（ユーザー名／Google名／住所のいずれか）が無ければ座標。 */
  val displayName: String
    get() = name ?: String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
