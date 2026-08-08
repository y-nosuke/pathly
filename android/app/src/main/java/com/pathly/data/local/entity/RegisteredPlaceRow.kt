package com.pathly.data.local.entity

/**
 * [com.pathly.data.local.dao.PlaceDao.observeRegisteredPlaces] の射影（地図マーカー用の最小情報）。
 * 名前は places.name → google_places.name → google_places.address の順にフォールバック（COALESCE）。
 */
data class RegisteredPlaceRow(
  val placeId: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
)
