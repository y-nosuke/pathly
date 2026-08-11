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

  /**
   * 緯度1度あたりの距離（メートル）。経度方向は緯度によって cos 倍に縮む。
   *
   * [distanceMeters] と同じ球面モデル（半径 [EARTH_RADIUS_METERS]）から導く。WGS84 の
   * 実測値（約 111,320m）を使うと距離計算とモデルがずれ、矩形が円をわずかに切ってしまう
   * （半径30mで約3cm不足＝境界ちょうどの場所を取り逃がす）。
   */
  private const val METERS_PER_DEGREE_LATITUDE = EARTH_RADIUS_METERS * Math.PI / 180.0

  /**
   * 矩形に持たせる余裕。丸め誤差で円を切らないための保険で、広い分は距離判定で落ちるため無害。
   */
  private const val BOUNDS_MARGIN = 1.01

  /** 近傍検索の前段で使う矩形範囲。 */
  data class Bounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
  )

  /**
   * ある地点から [radiusMeters] 以内を必ず含む矩形を返す。
   *
   * SQL で件数を絞ってから正確な距離判定をするための**前段**であり、矩形は円より広い
   * （角の分だけ余計に拾う）。呼び出し側で [distanceMeters] による判定を必ず行うこと。
   *
   * 経度の幅は緯度によって変わるので cos で補正する。極付近で発散しないよう cos に下限を
   * 置いている。日付変更線をまたぐ場合は矩形が破綻するが、またぐ経路は想定していない。
   */
  fun boundsAround(latitude: Double, longitude: Double, radiusMeters: Double): Bounds {
    val margin = radiusMeters * BOUNDS_MARGIN
    val deltaLatitude = margin / METERS_PER_DEGREE_LATITUDE
    val cosLatitude = kotlin.math.max(cos(Math.toRadians(latitude)), 0.01)
    val deltaLongitude = margin / (METERS_PER_DEGREE_LATITUDE * cosLatitude)
    return Bounds(
      minLatitude = latitude - deltaLatitude,
      maxLatitude = latitude + deltaLatitude,
      minLongitude = longitude - deltaLongitude,
      maxLongitude = longitude + deltaLongitude,
    )
  }

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
