package com.pathly.data.backup

import android.content.Context
import android.net.Uri
import com.pathly.BuildConfig
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.settings.MaintenanceStore
import com.pathly.data.settings.SettingsRepository
import com.pathly.service.LocationTrackingService
import com.pathly.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 記録・場所・設定を 1 つのファイルに書き出し、読み込む（→ docs/specs/backup.md）。
 *
 * 読み込みは**全部の入れ替え**で、その場では差し替えない。開いている DB を差し替えると壊れるうえ、
 * DAO や Flow を持つシングルトンが古い DB を見続けるため。代わりに「予約」を置いてアプリを再起動し、
 * 次の起動の最初（DB を誰も開いていないうち）に [applyPendingImport] が差し替える（→ adr/0027）。
 */
@Singleton
class DataBackupManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val database: PathlyDatabase,
) {
  /** 読み込む候補を展開しておく場所（確認画面の間だけ使う）。 */
  private val candidateDir get() = File(context.cacheDir, "backup-candidate")

  /** 読み込む直前の状態を退避しておくファイル（1 つだけ持ち、読み込むたびに上書きする）。 */
  private val beforeImportFile get() = File(context.filesDir, "backup/before-import.zip")

  /** 今のデータを [uri] に書き出す。記録中でも書き出せる（`VACUUM INTO` は一貫した写しを作る）。 */
  suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open $uri")
    output.use { writeSnapshot(it) }
  }

  /** 今のデータを [output] に書き出す。 */
  private fun writeSnapshot(output: java.io.OutputStream) {
    val snapshot = File(context.cacheDir, "backup-export.db")
    try {
      BackupArchive.snapshot(database.openHelper.writableDatabase, snapshot)
      val manifest = BackupArchive.manifest(
        applicationId = BuildConfig.APPLICATION_ID,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        databaseVersion = PathlyDatabase.VERSION,
        exportedAtMillis = System.currentTimeMillis(),
      )
      BackupArchive.write(output, manifest, snapshot, BackupArchive.settingsToJson(context, PREFS_NAMES))
    } finally {
      snapshot.delete()
      File(snapshot.path + "-journal").delete()
    }
  }

  /** [uri] のファイルを読み込み候補として展開・検証し、確認用の要約を返す。 */
  suspend fun prepareImport(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
    prepare { input.use { BackupArchive.extract(it, candidateDir) } }
  }

  /** 直前の読み込みで退避した状態を、読み込み候補として用意する。 */
  suspend fun prepareRestoreBeforeImport(): BackupSummary = withContext(Dispatchers.IO) {
    prepare { beforeImportFile.inputStream().use { BackupArchive.extract(it, candidateDir) } }
  }

  private fun prepare(extract: () -> Unit): BackupSummary {
    try {
      extract()
    } catch (e: Exception) {
      candidateDir.deleteRecursively()
      throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
    }
    return try {
      BackupArchive.inspect(candidateDir, PathlyDatabase.VERSION)
    } catch (e: Exception) {
      candidateDir.deleteRecursively()
      throw e as? BackupException ?: BackupException(BackupException.Reason.BROKEN, e)
    }
  }

  /**
   * 用意した候補の読み込みを確定する。今の状態を [beforeImportFile] に退避し、候補を予約に回す。
   * 呼び出し側は、このあとアプリを再起動すること（差し替えは次の起動で行われる）。
   */
  suspend fun commitImport() = withContext(Dispatchers.IO) {
    if (LocationTrackingService.isTracking || LocationTrackingService.isFinalizing.value) {
      throw BackupException(BackupException.Reason.RECORDING)
    }
    check(File(candidateDir, BackupArchive.ENTRY_DATABASE).isFile) { "No import candidate" }

    beforeImportFile.parentFile?.mkdirs()
    val saving = File(beforeImportFile.path + ".saving")
    saving.outputStream().use { writeSnapshot(it) }
    beforeImportFile.delete()
    saving.renameTo(beforeImportFile)

    val pending = pendingDir(context)
    pending.deleteRecursively()
    if (!candidateDir.renameTo(pending)) {
      candidateDir.copyRecursively(pending, overwrite = true)
      candidateDir.deleteRecursively()
    }
    logger.i("Import staged; restart required")
  }

  /** 読み込み候補を捨てる（確認画面でやめたとき）。 */
  fun discardCandidate() {
    candidateDir.deleteRecursively()
  }

  /** 読み込む前の状態を退避した日時。退避が無ければ null。 */
  fun beforeImportSavedAtMillis(): Long? = beforeImportFile.takeIf { it.isFile }?.lastModified()

  companion object {
    private val logger = Logger("DataBackupManager")

    /** 書き出しに含める設定ファイル。 */
    val PREFS_NAMES = listOf(SettingsRepository.PREFS_NAME, MaintenanceStore.PREFS_NAME)

    private fun pendingDir(context: Context) = File(context.filesDir, "backup/pending-import")

    /**
     * 予約された読み込みがあれば DB と設定を差し替える。`Application.onCreate` の**最初**
     * （Hilt の注入や WorkManager の初期化より前）に呼ぶ。失敗しても起動は止めない。
     */
    fun applyPendingImport(context: Context) {
      try {
        if (BackupArchive.applyPending(context, pendingDir(context), PathlyDatabase.DATABASE_NAME, PREFS_NAMES)) {
          logger.i("Pending import applied")
        }
      } catch (e: Exception) {
        logger.e("Failed to apply pending import", e)
      }
    }
  }
}
