package com.pathly.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.util.EncryptionHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

/**
 * データ持続性テスト - アプリ再インストール後のデータ保持をテスト
 * 受け入れ基準: アプリを再インストールしてもデータが失われない
 */
@RunWith(AndroidJUnit4::class)
class DataPersistenceTest {

  private lateinit var context: Context
  private lateinit var database: PathlyDatabase
  private lateinit var databaseFile: File
  private lateinit var encryptionHelper: EncryptionHelper

  companion object {
    private const val TEST_DB_NAME = "test_pathly_database"
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()

    // ファイルベースのデータベースを作成（永続性テストのため）
    database = Room.databaseBuilder(
      context,
      PathlyDatabase::class.java,
      TEST_DB_NAME
    )
      .allowMainThreadQueries()
      .build()

    databaseFile = context.getDatabasePath(TEST_DB_NAME)
    encryptionHelper = EncryptionHelper(context)
  }

  @After
  fun tearDown() {
    database.close()
    // テスト後のクリーンアップ
    databaseFile.delete()
    encryptionHelper.clearAllSecureData()
  }

  /**
   * 受け入れ基準テスト: アプリを再インストールしてもデータが失われない
   *
   * 注意: 実際のアプリ再インストールはテスト環境では困難なため、
   * データベースファイルの永続性とEncrypted SharedPreferencesの動作をテスト
   */
  @Test
  fun testDataPersistenceAfterAppRestart() = runBlocking {
    // Phase 1: 初期データの保存
    val originalTrack = GpsTrackEntity(
      id = 0,
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date()
    )

    val trackId = database.gpsTrackDao().insertTrack(originalTrack)
    assertTrue("データ挿入に失敗", trackId > 0)

    // GPS座標データも保存
    val gpsPoints = listOf(
      GpsPointEntity(
        id = 0,
        trackId = trackId,
        latitude = 35.6762,
        longitude = 139.6503,
        altitude = 10.0,
        accuracy = 5.0f,
        speed = 0.0f,
        bearing = 0.0f,
        timestamp = Date(System.currentTimeMillis()),
        createdAt = Date()
      ),
      GpsPointEntity(
        id = 0,
        trackId = trackId,
        latitude = 35.6763,
        longitude = 139.6504,
        altitude = 11.0,
        accuracy = 4.0f,
        speed = 1.5f,
        bearing = 45.0f,
        timestamp = Date(System.currentTimeMillis() + 30000),
        createdAt = Date()
      )
    )

    database.gpsPointDao().insertPoints(gpsPoints)

    // 暗号化データも保存
    encryptionHelper.saveSecureString("user_preference", "track_auto_start=true")
    encryptionHelper.saveSecureString("api_key", "test_secret_key_12345")

    // データベースファイルの存在確認
    assertTrue("データベースファイルが作成されていない", databaseFile.exists())
    assertTrue("データベースファイルが空", databaseFile.length() > 0)

    // データ保存確認
    val savedTracks = database.gpsTrackDao().getAllTracksSync()
    assertEquals("軌跡データが保存されていない", 1, savedTracks.size)

    val savedPoints = database.gpsPointDao().getPointsByTrackIdSync(trackId)
    assertEquals("GPS座標データが保存されていない", 2, savedPoints.size)

    // 暗号化データ確認
    val userPref = encryptionHelper.getSecureString("user_preference")
    assertEquals("暗号化データが保存されていない", "track_auto_start=true", userPref)

    // Phase 2: データベース接続を閉じる（アプリ終了をシミュレート）
    database.close()

    // Phase 3: 新しいデータベース接続を開く（アプリ再起動をシミュレート）
    database = Room.databaseBuilder(
      context,
      PathlyDatabase::class.java,
      TEST_DB_NAME
    )
      .allowMainThreadQueries()
      .build()

    // Phase 4: データの永続性確認
    val restoredTracks = database.gpsTrackDao().getAllTracksSync()
    assertEquals("再起動後に軌跡データが失われている", 1, restoredTracks.size)
    assertEquals("軌跡のアクティブ状態が保持されていない", true, restoredTracks[0].isActive)

    val restoredPoints = database.gpsPointDao().getPointsByTrackIdSync(trackId)
    assertEquals("再起動後にGPS座標データが失われている", 2, restoredPoints.size)
    assertEquals("GPS座標の緯度が保持されていない", 35.6762, restoredPoints[0].latitude, 0.0001)

    // 暗号化データの永続性確認
    val restoredUserPref = encryptionHelper.getSecureString("user_preference")
    assertEquals("再起動後に暗号化データが失われている", "track_auto_start=true", restoredUserPref)

    val restoredApiKey = encryptionHelper.getSecureString("api_key")
    assertEquals("機密データが保持されていない", "test_secret_key_12345", restoredApiKey)
  }

  /**
   * データベースバックアップとリストア機能のテスト
   */
  @Test
  fun testDatabaseBackupAndRestore() = runBlocking {
    // テストデータの作成
    val testTrack = GpsTrackEntity(
      id = 0,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      createdAt = Date(),
      updatedAt = Date()
    )

    val trackId = database.gpsTrackDao().insertTrack(testTrack)

    // バックアップファイルの作成
    val backupFile = File(context.filesDir, "pathly_backup_${System.currentTimeMillis()}.db")

    // データベースを閉じる
    database.close()

    // ファイルコピー（簡易バックアップ）
    val originalFile = databaseFile
    originalFile.copyTo(backupFile, overwrite = true)
    assertTrue("バックアップファイルが作成されていない", backupFile.exists())

    // 元のデータベースを削除（データ損失をシミュレート）
    originalFile.delete()
    assertFalse("元のデータベースが削除されていない", originalFile.exists())

    // バックアップからリストア
    backupFile.copyTo(originalFile, overwrite = true)
    assertTrue("データベースがリストアされていない", originalFile.exists())

    // 復元されたデータベースに接続
    database = Room.databaseBuilder(
      context,
      PathlyDatabase::class.java,
      TEST_DB_NAME
    )
      .allowMainThreadQueries()
      .build()

    // データが復元されているか確認
    val restoredTracks = database.gpsTrackDao().getAllTracksSync()
    assertEquals("バックアップからのデータ復元に失敗", 1, restoredTracks.size)
    assertEquals("復元されたデータの整合性に問題", false, restoredTracks[0].isActive)

    // クリーンアップ
    backupFile.delete()
  }

  /**
   * データベースの破損からの復旧テスト
   */
  @Test
  fun testDatabaseCorruptionRecovery() = runBlocking {
    // 正常なデータを保存
    val validTrack = GpsTrackEntity(
      id = 0,
      startTime = Date(),
      endTime = null,
      isActive = true,
      createdAt = Date(),
      updatedAt = Date()
    )

    database.gpsTrackDao().insertTrack(validTrack)
    database.close()

    // データベースファイルに不正なデータを書き込み（破損をシミュレート）
    databaseFile.writeText("CORRUPTED_DATA")
    assertTrue("データベースファイルが破損していない", databaseFile.exists())

    // 破損したデータベースへの接続を試行
    try {
      database = Room.databaseBuilder(
        context,
        PathlyDatabase::class.java,
        TEST_DB_NAME
      )
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .build()

      // データベースアクセスを試行（破損が検出される）
      val tracks = database.gpsTrackDao().getAllTracksSync()

      // fallbackToDestructiveMigrationにより、データベースが再作成される
      // この時点で空のデータベースになっている
      assertEquals("破損したデータベースが正常に再作成されていない", 0, tracks.size)

      // 新しいデータを保存できることを確認
      val newTrack = GpsTrackEntity(
        id = 0,
        startTime = Date(),
        endTime = null,
        isActive = true,
        createdAt = Date(),
        updatedAt = Date()
      )

      val newTrackId = database.gpsTrackDao().insertTrack(newTrack)
      assertTrue("再作成されたデータベースにデータを保存できない", newTrackId > 0)

    } catch (e: Exception) {
      // データベース破損の検出は想定される動作
      assertTrue(
        "データベース破損が適切にハンドリングされていない",
        e.message?.contains("database") == true ||
                e.message?.contains("corrupt") == true
      )
    }
  }

  /**
   * 暗号化キーの永続性テスト
   */
  @Test
  fun testEncryptionKeyPersistence() {
    // 暗号化キーを生成
    val originalPassphrase = encryptionHelper.getOrCreateDatabasePassphrase()
    assertNotNull("パスフレーズが生成されていない", originalPassphrase)
    assertTrue("パスフレーズの長さが不十分", originalPassphrase.length >= 16)

    // 新しいEncryptionHelperインスタンスを作成（アプリ再起動をシミュレート）
    val newEncryptionHelper = EncryptionHelper(context)
    val restoredPassphrase = newEncryptionHelper.getOrCreateDatabasePassphrase()

    // パスフレーズが保持されていることを確認
    assertEquals("暗号化キーが保持されていない", originalPassphrase, restoredPassphrase)

    // 暗号化機能の整合性確認
    assertTrue("暗号化システムの整合性に問題", newEncryptionHelper.verifyEncryptionIntegrity())
  }

  /**
   * 大容量データの永続性テスト
   */
  @Test
  fun testLargeDataPersistence() = runBlocking {
    val startTime = System.currentTimeMillis()

    // 大量の軌跡データを作成
    val largeTracks = mutableListOf<GpsTrackEntity>()
    for (i in 1..100) {
      largeTracks.add(
        GpsTrackEntity(
          id = 0,
          startTime = Date(startTime + (i * 60000)), // 1分間隔
          endTime = Date(startTime + (i * 60000) + 30000),
          isActive = false,
          createdAt = Date(startTime + (i * 60000)),
          updatedAt = Date(startTime + (i * 60000))
        )
      )
    }

    val trackIds = database.gpsTrackDao().insertTracks(largeTracks)
    assertEquals("大容量データの挿入に失敗", 100, trackIds.size)

    // 各軌跡に大量のGPS座標を追加
    var totalPoints = 0
    trackIds.forEachIndexed { index, trackId ->
      val points = mutableListOf<GpsPointEntity>()
      for (j in 0..49) { // 各軌跡に50個のGPS座標
        points.add(
          GpsPointEntity(
            id = 0,
            trackId = trackId,
            latitude = 35.6762 + (j * 0.0001),
            longitude = 139.6503 + (j * 0.0001),
            altitude = 10.0 + j,
            accuracy = 5.0f,
            speed = j * 0.1f,
            bearing = j * 7.2f,
            timestamp = Date(startTime + (index * 60000) + (j * 1000)),
            createdAt = Date()
          )
        )
      }
      database.gpsPointDao().insertPoints(points)
      totalPoints += points.size
    }

    // データベース接続を閉じて再開（永続性テスト）
    database.close()
    database = Room.databaseBuilder(
      context,
      PathlyDatabase::class.java,
      TEST_DB_NAME
    )
      .allowMainThreadQueries()
      .build()

    // 大容量データが保持されているか確認
    val restoredTracks = database.gpsTrackDao().getAllTracksSync()
    assertEquals("大容量軌跡データが保持されていない", 100, restoredTracks.size)

    // GPS座標データの総数確認
    val totalRestoredPoints = database.gpsPointDao().getTotalPointsCount()
    assertEquals("大容量GPS座標データが保持されていない", totalPoints, totalRestoredPoints)

    // データベースファイルサイズ確認（適切にデータが保存されている）
    assertTrue("データベースファイルサイズが適切でない", databaseFile.length() > 100000) // 100KB以上
  }
}