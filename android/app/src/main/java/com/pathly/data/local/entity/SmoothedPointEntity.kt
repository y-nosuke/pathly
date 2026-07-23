package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 補正後（スムージング済み）の点列。原データ [GpsPointEntity] は無改変で残し、
 * 補正結果はこのテーブルに併存させる（docs/designs/gps-smoothing.md）。
 *
 * 生点と一対一にはならない（ジャンプ除外でスキップされる点があり、再補正で
 * 生き残る点が変わりうる）ため、gps_points への列追加ではなく別テーブルにする。
 * [seq] はトラック内の補正後点列の順序。[sourcePointId] は由来の生点（トレース用）。
 */
@Entity(
  tableName = "smoothed_points",
  foreignKeys = [
    ForeignKey(
      entity = GpsTrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("trackId")],
)
data class SmoothedPointEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val trackId: Long,
  val seq: Int,
  val latitude: Double,
  val longitude: Double,
  val timestamp: Date,
  val sourcePointId: Long? = null,
  val createdAt: Date = Date(),
)
