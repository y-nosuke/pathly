package com.pathly.domain.model

import java.util.Date
import java.util.Locale

/**
 * 「場所」タブの一覧1件。場所（[place]）に、行きたい登録の情報（あれば）を添えたもの。
 * [wishlistId] が null なら行きたい未登録。詳細は docs/designs/wishlist.md を参照。
 */
data class PlaceListItem(
  val place: Place,
  val wishlistId: Long?,
  val priority: Priority?,
  val memo: String?,
  val visitedAt: Date?,
) {
  /** 行きたいに登録済みか。 */
  val isWishlisted: Boolean get() = wishlistId != null

  /** 訪問済みか（行きたい登録があり visitedAt が入っている場合）。 */
  val isVisited: Boolean get() = visitedAt != null

  /** 表示名。未命名なら住所→座標の順でフォールバックする。 */
  val displayName: String
    get() = place.name
      ?: place.address
      ?: String.format(Locale.US, "%.5f, %.5f", place.latitude, place.longitude)
}
