package com.pathly.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pathly.domain.model.RegisteredPlace

/** 近接確認で、何をしようとしていたか（文言を分ける）。 */
enum class NearbyConfirmPurpose(
  val message: String,
  val createNewLabel: String,
) {
  /** 地図の空き地点を場所として登録する。 */
  REGISTER_PLACE("新しく登録せず、この場所に紐付けますか？ 別の場所なら「新規で登録」を選んでください。", "新規で登録"),

  /** 立ち寄りを手で追加する。 */
  ADD_STOP("この立ち寄りをこの場所に紐付けますか？ 別の場所なら「新規で追加」を選んでください。", "新規で追加"),
}

/**
 * 近接確認。登録済みの場所を地図に出していない（トグルOFF・絞り込みで隠れている）ときのフォールバックで、
 * 場所を登録しよう・立ち寄りを追加しようとした地点の近く（検出半径）に既存の場所があれば、それに紐付けるか
 * 新規に作るかを確認する。重複 place を気づかず増やすのを防ぐ。
 */
@Composable
fun NearbyPlaceConfirmDialog(
  place: RegisteredPlace,
  purpose: NearbyConfirmPurpose,
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
          purpose.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onLink) { Text("この場所に紐付け") }
    },
    dismissButton = {
      TextButton(onClick = onCreateNew) { Text(purpose.createNewLabel) }
    },
  )
}
