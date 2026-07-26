package com.pathly.presentation.places

import com.pathly.domain.model.PlaceListItem

/** 一覧の絞り込み。 */
enum class PlacesFilter(val label: String) {
  ALL("すべて"),
  WISHLISTED("行きたい"),
  VISITED("訪問済み"),
}

data class PlacesState(
  val items: List<PlaceListItem> = emptyList(),
  val filter: PlacesFilter = PlacesFilter.ALL,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
) {
  /** 現在の絞り込みを適用した一覧。 */
  val filteredItems: List<PlaceListItem>
    get() = when (filter) {
      PlacesFilter.ALL -> items
      PlacesFilter.WISHLISTED -> items.filter { it.isWishlisted }
      PlacesFilter.VISITED -> items.filter { it.isVisited }
    }
}
