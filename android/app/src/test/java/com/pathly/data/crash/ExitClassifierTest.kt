package com.pathly.data.crash

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitClassifierTest {

  private val cached = RunningAppProcessInfo.IMPORTANCE_CACHED
  private val fgs = RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE

  @Test
  fun `落ちた・ANR・起動失敗・資源の使いすぎは、裏にいても異常`() {
    assertEquals(CrashKind.CRASH, ExitClassifier.kindOf(ApplicationExitInfo.REASON_CRASH, cached))
    assertEquals(CrashKind.NATIVE_CRASH, ExitClassifier.kindOf(ApplicationExitInfo.REASON_CRASH_NATIVE, cached))
    assertEquals(CrashKind.ANR, ExitClassifier.kindOf(ApplicationExitInfo.REASON_ANR, cached))
    assertEquals(CrashKind.START_FAILED, ExitClassifier.kindOf(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, cached))
    assertEquals(CrashKind.RESOURCE_OVERUSE, ExitClassifier.kindOf(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, cached))
  }

  @Test
  fun `メモリ不足などの停止は、前景か記録中（前景サービス）のときだけ異常`() {
    assertEquals(CrashKind.KILLED_BY_OS, ExitClassifier.kindOf(ApplicationExitInfo.REASON_LOW_MEMORY, fgs))
    assertEquals(
      CrashKind.KILLED_BY_OS,
      ExitClassifier.kindOf(ApplicationExitInfo.REASON_SIGNALED, RunningAppProcessInfo.IMPORTANCE_FOREGROUND),
    )
    assertEquals(CrashKind.KILLED_BY_OS, ExitClassifier.kindOf(ApplicationExitInfo.REASON_OTHER, RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE))
    assertNull(ExitClassifier.kindOf(ApplicationExitInfo.REASON_LOW_MEMORY, cached))
    assertNull(ExitClassifier.kindOf(ApplicationExitInfo.REASON_SIGNALED, RunningAppProcessInfo.IMPORTANCE_SERVICE))
  }

  @Test
  fun `自分で終えた・本人が止めた・更新・権限の変更は異常にしない`() {
    listOf(
      ApplicationExitInfo.REASON_EXIT_SELF,
      ApplicationExitInfo.REASON_USER_REQUESTED,
      ApplicationExitInfo.REASON_USER_STOPPED,
      ApplicationExitInfo.REASON_PACKAGE_UPDATED,
      ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
      ApplicationExitInfo.REASON_PERMISSION_CHANGE,
    ).forEach { reason -> assertNull("reason=$reason", ExitClassifier.kindOf(reason, fgs)) }
  }
}
