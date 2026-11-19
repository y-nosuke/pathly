package com.pathly.presentation.places

import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceListItem

/**
 * 場所の統計のカードに出す、帯に無い数字（→ docs/specs/screens.md の「統計のカード」）。数える場所は統計の帯と同じく
 * 絞り込み・名前の検索の後に残った場所。帯と同じ数字は [PlacesStatistics] にある。
 */
data class PlacesStatsDetails(
  /** いま行きたいにしている場所の数。 */
  val wishlistedCount: Int,
  /** 業種の内訳。多い順（同じなら絞り込みの欄の並び）。5 業種以上なら上位 3 業種と「ほかの業種」（[CategoryShare.facet] が null）。 */
  val categories: List<CategoryShare>,
) {
  companion object {
    /** 内訳に名前を出す業種の数（それより多ければ、残りを「ほかの業種」にまとめる）。 */
    const val TOP_CATEGORIES = 3

    fun of(items: List<PlaceListItem>): PlacesStatsDetails {
      val counts = items.groupingBy { PlaceCategoryFacet.of(it.place) }.eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<PlaceCategoryFacet, Int>> { it.value }.thenBy { it.key.ordinal })
        .map { CategoryShare(it.key, it.value) }
      // 「ほかの業種」が 1 業種だけになるなら、その業種の名前で出す。
      val categories = if (counts.size <= TOP_CATEGORIES + 1) {
        counts
      } else {
        counts.take(TOP_CATEGORIES) + CategoryShare(null, counts.drop(TOP_CATEGORIES).sumOf { it.count })
      }
      return PlacesStatsDetails(wishlistedCount = items.count { it.isWishlisted }, categories = categories)
    }
  }
}

/** 業種の内訳の 1 つ。[facet] が null なら「ほかの業種」（上位に入らなかった業種の合計）。 */
data class CategoryShare(val facet: PlaceCategoryFacet?, val count: Int) {
  // 業種の「その他」と並ぶので、取り違えないよう「ほかの業種」とする。
  val label: String get() = facet?.label ?: "ほかの業種"
}
