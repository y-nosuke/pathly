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
