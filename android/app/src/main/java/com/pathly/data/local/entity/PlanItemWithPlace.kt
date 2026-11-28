package com.pathly.data.local.entity

import androidx.room.Embedded

/** 計画の行に、場所の名前とアンカーの座標を添えた射影（場所の行だけ。メモの行は place の列が null）。 */
data class PlanItemWithPlace(
  @Embedded val item: PlanItemEntity,
  val placeName: String?,
  val googleName: String?,
  val googleAddress: String?,
  val placeLatitude: Double?,
  val placeLongitude: Double?,
)

/** 計画の一覧の 1 行。計画と、場所の行の数。 */
data class PlanListRow(
  @Embedded val plan: PlanEntity,
  val placeCount: Int,
)

/** 経路の始まり（計画に日付で結び付くかを見るため。点は読まない）。 */
data class TrackStartRow(
  val id: Long,
  val startTime: java.util.Date,
  val isActive: Boolean,
)

/** 経路で立ち寄った場所（経路と場所の組。同じ組は 1 つ）。 */
data class TrackVisitRow(
  val trackId: Long,
  val placeId: Long,
)

/** 計画の場所の行が指す場所（一覧の「行った N/M」を求めるため）。 */
data class PlanItemPlaceRow(
  val planId: Long,
  val placeId: Long,
)

/** 場所の名前とアンカーの座標（下書きの行を場所に結び付けるため）。 */
data class PlanPlaceRow(
  val id: Long,
  val placeName: String?,
  val googleName: String?,
  val googleAddress: String?,
  val latitude: Double,
  val longitude: Double,
)
