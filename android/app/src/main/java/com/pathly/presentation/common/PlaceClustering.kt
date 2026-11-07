package com.pathly.presentation.common

import com.pathly.domain.model.RegisteredPlace
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

// 登録済みの場所のマーカーを、画面上で近いものどうし 1 つにまとめる（クラスタ化）計算。
// 地図ライブラリの Clustering を使わず自前で持つ理由は adr/0028。
// 地図や Android に依存しない純粋な計算にして、ユニットテストで確かめられるようにしている。

/** 地図に 1 つのマーカーとして描くもの。1 件の場所か、近い場所のまとまり。 */
internal sealed interface PlaceMapEntry {
  data class Single(val place: RegisteredPlace) : PlaceMapEntry

  /** 近い場所のまとまり。位置はまとめた場所の座標の平均。 */
  data class Cluster(val places: List<RegisteredPlace>) : PlaceMapEntry {
    val latitude: Double = places.sumOf { it.latitude } / places.size
    val longitude: Double = places.sumOf { it.longitude } / places.size

    /** Compose の key。中身が同じなら同じマーカーとして扱う。 */
    val key: String = places.joinToString(",", prefix = "cluster:") { it.placeId.toString() }

    /** 中に「行きたい」が 1 件でもあるか。1 件ずつのマーカーと同じ旗で示す。 */
    val hasWishlisted: Boolean = places.any { it.isWishlisted }

    /**
     * まとめる上限のズーム（[MAX_CLUSTER_ZOOM]）の手前でも離れないほど近いか。
     * そうなら、タップで全部入るまで寄せると地図の最大ズームまで飛んでしまう（同じ座標なら
     * どこまで寄っても重なったまま）ので、上限のズームで止める。
     */
    val isTooCloseToSplit: Boolean get() = clusterPlaces(places, MAX_CLUSTER_ZOOM - 0.5f).size == 1
  }
}

/**
 * 画面上でこれより近い場所どうしをまとめる（dp）。丸バッジ（34dp）が重なり始める距離に
 * 少し余裕を足した値。ズームを [clusterZoomLevel] で丸めるので、実際の画面上の距離は
 * この値の 1〜1.4 倍の幅で揺れる。
 */
internal const val CLUSTER_RADIUS_DP = 44.0

/**
 * このズーム以上ではまとめない。建物の単位まで寄れば並びは読めるし、同じ座標の場所は
 * どこまで寄っても離れないので、まとまったまま開けなくなるのを防ぐ。
 */
internal const val MAX_CLUSTER_ZOOM = 18f

/**
 * まとめ直しに使うズーム。0.5 刻みに切り捨てる。
 *
 * 連続値のまま使うと、ピンチ操作の 1 フレームごとにまとめ直してマーカーを作り直すことになる。
 */
internal fun clusterZoomLevel(zoom: Float): Float = floor(zoom * 2f) / 2f

/**
 * [places] を [zoom] での画面上の距離でまとめる。
 *
 * まだどこにも入っていない場所を ID の順に起点にし、起点から [radiusDp] 以内の残りを
 * まとめる（貪欲法）。並びを ID で固定しているので、同じ入力なら同じ結果になる
 * （地図を動かしただけでまとまりが変わらない）。場所の数は多くても数百件なので O(n²) で足りる。
 *
 * 1 件しか入らなかったものは [PlaceMapEntry.Single] にする。
 */
internal fun clusterPlaces(
  places: List<RegisteredPlace>,
  zoom: Float,
  radiusDp: Double = CLUSTER_RADIUS_DP,
): List<PlaceMapEntry> {
  if (zoom >= MAX_CLUSTER_ZOOM) return places.map { PlaceMapEntry.Single(it) }

  // Google 地図の座標（Web メルカトル）。ズーム 0 で世界が 256dp 四方、1 段で 2 倍になる。
  val worldSize = 256.0 * 2.0.pow(zoom.toDouble())
  val sorted = places.sortedBy { it.placeId }
  val xs = DoubleArray(sorted.size) { (sorted[it].longitude + 180.0) / 360.0 * worldSize }
  val ys = DoubleArray(sorted.size) {
    val s = sin(sorted[it].latitude * PI / 180.0).coerceIn(-0.9999, 0.9999)
    (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * worldSize
  }
  val radius2 = radiusDp * radiusDp
  val assigned = BooleanArray(sorted.size)
  val entries = mutableListOf<PlaceMapEntry>()
  for (seed in sorted.indices) {
    if (assigned[seed]) continue
    val members = mutableListOf<RegisteredPlace>()
    for (i in seed until sorted.size) {
      if (assigned[i]) continue
      val dx = xs[i] - xs[seed]
      val dy = ys[i] - ys[seed]
      if (dx * dx + dy * dy <= radius2) {
        assigned[i] = true
        members += sorted[i]
      }
    }
    entries += if (members.size == 1) PlaceMapEntry.Single(members.single()) else PlaceMapEntry.Cluster(members)
  }
  return entries
}
