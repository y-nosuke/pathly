package com.pathly.domain.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 補正パラメータ。既定値は [TrackSmoother] の定数。調整ツールで値を変えて
 * 見比べ、良い値が決まったら定数に反映する。
 */
data class SmoothingParams(
  val maxSpeedMps: Double = TrackSmoother.MAX_SPEED_MPS,
  val window: Int = TrackSmoother.SMOOTHING_WINDOW,
)

/**
 * GPS軌跡のノイズを補正する（非破壊）。原データは変更せず、表示・距離計算用の
 * 補正後の点列を計算で返す。
 *
 * 補正は2段階:
 *  1. ジャンプ除外 … 直前点との速度が非現実的な点を除外（明らかな外れ値）
 *  2. 精度重み付き平滑化 … 各点を近傍点の加重平均に置き換える。重みは精度の
 *     二乗の逆数（accuracy が小さい＝良い fix ほど強く効く）。ハードなしきい値を
 *     使わず、精度そのもので自動的に良い点を優先する。
 */
object TrackSmoother {

  /** 直前の採用点からの速度がこれを超える点は GPS ジャンプとして除外する。単位: m/s（約200km/h）。 */
  const val MAX_SPEED_MPS = 55.0

  /** 平滑化の窓サイズ（奇数）。大きいほど滑らかになる。 */
  const val SMOOTHING_WINDOW = 5

  private const val EARTH_RADIUS_METERS = 6371000.0

  /** 補正後の点列を返す。点が少ない場合はそのまま返す。 */
  fun smooth(points: List<GpsPoint>, params: SmoothingParams = SmoothingParams()): List<GpsPoint> {
    if (points.size < 3) return points
    val filtered = removeJumps(points, params.maxSpeedMps)
    return accuracyWeightedSmooth(filtered, params.window)
  }

  private fun removeJumps(points: List<GpsPoint>, maxSpeedMps: Double): List<GpsPoint> {
    val result = mutableListOf<GpsPoint>()
    for (point in points) {
      val last = result.lastOrNull()
      if (last != null) {
        val seconds = (point.timestamp.time - last.timestamp.time) / 1000.0
        if (seconds > 0 && distanceMeters(last, point) / seconds > maxSpeedMps) continue
      }
      result.add(point)
    }
    return if (result.size >= 2) result else points
  }

  private fun accuracyWeightedSmooth(points: List<GpsPoint>, window: Int): List<GpsPoint> {
    if (window < 3 || points.size < window) return points
    val half = window / 2
    return points.mapIndexed { index, point ->
      // 端点は平滑化せずそのまま（始点・終点をずらさない）
      if (index < half || index >= points.size - half) {
        point
      } else {
        var sumWeight = 0.0
        var sumLat = 0.0
        var sumLon = 0.0
        for (j in (index - half)..(index + half)) {
          // 精度の二乗の逆数を重みにする（+1 でゼロ除算と過大な重みを防ぐ）
          val accuracy = points[j].accuracy.toDouble()
          val weight = 1.0 / (accuracy * accuracy + 1.0)
          sumWeight += weight
          sumLat += weight * points[j].latitude
          sumLon += weight * points[j].longitude
        }
        point.copy(
          latitude = sumLat / sumWeight,
          longitude = sumLon / sumWeight,
        )
      }
    }
  }

  /** 点列の総移動距離（メートル）。 */
  fun totalDistanceMeters(points: List<GpsPoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
      total += distanceMeters(points[i - 1], points[i])
    }
    return total
  }

  /**
   * 曲がり角の合計（度）。連続する区間の進行方向の変化量を足し合わせたもの。
   * ノイズで経路がギザギザだと大きくなる（＝ジャギーさの指標）。補正で下がる。
   */
  fun totalTurningDegrees(points: List<GpsPoint>): Double {
    if (points.size < 3) return 0.0
    var total = 0.0
    for (i in 1 until points.size - 1) {
      val b1 = bearingDegrees(points[i - 1], points[i])
      val b2 = bearingDegrees(points[i], points[i + 1])
      var diff = abs(b2 - b1) % 360.0
      if (diff > 180.0) diff = 360.0 - diff
      total += diff
    }
    return total
  }

  private fun bearingDegrees(a: GpsPoint, b: GpsPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
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
