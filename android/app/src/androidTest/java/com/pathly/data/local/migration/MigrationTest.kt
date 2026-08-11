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

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
