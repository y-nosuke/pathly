package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 行きたい場所（計画）。places を参照し、計画に属する動的情報（優先度・メモ・訪問済み）を持つ。
 * 1 place につき最大1件（placeId は UNIQUE）。詳細は docs/designs/wishlist.md を参照。
 */
@Entity(
  tableName = "wishlist",
  foreignKeys = [
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["placeId"], unique = true)],
)
data class WishlistEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val placeId: Long,
  /** 優先度（0=低 / 1=中 / 2=高）。ドメインの Priority に対応。 */
  val priority: Int,
  val memo: String? = null,
  /** 訪問済み日時（null=未訪問）。 */
  val visitedAt: Date? = null,
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
