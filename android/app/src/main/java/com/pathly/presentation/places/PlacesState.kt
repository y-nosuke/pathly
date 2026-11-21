package com.pathly.presentation.places

import androidx.lifecycle.SavedStateHandle
import com.pathly.R
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.RegionCount
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.categoryFacetCounts
import com.pathly.domain.model.matchesAnyOf
import com.pathly.domain.model.regionCounts
import com.pathly.presentation.common.ListMapMode
import com.pathly.presentation.common.StatsBarItem
import com.pathly.presentation.common.StatsTileTone
import com.pathly.presentation.common.getEnum
import com.pathly.presentation.common.getEnumSet
import com.pathly.presentation.common.getRegionSet
import com.pathly.presentation.common.matchesNameQuery
import com.pathly.presentation.common.putEnum
import com.pathly.presentation.common.putEnumSet
import com.pathly.presentation.common.putRegionSet
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

/**
 * 一覧の並べ替え軸。既定の向き（降順=大きい/新しいが先）と、表示のメニューに出す向きの文言（軸に合わせて
 * 「新しい順」「多い順」など。昇順・降順という言葉は使わない。履歴の TrackSort と同じ流儀）も持つ。
 */
enum class PlaceSort(
  val label: String,
  val defaultDescending: Boolean,
  val descendingLabel: String,
  val ascendingLabel: String,
) {
  REGISTERED("登録順", true, "新しい順", "古い順"),
  VISITED("訪問順", true, "新しい順", "古い順"),
  VISIT_COUNT("訪問回数順", true, "多い順", "少ない順"),

  /** 行きたいで行った回数（行きたいに入れて実際に行った回数 → adr/0036）。 */
  FULFILLED_WISH_COUNT("行きたいで行った回数順", true, "多い順", "少ない順"),
  PRIORITY("優先度順", true, "高い順", "低い順"),
  NAME("名前順", false, "ん → あ", "あ → ん"),
  UPDATED("更新順", true, "新しい順", "古い順"),
  ;

  /** 向きの文言。 */
  fun directionLabel(descending: Boolean): String = if (descending) descendingLabel else ascendingLabel
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
  // 絞り込みは4軸独立（行きたい / 訪問状況 / 業種 / 地域）。行きたい・訪問状況はそれぞれ3状態。
  val wishlistFilter: WishlistFilter = WishlistFilter.ANY,
  val visitedFilter: VisitedFilter = VisitedFilter.ANY,
  // 業種は複数選べて、選んだどれかに当たれば残す（OR）。空＝指定なし。
  val categoryFilter: Set<PlaceCategoryFacet> = emptySet(),
  // 地域（都道府県・市区町村）。複数選べて、選んだどれかに当たれば残す（OR）。空＝指定なし。
  val regionFilter: Set<RegionKey> = emptySet(),
  // 名前の検索語（表示名に含むものだけ残す）。空＝指定なし。ほかの絞り込みとは AND。
  val nameQuery: String = "",
  val sort: PlaceSort = PlaceSort.REGISTERED,
  val sortDescending: Boolean = PlaceSort.REGISTERED.defaultDescending,
  // 一覧の分け方と、いまの分け方で開いているまとまり（[PlaceGroup.key]）。絞り込みとは独立した見せ方の設定で、
  // アプリを閉じても残す（設定に保存する。「初期状態に戻す」では変えない）。
  val grouping: PlaceGrouping = PlaceGrouping.NONE,
  val openGroups: Set<String> = emptySet(),
  // 一覧で見るか地図で見るか。絞り込みと同じく、アプリを使っている間は保つ。
  val viewMode: ListMapMode = ListMapMode.LIST,
  // 地図表示で、地図に重ねた見出し・検索欄・絞り込みをたたんでいるか。たたんでも小さな帯は残る。
  val controlsCollapsed: Boolean = false,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val search: SearchState = SearchState(),
  // 登録・紐付けのたびに増やす合図。地図で追加・検索して追加の画面は、これが進んだら一覧へ戻る。
  // 削除の取り消し・近接確認の確認待ちは場所の編集の部品（PlaceEditActions）が持つ。
  val registerToken: Int = 0,
  // 下のスナックバーに出す一言（「「名前」を登録しました」/「保存しました」など）。出したら消化して null に戻す。
  val registerMessage: String? = null,
) {
  /** 絞り込み（名前の検索を含む）を一切かけていないか。 */
  val noFilter: Boolean
    get() = wishlistFilter == WishlistFilter.ANY &&
      visitedFilter == VisitedFilter.ANY &&
      categoryFilter.isEmpty() &&
      regionFilter.isEmpty() &&
      nameQuery.isBlank()

  /** 絞り込み・名前の検索・並べ替え（軸と向き）が、開いたときのまま（初期状態）か。違えば「初期状態に戻す」を出す。 */
  val isInitialListOptions: Boolean
    get() = noFilter && sort == PlacesState().sort && sortDescending == PlacesState().sortDescending

  /** かけている絞り込みの数（名前の検索・行きたい・訪問状況・業種・地域）。たたんだ帯に出す。 */
  val activeFilterCount: Int
    get() = listOf(
      nameQuery.isNotBlank(),
      wishlistFilter != WishlistFilter.ANY,
      visitedFilter != VisitedFilter.ANY,
      categoryFilter.isNotEmpty(),
      regionFilter.isNotEmpty(),
    ).count { it }

  /**
   * 一覧を先頭へ戻す条件（絞り込み・並べ替え・先頭の場所）をまとめた値。これが変わったときだけ先頭へ戻す。
   * 画面の状態として保存するので文字列にする。
   */
  val scrollResetKey: String
    get() = listOf(
      wishlistFilter.name,
      visitedFilter.name,
      categoryFilter.map { it.name }.sorted().joinToString(","),
      regionFilter.map { "${it.countryCode}/${it.prefecture}/${it.city}" }.sorted().joinToString(","),
      nameQuery.trim(),
      sort.name,
      sortDescending.toString(),
      grouping.name,
      // 分けているときは先頭の行（見出し）で見る。閉じたまとまりに場所が足されただけでは動かさない。
      listRows.firstOrNull()?.listKey?.toString().orEmpty(),
    ).joinToString("|")

  /**
   * 業種ごとの場所の件数（絞り込みの選択肢に添える）。業種以外の絞り込み（行きたい・訪問・地域・名前の検索）に合う
   * 場所で数え、全体にはあるのに絞り込みで 0 件になった業種は 0 で残る（→ [categoryFacetCounts]）。
   */
  val categoryCounts: Map<PlaceCategoryFacet, Int>
    get() = categoryFacetCounts(items, { listOf(PlaceCategoryFacet.of(it.place)) }) { matchesBase(it) && regionOk(it) }

  /** 地域ごとの場所の件数。地域以外の絞り込み（行きたい・訪問・業種・名前の検索）に合う場所で数える（→ [regionCounts]）。 */
  val regionCounts: List<RegionCount>
    get() = regionCounts(items, { listOf(it.region) }) { matchesBase(it) && categoryOk(it) }

  /** 現在の絞り込み・並べ替えを適用した一覧。 */
  val visibleItems: List<PlaceListItem>
    get() {
      val filtered = items.filter { matchesBase(it) && categoryOk(it) && regionOk(it) }

      // 各軸は昇順の比較器で定義し、降順ならまとめて反転する。
      // 元の items は登録が新しい順なので、同値のときはその並び（＝安定ソート）を保つ。
      val ascending: Comparator<PlaceListItem> = when (sort) {
        PlaceSort.REGISTERED -> compareBy { it.place.createdAt }

        PlaceSort.UPDATED -> compareBy { it.place.updatedAt }

        // 実訪問の記録が無いもの（未訪問・手動で訪問済みにしただけ）は null で、昇順で先頭・降順で末尾。
        PlaceSort.VISITED -> compareBy(nullsFirst()) { it.visitRecencyAt }

        PlaceSort.VISIT_COUNT -> compareBy { it.visitCount }

        PlaceSort.FULFILLED_WISH_COUNT -> compareBy { it.fulfilledWishCount }

        // 行きたい未登録は value 無し（-1）扱いで最下位。
        PlaceSort.PRIORITY -> compareBy { it.priority?.value ?: -1 }

        PlaceSort.NAME -> {
          val collator = Collator.getInstance(Locale.JAPANESE)
          compareBy(collator) { it.displayName }
        }
      }
      return filtered.sortedWith(if (sortDescending) ascending.reversed() else ascending)
    }

  /**
   * 一覧に並べる行。分けないなら場所だけ、分けるならまとまりの見出しと、開いたまとまりの中の場所。
   * 名前の検索中は、当たった場所が見えるよう（残ったまとまりはどれも当たった場所を含む）すべて開いて見せる。
   * 覚えている開閉（[openGroups]）は変えないので、検索語を消すと元に戻る。
   */
  val listRows: List<PlacesListRow>
    get() {
      val items = visibleItems
      if (grouping == PlaceGrouping.NONE) return items.map { PlacesListRow.Item(it, depth = 0) }
      return flattenGroups(groupPlaces(items, grouping), openGroups, expandAll = nameQuery.isNotBlank())
    }

  /** 一覧の先頭の統計。一覧と同じく、絞り込み・名前の検索の後に残った場所で数える。 */
  val statistics: PlacesStatistics
    get() = PlacesStatistics.of(visibleItems)

  /** 統計のカードに出す、帯に無い数字。数える範囲は [statistics] と同じ。 */
  val statsDetails: PlacesStatsDetails
    get() = PlacesStatsDetails.of(visibleItems)

  private fun categoryOk(item: PlaceListItem): Boolean = categoryFilter.isEmpty() || PlaceCategoryFacet.of(item.place) in categoryFilter

  private fun regionOk(item: PlaceListItem): Boolean = regionFilter.matchesAnyOf(listOf(item.region))

  /** 業種・地域以外の絞り込み（行きたい・訪問・名前の検索）に合うか。 */
  private fun matchesBase(item: PlaceListItem): Boolean {
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
    return wishlistOk && visitedOk && matchesNameQuery(item.displayName, nameQuery)
  }
}

/**
 * 場所タブの統計（場所の数・訪問済み・行きたいの達成）。
 *
 * 行きたいは行くと自動で外れて履歴に移る（→ adr/0036）ので、「いま行きたいのうち訪問済み」では行けたものほど
 * 数えから抜けてしまう。そこで達成は**行きたいにした場所**（いま行きたい、または行きたいで行ったことがある）を分母に、
 * そのうち**行きたいで行ったことがある**場所を分子に数える。行った後に入れ直した場所は両方に入る。
 */
data class PlacesStatistics(
  val placeCount: Int,
  /** 訪問済みの数（判定は一覧と同じ → [PlaceListItem.isVisited]）。 */
  val visitedCount: Int,
  /** 行きたいにした場所の数。 */
  val wishedCount: Int,
  /** そのうち行きたいで行ったことがある場所の数。 */
  val fulfilledCount: Int,
) {
  /**
   * 行きたいの達成の割合（%・切り捨て）。行きたいにした場所が無ければ null（割合は出さない）。
   * 四捨五入だと、まだ残っているのに 100% と出ることがある（199 / 200）ので切り捨てる。
   */
  val fulfilledPercent: Int?
    get() = if (wishedCount == 0) null else fulfilledCount * 100 / wishedCount

  companion object {
    fun of(items: List<PlaceListItem>): PlacesStatistics = PlacesStatistics(
      placeCount = items.size,
      visitedCount = items.count { it.isVisited },
      wishedCount = items.count { it.isWishlisted || it.fulfilledWishCount > 0 },
      fulfilledCount = items.count { it.fulfilledWishCount > 0 },
    )
  }
}

/**
 * 統計の帯のタイル。場所「42 か所」・訪問済み「30」・行けた「2 / 7」（行きたいにした場所のうち行きたいで行けた数。
 * タイルに収まるよう名前を縮め、旗と紅で行きたいのことと分かるようにする。割合は広げたカードで出す）。
 * 絞り込み中は、絞った数字を全体と取り違えないよう「42 / 176 か所」と全体の数を添える（履歴の帯と同じ）。
 */
internal fun placesStatsBarItems(statistics: PlacesStatistics, totalCount: Int, isFiltered: Boolean): List<StatsBarItem> {
  val count = if (isFiltered) "${statistics.placeCount} / $totalCount か所" else "${statistics.placeCount} か所"
  return listOf(
    StatsBarItem(icon = R.drawable.ic_place, value = count, label = "場所", tone = StatsTileTone.PRIMARY),
    StatsBarItem(icon = R.drawable.ic_check_circle, value = "${statistics.visitedCount}", label = "訪問済み", tone = StatsTileTone.GREEN),
    StatsBarItem(
      icon = R.drawable.ic_flag,
      value = "${statistics.fulfilledCount} / ${statistics.wishedCount}",
      label = "行けた",
      tone = StatsTileTone.WISHLIST,
    ),
  )
}

private const val KEY_WISHLIST = "places.wishlistFilter"
private const val KEY_VISITED = "places.visitedFilter"
private const val KEY_CATEGORY = "places.categoryFilter"
private const val KEY_REGION = "places.regionFilter"
private const val KEY_NAME_QUERY = "places.nameQuery"
private const val KEY_SORT = "places.sort"
private const val KEY_SORT_DESCENDING = "places.sortDescending"
private const val KEY_VIEW_MODE = "places.viewMode"
private const val KEY_CONTROLS_COLLAPSED = "places.controlsCollapsed"

/** [handle] に保存した絞り込み・並べ替えを反映した状態。保存が無ければ既定のまま。 */
fun PlacesState.withListOptionsFrom(handle: SavedStateHandle): PlacesState {
  val sort = handle.getEnum(KEY_SORT, sort)
  return copy(
    wishlistFilter = handle.getEnum(KEY_WISHLIST, wishlistFilter),
    visitedFilter = handle.getEnum(KEY_VISITED, visitedFilter),
    categoryFilter = handle.getEnumSet<PlaceCategoryFacet>(KEY_CATEGORY),
    regionFilter = handle.getRegionSet(KEY_REGION),
    nameQuery = handle.get<String>(KEY_NAME_QUERY).orEmpty(),
    sort = sort,
    sortDescending = handle.get<Boolean>(KEY_SORT_DESCENDING) ?: sort.defaultDescending,
    viewMode = handle.getEnum(KEY_VIEW_MODE, viewMode),
    controlsCollapsed = handle.get<Boolean>(KEY_CONTROLS_COLLAPSED) ?: controlsCollapsed,
  )
}

/** 絞り込み・並べ替えを [handle] に保存する（アプリが裏で落とされても戻せるように）。 */
fun PlacesState.saveListOptionsTo(handle: SavedStateHandle) {
  handle.putEnum(KEY_WISHLIST, wishlistFilter)
  handle.putEnum(KEY_VISITED, visitedFilter)
  handle.putEnumSet(KEY_CATEGORY, categoryFilter)
  handle.putRegionSet(KEY_REGION, regionFilter)
  handle[KEY_NAME_QUERY] = nameQuery
  handle.putEnum(KEY_SORT, sort)
  handle[KEY_SORT_DESCENDING] = sortDescending
  handle.putEnum(KEY_VIEW_MODE, viewMode)
  handle[KEY_CONTROLS_COLLAPSED] = controlsCollapsed
}
