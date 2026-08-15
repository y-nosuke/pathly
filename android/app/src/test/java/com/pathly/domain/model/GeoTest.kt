package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 距離計算と、近傍検索の前段で使う矩形を検証する。
 *
 * 矩形は「円を必ず覆う」ことが要件で、狭すぎると近くの場所を取り逃がす（＝同じ場所が
 * 二重に作られる）。広すぎる分は距離判定で落ちるので安全側。
 */
class GeoTest {

  @Test
  fun `緯度1度は約111km`() {
    val meters = Geo.distanceMeters(35.0, 139.0, 36.0, 139.0)

    assertEquals(111_195.0, meters, 500.0)
  }

  @Test
  fun `同一地点の距離は0`() {
    assertEquals(0.0, Geo.distanceMeters(35.0, 139.0, 35.0, 139.0), 0.0001)
  }

  @Test
  fun `矩形は半径の円を必ず覆う`() {
    val latitude = 35.681
    val longitude = 139.767
    val radius = 30.0
    val bounds = Geo.boundsAround(latitude, longitude, radius)

    // 東西南北それぞれ、矩形の端までの距離が半径以上あること（＝円を覆う＝取り逃がしが無い）。
    // 距離計算と矩形が別のモデルだと、ここが半径をわずかに下回って境界の場所を取り逃がす。
    val northMeters = Geo.distanceMeters(latitude, longitude, bounds.maxLatitude, longitude)
    val southMeters = Geo.distanceMeters(latitude, longitude, bounds.minLatitude, longitude)
    val eastMeters = Geo.distanceMeters(latitude, longitude, latitude, bounds.maxLongitude)
    val westMeters = Geo.distanceMeters(latitude, longitude, latitude, bounds.minLongitude)
    assertTrue("北端が半径以上: $northMeters", northMeters >= radius)
    assertTrue("南端が半径以上: $southMeters", southMeters >= radius)
    assertTrue("東端が半径以上: $eastMeters", eastMeters >= radius)
    assertTrue("西端が半径以上: $westMeters", westMeters >= radius)
  }

  @Test
  fun `高緯度ほど経度の幅が広がる`() {
    val narrow = Geo.boundsAround(0.0, 139.0, 30.0)
    val wide = Geo.boundsAround(60.0, 139.0, 30.0)

    val narrowWidth = narrow.maxLongitude - narrow.minLongitude
    val wideWidth = wide.maxLongitude - wide.minLongitude
    assertTrue("緯度60度のほうが経度幅が広い", wideWidth > narrowWidth)
  }

  @Test
  fun `極付近でも経度幅が発散しない`() {
    val bounds = Geo.boundsAround(89.999, 139.0, 30.0)

    val width = bounds.maxLongitude - bounds.minLongitude
    assertTrue("有限の幅に収まる: $width", width.isFinite() && width < 360.0)
  }
}
