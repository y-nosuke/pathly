package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 計画に結び付いた経路（→ adr/0050。v23）。1 つの経路が結び付く計画は 1 つだけ（[trackId] が主キー）。
 * [source] は結び付き方（`PlanTrackSource` の名前: START＝記録の開始で選んだ・MANUAL＝手で）。
 * 計画か経路を消すと消える（どちらも CASCADE）。
 */
@Entity(
  tableName = "plan_tracks",
  foreignKeys = [
    ForeignKey(
      entity = GpsTrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = PlanEntity::class,
      parentColumns = ["id"],
      childColumns = ["planId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["planId"])],
)
data class PlanTrackEntity(
  @PrimaryKey
  val trackId: Long,
  val planId: Long,
  val source: String,
)

/** 経路が結び付いている計画とその名前（「経路を結び付ける」で、ほかの計画から付け替えると添えるため）。 */
data class TrackPlanRow(
  val trackId: Long,
  val planId: Long,
  val title: String,
)
