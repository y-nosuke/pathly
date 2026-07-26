package com.pathly.domain.model

import java.util.Date
import java.util.Locale

/**
 * 行きたい場所1件。場所そのもの（[place]）＋計画情報（優先度・メモ・訪問済み）。
 * 詳細は docs/designs/wishlist.md を参照。
 */
data class WishlistItem(
  val id: Long,
  val place: Place,
  val priority: Priority,
  val memo: String?,
  val visitedAt: Date?,
  val createdAt: Date,
  val updatedAt: Date,
) {
  /** 訪問済みか（[visitedAt] が入っていれば済み）。 */
  val isVisited: Boolean get() = visitedAt != null

  /** 一覧表示用の名前。未命名なら住所→座標の順でフォールバックする。 */
  val displayName: String
    get() = place.name
      ?: place.address
      ?: String.format(Locale.US, "%.5f, %.5f", place.latitude, place.longitude)
}
