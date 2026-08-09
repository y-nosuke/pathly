package com.pathly.domain.model

import java.util.Date

data class GpsTrack(
  val id: Long,
  val startTime: Date,
  val endTime: Date?,
  val isActive: Boolean,
  /** ユーザーが付けた経路名。null/空なら未命名。 */
  val name: String? = null,
  /** お気に入り登録フラグ。 */
  val isFavorite: Boolean = false,
  /** この経路の立ち寄り件数（一覧の件数表示・並べ替え用。リポジトリが集計して渡す）。 */
  val stopCount: Int = 0,
  val points: List<GpsPoint> = emptyList(),
  val createdAt: Date,
  val updatedAt: Date,
  /**
   * 保存済みの補正後点列（smoothed_points）。リポジトリが読み込んで渡す。
   * null または空なら [points] から都度計算にフォールバックする（docs/designs/gps-smoothing.md）。
   */
  val smoothedOverride: List<GpsPoint>? = null,
) {
  /** 名前が付いているか（空白のみは未命名扱い）。 */
  val hasName: Boolean
    get() = !name.isNullOrBlank()

  /**
   * ノイズ補正後の点列（非破壊・表示や距離計算に使う）。原データ [points] は保持する。
   * 保存済みがあればそれを、無ければ都度計算した結果を使う。
   */
  val smoothedPoints: List<GpsPoint>
    get() = smoothedOverride?.takeIf { it.isNotEmpty() } ?: computedSmoothedPoints

  private val computedSmoothedPoints: List<GpsPoint> by lazy { TrackSmoother.smooth(points) }

  /** 補正後の点列で計算した総移動距離。 */
  val totalDistanceMeters: Double
    get() = calculateDistance(smoothedPoints)

  private fun calculateDistance(pts: List<GpsPoint>): Double {
    if (pts.size < 2) return 0.0

    var totalDistance = 0.0
    for (i in 1 until pts.size) {
      totalDistance += Geo.distanceMeters(pts[i - 1], pts[i])
    }
    return totalDistance
  }
}
