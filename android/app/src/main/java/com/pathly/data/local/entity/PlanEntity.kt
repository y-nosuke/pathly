package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 計画（→ adr/0050）。行（[PlanItemEntity]）を日ごとに並べる入れ物。
 *
 * - [startDate] は開始日（エポック日＝1970-01-01 からの日数。時刻なし）。null は「日付なし」。
 * - [dayCount] は 1 以上（1 は日帰り）。開始日が無くても持つ。
 */
@Entity(tableName = "plans")
data class PlanEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val note: String? = null,
  val startDate: Long? = null,
  val dayCount: Int = 1,
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
