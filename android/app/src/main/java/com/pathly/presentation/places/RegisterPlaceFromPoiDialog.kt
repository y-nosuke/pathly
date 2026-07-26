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

/**
 * 地図上の施設（POI）をタップしたときに出す共通の登録ダイアログ。
 * 記録画面・経路詳細・場所詳細のどのマップからでも使う（シートや入力欄と干渉しないようモーダル）。
 * 名前は POI 名で初期化。行きたい ON なら呼び出し側で wishlist にも入れる。
 */
@Composable
internal fun RegisterPlaceFromPoiDialog(
  poi: PointOfInterest,
  onDismiss: () -> Unit,
  onRegister: (name: String, wishlist: Boolean) -> Unit,
) {
  var name by remember(poi) { mutableStateOf(poi.name) }
  var wishlist by remember(poi) { mutableStateOf(false) }

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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("行きたいに登録")
          Switch(checked = wishlist, onCheckedChange = { wishlist = it })
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onRegister(name, wishlist) }) { Text("登録") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
