package com.pathly.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 書き出しファイル（zip）の形と、その読み書き・検証。
 *
 * 中身は 3 つだけ:
 * - [ENTRY_MANIFEST] … 書き出したアプリの版・DB のバージョン・日時
 * - [ENTRY_DATABASE] … Room の DB の写し（`VACUUM INTO` で作った一貫した単一ファイル）
 * - [ENTRY_SETTINGS] … SharedPreferences の中身（型つき）
 *
 * Android の Context に依存するのは SharedPreferences の読み書きだけにして、テストから
 * DB 名・設定ファイル名・置き場所を差し替えられるようにしてある。
 */
object BackupArchive {
  const val FORMAT_VERSION = 1
  const val ENTRY_MANIFEST = "manifest.json"
  const val ENTRY_DATABASE = "pathly.db"
  const val ENTRY_SETTINGS = "settings.json"

  private val KNOWN_ENTRIES = setOf(ENTRY_MANIFEST, ENTRY_DATABASE, ENTRY_SETTINGS)

  /**
   * 開いている DB [db] の一貫した写しを [target] に作る。記録中の書き込みと並んでも、ある時点の
   * 状態になり、WAL の中身も取り込まれる。写しは -wal に頼らない単一ファイルにする。
   */
  fun snapshot(db: SupportSQLiteDatabase, target: File) {
    target.delete()
    db.execSQL("VACUUM INTO '${target.path.replace("'", "''")}'")
    SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
      copy.rawQuery("PRAGMA journal_mode = DELETE", null).use { it.moveToFirst() }
    }
  }

  /** 書き出しに必要な 3 つを zip にまとめる。 */
  fun write(output: OutputStream, manifest: JSONObject, database: File, settings: JSONObject) {
    ZipOutputStream(output.buffered()).use { zip ->
      zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
      zip.write(manifest.toString(2).toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry(ENTRY_DATABASE))
      database.inputStream().use { it.copyTo(zip) }
      zip.closeEntry()
      zip.putNextEntry(ZipEntry(ENTRY_SETTINGS))
      zip.write(settings.toString(2).toByteArray())
      zip.closeEntry()
    }
  }

  /**
   * zip を [dir] に展開する。知っている名前のエントリーだけを取り出し、それ以外は読み飛ばす
   * （パスを含む名前で置き場所の外へ書かせない）。
   */
  fun extract(input: InputStream, dir: File) {
    dir.deleteRecursively()
    dir.mkdirs()
    ZipInputStream(input.buffered()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        if (!entry.isDirectory && entry.name in KNOWN_ENTRIES) {
          File(dir, entry.name).outputStream().use { zip.copyTo(it) }
        }
        zip.closeEntry()
      }
    }
  }

  /**
   * 展開した中身が読み込めるかを確かめ、確認画面に出す要約を返す。
   * 読み込めないときは [BackupException] を投げる。
   */
  fun inspect(dir: File, currentDatabaseVersion: Int): BackupSummary {
    val manifestFile = File(dir, ENTRY_MANIFEST)
    val databaseFile = File(dir, ENTRY_DATABASE)
    if (!manifestFile.isFile || !databaseFile.isFile || !File(dir, ENTRY_SETTINGS).isFile) {
      throw BackupException(BackupException.Reason.NOT_A_BACKUP)
    }
    val manifest = runCatching { JSONObject(manifestFile.readText()) }
      .getOrElse { throw BackupException(BackupException.Reason.NOT_A_BACKUP, it) }
    if (manifest.optInt("formatVersion", -1) != FORMAT_VERSION) {
      throw BackupException(BackupException.Reason.UNSUPPORTED_FORMAT)
    }

    val db = runCatching {
      SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }.getOrElse { throw BackupException(BackupException.Reason.BROKEN, it) }
    return db.use {
      val version = it.version
      // 新しいアプリで書き出した DB は、古いアプリでは開けない（下げるマイグレーションは無い）。
      if (version > currentDatabaseVersion) {
        throw BackupException(BackupException.Reason.NEWER_VERSION)
      }
      val integrity = it.rawQuery("PRAGMA integrity_check", null).use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
      if (integrity != "ok") throw BackupException(BackupException.Reason.BROKEN)
      val trackCount = runCatching { it.count("gps_tracks") }
        .getOrElse { e -> throw BackupException(BackupException.Reason.NOT_A_BACKUP, e) }
      val placeCount = runCatching { it.count("places") }
        .getOrElse { e -> throw BackupException(BackupException.Reason.NOT_A_BACKUP, e) }
      BackupSummary(
        exportedAtMillis = manifest.optLong("exportedAt"),
        appVersionName = manifest.optString("versionName"),
        databaseVersion = version,
        trackCount = trackCount,
        placeCount = placeCount,
      )
    }
  }

  private fun SQLiteDatabase.count(table: String): Int = rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
    c.moveToFirst()
    c.getInt(0)
  }

  /** 書き出す側の manifest。 */
  fun manifest(applicationId: String, versionName: String, versionCode: Int, databaseVersion: Int, exportedAtMillis: Long): JSONObject = JSONObject()
    .put("formatVersion", FORMAT_VERSION)
    .put("applicationId", applicationId)
    .put("versionName", versionName)
    .put("versionCode", versionCode)
    .put("databaseVersion", databaseVersion)
    .put("exportedAt", exportedAtMillis)

  /**
   * 設定ファイルの中身を型つきで JSON にする（`{"<ファイル名>": {"<キー>": {"type": .., "value": ..}}}`）。
   * 戻すときに Int と Long などを取り違えないよう、型を一緒に持つ。
   */
  fun settingsToJson(context: Context, prefsNames: List<String>): JSONObject {
    val root = JSONObject()
    prefsNames.forEach { name ->
      val file = JSONObject()
      context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
        val typed = when (value) {
          is Boolean -> JSONObject().put("type", "boolean").put("value", value)
          is Int -> JSONObject().put("type", "int").put("value", value)
          is Long -> JSONObject().put("type", "long").put("value", value)
          is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
          is String -> JSONObject().put("type", "string").put("value", value)
          else -> null // StringSet は使っていない
        }
        if (typed != null) file.put(key, typed)
      }
      root.put(name, file)
    }
    return root
  }

  /**
   * 書き出した設定で [prefsNames] の各ファイルを**丸ごと置き換える**（無いキーは消える）。
   * 書き出しに無いファイルは空にする（古い版の書き出しには無い設定＝既定値に戻す）。
   */
  fun restoreSettings(context: Context, prefsNames: List<String>, json: JSONObject) {
    prefsNames.forEach { name ->
      // 起動直後に書いてすぐ読むので、非同期の apply ではなく commit で確定させる。
      context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) {
        clear()
        val file = json.optJSONObject(name)
        file?.keys()?.forEach { key ->
          val typed = file.getJSONObject(key)
          when (typed.getString("type")) {
            "boolean" -> putBoolean(key, typed.getBoolean("value"))
            "int" -> putInt(key, typed.getInt("value"))
            "long" -> putLong(key, typed.getLong("value"))
            "float" -> putFloat(key, typed.getDouble("value").toFloat())
            "string" -> putString(key, typed.getString("value"))
          }
        }
      }
    }
  }

  /**
   * 置き場所 [pendingDir] に読み込みが予約されていれば、DB と設定を差し替える。
   * **DB を誰も開いていない、アプリの起動直後にだけ呼ぶこと**（開いたまま差し替えると壊れる）。
   * 差し替えたら true。
   *
   * DB を差し替える前に落ちたら予約は残り、次の起動でやり直される。差し替えたあとは、設定の復元に
   * 失敗しても予約を必ず消す（残すと、起動のたびに DB が予約の中身へ巻き戻る）。
   */
  fun applyPending(context: Context, pendingDir: File, databaseName: String, prefsNames: List<String>): Boolean {
    val database = File(pendingDir, ENTRY_DATABASE)
    val settingsFile = File(pendingDir, ENTRY_SETTINGS)
    if (!database.isFile || !settingsFile.isFile) return false
    // 読めない設定なら差し替え自体をやめる（DB だけ入れ替わった半端な状態を作らない）。
    val settings = runCatching { JSONObject(settingsFile.readText()) }.getOrElse {
      pendingDir.deleteRecursively()
      throw it
    }

    val target = context.getDatabasePath(databaseName)
    target.parentFile?.mkdirs()
    // 同じディレクトリに写してから入れ替える（途中で落ちても、中途半端な DB を開かせない）。
    val staged = File(target.parentFile, "$databaseName.importing")
    database.copyTo(staged, overwrite = true)
    context.deleteDatabase(databaseName) // 本体と -wal / -shm / -journal を消す
    if (!staged.renameTo(target)) {
      staged.copyTo(target, overwrite = true)
      staged.delete()
    }
    try {
      restoreSettings(context, prefsNames, settings)
    } finally {
      pendingDir.deleteRecursively()
    }
    return true
  }
}

/** 読み込む前の確認に出す、書き出しファイルの要約。 */
data class BackupSummary(
  val exportedAtMillis: Long,
  val appVersionName: String,
  val databaseVersion: Int,
  val trackCount: Int,
  val placeCount: Int,
)

/** 書き出し・読み込みの失敗。[reason] で利用者に見せる文言を分ける。 */
class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
  enum class Reason {
    /** Pathly の書き出しファイルではない（zip でない・中身が足りない・表が無い）。 */
    NOT_A_BACKUP,

    /** 知らない形式の版（将来の形式）。 */
    UNSUPPORTED_FORMAT,

    /** 今のアプリより新しい版で書き出された。 */
    NEWER_VERSION,

    /** DB が壊れている。 */
    BROKEN,

    /** 記録中は読み込めない。 */
    RECORDING,
  }
}
