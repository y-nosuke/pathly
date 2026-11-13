package com.pathly.data.crash

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatTest {

  private fun exitRecord(
    reason: Int,
    importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
    trace: String? = null,
    summary: String? = "0.5.0\n412",
    description: String? = "desc",
  ) = ExitRecord(
    pid = 100,
    timestampMillis = 1_000L,
    reason = reason,
    importance = importance,
    status = 0,
    description = description,
    pssKb = 2048,
    rssKb = 4096,
    processStateSummary = summary,
    trace = trace,
  )

  @Test
  fun `座標の形の数は伏せ、行番号やふつうの数は残す`() {
    val text = "Bad location 35.681236,139.767125 at Foo.kt:42 size=12 ratio=0.5 v1.2.3"
    val sanitized = CrashReportFormat.sanitize(text)
    assertFalse(sanitized.contains("35.681236"))
    assertFalse(sanitized.contains("139.767125"))
    assertTrue(sanitized.contains("<数値>,<数値>"))
    assertTrue(sanitized.contains("Foo.kt:42"))
    assertTrue(sanitized.contains("ratio=0.5"))
    assertTrue(sanitized.contains("v1.2.3"))
  }

  @Test
  fun `負の座標も伏せる`() {
    assertEquals("lat <数値>", CrashReportFormat.sanitize("lat -33.8688"))
  }

  @Test
  fun `64KB を超える中身は切り詰めて印を付ける`() {
    val long = "a".repeat(CrashReportFormat.MAX_DETAIL_CHARS + 10)
    val truncated = CrashReportFormat.truncate(long)
    assertTrue(truncated.startsWith("a".repeat(CrashReportFormat.MAX_DETAIL_CHARS)))
    assertTrue(truncated.endsWith("以下を省きました）"))
    assertEquals("short", CrashReportFormat.truncate("short"))
  }

  @Test
  fun `書いたものを読み戻すと同じになる`() {
    val report = CrashReport(
      occurredAtMillis = 1_731_550_000_000L,
      kind = CrashKind.KILLED_BY_OS,
      versionName = "0.5.0+dev.3.abc1234",
      versionCode = 412,
      pid = 1234,
      osReason = ApplicationExitInfo.REASON_LOW_MEMORY,
      device = "Google Pixel 8 / Android 16 (API 36)",
      detail = "理由: LOW_MEMORY\n---\n途中に区切りと同じ行があっても中身として読む",
    )
    assertEquals(report, CrashReportFormat.decode(CrashReportFormat.encode(report)))
  }

  @Test
  fun `版が分からない記録も読み戻せる`() {
    val report = CrashReport(1L, CrashKind.ANR, null, null, 1, ApplicationExitInfo.REASON_ANR, "d", "x")
    assertEquals(report, CrashReportFormat.decode(CrashReportFormat.encode(report)))
  }

  @Test
  fun `知らない形のファイルは読まない`() {
    assertNull(CrashReportFormat.decode("hello"))
    assertNull(CrashReportFormat.decode("pathly-crash-report: 1\nkind: CRASH\n---\nx"))
    assertNull(CrashReportFormat.decode("pathly-crash-report: 1\noccurredAt: 1\nkind: NOPE\n---\nx"))
  }

  @Test
  fun `例外からの記録はスレッド名とスタックトレースを持ち、座標を伏せる`() {
    val report = CrashReportFormat.fromThrowable(
      threadName = "main",
      throwable = IllegalStateException("at 35.681236"),
      occurredAtMillis = 5L,
      pid = 7,
      versionName = "0.5.0",
      versionCode = 412,
      device = "dev",
    )
    assertEquals(CrashKind.CRASH, report.kind)
    assertTrue(report.detail.startsWith("スレッド: main\njava.lang.IllegalStateException: at <数値>"))
    assertTrue(report.detail.contains("CrashReportFormatTest"))
    assertEquals("0.5.0", report.versionName)
    assertEquals(412L, report.versionCode)
  }

  @Test
  fun `OS の記録からは預けた版と理由・状態を持つ記録を作る`() {
    val report = CrashReportFormat.fromExitRecord(
      exitRecord(ApplicationExitInfo.REASON_LOW_MEMORY, RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE),
      device = "dev",
    )!!
    assertEquals(CrashKind.KILLED_BY_OS, report.kind)
    assertEquals("0.5.0", report.versionName)
    assertEquals(412L, report.versionCode)
    assertEquals(ApplicationExitInfo.REASON_LOW_MEMORY, report.osReason)
    assertTrue(report.detail.contains("理由: LOW_MEMORY"))
    assertTrue(report.detail.contains("そのときの状態: FOREGROUND_SERVICE"))
    assertTrue(report.detail.contains("PSS 2 MB / RSS 4 MB"))
  }

  @Test
  fun `ANR はスレッドの様子を付ける`() {
    val report = CrashReportFormat.fromExitRecord(exitRecord(ApplicationExitInfo.REASON_ANR, trace = "\"main\" prio=5"), "dev")!!
    assertEquals(CrashKind.ANR, report.kind)
    assertTrue(report.detail.contains("--- スレッドの様子 ---\n\"main\" prio=5"))
  }

  @Test
  fun `版を預けていなければ版は不明`() {
    val report = CrashReportFormat.fromExitRecord(exitRecord(ApplicationExitInfo.REASON_ANR, summary = null), "dev")!!
    assertNull(report.versionName)
    assertNull(report.versionCode)
  }

  @Test
  fun `ふつうの終わり方からは記録を作らない`() {
    assertNull(CrashReportFormat.fromExitRecord(exitRecord(ApplicationExitInfo.REASON_EXIT_SELF), "dev"))
  }

  @Test
  fun `版の預け方と読み方が対になっている`() {
    val encoded = CrashReportFormat.encodeVersion("0.5.0+dev.3.abc1234", 412)
    assertTrue(encoded.toByteArray().size <= 128)
    assertEquals("0.5.0+dev.3.abc1234" to 412L, CrashReportFormat.decodeVersion(encoded))
  }

  @Test
  fun `版の名前が長すぎても OS に預けられる大きさに収め、番号は残す`() {
    val encoded = CrashReportFormat.encodeVersion("版".repeat(100), 412)
    assertTrue(encoded.toByteArray().size <= 128)
    assertEquals(412L, CrashReportFormat.decodeVersion(encoded).second)
  }
}
