package com.pathly.domain.repository

import com.pathly.domain.model.GpsTrack
import kotlinx.coroutines.flow.Flow

interface GpsTrackRepository {
  fun getAllTracks(): Flow<List<GpsTrack>>
    fun getAllTracksWithPoints(): Flow<List<GpsTrack>>
  suspend fun getTrackById(trackId: Long): GpsTrack?
  suspend fun getActiveTrack(): GpsTrack?
  suspend fun deleteTrack(track: GpsTrack)
}