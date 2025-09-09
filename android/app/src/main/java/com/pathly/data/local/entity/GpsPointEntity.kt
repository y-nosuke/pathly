package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
  tableName = "gps_points",
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
data class GpsPointEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val trackId: Long,
  val latitude: Double,
  val longitude: Double,
  val altitude: Double? = null,
  val accuracy: Float,
  val speed: Float? = null,
  val bearing: Float? = null,
  val timestamp: Date,
  val createdAt: Date = Date(),
)
