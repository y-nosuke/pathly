package com.pathly.di

import com.pathly.data.repository.GpsTrackRepositoryImpl
import com.pathly.data.repository.PlaceRepositoryImpl
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

  @Binds
  @Singleton
  abstract fun bindGpsTrackRepository(
    gpsTrackRepositoryImpl: GpsTrackRepositoryImpl,
  ): GpsTrackRepository

  @Binds
  @Singleton
  abstract fun bindPlaceRepository(
    placeRepositoryImpl: PlaceRepositoryImpl,
  ): PlaceRepository
}
