package com.pathly.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 補正パラメータ。既定値は [TrackSmoother] の定数。調整ツールで値を変えて
 * 見比べ、良い値が決まったら定数に反映する。
 */
data class SmoothingParams(
  val maxAccuracyMeters: Float = TrackSmoother.MAX_ACCURACY_METERS,
  val maxSpeedMps: Double = TrackSmoother.MAX_SPEED_MPS,
  val minStepMeters: Double = TrackSmoother.MIN_STEP_METERS,
  val smoothingWindow: Int = TrackSmoother.SMOOTHING_WINDOW,
)

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

  /** 点列の総移動距離（メートル）。 */
  fun totalDistanceMeters(points: List<GpsPoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
      total += distanceMeters(points[i - 1], points[i])
    }
    return total
  }

  /** 補正後の点列を返す。点が少ない場合はそのまま返す。 */
  fun smooth(points: List<GpsPoint>, params: SmoothingParams = SmoothingParams()): List<GpsPoint> {
    if (points.size < 3) return points
    val filtered = filter(points, params)
    return movingAverage(filtered, params.smoothingWindow)
  }

  private fun filter(points: List<GpsPoint>, params: SmoothingParams): List<GpsPoint> {
    val result = mutableListOf<GpsPoint>()
    for (point in points) {
      // 精度フィルタ
      if (point.accuracy > params.maxAccuracyMeters) continue

      val last = result.lastOrNull()
      if (last != null) {
        val meters = distanceMeters(last, point)
        val seconds = (point.timestamp.time - last.timestamp.time) / 1000.0

        // GPS ジャンプ（非現実的な速度）を除外
        if (seconds > 0 && meters / seconds > params.maxSpeedMps) continue

        // 微小移動（停止時のドリフト）を間引く
        if (meters < params.minStepMeters) continue
      }
      result.add(point)
    }
    // フィルタで点がほぼ消えた場合は安全策として原データを返す
    return if (result.size >= 2) result else points
  }

  private fun movingAverage(points: List<GpsPoint>, window: Int): List<GpsPoint> {
    if (window < 3 || points.size < window) return points
    val half = window / 2
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
        val count = half * 2 + 1
        point.copy(
          latitude = sumLat / count,
          longitude = sumLon / count,
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
