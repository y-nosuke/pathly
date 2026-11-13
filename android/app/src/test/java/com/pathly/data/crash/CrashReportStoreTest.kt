package com.pathly.data.crash

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashReportStoreTest {

  private lateinit var dir: File
  private lateinit var files: CrashReportFiles
  private val source = mockk<ExitReasonSource>(relaxed = true)

  @Before
  fun setUp() {
    dir = Files.createTempDirectory("crash").toFile()
    files = CrashReportFiles(dir)
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  private fun store(dispatcher: TestDispatcher) = CrashReportStore(files, source, "dev", dispatcher)

  private fun exit(pid: Int, at: Long, reason: Int, importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND) = ExitRecord(pid, at, reason, importance, 0, null, 0, 0, "0.5.0\n412", null)

  /** 本物と同じく、まだ取り込んでいないものだけを返す。 */
  private fun givenExits(records: List<ExitRecord>) {
    every { source.read(any()) } answers {
      val isNew = firstArg<(Int, Long) -> Boolean>()
      records.filter { isNew(it.pid, it.timestampMillis) }
    }
  }

  private fun crash(pid: Int, at: Long) = CrashReport(at, CrashKind.CRASH, "0.5.0", 412, pid, null, "dev", "stack")

  @Test
  fun `OS の記録から異常だけを取り込み、新しい順に並べる`() = runTest {
    givenExits(
      listOf(
        exit(1, 3_000, ApplicationExitInfo.REASON_ANR),
        exit(2, 2_000, ApplicationExitInfo.REASON_EXIT_SELF),
        exit(3, 1_000, ApplicationExitInfo.REASON_LOW_MEMORY, RunningAppProcessInfo.IMPORTANCE_CACHED),
        exit(4, 500, ApplicationExitInfo.REASON_CRASH_NATIVE),
      ),
    )
    val store = store(StandardTestDispatcher(testScheduler))
    store.refresh()
    assertEquals(listOf(CrashKind.ANR, CrashKind.NATIVE_CRASH), store.reports.value.map { it.kind })
    assertEquals(listOf(3_000L, 500L), store.reports.value.map { it.occurredAtMillis })
  }

  @Test
  fun `一度取り込んだ OS の記録は、消したあとも二度と取り込まない`() = runTest {
    givenExits(listOf(exit(1, 3_000, ApplicationExitInfo.REASON_ANR)))
    val store = store(StandardTestDispatcher(testScheduler))
    store.refresh()
    assertEquals(1, store.reports.value.size)

    store.clear()
    store.refresh()
    assertTrue(store.reports.value.isEmpty())
  }

  @Test
  fun `アプリが書いた落ち方と同じものは OS の記録から重ねない`() = runTest {
    files.write(crash(pid = 7, at = 10_000))
    givenExits(
      listOf(
        exit(7, 10_500, ApplicationExitInfo.REASON_CRASH),
        // アプリが書けなかった落ち方（別のプロセス）は穴埋めとして入れる
        exit(8, 20_000, ApplicationExitInfo.REASON_CRASH),
      ),
    )
    val store = store(StandardTestDispatcher(testScheduler))
    store.refresh()
    assertEquals(listOf(8 to 20_000L, 7 to 10_000L), store.reports.value.map { it.pid to it.occurredAtMillis })
    assertEquals("stack", store.reports.value.last().detail)
  }

  @Test
  fun `新しい順に 5 件までしか残さない`() = runTest {
    givenExits((1..7).map { exit(it, it * 1_000L, ApplicationExitInfo.REASON_ANR) })
    val store = store(StandardTestDispatcher(testScheduler))
    store.refresh()
    assertEquals(listOf(7_000L, 6_000L, 5_000L, 4_000L, 3_000L), store.reports.value.map { it.occurredAtMillis })
    assertEquals(CrashReportFiles.MAX_REPORTS, dir.listFiles { f -> f.name.endsWith(".txt") }!!.size)
  }

  @Test
  fun `端末の時計が戻って前より古い時刻になっても、新しい記録は取り込む`() = runTest {
    givenExits(listOf(exit(1, 5_000, ApplicationExitInfo.REASON_ANR)))
    val store = store(StandardTestDispatcher(testScheduler))
    store.refresh()
    givenExits(listOf(exit(2, 1_000, ApplicationExitInfo.REASON_ANR), exit(1, 5_000, ApplicationExitInfo.REASON_ANR)))
    store.refresh()
    assertEquals(listOf(1 to 5_000L, 2 to 1_000L), store.reports.value.map { it.pid to it.occurredAtMillis })
  }

  @Test
  fun `OS の記録が読めなくても、残してある記録は見せる`() = runTest {
    files.write(crash(pid = 1, at = 1_000))
    every { source.read(any()) } throws SecurityException("boom")
    val store = store(StandardTestDispatcher(testScheduler))
    store.refresh()
    assertEquals(1, store.reports.value.size)
  }

  @Test
  fun `起動時に版を OS に預ける`() {
    CrashReportStore(files, source, "dev", StandardTestDispatcher()).recordVersion("0.5.0", 412)
    verify { source.setProcessStateSummary("0.5.0\n412") }
  }
}
