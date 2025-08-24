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
) {
  val totalDistanceMeters: Double
    get() = calculateDistance()

  private fun calculateDistance(): Double {
    if (points.size < 2) return 0.0

    var totalDistance = 0.0
    for (i in 1 until points.size) {
      val prevPoint = points[i - 1]
      val currPoint = points[i]
      totalDistance += distanceBetween(
        prevPoint.latitude, prevPoint.longitude,
        currPoint.latitude, currPoint.longitude
      )
    }
    return totalDistance
  }

  private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadius * c
  }
}