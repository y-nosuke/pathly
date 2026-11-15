package com.pathly.presentation.places

import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.japaneseCollator
import com.pathly.domain.model.regionTopKeyOrder

/**
 * 場所一覧のまとまり 1 つ（→ specs/screens.md「5. 場所画面」）。
 *
 * [key] は開閉を覚えるための名前（分け方の中で一意・アプリを閉じても変わらない）。[count] は中の場所すべて
 * （[children] の中も含む）の数。[items] はこのまとまりの直下に置く場所（地域なら市区町村が分からない場所）。
 */
data class PlaceGroup(
  val key: String,
  val label: String,
  val count: Int,
  val children: List<PlaceGroup> = emptyList(),
  val items: List<PlaceListItem> = emptyList(),
)

/** 分けた一覧の 1 行（見出しか場所）。[depth] は字下げの段（いちばん外の見出しが 0）。 */
sealed interface PlacesListRow {
  val depth: Int

  /** 一覧（LazyColumn）での行の名前。場所は id、見出しはまとまりの [PlaceGroup.key]。 */
  val listKey: Any
    get() = when (this) {
      is Header -> "group:${group.key}"
      is Item -> item.place.id
    }

  data class Header(val group: PlaceGroup, override val depth: Int, val expanded: Boolean) : PlacesListRow

  data class Item(val item: PlaceListItem, override val depth: Int) : PlacesListRow
}

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

/**
 * まとまりを、一覧に並べる行にする。閉じたまとまりは見出しだけ。[expandAll] なら（名前の検索中）すべて開いて見せる。
 * 開いたまとまりの中は、下の段のまとまり → 直下の場所の順。
 */
fun flattenGroups(groups: List<PlaceGroup>, openKeys: Set<String>, expandAll: Boolean): List<PlacesListRow> {
  val rows = mutableListOf<PlacesListRow>()
  fun add(group: PlaceGroup, depth: Int) {
    val expanded = expandAll || group.key in openKeys
    rows += PlacesListRow.Header(group, depth, expanded)
    if (!expanded) return
    group.children.forEach { add(it, depth + 1) }
    group.items.forEach { rows += PlacesListRow.Item(it, depth + 1) }
  }
  groups.forEach { add(it, 0) }
  return rows
}
