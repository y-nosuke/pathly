package com.pathly.presentation.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.Stop
import com.pathly.util.DateFormatters
import java.util.Locale

// 経路詳細から開くダイアログ群（場所名の編集・立ち寄りメモ・GPS詳細のデバッグ表示）。

/**
 * デバッグ用: 生 GPS 点（gps_points）の付随情報を確認するダイアログ。
 * 保存はしているが通常 UI に出していない値（provider / 各種精度 / MSL 高度 / 単調時刻 / mock / extras）を
 * 集計＋点ごとに一覧する。デバッグビルドからのみ開く。
 */
@Composable
internal fun GpsDebugDialog(
  points: List<GpsPoint>,
  onDismiss: () -> Unit,
) {
  fun f(v: Float?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "―"
  fun d(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "―"

  val providers = points.mapNotNull { it.provider }.groupingBy { it }.eachCount()
  val withVertical = points.count { it.verticalAccuracyMeters != null }
  val withMsl = points.count { it.mslAltitudeMeters != null }
  val withExtras = points.count { !it.extrasJson.isNullOrBlank() }
  val mockCount = points.count { it.isMock }
  val accuracies = points.map { it.accuracy }
  val accSummary = if (accuracies.isEmpty()) {
    "―"
  } else {
    "min ${f(accuracies.min())} / 平均 ${f(accuracies.average().toFloat())} / max ${f(accuracies.max())}"
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("GPS詳細（デバッグ）") },
    text = {
      Column {
        // 集計サマリ。
        Text("点数: ${points.size}", style = MaterialTheme.typography.bodySmall)
        Text(
          "provider: " + (providers.entries.joinToString { "${it.key}×${it.value}" }.ifEmpty { "―" }),
          style = MaterialTheme.typography.bodySmall,
        )
        Text("水平精度(m): $accSummary", style = MaterialTheme.typography.bodySmall)
        Text(
          "鉛直精度あり: $withVertical / MSL高度あり: $withMsl / extrasあり: $withExtras / mock: $mockCount",
          style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 点ごとの明細（多いのでスクロール）。
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
          itemsIndexed(points) { i, p ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
              Text(
                "#$i  ${DateFormatters.time(p.timestamp)}" +
                  (if (p.isMock) "  [MOCK]" else ""),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                "acc=${f(p.accuracy)} vacc=${f(p.verticalAccuracyMeters)} " +
                  "sacc=${f(p.speedAccuracyMetersPerSecond)} bacc=${f(p.bearingAccuracyDegrees)}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                "alt=${d(p.altitude)} msl=${d(p.mslAltitudeMeters)}(±${f(p.mslAltitudeAccuracyMeters)}) " +
                  "spd=${f(p.speed)} brg=${f(p.bearing)}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                "provider=${p.provider ?: "―"}  ert=${p.elapsedRealtimeNanos}",
                style = MaterialTheme.typography.bodySmall,
              )
              if (!p.extrasJson.isNullOrBlank()) {
                Text(
                  "extras=${p.extrasJson}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

@Composable
internal fun PlaceNameDialog(
  stop: Stop,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember(stop.id) { mutableStateOf(stop.place.name ?: "") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("場所の名前") },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("例: スターバックス ◯◯店") },
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

/** 立ち寄り（訪問）のメモを編集するダイアログ。stop 単位・複数行入力。空で保存すると消える。 */
@Composable
internal fun StopNoteDialog(
  stop: Stop,
  onDismiss: () -> Unit,
  onConfirm: (String?) -> Unit,
) {
  var text by remember(stop.id) { mutableStateOf(stop.note ?: "") }
  // 開いたらすぐ入力できるよう、メモ欄にフォーカスして IME を出す。
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("メモ") },
    text = {
      Column {
        Text(
          text = stop.place.displayName,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          placeholder = { Text("この立ち寄りのメモ（例: 限定パフェが美味しかった）") },
          minLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
