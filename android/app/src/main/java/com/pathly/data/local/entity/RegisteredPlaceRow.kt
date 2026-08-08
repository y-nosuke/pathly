package com.pathly.data.local.entity

import java.util.Date

/**
 * [com.pathly.data.local.dao.PlaceDao.observeRegisteredPlaces] の射影（地図マーカー用）。
 * 名前は places.name → google_places.name → google_places.address の順にフォールバック（COALESCE）。
 * 状態（行きたい／訪問済み）を色・グリフで描き分けるため、wishlist 有無・立ち寄り件数・手動訪問日時も持つ。
 */
data class RegisteredPlaceRow(
  val placeId: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  /** 行きたい登録の件数（>0 で行きたい）。 */
  val wishlistCount: Int,
  /** この場所への立ち寄り（訪問）件数。 */
  val visitCount: Int,
  /** 手動で「訪問済み」にした日時（無ければ null）。 */
  val visitedAt: Date?,
)
