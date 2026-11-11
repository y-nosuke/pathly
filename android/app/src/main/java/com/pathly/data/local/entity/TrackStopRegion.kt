package com.pathly.data.local.entity

/**
 * 経路（trackId）に立ち寄った場所の地域。経路一覧を地域で絞り込むための集計結果（→ adr/0029）。
 * 地域をまだ引いていない・分からない場所は [countryCode] も [prefecture] も [city] も null で出る（地域不明）。
 */
data class TrackStopRegion(
  val trackId: Long,
  val countryCode: String?,
  val prefecture: String?,
  val city: String?,
)
