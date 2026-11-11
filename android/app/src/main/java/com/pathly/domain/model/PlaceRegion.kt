package com.pathly.domain.model

import java.text.Collator
import java.util.Locale

/**
 * 場所の地域。場所の座標から住所を引いて付ける（→ adr/0029）。
 * 日本は都道府県 → 市区町村、海外は国 → 都市で見る（海外の州は国ごとに取り方が揃わないので使わない）。
 * [countryCode] は ISO 3166-1 の 2 文字（日本は [JAPAN]）。null なら地域不明（まだ引いていない・海の上など）。
 *
 * 保存してある値から作るときは [of] で揃える。
 */
data class PlaceRegion(val countryCode: String?, val prefecture: String?, val city: String?) {

  val isUnknown: Boolean get() = countryCode == null

  /** 選択肢の 1 段目（日本は都道府県、海外は国、分からなければ地域不明）。 */
  val topKey: RegionKey
    get() = when (countryCode) {
      null -> RegionKey.UNKNOWN
      JAPAN -> RegionKey(JAPAN, prefecture)
      else -> RegionKey(countryCode)
    }

  companion object {
    const val JAPAN = "JP"
    val UNKNOWN = PlaceRegion(null, null, null)

    /**
     * 保存してある国・都道府県（州）・市区町村から地域を作る。国が無くても都道府県が日本のものなら日本とみる。
     * 日本で都道府県が分からなければ地域不明。海外は州を落とす（都市が分からなければ州などを都市の代わりにする。
     * ソウルのように市が `adminArea` にしか入らない所があるため）。
     */
    fun of(countryCode: String?, prefecture: String?, city: String?): PlaceRegion {
      val pref = prefecture?.takeIf { it.isNotBlank() }
      val country = countryCode?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
        ?: JAPAN.takeIf { pref in JAPANESE_PREFECTURES }
      val town = city?.takeIf { it.isNotBlank() }
      return when {
        country == null -> UNKNOWN
        country == JAPAN -> if (pref == null) UNKNOWN else PlaceRegion(JAPAN, pref, town)
        else -> PlaceRegion(country, null, town ?: pref)
      }
    }
  }
}

/**
 * 地域の絞り込みで選ぶもの。
 * - 日本: `RegionKey("JP", 都道府県)` はその都道府県まるごと、`city` もあればその市区町村だけ。
 * - 海外: `RegionKey(国)` はその国まるごと、`city` もあればその都市だけ。
 * - [countryCode] が null なら「地域不明」。
 */
data class RegionKey(val countryCode: String?, val prefecture: String? = null, val city: String? = null) {

  fun matches(region: PlaceRegion): Boolean = when {
    countryCode == null -> region.isUnknown
    region.countryCode != countryCode -> false
    prefecture != null && region.prefecture != prefecture -> false
    else -> city == null || region.city == city
  }

  /** 1 段目（都道府県・国・地域不明）のキー。 */
  val top: RegionKey get() = if (city == null) this else copy(city = null)

  /** 選択肢に出す名前（市区町村・都市は 1 段目の下に並ぶので、その名前だけ）。 */
  val label: String
    get() = city ?: topLabel

  /** チップなど、1 段目の下に並ばない所に出す名前。同じ名前の市（府中市など）を見分けられるよう 1 段目を添える。 */
  val fullLabel: String
    get() = if (city != null && countryCode != null) "$topLabel $city" else label

  private val topLabel: String
    get() = prefecture ?: countryCode?.let(::countryName) ?: UNKNOWN_LABEL

  companion object {
    val UNKNOWN = RegionKey(null)
    const val UNKNOWN_LABEL = "地域不明"
  }
}

/** 国コードの日本語名（アメリカ合衆国 など）。名前が分からなければコードのまま。 */
fun countryName(countryCode: String): String = countryNames.getOrPut(countryCode) {
  runCatching {
    Locale.Builder().setRegion(countryCode).build().getDisplayCountry(Locale.JAPANESE)
  }.getOrNull()?.takeIf { it.isNotBlank() } ?: countryCode
}

// 並べ替えのたびに名前を作り直さないよう覚えておく（国の数は高々数百）。
private val countryNames = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * 地域の選択に当たるか。選択が空なら絞らない。[regions] のどれかが、選んだどれかに当たれば残す（OR）。
 * 経路は立ち寄った場所の地域すべて、場所は 1 つ。
 */
fun Set<RegionKey>.matchesAnyOf(regions: Iterable<PlaceRegion>): Boolean = isEmpty() || regions.any { r -> any { it.matches(r) } }

/** 地域の選択肢の 1 段目（都道府県・国・地域不明）と、その中の市区町村・都市の件数。 */
data class RegionCount(
  val key: RegionKey,
  val count: Int,
  val cities: List<Pair<String, Int>>,
) {
  fun cityKey(city: String): RegionKey = key.copy(city = city)
}

/**
 * 地域の選択肢と件数（業種の [categoryFacetCounts] と同じ考え方）。[items] 全体に 1 件でもある地域を選択肢にし、
 * 件数は地域以外の絞り込みに合うもの（[counted] が true）だけを数える。1 件が同じ地域に何度当たっても 1 と数える
 * （経路は立ち寄りが複数あるため）。並びは日本の都道府県が北から（コード順）、次に海外の国（名前順）、地域不明が最後。
 * 市区町村・都市は件数の多い順。
 */
fun <T> regionCounts(
  items: List<T>,
  regionsOf: (T) -> Iterable<PlaceRegion>,
  counted: (T) -> Boolean,
): List<RegionCount> {
  val topCounts = mutableMapOf<RegionKey, Int>()
  val cityCounts = mutableMapOf<RegionKey, MutableMap<String, Int>>()
  items.forEach { item ->
    val add = if (counted(item)) 1 else 0
    val regions = regionsOf(item).toList()
    regions.map { it.topKey }.toSet().forEach { k -> topCounts[k] = (topCounts[k] ?: 0) + add }
    regions.filter { !it.isUnknown && it.city != null }.map { it.topKey to it.city!! }.toSet().forEach { (k, c) ->
      val cities = cityCounts.getOrPut(k) { mutableMapOf() }
      cities[c] = (cities[c] ?: 0) + add
    }
  }
  return topCounts.entries
    .sortedWith(compareBy(topKeyOrder) { it.key })
    .map { (k, n) ->
      val cities = cityCounts[k].orEmpty().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(japaneseCollator) { it.key })
        .map { it.key to it.value }
      RegionCount(k, n, cities)
    }
}

/** 選んだ地域を、選択肢と同じ並び（1 段目の順 → 市区町村・都市）に揃える。チップの文言を選んだ順に依らせない。 */
fun Collection<RegionKey>.sortedForDisplay(): List<RegionKey> = sortedWith(
  compareBy(topKeyOrder) { it: RegionKey -> it.top }
    .thenBy { it.city != null }
    .thenBy(japaneseCollator) { it.city.orEmpty() },
)

/** 地域の名前の並べ替えに使う（毎回作らない。一覧の件数は描き直しのたびに数え直すため）。 */
private val japaneseCollator: Collator = Collator.getInstance(Locale.JAPANESE)

/**
 * 1 段目の並び。日本の都道府県（全国地方公共団体コード順。知らない名前はその後ろ）→ 海外の国（日本語名の順）→ 地域不明。
 */
private val topKeyOrder: Comparator<RegionKey> = compareBy<RegionKey> {
  when (it.countryCode) {
    PlaceRegion.JAPAN -> JAPANESE_PREFECTURES.indexOf(it.prefecture).let { i -> if (i < 0) JAPANESE_PREFECTURES.size else i }
    null -> Int.MAX_VALUE
    else -> Int.MAX_VALUE - 1
  }
}.thenBy(japaneseCollator) { it.label }

/** 日本の都道府県（全国地方公共団体コード順）。 */
val JAPANESE_PREFECTURES = listOf(
  "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
  "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
  "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県",
  "三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
  "鳥取県", "島根県", "岡山県", "広島県", "山口県",
  "徳島県", "香川県", "愛媛県", "高知県",
  "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県",
)
