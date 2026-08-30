package com.pathly.presentation.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// 経路そのものを操作するダイアログ。一覧（HistoryScreen）と詳細（TrackDetailScreen）で共有する。

/**
 * 経路の名前を付ける・直すダイアログ。空で保存すると**未命名に戻る**（→ docs/specs/tracks.md）。
 * 一覧のカードのメニューからも、詳細のヘッダーからも同じものを開く。
 */
@Composable
internal fun RenameTrackDialog(
  initialName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("経路の名前") },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("例: 鎌倉さんぽ") },
        supportingText = { Text("空にすると未命名に戻ります") },
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
