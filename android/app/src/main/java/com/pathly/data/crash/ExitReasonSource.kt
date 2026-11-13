package com.pathly.data.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject

/** OS が覚えている「前回までの終わり方」。テストで差し替えられるよう、OS の呼び出しをここに閉じる。 */
interface ExitReasonSource {
  /**
   * このアプリの終わり方のうち、[isNew] が true のものだけ（新しい順）。
   * ANR のスレッドの様子は大きいので、新しいものだけ読む。
   */
  fun read(isNew: (pid: Int, timestampMillis: Long) -> Boolean): List<ExitRecord>

  /** いま動いているプロセスに添える覚え書き。次に読むとき [ExitRecord.processStateSummary] に入って戻る。 */
  fun setProcessStateSummary(summary: String)
}

class AndroidExitReasonSource @Inject constructor(
  @ApplicationContext private val context: Context,
) : ExitReasonSource {

  private val activityManager: ActivityManager
    get() = context.getSystemService(ActivityManager::class.java)

  override fun read(isNew: (pid: Int, timestampMillis: Long) -> Boolean): List<ExitRecord> = activityManager.getHistoricalProcessExitReasons(null, 0, 0).filter { isNew(it.pid, it.timestamp) }.map { info ->
    ExitRecord(
      pid = info.pid,
      timestampMillis = info.timestamp,
      reason = info.reason,
      importance = info.importance,
      status = info.status,
      description = info.description,
      pssKb = info.pss,
      rssKb = info.rss,
      processStateSummary = info.processStateSummary?.toString(Charsets.UTF_8),
      // ネイティブの異常終了の traceInputStream はバイナリ（tombstone の protobuf）なので読まない。
      trace = if (info.reason == ApplicationExitInfo.REASON_ANR) info.traceInputStream?.use { readHead(it) } else null,
    )
  }

  override fun setProcessStateSummary(summary: String) {
    activityManager.setProcessStateSummary(summary.toByteArray(Charsets.UTF_8))
  }

  /** 先頭だけ読む（全スレッドの様子は数百 KB になりうる。残すのは 64KB まで）。 */
  private fun readHead(input: InputStream): String {
    val buffer = ByteArray(CrashReportFormat.MAX_DETAIL_CHARS * 2)
    var total = 0
    while (total < buffer.size) {
      val n = input.read(buffer, total, buffer.size - total)
      if (n < 0) break
      total += n
    }
    return String(buffer, 0, total, Charsets.UTF_8)
  }
}
