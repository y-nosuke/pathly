package com.pathly.data.crash

import android.content.Context
import android.os.Build
import android.os.Process
import com.pathly.BuildConfig

/**
 * 処理されなかった例外で落ちる瞬間に、スタックトレース・日時・版を端末の中に書く（→ docs/specs/crash-reports.md）。
 *
 * 書いたあとは元のハンドラに渡すので、OS から見た落ち方（「アプリが停止しました」・プロセスの終了）は変わらない。
 */
object CrashHandler {

  /** `Application.onCreate` のいちばん最初に呼ぶ（それより後で落ちたものしか拾えない）。 */
  fun install(context: Context) {
    val files = CrashReportFiles(CrashReportFiles.dir(context))
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        files.write(
          CrashReportFormat.fromThrowable(
            threadName = thread.name,
            throwable = throwable,
            occurredAtMillis = System.currentTimeMillis(),
            pid = Process.myPid(),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            device = deviceDescription(),
          ),
        )
        files.prune()
      } catch (_: Throwable) {
        // 書けなくても落ち方は変えない。次の起動で OS の記録（REASON_CRASH）から拾える。
      }
      previous?.uncaughtException(thread, throwable)
    }
  }

  /** 機種と Android の版。端末固有の ID は入れない。 */
  fun deviceDescription(): String = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
