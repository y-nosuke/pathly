package com.pathly.domain.model

/**
 * ある地点から [distanceMeters] だけ離れた登録済みの場所。手でまとめる相手の候補（場所の編集の
 * 「ほかの場所とまとめる」）に、近い順と距離を見せるために使う。
 */
data class PlaceWithDistance(
  val place: RegisteredPlace,
  val distanceMeters: Double,
)
