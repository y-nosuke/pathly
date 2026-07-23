package com.pathly.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS軌跡から立ち寄り場所を検出する（非破壊・計算）。
 *
 * 連続する点が [radiusMeters] の範囲にとどまり続け、その滞在時間が
 * [minDurationMs] 以上になったら1つの立ち寄りとみなす。ノイズの影響を減らすため、
 * 補正後の点列（[GpsTrack.smoothedPoints]）に対して適用することを想定している。
 * 検出結果の永続化・命名は PlaceRepository が担う（docs/designs/places-and-stops.md）。
 *
 * しきい値は実データを見ながら調整する。
 */
object StopDetector {

  /** 立ち寄りとみなす範囲の半径。単位: メートル。 */
  const val RADIUS_METERS = 50.0

  /** 立ち寄りとみなす最小滞在時間。単位: ミリ秒（3分）。 */
  const val MIN_DURATION_MS = 3 * 60 * 1000L

  private const val EARTH_RADIUS_METERS = 6371000.0

  fun detect(
    points: List<GpsPoint>,
    radiusMeters: Double = RADIUS_METERS,
    minDurationMs: Long = MIN_DURATION_MS,
  ): List<DetectedStop> {
    if (points.size < 2) return emptyList()

    val stops = mutableListOf<DetectedStop>()
    var i = 0
    while (i < points.size) {
      // points[i] を起点にクラスタを伸ばす。重心から radius 以内の間だけ広げる。
      var sumLat = points[i].latitude
      var sumLon = points[i].longitude
      var count = 1
      var j = i + 1
      while (j < points.size) {
        val centroidLat = sumLat / count
        val centroidLon = sumLon / count
        if (distanceMeters(centroidLat, centroidLon, points[j].latitude, points[j].longitude) <= radiusMeters) {
          sumLat += points[j].latitude
          sumLon += points[j].longitude
          count++
          j++
        } else {
          break
        }
      }

      val duration = points[j - 1].timestamp.time - points[i].timestamp.time
      if (count >= 2 && duration >= minDurationMs) {
        stops.add(
          DetectedStop(
            latitude = sumLat / count,
            longitude = sumLon / count,
            arrivalTime = points[i].timestamp,
            departureTime = points[j - 1].timestamp,
            pointCount = count,
          ),
        )
        i = j // クラスタの先へ飛ぶ
      } else {
        i++
      }
    }
    return stops
  }

  private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
      sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(h), sqrt(1 - h))
  }
}
