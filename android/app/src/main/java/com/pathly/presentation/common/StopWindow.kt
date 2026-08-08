package com.pathly.presentation.common

import com.pathly.domain.model.GpsPoint
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 指定座標付近の滞在区間（到着–出発）を軌跡点から推定する。手動追加や登録済みマーカーへの紐付けで、
 * 選んだ地点の滞在時間帯を軌跡から決めるのに使う。60m 以内の点があればその時刻の最小〜最大、
 * 無ければ最寄り点の時刻（＝滞在0扱い）。点が無ければ現在時刻。
 */
fun deriveStopWindow(points: List<GpsPoint>, latitude: Double, longitude: Double): Pair<Date, Date> {
  if (points.isEmpty()) return Date() to Date()
  val near = points.filter { distanceMeters(it.latitude, it.longitude, latitude, longitude) <= 60.0 }
  if (near.isNotEmpty()) {
    val times = near.map { it.timestamp }
    return times.min() to times.max()
  }
  val nearest = points.minByOrNull { distanceMeters(it.latitude, it.longitude, latitude, longitude) }!!
  return nearest.timestamp to nearest.timestamp
}

/** 2点間の距離（メートル、Haversine）。 */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val earthRadius = 6371000.0
  val dLat = Math.toRadians(lat2 - lat1)
  val dLon = Math.toRadians(lon2 - lon1)
  val a = sin(dLat / 2) * sin(dLat / 2) +
    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
    sin(dLon / 2) * sin(dLon / 2)
  return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
