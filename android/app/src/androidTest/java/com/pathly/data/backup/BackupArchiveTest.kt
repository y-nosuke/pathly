package com.pathly.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.migration.DatabaseMigrations
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/**
 * 書き出し → 検証 → 次の起動での差し替え、の往復と、読み込めないファイルの拒否を確かめる。
 * 本物の DB 名・設定ファイルは使わず、テスト用の名前に差し替える。
 */
@RunWith(AndroidJUnit4::class)
class BackupArchiveTest {

  private lateinit var context: Context
  private lateinit var workDir: File
  private var database: PathlyDatabase? = null

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    workDir = File(context.cacheDir, "backup-archive-test").apply {
      deleteRecursively()
      mkdirs()
    }
    context.deleteDatabase(DB_NAME)
    PREFS.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit(commit = true) { clear() } }
  }

  @After
  fun tearDown() {
    database?.close()
    context.deleteDatabase(DB_NAME)
    workDir.deleteRecursively()
  }

  private fun openDatabase(): PathlyDatabase = Room.databaseBuilder(context, PathlyDatabase::class.java, DB_NAME)
    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
    .allowMainThreadQueries()
    .build()
    .also { database = it }

  /** いまの DB と設定を zip に書き出して、そのバイト列を返す。 */
  private fun exportBytes(db: PathlyDatabase): ByteArray {
    val snapshot = File(workDir, "snapshot.db")
    BackupArchive.snapshot(db.openHelper.writableDatabase, snapshot)
    val out = ByteArrayOutputStream()
    BackupArchive.write(
      out,
      BackupArchive.manifest("com.pathly.test", "0.0.0", 1, PathlyDatabase.VERSION, 1_700_000_000_000),
      snapshot,
      BackupArchive.settingsToJson(context, PREFS),
    )
    return out.toByteArray()
  }

  private fun extract(bytes: ByteArray, name: String): File = File(workDir, name).also {
    BackupArchive.extract(ByteArrayInputStream(bytes), it)
  }

  @Test
  fun exportThenApplyRestoresDatabaseAndSettings() = runBlocking {
    val db = openDatabase()
    db.gpsTrackDao().insertTrack(GpsTrackEntity(startTime = Date(), isActive = false))
    db.gpsTrackDao().insertTrack(GpsTrackEntity(startTime = Date(), isActive = false))
    context.getSharedPreferences(PREFS[0], Context.MODE_PRIVATE).edit(commit = true) {
      putInt("interval", 30)
      putString("accuracy", "high")
      putBoolean("show", true)
      putLong("big", 1L shl 40)
      putFloat("ratio", 0.5f)
    }
    context.getSharedPreferences(PREFS[1], Context.MODE_PRIVATE).edit(commit = true) { putInt("generation", 2) }

    val bytes = exportBytes(db)

    // 書き出したあとにデータと設定を変える（読み込めば書き出した時点に戻るはず）。
    db.gpsTrackDao().insertTrack(GpsTrackEntity(startTime = Date(), isActive = false))
    context.getSharedPreferences(PREFS[0], Context.MODE_PRIVATE).edit(commit = true) {
      putInt("interval", 5)
      putString("added_later", "x")
    }
    assertEquals(3, db.gpsTrackDao().getTrackCount())

    val candidate = extract(bytes, "candidate")
    val summary = BackupArchive.inspect(candidate, PathlyDatabase.VERSION)
    assertEquals(2, summary.trackCount)
    assertEquals(PathlyDatabase.VERSION, summary.databaseVersion)
    assertEquals(1_700_000_000_000, summary.exportedAtMillis)

    // 差し替えは DB を閉じた状態（＝次の起動の最初）で行う。
    db.close()
    database = null
    val pending = File(workDir, "pending")
    assertTrue(candidate.renameTo(pending))
    assertTrue(BackupArchive.applyPending(context, pending, DB_NAME, PREFS))
    assertFalse("予約は消える", pending.exists())

    val reopened = openDatabase()
    assertEquals(2, reopened.gpsTrackDao().getTrackCount())
    val settings = context.getSharedPreferences(PREFS[0], Context.MODE_PRIVATE)
    assertEquals(30, settings.getInt("interval", 0))
    assertEquals("high", settings.getString("accuracy", null))
    assertEquals(true, settings.getBoolean("show", false))
    assertEquals(1L shl 40, settings.getLong("big", 0))
    assertEquals(0.5f, settings.getFloat("ratio", 0f))
    assertFalse("書き出しに無いキーは消える", settings.contains("added_later"))
    assertEquals(2, context.getSharedPreferences(PREFS[1], Context.MODE_PRIVATE).getInt("generation", 0))
  }

  @Test
  fun applyWithoutPendingDoesNothing() {
    assertFalse(BackupArchive.applyPending(context, File(workDir, "none"), DB_NAME, PREFS))
  }

  @Test
  fun newerDatabaseVersionIsRejected() {
    val db = openDatabase()
    val bytes = exportBytes(db)
    val candidate = extract(bytes, "candidate")
    // 今のアプリより新しい版で書き出されたことにする。
    SQLiteDatabase.openDatabase(File(candidate, BackupArchive.ENTRY_DATABASE).path, null, SQLiteDatabase.OPEN_READWRITE)
      .use { it.version = PathlyDatabase.VERSION + 1 }

    assertRejected(BackupException.Reason.NEWER_VERSION) { BackupArchive.inspect(candidate, PathlyDatabase.VERSION) }
  }

  @Test
  fun fileThatIsNotABackupIsRejected() {
    val candidate = extract("not a zip".toByteArray(), "candidate")
    assertRejected(BackupException.Reason.NOT_A_BACKUP) { BackupArchive.inspect(candidate, PathlyDatabase.VERSION) }
  }

  @Test
  fun unknownFormatVersionIsRejected() {
    val db = openDatabase()
    val candidate = extract(exportBytes(db), "candidate")
    File(candidate, BackupArchive.ENTRY_MANIFEST).writeText("""{"formatVersion": 999}""")
    assertRejected(BackupException.Reason.UNSUPPORTED_FORMAT) { BackupArchive.inspect(candidate, PathlyDatabase.VERSION) }
  }

  private fun assertRejected(expected: BackupException.Reason, block: () -> Unit) {
    try {
      block()
      fail("Expected $expected")
    } catch (e: BackupException) {
      assertEquals(expected, e.reason)
    }
  }

  private companion object {
    const val DB_NAME = "backup_archive_test_db"
    val PREFS = listOf("backup_archive_test_settings", "backup_archive_test_maintenance")
  }
}
