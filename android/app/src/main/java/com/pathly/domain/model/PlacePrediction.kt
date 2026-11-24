package com.pathly.domain.model

/**
 * キーワード検索の候補（Autocomplete の1件）。選ぶと fetchPlace で [PlaceSearchResult] にする。
 *
 * @property isRegion 地域（国・都道府県・市区町村・郵便番号など）の候補か。施設なら false。
 *   記録画面の検索窓は、地域の候補に「地域へ移動」も出す（→ docs/specs/map.md の「地図の検索窓」）。
 */
data class PlacePrediction(
  val placeId: String,
  val primaryText: String,
  val secondaryText: String,
  val isRegion: Boolean = false,
)
