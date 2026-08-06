package com.pathly.presentation.places

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import java.text.Collator
import java.util.Locale

/** 行きたい登録の絞り込み（1軸）。訪問状況とは独立。 */
enum class WishlistFilter(val chipLabel: String) {
  ANY("行きたい"),
  WISHLISTED("行きたい"),
  NOT_WISHLISTED("行きたい以外"),
}

/** 訪問状況の絞り込み（1軸）。行きたい絞り込みとは独立。 */
enum class VisitedFilter(val chipLabel: String) {
  ANY("訪問"),
  VISITED("訪問済み"),
  UNVISITED("未訪問"),
}

/** 一覧の並べ替え軸。既定の向き（降順=大きい/新しいが先）も持つ。 */
enum class PlaceSort(val label: String, val defaultDescending: Boolean) {
  REGISTERED("登録順", true),
  VISITED("訪問順", true),
  VISIT_COUNT("訪問回数順", true),
  PRIORITY("優先度順", true),
  NAME("名前順", false),
  UPDATED("更新順", true),
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
  // 絞り込みは2軸独立（行きたい / 訪問状況）。それぞれ3状態。
  val wishlistFilter: WishlistFilter = WishlistFilter.ANY,
  val visitedFilter: VisitedFilter = VisitedFilter.ANY,
  val sort: PlaceSort = PlaceSort.REGISTERED,
  val sortDescending: Boolean = PlaceSort.REGISTERED.defaultDescending,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val search: SearchState = SearchState(),
  // 削除のたびに増やすワンショット通知。一覧側がこれを監視して取り消しスナックバーを出す。
  val undoDeleteToken: Int = 0,
  // 直近に削除した場所の表示名（スナックバー文言用）。
  val undoDeleteName: String? = null,
) {
  /** 絞り込みを一切かけていないか（「すべて」チップの選択表示に使う）。 */
  val noFilter: Boolean
    get() = wishlistFilter == WishlistFilter.ANY && visitedFilter == VisitedFilter.ANY

  /** 現在の絞り込み・並べ替えを適用した一覧。 */
  val visibleItems: List<PlaceListItem>
    get() {
      val filtered = items.filter { item ->
        val wishlistOk = when (wishlistFilter) {
          WishlistFilter.ANY -> true
          WishlistFilter.WISHLISTED -> item.isWishlisted
          WishlistFilter.NOT_WISHLISTED -> !item.isWishlisted
        }
        val visitedOk = when (visitedFilter) {
          VisitedFilter.ANY -> true
          VisitedFilter.VISITED -> item.isVisited
          VisitedFilter.UNVISITED -> !item.isVisited
        }
        wishlistOk && visitedOk
      }

      // 各軸は昇順の比較器で定義し、降順ならまとめて反転する。
      // 元の items は登録が新しい順なので、同値のときはその並び（＝安定ソート）を保つ。
      val ascending: Comparator<PlaceListItem> = when (sort) {
        PlaceSort.REGISTERED -> compareBy { it.place.createdAt }
        PlaceSort.UPDATED -> compareBy { it.place.updatedAt }
        // 実訪問の記録が無いもの（未訪問・手動で訪問済みにしただけ）は null で、昇順で先頭・降順で末尾。
        PlaceSort.VISITED -> compareBy(nullsFirst()) { it.visitRecencyAt }
        PlaceSort.VISIT_COUNT -> compareBy { it.visitCount }
        // 行きたい未登録は value 無し（-1）扱いで最下位。
        PlaceSort.PRIORITY -> compareBy { it.priority?.value ?: -1 }
        PlaceSort.NAME -> {
          val collator = Collator.getInstance(Locale.JAPANESE)
          compareBy(collator) { it.displayName }
        }
      }
      return filtered.sortedWith(if (sortDescending) ascending.reversed() else ascending)
    }
}
