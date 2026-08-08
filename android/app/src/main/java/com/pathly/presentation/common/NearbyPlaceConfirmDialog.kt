package com.pathly.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pathly.domain.model.RegisteredPlace

/**
 * 手動追加の近接確認（③）。登録済みの場所を地図に出していない（トグルOFF）ときのフォールバックで、
 * 追加しようとした地点の近く（検出半径）に既存の場所があれば、それに紐付けるか新規に作るかを確認する。
 * 重複 place を気づかず増やすのを防ぐ。
 */
@Composable
fun NearbyPlaceConfirmDialog(
  place: RegisteredPlace,
  onLink: () -> Unit,
  onCreateNew: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("近くに登録済みの場所があります") },
    text = {
      Column {
        Text(place.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
          place.statusLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          "この立ち寄りをこの場所に紐付けますか？ 別の場所なら「新規で追加」を選んでください。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onLink) { Text("この場所に紐付け") }
    },
    dismissButton = {
      TextButton(onClick = onCreateNew) { Text("新規で追加") }
    },
  )
}
