package com.pathly.domain.model

import java.util.Date

/**
 * 立ち寄り場所（一定範囲に一定時間とどまった箇所）。原データからの計算結果で、
 * 現状は永続化しない（非破壊）。
 */
data class Stop(
  val latitude: Double,
  val longitude: Double,
  val arrivalTime: Date,
  val departureTime: Date,
  val pointCount: Int,
) {
  /** 滞在時間（ミリ秒）。 */
  val durationMillis: Long get() = departureTime.time - arrivalTime.time

  /** 滞在時間（分・切り捨て）。 */
  val durationMinutes: Int get() = (durationMillis / 1000 / 60).toInt()
}
