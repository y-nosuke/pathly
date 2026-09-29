package com.pathly.presentation.places

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.presentation.common.getEnum
import com.pathly.presentation.common.getEnumSet
import com.pathly.presentation.common.putEnum
import com.pathly.presentation.common.putEnumSet
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
  // 絞り込みは3軸独立（行きたい / 訪問状況 / 業種）。行きたい・訪問状況はそれぞれ3状態。
  val wishlistFilter: WishlistFilter = WishlistFilter.ANY,
  val visitedFilter: VisitedFilter = VisitedFilter.ANY,
  // 業種は複数選べて、選んだどれかに当たれば残す（OR）。空＝指定なし。
  val categoryFilter: Set<PlaceCategoryFacet> = emptySet(),
  val sort: PlaceSort = PlaceSort.REGISTERED,
  val sortDescending: Boolean = PlaceSort.REGISTERED.defaultDescending,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val search: SearchState = SearchState(),
  // 削除のたびに増やすワンショット通知。一覧側がこれを監視して取り消しスナックバーを出す。
  val deleteUndo: PlaceDeleteUndo = PlaceDeleteUndo(),
  // 登録のたびに増やすワンショット通知。一覧側がこれを監視して登録スナックバーを出す（削除と統一）。
  // 空き地点の登録で近くに既存の場所が見つかったときの確認待ち（紐付け/新規をユーザーが選ぶ）。
  val nearbyRegisterPrompt: NearbyRegisterPrompt? = null,
  val registerToken: Int = 0,
  // 直近の登録結果の文言（「登録しました」/「この場所は登録済みです」）。
  val registerMessage: String? = null,
  // 場所詳細の地図に「登録済みの場所」を出すか（画面別トグル）。
  val showRegisteredPlaces: Boolean = false,
) {
  /** 絞り込みを一切かけていないか（「すべて」チップの選択表示に使う）。 */
  val noFilter: Boolean
    get() = wishlistFilter == WishlistFilter.ANY && visitedFilter == VisitedFilter.ANY && categoryFilter.isEmpty()

  /**
   * 一覧を先頭へ戻す条件（絞り込み・並べ替え・先頭の場所）をまとめた値。これが変わったときだけ先頭へ戻す。
   * 画面の状態として保存するので文字列にする。
   */
  val scrollResetKey: String
    get() = listOf(
      wishlistFilter.name,
      visitedFilter.name,
      categoryFilter.map { it.name }.sorted().joinToString(","),
      sort.name,
      sortDescending.toString(),
      visibleItems.firstOrNull()?.place?.id?.toString().orEmpty(),
    ).joinToString("|")

  /** 業種ごとの場所の件数（絞り込みの選択肢に添える）。0 件の業種は入らない。 */
  val categoryCounts: Map<PlaceCategoryFacet, Int>
    get() = items.groupingBy { PlaceCategoryFacet.of(it.place) }.eachCount()

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
        val categoryOk = categoryFilter.isEmpty() || PlaceCategoryFacet.of(item.place) in categoryFilter
        wishlistOk && visitedOk && categoryOk
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

private const val KEY_WISHLIST = "places.wishlistFilter"
private const val KEY_VISITED = "places.visitedFilter"
private const val KEY_CATEGORY = "places.categoryFilter"
private const val KEY_SORT = "places.sort"
private const val KEY_SORT_DESCENDING = "places.sortDescending"

/** [handle] に保存した絞り込み・並べ替えを反映した状態。保存が無ければ既定のまま。 */
fun PlacesState.withListOptionsFrom(handle: SavedStateHandle): PlacesState {
  val sort = handle.getEnum(KEY_SORT, sort)
  return copy(
    wishlistFilter = handle.getEnum(KEY_WISHLIST, wishlistFilter),
    visitedFilter = handle.getEnum(KEY_VISITED, visitedFilter),
    categoryFilter = handle.getEnumSet<PlaceCategoryFacet>(KEY_CATEGORY),
    sort = sort,
    sortDescending = handle.get<Boolean>(KEY_SORT_DESCENDING) ?: sort.defaultDescending,
  )
}

/** 絞り込み・並べ替えを [handle] に保存する（アプリが裏で落とされても戻せるように）。 */
fun PlacesState.saveListOptionsTo(handle: SavedStateHandle) {
  handle.putEnum(KEY_WISHLIST, wishlistFilter)
  handle.putEnum(KEY_VISITED, visitedFilter)
  handle.putEnumSet(KEY_CATEGORY, categoryFilter)
  handle.putEnum(KEY_SORT, sort)
  handle[KEY_SORT_DESCENDING] = sortDescending
}
