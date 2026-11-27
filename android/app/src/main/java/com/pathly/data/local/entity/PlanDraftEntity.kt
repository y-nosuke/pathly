package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 計画の下書き（保存していない編集の途中 → docs/specs/plans.md の「保存と下書き」）。
 *
 * - [planId] は保存済みの計画（null は一度も保存していない新しい計画）。1 つの計画に下書きは 1 つ（UNIQUE）。
 *   計画を消すと下書きも消える（CASCADE）。
 * - [json] は計画と行の中身（`PlanDraftCodec`）。行は場所を id で指すだけなので、読むときに場所が消えていれば落とす。
 */
@Entity(
  tableName = "plan_drafts",
  foreignKeys = [
    ForeignKey(
      entity = PlanEntity::class,
      parentColumns = ["id"],
      childColumns = ["planId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["planId"], unique = true)],
)
data class PlanDraftEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val planId: Long? = null,
  val json: String,
  val updatedAt: Date = Date(),
)
