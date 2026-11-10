package com.pathly.domain.model

/**
 * 地図の範囲（緯度・経度の四角形）。地図に映っている範囲で経路を探すのに使う。
 *
 * 東経 180 度をまたぐ範囲では [west] が [east] より大きくなる（地図 SDK の LatLngBounds と同じ持ち方）。
 */
data class GeoBounds(
  val south: Double,
  val west: Double,
  val north: Double,
  val east: Double,
) {
  /** 東経 180 度をまたいでいるか。 */
  val crossesAntimeridian: Boolean
    get() = west > east

  /** 範囲の中に入るか（境界を含む）。 */
  fun contains(latitude: Double, longitude: Double): Boolean {
    if (latitude < south || latitude > north) return false
    return if (crossesAntimeridian) {
      longitude >= west || longitude <= east
    } else {
      longitude in west..east
    }
  }
}
