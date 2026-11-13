package com.pathly.presentation.settings

import android.app.ApplicationExitInfo
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.data.crash.CrashKind
import com.pathly.data.crash.CrashReport
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashReportSectionTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private val crash = CrashReport(
    occurredAtMillis = 1_731_550_000_000L,
    kind = CrashKind.CRASH,
    versionName = "0.5.0",
    versionCode = 412,
    pid = 1,
    device = "Google Pixel 8 / Android 16 (API 36)",
    detail = "スレッド: main\njava.lang.IllegalStateException: boom\n\tat com.pathly.Foo.bar(Foo.kt:1)",
  )
  private val killed = crash.copy(
    occurredAtMillis = 1_731_460_000_000L,
    kind = CrashKind.KILLED_BY_OS,
    osReason = ApplicationExitInfo.REASON_LOW_MEMORY,
    pid = 2,
    detail = "理由: LOW_MEMORY（3）",
  )

  private var cleared = 0

  private fun setSection(reports: List<CrashReport>) {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        Column { CrashReportSection(reports = reports, onClear = { cleared++ }) }
      }
    }
  }

  @Test
  fun `記録が無ければありませんと出し、消すボタンは出さない`() {
    setSection(emptyList())
    composeTestRule.onNodeWithText("ありません").assertIsDisplayed()
    composeTestRule.onNodeWithText("記録を消す").assertDoesNotExist()
  }

  @Test
  fun `記録を 1 件 1 行で種類つきで並べる`() {
    setSection(listOf(crash, killed))
    composeTestRule.onAllNodesWithTag(CRASH_REPORT_ROW_TAG).assertCountEquals(2)
    composeTestRule.onNodeWithText("落ちた（例外）", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("OS に止められた（メモリ不足）", substring = true).assertIsDisplayed()
  }

  @Test
  fun `押すと全文が出て、コピーするとクリップボードに全文が入る`() {
    setSection(listOf(crash))
    composeTestRule.onNodeWithTag(CRASH_REPORT_ROW_TAG).performClick()
    composeTestRule.onNodeWithTag(CRASH_REPORT_DETAIL_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithText("java.lang.IllegalStateException: boom", substring = true).assertIsDisplayed()

    composeTestRule.onNodeWithText("コピー").performClick()
    composeTestRule.waitForIdle()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var copied: String? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }
    assertEquals(crashReportText(crash), copied)

    composeTestRule.onNodeWithText("閉じる").performClick()
    composeTestRule.onNodeWithTag(CRASH_REPORT_DETAIL_TAG).assertDoesNotExist()
  }

  @Test
  fun `消すは確かめてから消す`() {
    setSection(listOf(crash))
    composeTestRule.onNodeWithText("記録を消す").performClick()
    composeTestRule.onNodeWithText("やめる").performClick()
    assertEquals(0, cleared)

    composeTestRule.onNodeWithText("記録を消す").performClick()
    composeTestRule.onNodeWithText("消す").performClick()
    assertTrue(cleared == 1)
  }
}
