package com.pathly.presentation.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
 * 欄の名札（ラベル）に、名前が無いときに見出しになる日付（[defaultName]）を出す（場所の名前のダイアログと同じ形。
 * 空のうちは欄の中に薄い字で、打ち始めると枠の上に移る）。
 */
@Composable
internal fun RenameTrackDialog(
  initialName: String,
  defaultName: String,
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
        label = { Text(defaultName) },
        // 欄の中の名札（日付）は、打った字（自分の名前）と見分けられるよう薄くする。
        colors = OutlinedTextFieldDefaults.colors(unfocusedLabelColor = MaterialTheme.colorScheme.outline),
        placeholder = { Text("例: 鎌倉さんぽ") },
        supportingText = { Text("空のときは日付で表示します") },
      )
    },
    confirmButton = {
      // 名前が変わっていないうちは押せない（名前の無い経路を空のまま保存しても何も起きないため）。
      // 名前を消して空にしたときは押せる（未命名に戻す）。
      TextButton(onClick = { onConfirm(text) }, enabled = text.trim() != initialName.trim()) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
