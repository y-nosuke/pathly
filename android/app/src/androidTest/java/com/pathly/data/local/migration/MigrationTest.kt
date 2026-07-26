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

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
