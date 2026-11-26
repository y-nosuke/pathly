package com.pathly.di

import com.pathly.data.crash.AndroidExitReasonSource
import com.pathly.data.crash.ExitReasonSource
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.repository.GpsTrackRepositoryImpl
import com.pathly.data.repository.PlaceRepositoryImpl
import com.pathly.data.repository.WishlistRepositoryImpl
import com.pathly.data.settings.SettingsRepositoryImpl
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.PlaceSearchRepository
import com.pathly.domain.repository.SettingsRepository
import com.pathly.domain.repository.WishlistRepository
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

  @Binds
  @Singleton
  abstract fun bindWishlistRepository(
    wishlistRepositoryImpl: WishlistRepositoryImpl,
  ): WishlistRepository

  @Binds
  @Singleton
  abstract fun bindSettingsRepository(
    settingsRepositoryImpl: SettingsRepositoryImpl,
  ): SettingsRepository

  @Binds
  @Singleton
  abstract fun bindPlaceSearchRepository(
    placesTextSearcher: PlacesTextSearcher,
  ): PlaceSearchRepository

  @Binds
  @Singleton
  abstract fun bindExitReasonSource(
    androidExitReasonSource: AndroidExitReasonSource,
  ): ExitReasonSource
}
