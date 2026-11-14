package com.pathly.data.local.entity

/**
 * 同じ施設の相手がいて、まとめるか別のまま残すかをまだ決めていない場所（→ adr/0037）。
 * [placeName] / [otherName] は表示名（自分で付けた名前 → Google の名前 → 住所）。どれも無ければ null。
 */
data class SameFacilityPendingRow(
  val placeId: Long,
  val placeName: String?,
  val otherPlaceId: Long,
  val otherName: String?,
)
