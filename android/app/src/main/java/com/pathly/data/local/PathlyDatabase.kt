package com.pathly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pathly.data.local.converter.DateConverter
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.migration.DatabaseMigrations
import com.pathly.util.EncryptionHelper
import com.pathly.util.Logger

@Database(
  entities = [GpsTrackEntity::class, GpsPointEntity::class],
  version = 1,
  exportSchema = false,
)
@TypeConverters(DateConverter::class)
abstract class PathlyDatabase : RoomDatabase() {

  abstract fun gpsTrackDao(): GpsTrackDao
  abstract fun gpsPointDao(): GpsPointDao

  companion object {
    const val DATABASE_NAME = "pathly_database"
    private val logger = Logger("PathlyDatabase")

    @Volatile
    private var INSTANCE: PathlyDatabase? = null

    fun getInstance(context: Context): PathlyDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = createDatabase(context)
        INSTANCE = instance
        instance
      }
    }

    private fun createDatabase(context: Context): PathlyDatabase {
      logger.i("Creating PathlyDatabase instance")

      val encryptionHelper = EncryptionHelper(context)

      // 暗号化の健全性をチェック
      if (!encryptionHelper.verifyEncryptionIntegrity()) {
        logger.w("Encryption integrity check failed, continuing without encryption")
      }

      return Room.databaseBuilder(
        context.applicationContext,
        PathlyDatabase::class.java,
        DATABASE_NAME,
      )
        // マイグレーション設定
        .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
        // マイグレーション失敗時の処理（全テーブル削除）
        .fallbackToDestructiveMigration(true)
        // データベースコールバック設定
        .addCallback(object : RoomDatabase.Callback() {
          override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            logger.i("Database created successfully")

            // 初期化時のインデックス作成
            createOptimalIndexes(db)
          }

          override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            logger.d("Database opened, version: ${db.version}")

            try {
              // WALモードを有効化（パフォーマンス向上）
              val walCursor = db.query("PRAGMA journal_mode = WAL")
              walCursor.close()

              // 外部キー制約を有効化
              val fkCursor = db.query("PRAGMA foreign_keys = ON")
              fkCursor.close()

              logger.d("Database pragmas configured successfully")
            } catch (e: Exception) {
              logger.w("Failed to configure database pragmas, continuing without them", e)
            }

            // データベース統計を取得
            logDatabaseStats(db)
          }
        })
        .build()
    }

    /**
     * パフォーマンス最適化のためのインデックス作成
     */
    private fun createOptimalIndexes(db: SupportSQLiteDatabase) {
      try {
        // GPS軌跡用インデックス
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_gps_tracks_created_at ON gps_tracks(created_at DESC)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_gps_tracks_is_active ON gps_tracks(is_active)",
        )

        // GPS座標用インデックス
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_gps_points_track_id_timestamp ON gps_points(track_id, timestamp)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_gps_points_location ON gps_points(latitude, longitude)",
        )

        logger.i("Optimal indexes created successfully")
      } catch (e: Exception) {
        logger.e("Failed to create optimal indexes", e)
      }
    }

    /**
     * データベース統計情報をログ出力
     */
    private fun logDatabaseStats(db: SupportSQLiteDatabase) {
      try {
        val cursor = db.query("SELECT COUNT(*) FROM gps_tracks")
        if (cursor.moveToFirst()) {
          val trackCount = cursor.getInt(0)
          logger.i("Database stats: $trackCount tracks")
        }
        cursor.close()
      } catch (e: Exception) {
        logger.e("Failed to log database stats", e)
      }
    }

    /**
     * データベースの手動削除（テスト用）
     */
    fun deleteDatabase(context: Context): Boolean {
      return try {
        context.deleteDatabase(DATABASE_NAME)
        INSTANCE = null
        logger.i("Database deleted successfully")
        true
      } catch (e: Exception) {
        logger.e("Failed to delete database", e)
        false
      }
    }

    /**
     * データベース接続のクリーンアップ
     */
    fun closeDatabase() {
      synchronized(this) {
        INSTANCE?.close()
        INSTANCE = null
        logger.i("Database connection closed")
      }
    }

    /**
     * データベースの健全性チェック
     */
    suspend fun performIntegrityCheck(context: Context): Boolean {
      return try {
        val database = getInstance(context)
        // 簡単な健全性チェックとして、データベースへの接続をテスト
        val trackDao = database.gpsTrackDao()
        trackDao.getTrackCount() // データベースアクセステスト
        logger.i("Database integrity check: PASSED")
        true
      } catch (e: Exception) {
        logger.e("Database integrity check failed", e)
        false
      }
    }
  }
}
