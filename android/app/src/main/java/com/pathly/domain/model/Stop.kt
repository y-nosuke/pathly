package com.pathly.domain.model

import java.util.Date

/**
 * 立ち寄り（＝訪問）。「どの経路で・どの [Place] に・いつからいつまで」いたかを表す。
 * places と gps_tracks を結ぶ関連。詳細は docs/designs/places-and-stops.md を参照。
 */
data class Stop(
  val id: Long,
  val place: Place,
  val trackId: Long,
  val arrivalTime: Date,
  val departureTime: Date,
  /** その訪問のメモ（stop 単位。null/空=メモ無し）。場所名（place 単位）とは別。 */
  val note: String? = null,
) {
  /** 滞在時間（ミリ秒）。 */
  val durationMillis: Long get() = departureTime.time - arrivalTime.time

  /** 滞在時間（分・切り捨て）。 */
  val durationMinutes: Int get() = (durationMillis / 1000 / 60).toInt()
}
