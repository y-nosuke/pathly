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
   * バージョン4から5へのマイグレーション。
   * 行きたい場所（計画）を保存する wishlist テーブルを追加する
   * （docs/designs/wishlist.md）。1 place につき最大1件（placeId は UNIQUE）。
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 4 to 5")

        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `wishlist` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`placeId` INTEGER NOT NULL, " +
            "`priority` INTEGER NOT NULL, " +
            "`memo` TEXT, " +
            "`visitedAt` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_wishlist_placeId` ON `wishlist` (`placeId`)",
        )

        Logger.i(TAG, "Migration from version 4 to 5 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 4 to 5 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン5から6へのマイグレーション。
   * 立ち寄り（訪問）ごとのメモを保存する stops.note 列を追加する
   * （docs/designs/places-and-stops.md）。メモは stop 単位で、場所名（place 単位）とは別。
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 5 to 6")

        db.execSQL("ALTER TABLE `stops` ADD COLUMN `note` TEXT")

        Logger.i(TAG, "Migration from version 5 to 6 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 5 to 6 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン6から7へのマイグレーション。場所データを Google 由来とユーザー入力に分離する
   * （docs/designs/place-info-enrichment.md / adr/0001）。
   *
   * - places に note を追加（wishlist.memo を移送）
   * - google_places を新設（解決済みの googlePlaceId と places.address を移送）
   * - 不要列を削除: places.address / wishlist.memo / place_resolutions.googlePlaceId
   *
   * minSdk 34（SQLite 3.35+）の `ALTER TABLE ... DROP COLUMN` を使い、列を落とす前に中身を移送する。
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 6 to 7")

        // 1. places に note を追加し、wishlist.memo を移送する（落とす前に読む）。
        db.execSQL("ALTER TABLE `places` ADD COLUMN `note` TEXT")
        db.execSQL(
          "UPDATE `places` SET `note` = " +
            "(SELECT w.`memo` FROM `wishlist` w WHERE w.`placeId` = `places`.`id`) " +
            "WHERE EXISTS " +
            "(SELECT 1 FROM `wishlist` w WHERE w.`placeId` = `places`.`id` AND w.`memo` IS NOT NULL)",
        )

        // 2. google_places を作り、解決済み（googlePlaceId 非 null）＋places.address を移送する。
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `google_places` (" +
            "`placeId` INTEGER NOT NULL, " +
            "`googlePlaceId` TEXT NOT NULL, " +
            "`name` TEXT, " +
            "`address` TEXT, " +
            "`category` TEXT, " +
            "PRIMARY KEY(`placeId`), " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
          "INSERT INTO `google_places` (`placeId`, `googlePlaceId`, `name`, `address`, `category`) " +
            "SELECT r.`placeId`, r.`googlePlaceId`, NULL, p.`address`, NULL " +
            "FROM `place_resolutions` r JOIN `places` p ON p.`id` = r.`placeId` " +
            "WHERE r.`googlePlaceId` IS NOT NULL",
        )

        // 3. 移送し終えた不要列を落とす。
        db.execSQL("ALTER TABLE `places` DROP COLUMN `address`")
        db.execSQL("ALTER TABLE `wishlist` DROP COLUMN `memo`")
        db.execSQL("ALTER TABLE `place_resolutions` DROP COLUMN `googlePlaceId`")

        Logger.i(TAG, "Migration from version 6 to 7 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 6 to 7 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン7から8へのマイグレーション。経路（お出掛け）に名前とお気に入りを持たせる
   * （docs/designs/track-list.md / adr/0003）。
   *
   * - gps_tracks.name を追加（null=未命名。一覧の見出しは名前が無ければ日付）
   * - gps_tracks.isFavorite を追加（0=非お気に入り）
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        Logger.i(TAG, "Starting migration from version 7 to 8")

        db.execSQL("ALTER TABLE `gps_tracks` ADD COLUMN `name` TEXT")
        db.execSQL("ALTER TABLE `gps_tracks` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")

        Logger.i(TAG, "Migration from version 7 to 8 completed successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Migration from version 7 to 8 failed", e)
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
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
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
