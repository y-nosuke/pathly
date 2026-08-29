package com.pathly.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.data.local.PathlyDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room マイグレーションの検証（MigrationTestHelper）。
 *
 * 各バージョンのスキーマは `app/schemas/` に出力（exportSchema=true）し、androidTest の
 * assets として読み込む。開始バージョンで DB を作成 → マイグレーション適用 → 生成された
 * スキーマが目標バージョンの JSON と一致するかを検証する。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    PathlyDatabase::class.java,
  )

  @Test
  fun migrate4To5_addsWishlistTable() {
    // v4 のスキーマで DB を作成し、FK 用に place を1件入れておく。
    helper.createDatabase(TEST_DB, 4).apply {
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, address, createdAt, updatedAt) " +
          "VALUES (1, 'テスト場所', 35.0, 139.0, NULL, 0, 0)",
      )
      close()
    }

    // 4→5 を適用。生成スキーマが 5.json と一致しなければここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      5,
      true,
      DatabaseMigrations.MIGRATION_4_5,
    )

    // wishlist に挿入でき、値が保持されることを確認。
    db.execSQL(
      "INSERT INTO wishlist (id, placeId, priority, memo, visitedAt, createdAt, updatedAt) " +
        "VALUES (1, 1, 2, 'また行きたい', NULL, 0, 0)",
    )
    db.query("SELECT placeId, priority, memo FROM wishlist WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1L, cursor.getLong(0))
      assertEquals(2, cursor.getInt(1))
      assertEquals("また行きたい", cursor.getString(2))
    }
    db.close()
  }

  @Test
  fun migrate5To6_addsStopNoteColumn() {
    // v5 のスキーマで DB を作成し、FK 用に track と place を入れておく。
    helper.createDatabase(TEST_DB, 5).apply {
      execSQL(
        "INSERT INTO gps_tracks (id, startTime, endTime, isActive, createdAt, updatedAt) " +
          "VALUES (1, 0, NULL, 0, 0, 0)",
      )
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, address, createdAt, updatedAt) " +
          "VALUES (1, 'テスト場所', 35.0, 139.0, NULL, 0, 0)",
      )
      close()
    }

    // 5→6 を適用。生成スキーマが 6.json と一致しなければここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      6,
      true,
      DatabaseMigrations.MIGRATION_5_6,
    )

    // 追加した note 列に立ち寄りのメモを保存でき、保持されることを確認。
    db.execSQL(
      "INSERT INTO stops (id, placeId, trackId, arrivalTime, departureTime, note, createdAt) " +
        "VALUES (1, 1, 1, 0, 60000, '限定パフェが美味しかった', 0)",
    )
    db.query("SELECT note FROM stops WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("限定パフェが美味しかった", cursor.getString(0))
    }
    db.close()
  }

  @Test
  fun migrate6To7_separatesGoogleData_andMigratesMemoToNote() {
    // v6 のスキーマで DB を作成し、住所つき place・解決済み（googlePlaceId 有）・memo つき wishlist を入れる。
    helper.createDatabase(TEST_DB, 6).apply {
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, address, createdAt, updatedAt) " +
          "VALUES (1, NULL, 35.0, 139.0, '東京都千代田区', 0, 0)",
      )
      execSQL(
        "INSERT INTO place_resolutions (placeId, resolvedAt, googlePlaceId) VALUES (1, 100, 'gp-1')",
      )
      execSQL(
        "INSERT INTO wishlist (id, placeId, priority, memo, visitedAt, createdAt, updatedAt) " +
          "VALUES (1, 1, 2, 'また行きたい', NULL, 0, 0)",
      )
      close()
    }

    // 6→7 を適用。生成スキーマが 7.json と一致しなければここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      7,
      true,
      DatabaseMigrations.MIGRATION_6_7,
    )

    // wishlist.memo が places.note に移送されている。
    db.query("SELECT note FROM places WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("また行きたい", cursor.getString(0))
    }

    // 解決済みの googlePlaceId と places.address が google_places に移送されている。
    db.query("SELECT googlePlaceId, address FROM google_places WHERE placeId = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("gp-1", cursor.getString(0))
      assertEquals("東京都千代田区", cursor.getString(1))
    }

    // wishlist は priority を保持したまま（memo 列は削除済み）。
    db.query("SELECT priority FROM wishlist WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(2, cursor.getInt(0))
    }
    db.close()
  }

  @Test
  fun migrate10To11_addsTotalDistanceColumn() {
    // v10 のスキーマで DB を作成し、既存の完了済み経路を1件入れておく。
    helper.createDatabase(TEST_DB, 10).apply {
      execSQL(
        "INSERT INTO gps_tracks (id, startTime, endTime, isActive, name, isFavorite, createdAt, updatedAt) " +
          "VALUES (1, 0, 100, 0, '散歩', 1, 0, 0)",
      )
      close()
    }

    // 10→11 を適用。生成スキーマが 11.json と一致しなければここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      11,
      true,
      DatabaseMigrations.MIGRATION_10_11,
    )

    // 既存行は距離が未計算（NULL）で残り、他の列は保持される。NULL は起動時のバックフィル対象。
    db.query("SELECT name, isFavorite, totalDistanceMeters FROM gps_tracks WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("散歩", cursor.getString(0))
      assertEquals(1, cursor.getInt(1))
      assertTrue("既存経路の距離は未計算(NULL)", cursor.isNull(2))
    }

    // 新しい列に値を書ける。
    db.execSQL("UPDATE gps_tracks SET totalDistanceMeters = 1234.5 WHERE id = 1")
    db.query("SELECT totalDistanceMeters FROM gps_tracks WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1234.5, cursor.getDouble(0), 0.001)
    }
    db.close()
  }

  @Test
  fun migrate11To12_addsPlaceLocationIndex() {
    // v11 のスキーマで DB を作成し、既存の場所を1件入れておく。
    helper.createDatabase(TEST_DB, 11).apply {
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, note, source, createdAt, updatedAt) " +
          "VALUES (1, 'テスト場所', 35.0, 139.0, NULL, 'USER', 0, 0)",
      )
      close()
    }

    // 11→12 を適用。索引名が Room の生成規約とずれていればここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      12,
      true,
      DatabaseMigrations.MIGRATION_11_12,
    )

    // 既存行は保持される。
    db.query("SELECT name FROM places WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("テスト場所", cursor.getString(0))
    }

    // 座標の索引が作られている（近傍検索が全表走査にならない根拠）。
    db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'places'").use { cursor ->
      val names = buildList {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
      assertTrue("座標索引がある: $names", names.contains("index_places_latitude_longitude"))
    }
    db.close()
  }

  @Test
  fun migrate12To13_normalizesCategoryIntoMasterTable() {
    // v12 のスキーマで DB を作成し、旧形式（表示名を直接持つ）の Google データを1件入れておく。
    helper.createDatabase(TEST_DB, 12).apply {
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, note, source, createdAt, updatedAt) " +
          "VALUES (1, 'テスト場所', 35.0, 139.0, NULL, 'USER', 0, 0)",
      )
      execSQL(
        "INSERT INTO google_places (placeId, googlePlaceId, name, address, category) " +
          "VALUES (1, 'gp-1', 'テストカフェ', '東京都1-1', 'カフェ')",
      )
      close()
    }

    // 12→13 を適用。作り直した google_places の DDL が Room の生成物とずれていればここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      13,
      true,
      DatabaseMigrations.MIGRATION_12_13,
    )

    // 名前・住所・googlePlaceId は作り直しをまたいで保持される。業種は引き直すので未設定。
    db.query("SELECT googlePlaceId, name, address, categoryId FROM google_places WHERE placeId = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("gp-1", cursor.getString(0))
      assertEquals("テストカフェ", cursor.getString(1))
      assertEquals("東京都1-1", cursor.getString(2))
      assertTrue("業種は引き直すので null", cursor.isNull(3))
    }

    // マスタに業種を入れて参照できる（外部キーが張れている＝参照先が正しい）。
    db.execSQL("INSERT INTO google_place_categories (id, code, displayName) VALUES (1, 'cafe', 'カフェ')")
    db.execSQL("UPDATE google_places SET categoryId = 1 WHERE placeId = 1")
    db.query(
      "SELECT c.code, c.displayName FROM google_places g " +
        "JOIN google_place_categories c ON c.id = g.categoryId WHERE g.placeId = 1",
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("cafe", cursor.getString(0))
      assertEquals("カフェ", cursor.getString(1))
    }
    db.close()
  }

  @Test
  fun migrate13To14_movesManualVisitedOutOfWishlist() {
    // v13 のスキーマで、行きたい＋訪問済みの場所と、行きたいだけの場所を用意する。
    helper.createDatabase(TEST_DB, 13).apply {
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, note, source, createdAt, updatedAt) " +
          "VALUES (1, '訪問済みの場所', 35.0, 139.0, NULL, 'USER', 0, 0), " +
          "(2, '未訪問の場所', 35.1, 139.1, NULL, 'USER', 0, 0)",
      )
      execSQL(
        "INSERT INTO wishlist (id, placeId, priority, visitedAt, createdAt, updatedAt) " +
          "VALUES (10, 1, 2, 1700000000000, 0, 0), (11, 2, 1, NULL, 0, 0)",
      )
      close()
    }

    // 13→14 を適用。作り直した wishlist の DDL が Room の生成物とずれていればここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      14,
      true,
      DatabaseMigrations.MIGRATION_13_14,
    )

    // 印があったものだけ visited_places へ移る（日時はそのまま）。
    db.query("SELECT placeId, markedAt FROM visited_places").use { cursor ->
      assertEquals(1, cursor.count)
      assertTrue(cursor.moveToFirst())
      assertEquals(1L, cursor.getLong(0))
      assertEquals(1700000000000L, cursor.getLong(1))
    }

    // wishlist は id・優先度を保ったまま残る（訪問済みの列だけが消える）。
    db.query("SELECT id, placeId, priority FROM wishlist ORDER BY id").use { cursor ->
      assertEquals(2, cursor.count)
      assertTrue(cursor.moveToFirst())
      assertEquals(10L, cursor.getLong(0))
      assertEquals(1L, cursor.getLong(1))
      assertEquals(2, cursor.getInt(2))
    }

    // 行きたいを外しても訪問済みの印は残る（これが v14 の目的）。
    db.execSQL("DELETE FROM wishlist WHERE placeId = 1")
    db.query("SELECT COUNT(*) FROM visited_places WHERE placeId = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1, cursor.getInt(0))
    }
    db.close()
  }

  @Test
  fun migrate14To15_movesGoogleCoordinateOutOfPlaces() {
    // v14 のスキーマで、施設に紐付いた場所（座標は既に施設のものへ上書き済み）と、
    // 紐付いていない場所を用意する。
    helper.createDatabase(TEST_DB, 14).apply {
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, note, source, createdAt, updatedAt) " +
          "VALUES (1, NULL, 35.7, 139.7, NULL, 'DETECTED', 0, 0), " +
          "(2, '名前だけの場所', 35.1, 139.1, NULL, 'USER', 0, 0)",
      )
      execSQL(
        "INSERT INTO google_places (placeId, googlePlaceId, name, address, categoryId) " +
          "VALUES (1, 'gp-1', '清瀧神社', '千葉県浦安市…', NULL)",
      )
      close()
    }

    // 14→15 を適用。追加した列の DDL が Room の生成物とずれていればここで失敗する。
    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      15,
      true,
      DatabaseMigrations.MIGRATION_14_15,
    )

    // 表示位置を変えないため、いまの places の座標を google_places へ引き継ぐ。
    db.query("SELECT latitude, longitude FROM google_places WHERE placeId = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(35.7, cursor.getDouble(0), 1e-9)
      assertEquals(139.7, cursor.getDouble(1), 1e-9)
    }

    // places の座標（同定のアンカー）はそのまま。紐付いていない場所にも影響しない。
    db.query("SELECT latitude FROM places ORDER BY id").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(35.7, cursor.getDouble(0), 1e-9)
      assertTrue(cursor.moveToNext())
      assertEquals(35.1, cursor.getDouble(0), 1e-9)
    }
    db.close()
  }

  @Test
  fun migrate14To15_mergesDuplicatePlacesOfSameFacility() {
    // 同じ施設に化けた place が3つ（1つはユーザーが名前を付けている）。それぞれに立ち寄りがある。
    helper.createDatabase(TEST_DB, 14).apply {
      execSQL(
        "INSERT INTO gps_tracks (id, startTime, endTime, isActive, name, isFavorite, totalDistanceMeters, createdAt, updatedAt) " +
          "VALUES (1, 0, NULL, 0, NULL, 0, NULL, 0, 0)",
      )
      execSQL(
        "INSERT INTO places (id, name, latitude, longitude, note, source, createdAt, updatedAt) " +
          "VALUES (1, NULL, 35.0, 139.0, NULL, 'DETECTED', 0, 0), " +
          "(2, 'いつもの神社', 35.0001, 139.0, NULL, 'DETECTED', 0, 0), " +
          "(3, NULL, 35.0002, 139.0, NULL, 'DETECTED', 0, 0)",
      )
      execSQL(
        "INSERT INTO google_places (placeId, googlePlaceId, name, address, categoryId) " +
          "VALUES (1, 'gp-shrine', '清瀧神社', NULL, NULL), " +
          "(2, 'gp-shrine', '清瀧神社', NULL, NULL), " +
          "(3, 'gp-shrine', '清瀧神社', NULL, NULL)",
      )
      execSQL(
        "INSERT INTO stops (id, placeId, trackId, arrivalTime, departureTime, note, createdAt) " +
          "VALUES (1, 1, 1, 0, 1000, NULL, 0), (2, 3, 1, 2000, 3000, NULL, 0)",
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(
      TEST_DB,
      15,
      true,
      DatabaseMigrations.MIGRATION_14_15,
    )

    // ユーザーが名前を付けた place が生き残り、未編集の2つは吸収される。
    db.query("SELECT id FROM places ORDER BY id").use { cursor ->
      assertEquals(1, cursor.count)
      assertTrue(cursor.moveToFirst())
      assertEquals(2L, cursor.getLong(0))
    }
    // 立ち寄りは消えず、寄せ先を指すようになる。
    db.query("SELECT placeId FROM stops ORDER BY id").use { cursor ->
      assertEquals(2, cursor.count)
      assertTrue(cursor.moveToFirst())
      assertEquals(2L, cursor.getLong(0))
      assertTrue(cursor.moveToNext())
      assertEquals(2L, cursor.getLong(0))
    }
    // 子テーブルに孤児が残らない（CASCADE に頼らず明示的に消しているため）。
    db.query("SELECT COUNT(*) FROM google_places").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1, cursor.getInt(0))
    }
    db.close()
  }

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
