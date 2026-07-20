package com.pathly.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS軌跡のノイズを補正する（非破壊）。原データは変更せず、表示・距離計算用の
 * 補正後の点列を計算で返す。
 *
 * 補正は3段階:
 *  1. 精度フィルタ … accuracy が大きい（弱い）fix を除外
 *  2. ジャンプ除外 … 直前点との速度が非現実的な点を除外
 *  3. 微小移動の間引き＋移動平均 … 停止時のドリフトとジッタを平滑化
 *
 * しきい値は実機で軌跡を見ながら調整する。
 */
object TrackSmoother {

  /** これより精度が悪い（値が大きい）点は除外する。単位: メートル。 */
  const val MAX_ACCURACY_METERS = 30f

  /** 直前の採用点からの速度がこれを超える点は GPS ジャンプとして除外する。単位: m/s（約200km/h）。 */
  const val MAX_SPEED_MPS = 55.0

  /** 直前の採用点からの移動がこれ未満なら間引く（停止時のドリフト抑制）。単位: メートル。 */
  const val MIN_STEP_METERS = 3.0

  /** 移動平均の窓サイズ（奇数）。 */
  const val SMOOTHING_WINDOW = 3

  private const val EARTH_RADIUS_METERS = 6371000.0

  /** 補正後の点列を返す。点が少ない場合はそのまま返す。 */
  fun smooth(points: List<GpsPoint>): List<GpsPoint> {
    if (points.size < 3) return points
    val filtered = filter(points)
    return movingAverage(filtered)
  }

  private fun filter(points: List<GpsPoint>): List<GpsPoint> {
    val result = mutableListOf<GpsPoint>()
    for (point in points) {
      // 精度フィルタ
      if (point.accuracy > MAX_ACCURACY_METERS) continue

      val last = result.lastOrNull()
      if (last != null) {
        val meters = distanceMeters(last, point)
        val seconds = (point.timestamp.time - last.timestamp.time) / 1000.0

        // GPS ジャンプ（非現実的な速度）を除外
        if (seconds > 0 && meters / seconds > MAX_SPEED_MPS) continue

        // 微小移動（停止時のドリフト）を間引く
        if (meters < MIN_STEP_METERS) continue
      }
      result.add(point)
    }
    // フィルタで点がほぼ消えた場合は安全策として原データを返す
    return if (result.size >= 2) result else points
  }

  private fun movingAverage(points: List<GpsPoint>): List<GpsPoint> {
    if (points.size < SMOOTHING_WINDOW) return points
    val half = SMOOTHING_WINDOW / 2
    return points.mapIndexed { index, point ->
      // 端点は平滑化せずそのまま（始点・終点をずらさない）
      if (index < half || index >= points.size - half) {
        point
      } else {
        var sumLat = 0.0
        var sumLon = 0.0
        for (j in (index - half)..(index + half)) {
          sumLat += points[j].latitude
          sumLon += points[j].longitude
        }
        point.copy(
          latitude = sumLat / SMOOTHING_WINDOW,
          longitude = sumLon / SMOOTHING_WINDOW,
        )
      }
    }
  }

  private fun distanceMeters(a: GpsPoint, b: GpsPoint): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
      sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(h), sqrt(1 - h))
  }
}
