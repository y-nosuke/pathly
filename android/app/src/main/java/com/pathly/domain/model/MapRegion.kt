package com.pathly.domain.model

/**
 * 地名で探して地図を移す先の地域（記録画面の「地域へ移動」）。地図はこの [bounds] が収まるように移し、
 * 中心（[latitude], [longitude]）に名前の目印を出す。場所に付ける地域（[PlaceRegion]）とは別物。
 */
data class MapRegion(
  val bounds: GeoBounds,
  val latitude: Double,
  val longitude: Double,
)
