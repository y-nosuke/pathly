package com.pathly.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pathly.util.Logger

/**
 * Roomデータベースのマイグレーション定義
 * 将来のスキーマ変更に対応するためのマイグレーション機能
 */
object DatabaseMigrations {

  private const val TAG = "DatabaseMigrations"

  /**
   * バージョン1から2へのマイグレーション。
   * 立ち寄り場所の永続化のため places / stops テーブルを追加する
   * （docs/designs/places-and-stops.md）。
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 1 to 2")

        // places（場所そのもの・経路と独立）
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `places` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT, " +
            "`latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, " +
            "`address` TEXT, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL)",
        )

        // stops（立ち寄り＝places と gps_tracks の関連）
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `stops` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`placeId` INTEGER NOT NULL, " +
            "`trackId` INTEGER NOT NULL, " +
            "`arrivalTime` INTEGER NOT NULL, " +
            "`departureTime` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`trackId`) REFERENCES `gps_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stops_placeId` ON `stops` (`placeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stops_trackId` ON `stops` (`trackId`)")

        Logger.i(TAG, "Migration from version 1 to 2 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 1 to 2 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン2から3へのマイグレーション。
   * 補正後（スムージング済み）の点列を保存する smoothed_points テーブルを追加する
   * （docs/designs/gps-smoothing.md）。
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 2 to 3")

        // smoothed_points（補正後の点列・gps_tracks に従属）
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `smoothed_points` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`trackId` INTEGER NOT NULL, " +
            "`seq` INTEGER NOT NULL, " +
            "`latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, " +
            "`sourcePointId` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`trackId`) REFERENCES `gps_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_smoothed_points_trackId` ON `smoothed_points` (`trackId`)",
        )

        Logger.i(TAG, "Migration from version 2 to 3 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 2 to 3 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン3から4へのマイグレーション。
   * Google Places の解決ログ place_resolutions テーブルを追加する
   * （docs/designs/places-and-stops.md）。
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 3 to 4")

        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `place_resolutions` (" +
            "`placeId` INTEGER NOT NULL, " +
            "`resolvedAt` INTEGER NOT NULL, " +
            "`googlePlaceId` TEXT, " +
            "PRIMARY KEY(`placeId`), " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        Logger.i(TAG, "Migration from version 3 to 4 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 3 to 4 failed", e)
        throw e
      }
    }
  }

  /**
   * 現在利用可能な全てのマイグレーション
   */
  val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    // 将来のマイグレーションをここに追加
  )

  /**
   * マイグレーション履歴をログに記録
   */
  fun logMigrationHistory() {
    Logger.i(TAG, "Available database migrations:")
    ALL_MIGRATIONS.forEach { migration ->
      Logger.i(TAG, "- Migration ${migration.startVersion} -> ${migration.endVersion}")
    }
  }

  /**
   * データベースバージョン確認
   */
  fun validateDatabaseVersion(currentVersion: Int, expectedVersion: Int): Boolean {
    val isValid = currentVersion == expectedVersion
    Logger.i(
      TAG,
      "Database version validation: current=$currentVersion, expected=$expectedVersion, valid=$isValid",
    )
    return isValid
  }

  /**
   * マイグレーション失敗時の復旧処理
   */
  fun handleMigrationFailure(fromVersion: Int, toVersion: Int, error: Throwable) {
    Logger.e(TAG, "Migration failed from version $fromVersion to $toVersion", error)

    // マイグレーション失敗時の追加処理をここに実装
    // 例：エラー報告、バックアップ復元など
  }

  /**
   * 開発用：データベーススキーマの破壊的再構築
   * 本番環境では使用禁止
   */
  fun performDestructiveMigration() {
    Logger.w(TAG, "DESTRUCTIVE MIGRATION - This will delete all data!")
    // この機能は fallbackToDestructiveMigration() で自動的に処理される
  }
}
