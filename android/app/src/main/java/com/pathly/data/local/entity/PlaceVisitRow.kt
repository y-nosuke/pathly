package com.pathly.data.local.entity

import java.util.Date

/**
 * ある場所への訪問1件（stops × gps_tracks の JOIN 射影）。
 * 「場所→関連経路の一覧」に使う。詳細は docs/designs/wishlist.md を参照。
 */
data class PlaceVisitRow(
  val trackId: Long,
  val trackStartTime: Date,
  val arrivalTime: Date,
  val departureTime: Date,
  /** その訪問（stop）のメモ。null/空=メモ無し。 */
  val note: String?,
)
