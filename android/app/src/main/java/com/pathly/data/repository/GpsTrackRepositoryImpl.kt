package com.pathly.data.repository

import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsTrackRepositoryImpl @Inject constructor(
  private val gpsTrackDao: GpsTrackDao,
  private val gpsPointDao: GpsPointDao
) : GpsTrackRepository {

  override fun getAllTracks(): Flow<List<GpsTrack>> {
    return gpsTrackDao.getAllTracks().map { entities ->
      entities.map { entity ->
        entity.toGpsTrack()
      }
    }
  }

  override suspend fun getTrackById(trackId: Long): GpsTrack? {
    val trackEntity = gpsTrackDao.getTrackById(trackId) ?: return null
    val pointEntities = gpsPointDao.getPointsByTrackIdSync(trackId)

    return trackEntity.toGpsTrack(pointEntities.map { it.toGpsPoint() })
  }

  override suspend fun getActiveTrack(): GpsTrack? {
    val activeTrackEntity = gpsTrackDao.getActiveTrack() ?: return null
    val pointEntities = gpsPointDao.getPointsByTrackIdSync(activeTrackEntity.id)

    return activeTrackEntity.toGpsTrack(pointEntities.map { it.toGpsPoint() })
  }

  override suspend fun deleteTrack(track: GpsTrack) {
    val entity = GpsTrackEntity(
      id = track.id,
      startTime = track.startTime,
      endTime = track.endTime,
      isActive = track.isActive,
      createdAt = track.createdAt,
      updatedAt = track.updatedAt
    )
    gpsTrackDao.deleteTrack(entity)
  }

  private fun GpsTrackEntity.toGpsTrack(points: List<GpsPoint> = emptyList()): GpsTrack {
    return GpsTrack(
      id = this.id,
      startTime = this.startTime,
      endTime = this.endTime,
      isActive = this.isActive,
      points = points,
      createdAt = this.createdAt,
      updatedAt = this.updatedAt
    )
  }

  private fun GpsPointEntity.toGpsPoint(): GpsPoint {
    return GpsPoint(
      id = this.id,
      trackId = this.trackId,
      latitude = this.latitude,
      longitude = this.longitude,
      altitude = this.altitude,
      accuracy = this.accuracy,
      speed = this.speed,
      bearing = this.bearing,
      timestamp = this.timestamp,
      createdAt = this.createdAt
    )
  }
}