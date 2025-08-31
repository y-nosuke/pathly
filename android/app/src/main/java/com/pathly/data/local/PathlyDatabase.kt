package com.pathly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pathly.data.local.converter.DateConverter
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity

@Database(
    entities = [GpsTrackEntity::class, GpsPointEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class PathlyDatabase : RoomDatabase() {

    abstract fun gpsTrackDao(): GpsTrackDao
    abstract fun gpsPointDao(): GpsPointDao

    companion object {
        const val DATABASE_NAME = "pathly_database"

        @Volatile
        private var INSTANCE: PathlyDatabase? = null

        fun getInstance(context: Context): PathlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PathlyDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}