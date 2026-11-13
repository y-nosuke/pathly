package com.pathly.data.crash

import android.content.Context
import java.io.File

/**
 * 異常終了の記録を置くフォルダ（アプリの内部領域の `crash_reports/`）。1 件 1 ファイル。
 *
 * 落ちる瞬間（[CrashHandler]）にも使うので、Hilt にも Coroutines にも頼らない。
 * データの書き出しにも端末のバックアップにも含めない（`res/xml` の backup_rules・data_extraction_rules で除外）。
 */
class CrashReportFiles(private val dir: File) {

  fun write(report: CrashReport) {
    dir.mkdirs()
    val target = File(dir, "${report.occurredAtMillis}-${report.pid}$SUFFIX")
    val temp = File(dir, target.name + ".tmp")
    temp.writeText(CrashReportFormat.encode(report))
    temp.renameTo(target)
  }

  /** 新しい順。読めないファイルは飛ばす。 */
  fun list(): List<CrashReport> = reportFiles()
    .mapNotNull { file -> runCatching { CrashReportFormat.decode(file.readText()) }.getOrNull() }
    .sortedByDescending { it.occurredAtMillis }

  /** 新しい順に [keep] 件だけ残して、古いものを消す。 */
  fun prune(keep: Int = MAX_REPORTS) {
    reportFiles()
      .sortedByDescending { it.name.substringBefore('-').toLongOrNull() ?: 0L }
      .drop(keep)
      .forEach { it.delete() }
  }

  /** 記録を全部消す（OS の記録のどれを読んだかは残す。消したものが次の起動で戻らないように）。 */
  fun clear() {
    reportFiles().forEach { it.delete() }
  }

  /** 取り込み済みの OS の記録（`pid:時刻` の並び。古い順）。 */
  var seenExitKeys: List<String>
    get() = runCatching { File(dir, SEEN_FILE).readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    set(value) {
      dir.mkdirs()
      File(dir, SEEN_FILE).writeText(value.joinToString("\n"))
    }

  private fun reportFiles(): List<File> = dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }?.toList().orEmpty()

  companion object {
    /** 残す件数（→ docs/specs/crash-reports.md）。 */
    const val MAX_REPORTS = 5

    private const val SUFFIX = ".txt"
    private const val SEEN_FILE = "exit-info-seen"

    fun dir(context: Context): File = File(context.filesDir, "crash_reports")
  }
}
