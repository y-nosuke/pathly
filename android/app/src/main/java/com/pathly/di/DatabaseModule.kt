package com.pathly.di

import android.content.Context
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun providePathlyDatabase(@ApplicationContext context: Context): PathlyDatabase {
    return PathlyDatabase.getInstance(context)
  }

  @Provides
  fun provideGpsTrackDao(database: PathlyDatabase): GpsTrackDao {
    return database.gpsTrackDao()
  }

  @Provides
  fun provideGpsPointDao(database: PathlyDatabase): GpsPointDao {
    return database.gpsPointDao()
  }
}