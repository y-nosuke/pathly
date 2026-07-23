package com.pathly.domain.model

import java.util.Date

/**
 * [StopDetector] の検出結果（幾何のみ・非永続）。一定範囲に一定時間とどまった箇所。
 *
 * 永続化された立ち寄りは [Stop]（場所 [Place] と経路の関連）で表す。
 */
data class DetectedStop(
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
