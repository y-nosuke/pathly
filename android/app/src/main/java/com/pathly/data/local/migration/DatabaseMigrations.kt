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
   * バージョン1から2へのマイグレーション
   * 例: 新しいカラムの追加、インデックスの作成など
   */
  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 1 to 2")

        // 例: gps_tracks テーブルに新しいカラムを追加
        // db.execSQL(
        //     "ALTER TABLE gps_tracks ADD COLUMN notes TEXT DEFAULT ''"
        // )

        // 例: パフォーマンス向上のためのインデックス追加
        // db.execSQL(
        //     "CREATE INDEX IF NOT EXISTS index_gps_tracks_created_at ON gps_tracks(created_at)"
        // )

        Logger.i(TAG, "Migration from version 1 to 2 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 1 to 2 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン2から3へのマイグレーション
   * 例: テーブル構造の変更、データ型の変更など
   */
  val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 2 to 3")

        // 例: 新しいテーブルの作成
        // db.execSQL("""
        //     CREATE TABLE IF NOT EXISTS stops (
        //         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        //         track_id INTEGER NOT NULL,
        //         latitude REAL NOT NULL,
        //         longitude REAL NOT NULL,
        //         name TEXT NOT NULL,
        //         arrival_time INTEGER NOT NULL,
        //         departure_time INTEGER,
        //         created_at INTEGER NOT NULL,
        //         FOREIGN KEY(track_id) REFERENCES gps_tracks(id) ON DELETE CASCADE
        //     )
        // """.trimIndent())

        // 例: 新しいテーブルのインデックス作成
        // db.execSQL(
        //     "CREATE INDEX IF NOT EXISTS index_stops_track_id ON stops(track_id)"
        // )

        Logger.i(TAG, "Migration from version 2 to 3 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 2 to 3 failed", e)
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
