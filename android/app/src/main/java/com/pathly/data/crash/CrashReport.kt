package com.pathly.data.crash

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo

/** 異常終了の種類（→ docs/specs/crash-reports.md）。 */
enum class CrashKind {
  /** 処理されなかった例外で落ちた。 */
  CRASH,

  /** ネイティブのコードで落ちた。 */
  NATIVE_CRASH,

  /** 応答なし（ANR）。 */
  ANR,

  /** 起動の途中で失敗した。 */
  START_FAILED,

  /** 資源（CPU・電池など）の使いすぎで OS に止められた。 */
  RESOURCE_OVERUSE,

  /** メモリ不足などで OS に止められた（前景・前景サービス中だったときだけ）。 */
  KILLED_BY_OS,
}

/**
 * 異常終了の記録 1 件。端末の内部領域に 1 ファイルずつ持つ（[CrashReportFiles]）。
 *
 * [detail] は書く前に [CrashReportFormat.sanitize] で座標の形の数を伏せ、64KB までに切り詰めてある。
 */
data class CrashReport(
  val occurredAtMillis: Long,
  val kind: CrashKind,
  /** そのとき動いていた版。OS の記録で版を預けていなかったときは null。 */
  val versionName: String?,
  val versionCode: Long?,
  /** 落ちたプロセス。アプリ自身の記録と OS の記録を突き合わせるのに使う。 */
  val pid: Int,
  /** OS の終わり方の理由（[ApplicationExitInfo] の `REASON_*`）。アプリ自身が書いた記録では null。 */
  val osReason: Int? = null,
  /** 機種と Android の版（例: `Google Pixel 8 / Android 16 (API 36)`）。 */
  val device: String,
  val detail: String,
)

/** OS が覚えている終わり方 1 件（[ApplicationExitInfo] から、テストで扱える形に移したもの）。 */
data class ExitRecord(
  val pid: Int,
  val timestampMillis: Long,
  val reason: Int,
  val importance: Int,
  val status: Int,
  val description: String?,
  val pssKb: Long,
  val rssKb: Long,
  /** 起動時に預けた版（[CrashReportFormat.encodeVersion] の形）。 */
  val processStateSummary: String?,
  /** ANR のときのスレッドの様子（先頭だけ）。それ以外は null。 */
  val trace: String?,
)

/** OS の終わり方のうち、何を異常として一覧に入れるか。 */
object ExitClassifier {

  /** 異常なら種類を、ふつうの終わり方なら null を返す。 */
  fun kindOf(reason: Int, importance: Int): CrashKind? = when (reason) {
    ApplicationExitInfo.REASON_CRASH -> CrashKind.CRASH

    ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashKind.NATIVE_CRASH

    ApplicationExitInfo.REASON_ANR -> CrashKind.ANR

    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> CrashKind.START_FAILED

    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> CrashKind.RESOURCE_OVERUSE

    // 裏で休んでいるプロセスが片付けられるのはいつものこと。画面に出ていたか記録中だったときだけ異常とみなす。
    ApplicationExitInfo.REASON_LOW_MEMORY,
    ApplicationExitInfo.REASON_SIGNALED,
    ApplicationExitInfo.REASON_FREEZER,
    ApplicationExitInfo.REASON_DEPENDENCY_DIED,
    ApplicationExitInfo.REASON_OTHER,
    ApplicationExitInfo.REASON_UNKNOWN,
    -> if (importance <= RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) CrashKind.KILLED_BY_OS else null

    // 自分で終えた・本人が止めた・更新・権限の変更など。
    else -> null
  }

  /** 理由の定数名（コピーした文面で調べやすいよう、API の名前のまま出す）。 */
  fun reasonName(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
    ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
    ApplicationExitInfo.REASON_CRASH -> "CRASH"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
    ApplicationExitInfo.REASON_ANR -> "ANR"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
    ApplicationExitInfo.REASON_OTHER -> "OTHER"
    ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
    ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
    else -> "REASON_$reason"
  }

  /** 止められたときの重要度（前景・前景サービスなど）の名前。 */
  fun importanceName(importance: Int): String = when (importance) {
    RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
    RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
    RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
    RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
    RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
    RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
    RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "CANT_SAVE_STATE"
    RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
    RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
    else -> "IMPORTANCE_$importance"
  }
}
