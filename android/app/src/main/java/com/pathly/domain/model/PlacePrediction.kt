package com.pathly.domain.model

/**
 * キーワード検索の候補（Autocomplete の1件）。選ぶと fetchPlace で [PlaceSearchResult] にする。
 */
data class PlacePrediction(
  val placeId: String,
  val primaryText: String,
  val secondaryText: String,
)
