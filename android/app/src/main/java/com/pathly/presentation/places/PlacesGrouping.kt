package com.pathly.presentation.places

import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.japaneseCollator
import com.pathly.domain.model.regionTopKeyOrder
import com.pathly.presentation.common.ListGroup
import com.pathly.presentation.common.ListGroupRow
import com.pathly.presentation.common.flattenGroups

/** 場所一覧のまとまり 1 つ（→ specs/screens.md「5. 場所画面」）。 */
typealias PlaceGroup = ListGroup<PlaceListItem>

/**
 * [items]（絞り込み・並べ替えの後の並び）を [grouping] で分ける。まとまりの中は [items] の並びのまま。
 * 場所の無いまとまりは作らない。[PlaceGrouping.NONE] なら空。
 */
fun groupPlaces(items: List<PlaceListItem>, grouping: PlaceGrouping): List<PlaceGroup> = when (grouping) {
  PlaceGrouping.NONE -> emptyList()
  PlaceGrouping.REGION -> groupByRegion(items)
  PlaceGrouping.CATEGORY -> groupByCategory(items)
}

/**
 * 都道府県 → 市区町村（海外は国 → 都市）の 2 段。都道府県は北から → 海外の国（名前順）→ 地域不明
 * （絞り込みの選択肢と同じ並び）。市区町村・都市は名前順で、場所を足しても位置が動かない。
 * 市区町村が分からない場所は、都道府県の直下（市区町村の後ろ）に置く。
 */
private fun groupByRegion(items: List<PlaceListItem>): List<PlaceGroup> {
  val collator = japaneseCollator
  return items.groupBy { it.region.topKey }.entries
    .sortedWith(compareBy(regionTopKeyOrder) { it.key })
    .map { (top, members) ->
      val cities = members.filter { it.region.city != null }.groupBy { it.region.city!! }.entries
        .sortedWith(compareBy(collator) { it.key })
        .map { (city, cityItems) ->
          PlaceGroup(key = regionGroupKey(top.copy(city = city)), label = city, count = cityItems.size, items = cityItems)
        }
      PlaceGroup(
        key = regionGroupKey(top),
        label = top.label,
        count = members.size,
        children = cities,
        items = members.filter { it.region.city == null },
      )
    }
}

/** 業種の 1 段。並びは絞り込みの欄と同じ（その他・未分類が最後）。 */
private fun groupByCategory(items: List<PlaceListItem>): List<PlaceGroup> = items.groupBy { PlaceCategoryFacet.of(it.place) }.entries
  .sortedBy { it.key.ordinal }
  .map { (facet, members) -> PlaceGroup(key = "category:${facet.name}", label = facet.label, count = members.size, items = members) }

private fun regionGroupKey(key: RegionKey): String = "region:" + listOf(key.countryCode, key.prefecture, key.city).joinToString("|") { it.orEmpty() }

/** 場所のまとまりを、一覧に並べる行にする（→ [flattenGroups]）。開いているのは [openKeys] に入ったまとまり。 */
fun flattenPlaceGroups(groups: List<PlaceGroup>, openKeys: Set<String>, expandAll: Boolean): List<ListGroupRow<PlaceListItem>> = flattenGroups(groups, itemKey = { it.place.id }, expandAll = expandAll) { it.key in openKeys }
