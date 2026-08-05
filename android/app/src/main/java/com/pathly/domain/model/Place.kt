package com.pathly.domain.model

import java.util.Date
import java.util.Locale

/**
 * 場所そのもの（経路とは独立）。立ち寄りで検出した場所・手動追加・行きたい場所、
 * すべてここで管理する。詳細は docs/designs/place-info-enrichment.md を参照。
 *
 * 名前は「ユーザーが付けた名前（[name]）」と「Google 由来（[googleName]）」を分けて持ち、
 * 表示は [displayName] のフォールバックで解決する（基本は Google 名、上書きがあれば自分の名前）。
 *
 * @property name 自分で付けた名前。null は未命名。
 * @property googleName Google の施設名（未解決・POIに名前無しは null）。
 * @property googleAddress Google の住所。
 * @property category Google のカテゴリ（業種）。
 */
data class Place(
  val id: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  val note: String?,
  val googleName: String?,
  val googleAddress: String?,
  val category: String?,
  /** Google の place ID（Google マップで施設ページを開く参照キー）。未解決は null。 */
  val googlePlaceId: String?,
  val createdAt: Date,
  val updatedAt: Date,
) {
  /** 表示名。自分の名前 → Google 名 → 住所 → 座標 の順にフォールバックする。 */
  val displayName: String
    get() = name
      ?: googleName
      ?: googleAddress
      ?: String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
