package com.pathly.domain.model

/**
 * 同じ施設と分かった相手の場所（その施設の情報を既に持っている方 → adr/0037）。
 * [name] は相手の表示名（自分で付けた名前 → Google の名前 → 住所）。どれも無ければ null。
 */
data class SameFacilityPlace(
  val placeId: Long,
  val name: String?,
)

/**
 * 同じ施設の相手がいて、まとめるか別のまま残すかをまだ決めていない場所（経路詳細の「確認待ち」）。
 * [placeName] はこの場所の表示名（無ければ null）。
 */
data class SameFacilityPending(
  val placeId: Long,
  val placeName: String?,
  val other: SameFacilityPlace,
)
