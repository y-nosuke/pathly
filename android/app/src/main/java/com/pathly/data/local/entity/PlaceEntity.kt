package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 場所そのもの。経路とは独立して管理する（docs/designs/places-and-stops.md）。
 */
@Entity(tableName = "places")
data class PlaceEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String? = null,
  val latitude: Double,
  val longitude: Double,
  val address: String? = null,
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
