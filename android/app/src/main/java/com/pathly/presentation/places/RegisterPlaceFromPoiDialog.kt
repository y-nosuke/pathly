package com.pathly.presentation.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.PointOfInterest
import com.pathly.domain.model.Priority

/**
 * 地図上の施設（POI）をタップしたときに出す共通の登録ダイアログ。
 * 記録画面・経路詳細・場所詳細のどのマップからでも使う（シートや入力欄と干渉しないようモーダル）。
 * 「追加」フローと項目を揃える（名前・メモ・行きたい・優先度）。メモは常時、優先度は行きたい ON のとき。
 */
@Composable
internal fun RegisterPlaceFromPoiDialog(
  poi: PointOfInterest,
  onDismiss: () -> Unit,
  onRegister: (name: String, wishlist: Boolean, priority: Priority, memo: String?) -> Unit,
) {
  var name by remember(poi) { mutableStateOf(poi.name) }
  var memo by remember(poi) { mutableStateOf("") }
  var wishlist by remember(poi) { mutableStateOf(false) }
  var priority by remember(poi) { mutableStateOf(Priority.MEDIUM) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("この場所を登録") },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名前") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = memo,
          onValueChange = { memo = it },
          label = { Text("メモ（任意）") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("行きたいに登録")
          Switch(checked = wishlist, onCheckedChange = { wishlist = it })
        }
        if (wishlist) {
          Spacer(modifier = Modifier.height(8.dp))
          PrioritySelector(selected = priority, onSelect = { priority = it })
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onRegister(name, wishlist, priority, memo.ifBlank { null }) }) { Text("登録") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
