package com.pathly.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理計算のユーティリティ。距離計算はアプリ全体でここに一本化する
 * （以前は検出・補正・リポジトリ・UI に同じ haversine が5コピーあった）。
 */
object Geo {

  /** 地球半径（メートル）。球面近似で使う平均半径。 */
  const val EARTH_RADIUS_METERS = 6371000.0

  /** 2点間の大円距離（メートル）。球面近似（haversine）。 */
  fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
      sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(h), sqrt(1 - h))
  }

  /** [GpsPoint] 同士の距離（メートル）。 */
  fun distanceMeters(a: GpsPoint, b: GpsPoint): Double = distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

  /** a から b への方位角（度・0..360）。 */
  fun bearingDegrees(a: GpsPoint, b: GpsPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
  }
}
