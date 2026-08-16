package com.pathly.domain.model

import java.util.Date

/**
 * 「場所」タブの一覧1件。場所（[place]）に、行きたい登録の情報（あれば）と立ち寄り件数を添えたもの。
 * [wishlistId] が null なら行きたい未登録。詳細は docs/designs/wishlist.md を参照。
 */
data class PlaceListItem(
  val place: Place,
  val wishlistId: Long?,
  val priority: Priority?,
  val visitedAt: Date?,
  /** この場所への立ち寄り（訪問）件数。 */
  val visitCount: Int,
  /** 直近の立ち寄り日時（arrivalTime の最大）。立ち寄りが無ければ null。 */
  val lastStopAt: Date? = null,
) {
  /** 行きたいに登録済みか。 */
  val isWishlisted: Boolean get() = wishlistId != null

  /** 手動で訪問済みにしたか。 */
  val isManuallyVisited: Boolean get() = visitedAt != null

  /** 訪問済みか（実際に立ち寄った記録がある、または手動で訪問済みにした）。 */
  val isVisited: Boolean get() = visitCount > 0 || visitedAt != null

  /**
   * 訪問順の並べ替え用: **実際に立ち寄った時刻**（記録ベース＝[lastStopAt]）。
   * 手動の「訪問済み」（[visitedAt]）は"記帳した時刻"で実訪問時刻とは意味が違うため含めない。
   * 記録が無い（手動のみ・未訪問）場合は null で、訪問順では時刻不明として末尾に並ぶ。
   */
  val visitRecencyAt: Date? get() = lastStopAt

  /** 場所のメモ（places.note）。 */
  val note: String? get() = place.note

  /** 表示名（自分の名前 → Google 名 → 住所 → 座標）。 */
  val displayName: String get() = place.displayName

  /** 地図の吹き出し等に出す状態文言。[RegisteredPlace] のマーカーと同じ文言にする。 */
  val statusLabel: String get() = placeStatusLabel(isWishlisted, isVisited)
}
