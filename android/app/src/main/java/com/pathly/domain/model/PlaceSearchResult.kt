package com.pathly.domain.model

/**
 * キーワード検索で選んだ場所の確定情報（fetchPlace の結果）。
 * これを元に place を登録し、`place_resolutions` に googlePlaceId を記録する。
 */
data class PlaceSearchResult(
  val googlePlaceId: String,
  val name: String?,
  val address: String?,
  val category: PlaceCategory?,
  val latitude: Double,
  val longitude: Double,
)
