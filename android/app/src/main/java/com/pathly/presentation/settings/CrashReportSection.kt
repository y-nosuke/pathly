package com.pathly.presentation.settings

import android.app.ApplicationExitInfo
import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pathly.data.crash.CrashKind
import com.pathly.data.crash.CrashReport
import com.pathly.data.crash.CrashReportFiles
import com.pathly.util.DateFormatters
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 異常終了の記録（→ docs/specs/crash-reports.md）。1 件 1 行で並べ、押すと全文をダイアログで見せてコピーできる。
 */
@Composable
fun CrashReportSection(
  reports: List<CrashReport>,
  onClear: () -> Unit,
) {
  var opened by remember { mutableStateOf<CrashReport?>(null) }
  var confirmClear by remember { mutableStateOf(false) }

  Text(
    text = "異常終了の記録",
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = 24.dp),
  )
  Text(
    text = "落ちた・応答しなくなった・OS に止められたときの記録です（新しい順に ${CrashReportFiles.MAX_REPORTS} 件まで）。" +
      "端末の中にだけ残り、どこにも送りません。不具合を知らせるときに、コピーして貼ってください。",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (reports.isEmpty()) {
    Text(
      text = "ありません",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(vertical = 8.dp),
    )
  } else {
    reports.forEach { report ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { opened = report }
          .padding(vertical = 10.dp)
          .testTag(CRASH_REPORT_ROW_TAG),
      ) {
        Text(
          text = "${formatOccurredAt(report.occurredAtMillis)}  ${crashReportTitle(report)}",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    OutlinedButton(onClick = { confirmClear = true }) { Text("記録を消す") }
  }

  opened?.let { report -> CrashReportDialog(report, onDismiss = { opened = null }) }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      text = { Text("異常終了の記録をすべて消しますか？元には戻せません。") },
      confirmButton = {
        TextButton(onClick = {
          confirmClear = false
          onClear()
        }) { Text("消す") }
      },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("やめる") } },
    )
  }
}

@Composable
private fun CrashReportDialog(report: CrashReport, onDismiss: () -> Unit) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val text = crashReportText(report)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${formatOccurredAt(report.occurredAtMillis)} ${crashReportTitle(report)}") },
    text = {
      SelectionContainer {
        Text(
          text = text,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState())
            .testTag(CRASH_REPORT_DETAIL_TAG),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = {
        // Android 13 以降は OS がコピーしたことを知らせるので、アプリからは出さない。
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text))) }
      }) { Text("コピー") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

/** 一覧と見出しに出す種類。 */
fun crashReportTitle(report: CrashReport): String = when (report.kind) {
  CrashKind.CRASH -> "落ちた（例外）"
  CrashKind.NATIVE_CRASH -> "落ちた（ネイティブ）"
  CrashKind.ANR -> "応答なし（ANR）"
  CrashKind.START_FAILED -> "起動に失敗した"
  CrashKind.RESOURCE_OVERUSE -> "OS に止められた（資源の使いすぎ）"
  CrashKind.KILLED_BY_OS -> "OS に止められた（${osReasonLabel(report.osReason)}）"
}

private fun osReasonLabel(reason: Int?): String = when (reason) {
  ApplicationExitInfo.REASON_LOW_MEMORY -> "メモリ不足"
  ApplicationExitInfo.REASON_SIGNALED -> "強制終了"
  ApplicationExitInfo.REASON_FREEZER -> "凍結中"
  ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依存先の停止"
  ApplicationExitInfo.REASON_OTHER -> "その他"
  else -> "理由不明"
}

/** コピーして Issue に貼る全文。見出し（種類・日時・版・端末）と中身。 */
fun crashReportText(report: CrashReport): String = buildString {
  appendLine("種類: ${crashReportTitle(report)}")
  appendLine("日時: ${formatOccurredAt(report.occurredAtMillis)}")
  val version = report.versionName?.let { name -> name + (report.versionCode?.let { "（ビルド $it）" } ?: "") } ?: "不明"
  appendLine("版: $version")
  appendLine("端末: ${report.device}")
  appendLine()
  append(report.detail)
}

private fun formatOccurredAt(millis: Long): String = Date(millis).let { "${DateFormatters.shortDate(it)} ${DateFormatters.time(it)}" }

private const val CLIP_LABEL = "Pathly 異常終了の記録"
const val CRASH_REPORT_ROW_TAG = "crash_report_row"
const val CRASH_REPORT_DETAIL_TAG = "crash_report_detail"
