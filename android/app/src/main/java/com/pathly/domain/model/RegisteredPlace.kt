package com.pathly.domain.model

import java.util.Locale

/**
 * 地図に「登録済みの場所」マーカーとして出す最小情報。USER・DETECTED どちらの place も対象。
 * 立ち寄り（stops）とは別に、その地点に既知の場所があることを見せ、手動追加の重複を防ぐ導線に使う。
 *
 * マーカーは状態が分かるように色（訪問済み/未訪問）と、**業種**（[categoryGroup]）のグリフで描き分ける。
 * 判定は「場所」タブの [PlaceListItem] と揃える（[isWishlisted]=行きたい登録あり、[isVisited]=立ち寄り記録
 * ありor手動で訪問済み）。
 *
 * @property categoryCode Google の業種（`cafe` など）。未解決・業種なしは null。
 */
data class RegisteredPlace(
  val placeId: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  val isWishlisted: Boolean = false,
  val isVisited: Boolean = false,
  val categoryCode: String? = null,
  /** Google の施設情報があるか。業種の絞り込みで「未分類」と「その他」を分けるのに使う（一覧と同じ判定）。 */
  val hasGoogleInfo: Boolean = categoryCode != null,
) {
  /** マーカーのグリフを選ぶための分類。業種が無ければ [PlaceCategoryGroup.OTHER]。 */
  val categoryGroup: PlaceCategoryGroup get() = PlaceCategoryGroup.of(categoryCode)

  /** 業種の絞り込みの選択肢（一覧の [PlaceCategoryFacet.of] と同じ分け方）。 */
  val categoryFacet: PlaceCategoryFacet get() = PlaceCategoryFacet.of(hasGoogleInfo, categoryCode)

  /** 表示名。名前（ユーザー名／Google名／住所のいずれか）が無ければ座標。 */
  val displayName: String
    get() = name ?: String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

  /** マーカーのスニペット等に出す状態文言（行きたい／訪問済み／未訪問の組み合わせ）。 */
  val statusLabel: String get() = placeStatusLabel(isWishlisted, isVisited)
}

/**
 * 場所の状態文言。[RegisteredPlace] と [PlaceListItem] のどちらからも同じ文言を出すため、
 * 判定の元になる 2 つの真偽値だけを受ける。
 */
fun placeStatusLabel(isWishlisted: Boolean, isVisited: Boolean): String = when {
  isWishlisted && isVisited -> "行きたい・訪問済み"
  isWishlisted -> "行きたい・未訪問"
  isVisited -> "訪問済み"
  else -> "未訪問"
}
