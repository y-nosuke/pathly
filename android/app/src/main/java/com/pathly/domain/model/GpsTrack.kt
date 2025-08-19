package com.pathly.domain.model

import java.util.Date

data class GpsTrack(
  val id: Long,
  val startTime: Date,
  val endTime: Date?,
  val isActive: Boolean,
  val points: List<GpsPoint> = emptyList(),
  val createdAt: Date,
  val updatedAt: Date
)