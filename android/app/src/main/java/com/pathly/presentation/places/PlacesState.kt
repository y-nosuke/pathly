package com.pathly.presentation.places

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult

/** 一覧の絞り込み。 */
enum class PlacesFilter(val label: String) {
  ALL("すべて"),
  WISHLISTED("行きたい"),
  VISITED("訪問済み"),
}

/** キーワード検索の状態（追加＞検索して追加）。 */
data class SearchState(
  val query: String = "",
  val predictions: List<PlacePrediction> = emptyList(),
  val isSearching: Boolean = false,
  // 候補を確定して取得した結果（あれば入力フォームを表示）。
  val result: PlaceSearchResult? = null,
)

data class PlacesState(
  val items: List<PlaceListItem> = emptyList(),
  val filter: PlacesFilter = PlacesFilter.ALL,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val search: SearchState = SearchState(),
) {
  /** 現在の絞り込みを適用した一覧。 */
  val filteredItems: List<PlaceListItem>
    get() = when (filter) {
      PlacesFilter.ALL -> items
      PlacesFilter.WISHLISTED -> items.filter { it.isWishlisted }
      PlacesFilter.VISITED -> items.filter { it.isVisited }
    }
}
