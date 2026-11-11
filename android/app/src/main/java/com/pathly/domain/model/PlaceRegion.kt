package com.pathly.domain.model

import java.text.Collator
import java.util.Locale

/**
 * 場所の地域（都道府県・市区町村）。場所の座標から住所を引いて付ける（→ adr/0029）。
 * [prefecture] が null なら地域不明（まだ引いていない・オフライン・海の上など）。
 */
data class PlaceRegion(val prefecture: String?, val city: String?) {
  companion object {
    val UNKNOWN = PlaceRegion(null, null)
  }
}

/**
 * 地域の絞り込みで選ぶもの。[city] が null なら都道府県まるごと、あればその市区町村だけ。
 * [prefecture] も null なら「地域不明」。
 */
data class RegionKey(val prefecture: String?, val city: String? = null) {
  fun matches(region: PlaceRegion): Boolean = when {
    prefecture == null -> region.prefecture == null
    city == null -> region.prefecture == prefecture
    else -> region.prefecture == prefecture && region.city == city
  }

  /** 選択肢に出す名前（市区町村は都道府県の下に並ぶので市区町村名だけ）。 */
  val label: String
    get() = city ?: prefecture ?: UNKNOWN_LABEL

  /** チップなど、都道府県の下に並ばない所に出す名前。同じ名前の市（府中市など）を見分けられるよう都道府県を添える。 */
  val fullLabel: String
    get() = if (city != null && prefecture != null) "$prefecture $city" else label

  companion object {
    val UNKNOWN = RegionKey(null)
    const val UNKNOWN_LABEL = "地域不明"
  }
}

/**
 * 地域の選択に当たるか。選択が空なら絞らない。[regions] のどれかが、選んだどれかに当たれば残す（OR）。
 * 経路は立ち寄った場所の地域すべて、場所は 1 つ。
 */
fun Set<RegionKey>.matchesAnyOf(regions: Iterable<PlaceRegion>): Boolean = isEmpty() || regions.any { r -> any { it.matches(r) } }

/** 地域の選択肢の 1 段目（都道府県。null は地域不明）と、その中の市区町村の件数。 */
data class RegionCount(
  val prefecture: String?,
  val count: Int,
  val cities: List<Pair<String, Int>>,
) {
  val key: RegionKey get() = RegionKey(prefecture)
}

/**
 * 地域の選択肢と件数（業種の [categoryFacetCounts] と同じ考え方）。[items] 全体に 1 件でもある地域を選択肢にし、
 * 件数は地域以外の絞り込みに合うもの（[counted] が true）だけを数える。1 件が同じ都道府県・市区町村に何度
 * 当たっても 1 と数える（経路は立ち寄りが複数あるため）。並びは都道府県が北から（コード順）、地域不明が最後。
 * 市区町村は件数の多い順。
 */
fun <T> regionCounts(
  items: List<T>,
  regionsOf: (T) -> Iterable<PlaceRegion>,
  counted: (T) -> Boolean,
): List<RegionCount> {
  val prefCounts = linkedMapOf<String?, Int>()
  val cityCounts = mutableMapOf<String, MutableMap<String, Int>>()
  items.forEach { item ->
    val add = if (counted(item)) 1 else 0
    val regions = regionsOf(item).toList()
    regions.map { it.prefecture }.toSet().forEach { p -> prefCounts[p] = (prefCounts[p] ?: 0) + add }
    regions.mapNotNull { r -> r.prefecture?.let { p -> r.city?.let { c -> p to c } } }.toSet().forEach { (p, c) ->
      val cities = cityCounts.getOrPut(p) { mutableMapOf() }
      cities[c] = (cities[c] ?: 0) + add
    }
  }
  val collator = japaneseCollator
  return prefCounts.entries
    .sortedWith(compareBy<Map.Entry<String?, Int>> { prefectureOrder(it.key) }.thenBy(collator) { it.key.orEmpty() })
    .map { (p, n) ->
      val cities = p?.let { cityCounts[it] }.orEmpty().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(collator) { it.key })
        .map { it.key to it.value }
      RegionCount(p, n, cities)
    }
}

/** 選んだ地域を、選択肢と同じ並び（都道府県の順 → 市区町村）に揃える。チップの文言を選んだ順に依らせない。 */
fun Collection<RegionKey>.sortedForDisplay(): List<RegionKey> {
  val collator = japaneseCollator
  return sortedWith(
    compareBy<RegionKey> { prefectureOrder(it.prefecture) }
      .thenBy(collator) { it.prefecture.orEmpty() }
      .thenBy { it.city != null }
      .thenBy(collator) { it.city.orEmpty() },
  )
}

/** 地域の名前の並べ替えに使う（毎回作らない。一覧の件数は描き直しのたびに数え直すため）。 */
private val japaneseCollator: Collator = Collator.getInstance(Locale.JAPANESE)

/** 都道府県の並び（全国地方公共団体コード順）。知らない名前はその後ろ、地域不明は最後。 */
private fun prefectureOrder(prefecture: String?): Int = when (prefecture) {
  null -> Int.MAX_VALUE
  else -> PREFECTURES.indexOf(prefecture).let { if (it < 0) PREFECTURES.size else it }
}

private val PREFECTURES = listOf(
  "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
  "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
  "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県",
  "三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
  "鳥取県", "島根県", "岡山県", "広島県", "山口県",
  "徳島県", "香川県", "愛媛県", "高知県",
  "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県",
)
