package com.pathly.data.crash

import android.content.Context
import com.pathly.BuildConfig
import com.pathly.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 異常終了の記録（→ docs/specs/crash-reports.md）。アプリ自身が落ちる瞬間に書いたものと、
 * OS が覚えている終わり方のうち異常なものを、1 つの一覧（新しい順・[CrashReportFiles.MAX_REPORTS] 件まで）にまとめる。
 * 外には何も送らない。
 */
@Singleton
class CrashReportStore internal constructor(
  private val files: CrashReportFiles,
  private val source: ExitReasonSource,
  private val device: String,
  private val ioDispatcher: CoroutineDispatcher,
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    source: ExitReasonSource,
  ) : this(CrashReportFiles(CrashReportFiles.dir(context)), source, CrashHandler.deviceDescription(), Dispatchers.IO)

  private val logger = Logger("CrashReportStore")

  /** 取り込みと消去が重ならないように（消した直後に取り込み途中のものが書かれないように）。 */
  private val mutex = Mutex()

  private val _reports = MutableStateFlow<List<CrashReport>>(emptyList())
  val reports: StateFlow<List<CrashReport>> = _reports.asStateFlow()

  /** いま動いている版を OS に預ける。OS に止められたときの記録に、そのときの版を付けるため。起動時に呼ぶ。 */
  fun recordVersion(versionName: String = BuildConfig.VERSION_NAME, versionCode: Long = BuildConfig.VERSION_CODE.toLong()) {
    runCatching { source.setProcessStateSummary(CrashReportFormat.encodeVersion(versionName, versionCode)) }
      .onFailure { logger.w("Failed to set process state summary", it) }
  }

  /** OS の記録のうち、まだ取り込んでいない異常を取り込み、一覧を読み直す。起動時に呼ぶ。 */
  suspend fun refresh() = withContext(ioDispatcher) {
    mutex.withLock {
      try {
        importExitRecords()
        files.prune()
      } catch (e: Exception) {
        logger.e("Failed to import exit reasons", e)
      }
      _reports.value = runCatching { files.list() }.getOrDefault(emptyList())
    }
  }

  /** 記録を全部消す。 */
  suspend fun clear() = withContext(ioDispatcher) {
    mutex.withLock {
      files.clear()
      _reports.value = emptyList()
    }
  }

  private fun importExitRecords() {
    // 時刻ではなく（pid・時刻）の組で覚える。端末の時計が戻っても、新しい記録を取りこぼさない。
    val seen = files.seenExitKeys
    val records = source.read { pid, timestamp -> exitKey(pid, timestamp) !in seen }
    if (records.isEmpty()) return

    val existing = files.list()
    var imported = 0
    records.forEach { record ->
      val report = CrashReportFormat.fromExitRecord(record, device) ?: return@forEach
      // アプリ自身がスタックトレースを書けていれば、OS の「落ちた」は重ねない（書けなかったときの穴埋めだけ）。
      if (report.kind == CrashKind.CRASH && existing.any { it.isSameCrash(report) }) return@forEach
      files.write(report)
      imported++
    }
    files.seenExitKeys = (seen + records.map { exitKey(it.pid, it.timestampMillis) }).takeLast(MAX_SEEN_EXIT_KEYS)
    if (imported > 0) logger.i("Imported $imported exit reasons")
  }

  private fun CrashReport.isSameCrash(other: CrashReport): Boolean = kind == CrashKind.CRASH && pid == other.pid && abs(occurredAtMillis - other.occurredAtMillis) < SAME_CRASH_WINDOW_MILLIS

  private companion object {
    /** アプリが書いた時刻と OS が記録した時刻のずれの許容。 */
    const val SAME_CRASH_WINDOW_MILLIS = 60_000L

    /** 覚えておく OS の記録の数。OS が持つのは数十件までなので、それより多ければ足りる。 */
    const val MAX_SEEN_EXIT_KEYS = 100

    fun exitKey(pid: Int, timestampMillis: Long) = "$pid:$timestampMillis"
  }
}
