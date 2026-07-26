package com.pathly.presentation.wishlist

import com.pathly.domain.model.WishlistItem

/** 一覧の絞り込み。 */
enum class WishlistFilter(val label: String) {
  ALL("すべて"),
  UNVISITED("未訪問"),
  VISITED("訪問済み"),
}

data class WishlistState(
  val items: List<WishlistItem> = emptyList(),
  val filter: WishlistFilter = WishlistFilter.ALL,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
) {
  /** 現在の絞り込みを適用した一覧。 */
  val filteredItems: List<WishlistItem>
    get() = when (filter) {
      WishlistFilter.ALL -> items
      WishlistFilter.UNVISITED -> items.filter { !it.isVisited }
      WishlistFilter.VISITED -> items.filter { it.isVisited }
    }
}
