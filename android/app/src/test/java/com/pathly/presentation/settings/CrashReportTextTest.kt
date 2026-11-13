package com.pathly.presentation.settings

import android.app.ApplicationExitInfo
import com.pathly.data.crash.CrashKind
import com.pathly.data.crash.CrashReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTextTest {

  private fun report(kind: CrashKind, osReason: Int? = null, versionName: String? = "0.5.0", versionCode: Long? = 412) = CrashReport(1_731_550_000_000L, kind, versionName, versionCode, 1, osReason, "Google Pixel 8 / Android 16 (API 36)", "stack line")

  @Test
  fun `種類の見出しは OS の理由まで出す`() {
    assertEquals("落ちた（例外）", crashReportTitle(report(CrashKind.CRASH)))
    assertEquals("応答なし（ANR）", crashReportTitle(report(CrashKind.ANR)))
    assertEquals(
      "OS に止められた（メモリ不足）",
      crashReportTitle(report(CrashKind.KILLED_BY_OS, ApplicationExitInfo.REASON_LOW_MEMORY)),
    )
  }

  @Test
  fun `コピーする全文に種類・版・端末・中身が入る`() {
    val text = crashReportText(report(CrashKind.CRASH))
    assertTrue(text.startsWith("種類: 落ちた（例外）\n日時: "))
    assertTrue(text.contains("版: 0.5.0（ビルド 412）\n"))
    assertTrue(text.contains("端末: Google Pixel 8 / Android 16 (API 36)\n"))
    assertTrue(text.endsWith("\n\nstack line"))
  }

  @Test
  fun `版が分からないときは不明と出す`() {
    assertTrue(crashReportText(report(CrashKind.ANR, versionName = null, versionCode = null)).contains("版: 不明\n"))
  }
}
