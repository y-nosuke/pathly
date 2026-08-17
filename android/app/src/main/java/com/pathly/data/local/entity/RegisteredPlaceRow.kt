package com.pathly.data.local.entity

import java.util.Date

/**
 * [com.pathly.data.local.dao.PlaceDao.observeRegisteredPlaces] の射影（地図マーカー用）。
 * 名前は places.name → google_places.name → google_places.address の順にフォールバック（COALESCE）。
 * 状態（行きたい／訪問済み）と業種で描き分けるため、wishlist 有無・立ち寄り件数・手動訪問日時に加え、
 * 業種の code も持つ。
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
  /** 手動で「訪問済み」にした日時（無ければ null）。実際に訪れた日時ではない。 */
  val markedVisitedAt: Date?,
  /**
   * Google の業種（[com.pathly.data.local.entity.GooglePlaceCategoryEntity.code]）。
   * マーカーのグリフを業種で出し分けるために引く。表示名は要らない（地図に文字は出さない）ので取らない。
   */
  val categoryCode: String?,
)
