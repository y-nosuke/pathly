package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "gps_tracks")
data class GpsTrackEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val startTime: Date,
  val endTime: Date? = null,
  val isActive: Boolean = true,
  /** ユーザーが付けた経路名。null/空なら未命名（一覧では日付を見出しに使う）。 */
  val name: String? = null,
  /** お気に入り登録フラグ。 */
  val isFavorite: Boolean = false,
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
