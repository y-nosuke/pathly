package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 立ち寄り（訪問）。places と gps_tracks を結ぶ関連。
 * 経路を消したら立ち寄りも消える（CASCADE）が、場所（places）は残す。
 */
@Entity(
  tableName = "stops",
  foreignKeys = [
    ForeignKey(
      entity = GpsTrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.NO_ACTION,
    ),
  ],
  indices = [Index("placeId"), Index("trackId")],
)
data class StopEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val placeId: Long,
  val trackId: Long,
  val arrivalTime: Date,
  val departureTime: Date,
  val createdAt: Date = Date(),
)
