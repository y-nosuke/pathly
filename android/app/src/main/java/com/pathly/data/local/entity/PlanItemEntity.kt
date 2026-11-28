package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 計画の行（→ adr/0050）。[placeId] があれば場所の行、null なら予定の行（内容は [title]）。
 *
 * - [dayIndex] は 1〜計画の日数、[position] は日の中の順番（0 から）。
 * - [plannedTime] は着く時刻（0 時からの分）、[stayMinutes] は滞在の目安（分）。どちらも任意。
 * - 計画を消すと行も消える。場所を消すと、その場所の行も消える（どちらも CASCADE）。
 */
@Entity(
  tableName = "plan_items",
  foreignKeys = [
    ForeignKey(
      entity = PlanEntity::class,
      parentColumns = ["id"],
      childColumns = ["planId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["planId"]), Index(value = ["placeId"])],
)
data class PlanItemEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val planId: Long,
  val dayIndex: Int,
  val position: Int,
  val placeId: Long? = null,
  val title: String? = null,
  val note: String? = null,
  val plannedTime: Int? = null,
  val stayMinutes: Int? = null,
)
