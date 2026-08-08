package com.pathly.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.RegisteredPlace
import com.pathly.util.DateFormatters

/**
 * 地図の「登録済みの場所」マーカーをタップしたときの確認ダイアログ。
 * 選んだ既存 place（[place]）に、この経路の立ち寄りとして紐付ける（新規 place は作らない）。
 * 滞在区間は選んだ地点の座標付近の軌跡点から推定して見せる（[points]）。記録・履歴詳細で共用する。
 */
@Composable
fun LinkStopToPlaceDialog(
  place: RegisteredPlace,
  points: List<GpsPoint>,
  onConfirm: (arrival: java.util.Date, departure: java.util.Date) -> Unit,
  onDismiss: () -> Unit,
) {
  val (arrival, departure) = remember(place, points) {
    deriveStopWindow(points, place.latitude, place.longitude)
  }
  val durationMinutes = ((departure.time - arrival.time) / 1000 / 60).toInt()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("この場所に立ち寄りを追加") },
    text = {
      Column {
        Text(place.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
          place.statusLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val window = if (durationMinutes > 0) {
          "${DateFormatters.SHORT_TIME_FORMAT.format(arrival)}–" +
            "${DateFormatters.SHORT_TIME_FORMAT.format(departure)} ・ 滞在${durationMinutes}分"
        } else {
          "この地点付近（滞在時間は軌跡から推定）"
        }
        Text(window, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          "登録済みの場所に紐付けます（新しい場所は作りません）。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(arrival, departure) }) { Text("この場所に追加") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
