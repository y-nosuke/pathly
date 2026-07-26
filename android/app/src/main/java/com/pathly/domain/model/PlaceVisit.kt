package com.pathly.domain.model

import java.util.Date

/**
 * ある場所への訪問1件。「場所→関連経路の一覧」の1行。
 * その場所に立ち寄ったお出掛け（[trackId]）と、その場所での滞在時刻を持つ。
 */
data class PlaceVisit(
  val trackId: Long,
  /** お出掛け（経路）の開始日時＝一覧に出す「日付」。 */
  val outingDate: Date,
  val arrivalTime: Date,
  val departureTime: Date,
) {
  /** その場所での滞在時間（分・切り捨て）。 */
  val stayMinutes: Int get() = ((departureTime.time - arrivalTime.time) / 1000 / 60).toInt()
}
