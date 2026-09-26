package com.pathly.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.BuildConfig
import com.pathly.data.backup.BackupCounts
import com.pathly.data.backup.BackupException
import com.pathly.data.settings.LocationAccuracy
import com.pathly.util.DateFormatters
import java.util.Date

@Composable
fun SettingsScreen(
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val interval by viewModel.gpsIntervalSeconds.collectAsStateWithLifecycle()
  val accuracy by viewModel.locationAccuracy.collectAsStateWithLifecycle()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "設定",
      style = MaterialTheme.typography.headlineMedium,
    )

    Text(
      text = "GPS記録間隔",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 8.dp),
    )
    Text(
      text = "短いほど軌跡が滑らかですが電池を多く使います。長いほど省電力です。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    viewModel.gpsIntervalOptions.forEach { seconds ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .selectable(
            selected = interval == seconds,
            onClick = { viewModel.setGpsIntervalSeconds(seconds) },
          )
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = interval == seconds,
          onClick = { viewModel.setGpsIntervalSeconds(seconds) },
        )
        Text(
          text = gpsIntervalLabel(seconds),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }

    Text(
      text = "位置の取り方",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = "高精度は衛星（GNSS）を積極的に使うので、電車や車で移動中の取りこぼしが減りますが電池を多く使います。" +
        "トンネルなど衛星が見えない場所は、どちらでも取れません。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    viewModel.locationAccuracyOptions.forEach { option ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .selectable(
            selected = accuracy == option,
            onClick = { viewModel.setLocationAccuracy(option) },
          )
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = accuracy == option,
          onClick = { viewModel.setLocationAccuracy(option) },
        )
        Text(
          text = locationAccuracyLabel(option),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }

    Text(
      text = "※ 変更は次に記録を開始したときから反映されます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp),
    )

    BackupSection(viewModel)
  }
}

/**
 * 記録・場所・設定の書き出しと読み込み（→ docs/specs/backup.md）。
 * 読み込みは全部の入れ替えで、確定するとアプリを再起動して差し替える。
 */
@Composable
private fun BackupSection(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  val state by viewModel.backup.collectAsStateWithLifecycle()
  val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_ZIP)) { uri ->
    if (uri != null) viewModel.export(uri)
  }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) viewModel.prepareImport(uri)
  }

  Text(
    text = "データ",
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = 24.dp),
  )
  Text(
    text = "記録・場所・設定をまとめて1つのファイルに書き出し、あとで読み込めます。" +
      "端末の故障や機種変更に備えて、ときどき書き出しておいてください。",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
      onClick = { exportLauncher.launch(exportFileName()) },
      enabled = !state.busy,
    ) { Text("書き出す") }
    OutlinedButton(
      onClick = { importLauncher.launch(arrayOf(MIME_ZIP, "application/octet-stream")) },
      enabled = !state.busy && !isRecording,
    ) { Text("読み込む") }
  }
  state.beforeImportSavedAtMillis?.let { savedAt ->
    TextButton(
      onClick = viewModel::prepareUndoImport,
      enabled = !state.busy && !isRecording,
    ) { Text("読み込む前の状態に戻す（${formatDateTime(savedAt)}に退避）") }
  }
  if (isRecording) {
    Text(
      text = "記録中は読み込めません。記録を止めてから行ってください。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  if (state.busy) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
  }

  state.confirm?.let { confirm ->
    AlertDialog(
      onDismissRequest = viewModel::cancelImport,
      title = { Text(if (confirm.isUndo) "読み込む前の状態に戻しますか？" else "このファイルで置き換えますか？") },
      text = {
        val s = confirm.summary
        Text(
          "今のデータ（記録・場所・設定）をすべて、このファイルの内容に置き換えます。\n\n" +
            "・${if (confirm.isUndo) "退避" else "書き出し"}日時: ${formatDateTime(s.exportedAtMillis)}\n" +
            countLines(s.counts) + "\n\n" +
            "今のデータはアプリ内に退避され、「読み込む前の状態に戻す」で戻せます。" +
            "置き換えるとアプリが再起動します。",
        )
      },
      confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("置き換える") } },
      dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("やめる") } },
    )
  }

  state.message?.let { message ->
    AlertDialog(
      onDismissRequest = viewModel::dismissMessage,
      text = { Text(backupMessageText(message)) },
      confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
    )
  }

  if (state.restartRequired) {
    // 閉じさせない。差し替えは次の起動で行われるので、ここで必ず再起動する。
    AlertDialog(
      onDismissRequest = {},
      title = { Text("再起動します") },
      text = { Text("読み込みの準備ができました。アプリを再起動してデータを入れ替えます。") },
      confirmButton = { TextButton(onClick = { restartApp(context) }) { Text("再起動") } },
    )
  }
}

private const val MIME_ZIP = "application/zip"

/** 書き出しファイルの既定の名前。開発版はファイル名でも見分けられるようにする。 */
private fun exportFileName(): String {
  val prefix = if (BuildConfig.DEBUG) "pathly-dev-backup" else "pathly-backup"
  return "$prefix-${DateFormatters.fileStamp(Date())}.zip"
}

private fun formatDateTime(millis: Long): String = Date(millis).let { "${DateFormatters.shortDate(it)} ${DateFormatters.shortTime(it)}" }

/**
 * 書き出しファイルに入っている件数を、確認画面と書き出し完了で同じ形に並べる。
 * 古い版で書き出したファイルに無い表（null）は行ごと省く。
 */
private fun countLines(c: BackupCounts): String = buildList {
  add("・経路 ${number(c.tracks)}件" + (c.points?.let { "（位置 ${number(it)}点）" } ?: ""))
  c.stops?.let { add("・立ち寄り ${number(it)}件") }
  val placeDetail = listOfNotNull(
    c.wishlist?.let { "行きたい ${number(it)}件" },
    c.visited?.let { "訪問済み ${number(it)}件" },
  ).joinToString("・")
  add("・場所 ${number(c.places)}件" + if (placeDetail.isNotEmpty()) "（$placeDetail）" else "")
}.joinToString("\n")

private fun number(n: Int): String = "%,d".format(n)

private fun backupMessageText(message: BackupMessage): String = when (message) {
  is BackupMessage.Exported -> "書き出しました。\n\n" + countLines(message.counts)

  is BackupMessage.Failed -> when (message.reason) {
    BackupException.Reason.NOT_A_BACKUP -> "Pathly の書き出しファイルではないため、読み込めません。"
    BackupException.Reason.UNSUPPORTED_FORMAT -> "このアプリでは読めない形式のファイルです。アプリを最新にしてください。"
    BackupException.Reason.NEWER_VERSION -> "新しい版のアプリで書き出されたファイルです。アプリを最新にしてから読み込んでください。"
    BackupException.Reason.BROKEN -> "ファイルが壊れているため、読み込めません。"
    BackupException.Reason.RECORDING -> "記録中は読み込めません。記録を止めてから行ってください。"
    null -> "うまくいきませんでした。もう一度お試しください。"
  }
}

/**
 * アプリを起動し直す。読み込みは次の起動の最初に差し替えるので、プロセスごと終わらせる
 * （アクティビティを作り直すだけでは、古い DB を開いたシングルトンが残る）。
 */
private fun restartApp(context: Context) {
  val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
  context.startActivity(intent)
  Runtime.getRuntime().exit(0)
}

private fun locationAccuracyLabel(accuracy: LocationAccuracy): String = when (accuracy) {
  LocationAccuracy.BALANCED -> "省電力（標準）"
  LocationAccuracy.HIGH -> "高精度（電池大）"
}

private fun gpsIntervalLabel(seconds: Int): String = when (seconds) {
  5 -> "5秒（高精度・電池大）"
  10 -> "10秒（標準）"
  30 -> "30秒（省電力）"
  60 -> "60秒（最省電力）"
  else -> "${seconds}秒"
}
