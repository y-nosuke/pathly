package com.pathly.presentation.places

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import java.text.Collator
import java.util.Date
import java.util.Locale

/** 訪問状況の絞り込み（1軸）。行きたい絞り込みとは独立。 */
enum class VisitedFilter(val label: String) {
  ANY("指定なし"),
  VISITED("訪問済み"),
  UNVISITED("未訪問"),
}

/** 一覧の並べ替え。 */
enum class PlaceSort(val label: String) {
  REGISTERED("登録順"),
  VISITED("訪問順"),
  PRIORITY("優先度順"),
  NAME("名前順"),
  UPDATED("最近更新した順"),
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
  // 絞り込みは2軸独立: 「行きたいだけ」と「訪問状況」。
  val onlyWishlisted: Boolean = false,
  val visitedFilter: VisitedFilter = VisitedFilter.ANY,
  val sort: PlaceSort = PlaceSort.REGISTERED,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val search: SearchState = SearchState(),
  // 削除のたびに増やすワンショット通知。一覧側がこれを監視して取り消しスナックバーを出す。
  val undoDeleteToken: Int = 0,
  // 直近に削除した場所の表示名（スナックバー文言用）。
  val undoDeleteName: String? = null,
) {
  /** 現在の絞り込み・並べ替えを適用した一覧。 */
  val visibleItems: List<PlaceListItem>
    get() {
      val filtered = items.filter { item ->
        (!onlyWishlisted || item.isWishlisted) &&
          when (visitedFilter) {
            VisitedFilter.ANY -> true
            VisitedFilter.VISITED -> item.isVisited
            VisitedFilter.UNVISITED -> !item.isVisited
          }
      }
      return when (sort) {
        // 登録順（新しい順）。既定。
        PlaceSort.REGISTERED -> filtered.sortedByDescending { it.place.createdAt }
        // 最近いじった順。
        PlaceSort.UPDATED -> filtered.sortedByDescending { it.place.updatedAt }
        // 訪問順: 訪れた場所を新しい順に、未訪問は末尾。
        PlaceSort.VISITED -> filtered.sortedWith(
          compareByDescending<PlaceListItem> { it.visitRecencyAt != null }
            .thenByDescending { it.visitRecencyAt ?: Date(0) },
        )
        // 優先度順: 高→低。行きたい未登録は末尾、同順位内は登録が新しい順。
        PlaceSort.PRIORITY -> filtered.sortedWith(
          compareByDescending<PlaceListItem> { it.priority?.value ?: -1 }
            .thenByDescending { it.place.createdAt },
        )
        // 名前順: 表示名を五十音（日本語ロケール）で。
        PlaceSort.NAME -> {
          val collator = Collator.getInstance(Locale.JAPANESE)
          filtered.sortedWith(compareBy(collator) { it.displayName })
        }
      }
    }
}
