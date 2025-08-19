package com.pathly.domain.model

import java.util.Date

data class GpsPoint(
  val id: Long,
  val trackId: Long,
  val latitude: Double,
  val longitude: Double,
  val altitude: Double?,
  val accuracy: Float,
  val speed: Float?,
  val bearing: Float?,
  val timestamp: Date,
  val createdAt: Date
)