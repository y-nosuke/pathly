package com.pathly.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pathly.util.Logger

/**
 * Roomデータベースのマイグレーション定義
 * 将来のスキーマ変更に対応するためのマイグレーション機能
 */
object DatabaseMigrations {

  private val logger = Logger("DatabaseMigrations")

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
        logger.i("Starting migration from version 1 to 2")

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

        logger.i("Migration from version 1 to 2 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 1 to 2 failed", e)
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
        logger.i("Starting migration from version 2 to 3")

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

        logger.i("Migration from version 2 to 3 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 2 to 3 failed", e)
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
        logger.i("Starting migration from version 3 to 4")

        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `place_resolutions` (" +
            "`placeId` INTEGER NOT NULL, " +
            "`resolvedAt` INTEGER NOT NULL, " +
            "`googlePlaceId` TEXT, " +
            "PRIMARY KEY(`placeId`), " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        logger.i("Migration from version 3 to 4 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 3 to 4 failed", e)
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
        logger.i("Starting migration from version 4 to 5")

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

        logger.i("Migration from version 4 to 5 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 4 to 5 failed", e)
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
        logger.i("Starting migration from version 5 to 6")

        db.execSQL("ALTER TABLE `stops` ADD COLUMN `note` TEXT")

        logger.i("Migration from version 5 to 6 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 5 to 6 failed", e)
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
        logger.i("Starting migration from version 6 to 7")

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

        logger.i("Migration from version 6 to 7 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 6 to 7 failed", e)
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
        logger.i("Starting migration from version 7 to 8")

        db.execSQL("ALTER TABLE `gps_tracks` ADD COLUMN `name` TEXT")
        db.execSQL("ALTER TABLE `gps_tracks` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")

        logger.i("Migration from version 7 to 8 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 7 to 8 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン8から9へのマイグレーション。GPS 点に Location 由来の付随情報を保存する列を追加する
   * （docs/designs/gps-capture.md / adr/0004）。記録時にしか取れない情報を取り逃さないため。
   *
   * すべて追加列（既存行は NULL / 0）。DDL は Room がエンティティから生成するものと一致させること。
   */
  val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        logger.i("Starting migration from version 8 to 9")

        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `provider` TEXT")
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `verticalAccuracyMeters` REAL")
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `speedAccuracyMetersPerSecond` REAL")
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `bearingAccuracyDegrees` REAL")
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `mslAltitudeMeters` REAL")
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `mslAltitudeAccuracyMeters` REAL")
        db.execSQL(
          "ALTER TABLE `gps_points` ADD COLUMN `elapsedRealtimeNanos` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `isMock` INTEGER NOT NULL DEFAULT 0")
        // extras（Bundle）を JSON 文字列化して保存する列（バイナリではなくテキスト）。
        db.execSQL("ALTER TABLE `gps_points` ADD COLUMN `extrasJson` TEXT")

        logger.i("Migration from version 8 to 9 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 8 to 9 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン9から10へのマイグレーション。場所（places）に由来（source）列を追加する
   * （docs/designs/places-and-stops.md / adr/0005）。自動回収の対象判定に使う。
   *
   * 既存行は安全側の 'USER'（自動では消さない）でバックフィルする。以後、自動検出が作る場所は
   * 'DETECTED'、ユーザーが作る場所は 'USER' を明示的に入れる。DDL は Room の生成物と一致させること。
   */
  val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        logger.i("Starting migration from version 9 to 10")

        db.execSQL("ALTER TABLE `places` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'USER'")

        logger.i("Migration from version 9 to 10 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 9 to 10 failed", e)
        throw e
      }
    }
  }

  /**
   * v10 → v11: gps_tracks に総移動距離（メートル）を持たせる。
   *
   * 一覧の距離表示・距離順の並べ替えのために、毎回全経路の全GPS点を読み込んで
   * 平滑化し直していたのをやめ、確定時に計算した値を持つ。既存の経路は NULL
   * （未計算）で入り、初回起動時のバックフィルで埋める。
   */
  val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        logger.i("Starting migration from version 10 to 11")

        db.execSQL("ALTER TABLE `gps_tracks` ADD COLUMN `totalDistanceMeters` REAL")

        logger.i("Migration from version 10 to 11 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 10 to 11 failed", e)
        throw e
      }
    }
  }

  /**
   * v11 → v12: places の座標に索引を張る。
   *
   * 同一場所の判定（30m）と近接確認（50m）が全表走査になっていた。記録中は位置バッチごと、
   * 滞在中は毎回引かれるため、場所が増えるほど重くなる。索引名は Room が生成する規約
   * （index_<テーブル>_<列>...）に合わせること。ずれるとスキーマ検証で落ちる。
   */
  val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        logger.i("Starting migration from version 11 to 12")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_places_latitude_longitude` ON `places` (`latitude`, `longitude`)")

        logger.i("Migration from version 11 to 12 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 11 to 12 failed", e)
        throw e
      }
    }
  }

  /**
   * v12 → v13: Google のカテゴリ（業種）をマスタに正規化する。
   *
   * これまでは表示名（「カフェ」）を `google_places.category` に直接持っていた。同じ業種の場所の数だけ
   * 同じ文字列が重複するうえ、表示名はロケール依存なので、これを手掛かりに地図のアイコンを出し分けると
   * 言語設定で壊れる。**機械可読な primaryType（`cafe`）** を正とする `google_place_categories` を
   * 新設し、`google_places` はその id を参照する形にする。
   *
   * 既存行の `category` は表示名しか無く、対応する code を後から復元できない（「カフェ」から `cafe` を
   * 引き当てるのは推測になる）。そのため列ごと落とし、**業種は Google から引き直して埋めた**
   * （一度きりのバックフィルを流したうえで、その処理は削除済み。以後この経路で埋まる場所は無い）。
   *
   * 外部キーを増やすので `google_places` は作り直す。DDL は Room がエンティティから生成するものと
   * 一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        logger.i("Starting migration from version 12 to 13")

        // 1. 業種のマスタ。id はサロゲートで、業種の同一性は code の UNIQUE 索引が担保する。
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `google_place_categories` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`code` TEXT NOT NULL, " +
            "`displayName` TEXT)",
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_google_place_categories_code` " +
            "ON `google_place_categories` (`code`)",
        )

        // 2. google_places を作り直す（category を落とし、categoryId の外部キーを足す）。
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `google_places_new` (" +
            "`placeId` INTEGER NOT NULL, " +
            "`googlePlaceId` TEXT NOT NULL, " +
            "`name` TEXT, " +
            "`address` TEXT, " +
            "`categoryId` INTEGER, " +
            "PRIMARY KEY(`placeId`), " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`categoryId`) REFERENCES `google_place_categories`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        // 業種は引き直すので categoryId は NULL で入れる（旧 category は捨てる）。
        db.execSQL(
          "INSERT INTO `google_places_new` (`placeId`, `googlePlaceId`, `name`, `address`, `categoryId`) " +
            "SELECT `placeId`, `googlePlaceId`, `name`, `address`, NULL FROM `google_places`",
        )
        db.execSQL("DROP TABLE `google_places`")
        db.execSQL("ALTER TABLE `google_places_new` RENAME TO `google_places`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_google_places_categoryId` ON `google_places` (`categoryId`)")

        logger.i("Migration from version 12 to 13 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 12 to 13 failed", e)
        throw e
      }
    }
  }

  /**
   * バージョン13から14へのマイグレーション。
   * 手動の「訪問済み」を wishlist から visited_places に切り出す（adr/0020）。
   *
   * 訪問済みは行きたいとは別の軸なのに、wishlist の行に `visitedAt` として乗っていたため、
   * 行きたいに入れないと訪問済みにできず、行きたいを外すと訪問済みも消えていた。
   * 行の存在＝訪問済みにし、列名も `markedAt`（＝印を付けた日時。実際に訪れた日時ではない）に改める。
   *
   * 既存の印は移送する。wishlist からは列を落とすだけなので、優先度・登録日時はそのまま残る。
   *
   * DDL は Room がエンティティから生成するものと一致させること（起動時のスキーマ検証を通すため）。
   */
  val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
      try {
        logger.i("Starting migration from version 13 to 14")

        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `visited_places` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`placeId` INTEGER NOT NULL, " +
            "`markedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_visited_places_placeId` ON `visited_places` (`placeId`)",
        )
        // 既にある手動の印だけを移す（visitedAt が NULL の行は未訪問なので作らない）。
        db.execSQL(
          "INSERT INTO `visited_places` (`placeId`, `markedAt`) " +
            "SELECT `placeId`, `visitedAt` FROM `wishlist` WHERE `visitedAt` IS NOT NULL",
        )

        // wishlist から visitedAt を落とす（SQLite は列削除ができないので作り直す）。
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `wishlist_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`placeId` INTEGER NOT NULL, " +
            "`priority` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
          "INSERT INTO `wishlist_new` (`id`, `placeId`, `priority`, `createdAt`, `updatedAt`) " +
            "SELECT `id`, `placeId`, `priority`, `createdAt`, `updatedAt` FROM `wishlist`",
        )
        db.execSQL("DROP TABLE `wishlist`")
        db.execSQL("ALTER TABLE `wishlist_new` RENAME TO `wishlist`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wishlist_placeId` ON `wishlist` (`placeId`)")

        logger.i("Migration from version 13 to 14 completed successfully")
      } catch (e: Exception) {
        logger.e("Migration from version 13 to 14 failed", e)
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
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    // 将来のマイグレーションをここに追加
  )

  /**
   * マイグレーション履歴をログに記録
   */
  fun logMigrationHistory() {
    logger.i("Available database migrations:")
    ALL_MIGRATIONS.forEach { migration ->
      logger.i("- Migration ${migration.startVersion} -> ${migration.endVersion}")
    }
  }

  /**
   * データベースバージョン確認
   */
  fun validateDatabaseVersion(currentVersion: Int, expectedVersion: Int): Boolean {
    val isValid = currentVersion == expectedVersion
    logger.i("Database version validation: current=$currentVersion, expected=$expectedVersion, valid=$isValid")
    return isValid
  }

  /**
   * マイグレーション失敗時の復旧処理
   */
  fun handleMigrationFailure(fromVersion: Int, toVersion: Int, error: Throwable) {
    logger.e("Migration failed from version $fromVersion to $toVersion", error)

    // マイグレーション失敗時の追加処理をここに実装
    // 例：エラー報告、バックアップ復元など
  }

  /**
   * 開発用：データベーススキーマの破壊的再構築
   * 本番環境では使用禁止
   */
  fun performDestructiveMigration() {
    logger.w("DESTRUCTIVE MIGRATION - This will delete all data!")
    // この機能は fallbackToDestructiveMigration() で自動的に処理される
  }
}
