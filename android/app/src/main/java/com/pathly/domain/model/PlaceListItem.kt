package com.pathly.domain.model

import java.util.Date
import java.util.Locale

/**
 * 「場所」タブの一覧1件。場所（[place]）に、行きたい登録の情報（あれば）と立ち寄り件数を添えたもの。
 * [wishlistId] が null なら行きたい未登録。詳細は docs/designs/wishlist.md を参照。
 */
data class PlaceListItem(
  val place: Place,
  val wishlistId: Long?,
  val priority: Priority?,
  val memo: String?,
  val visitedAt: Date?,
  /** この場所への立ち寄り（訪問）件数。 */
  val visitCount: Int,
) {
  /** 行きたいに登録済みか。 */
  val isWishlisted: Boolean get() = wishlistId != null

  /** 手動で訪問済みにしたか。 */
  val isManuallyVisited: Boolean get() = visitedAt != null

  /** 訪問済みか（実際に立ち寄った記録がある、または手動で訪問済みにした）。 */
  val isVisited: Boolean get() = visitCount > 0 || visitedAt != null

  /** 表示名。未命名なら住所→座標の順でフォールバックする。 */
  val displayName: String
    get() = place.name
      ?: place.address
      ?: String.format(Locale.US, "%.5f, %.5f", place.latitude, place.longitude)
}
