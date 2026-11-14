package com.pathly.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 名前の無い場所を文中で呼ぶときの言い方。 */
private const val UNNAMED_PLACE = "名前の無い場所"

/**
 * 同じ施設と分かった場所に、まとめるか別のまま残すかを選んでもらう（→ adr/0037）。
 *
 * - **まとめる**: この場所を相手（その施設を既に持っている方）にまとめる。立ち寄り・メモ・行きたいは引き継ぐ。
 * - **別の施設を選び直す**: Google が隣の店などを拾ったとき。近くの施設から選び直す。
 * - **別の場所として残す**: どちらでもない。施設情報なしで残し、二度と聞かない。
 *
 * 「あとで」は何も決めずに閉じる（確認待ちのまま残る）。
 *
 * @param placeName この場所の表示名（無ければ null）。
 * @param otherName 相手の表示名（無ければ null）。
 * @param remaining このあとに残っている確認待ちの件数（経路詳細でまとめて聞くとき）。0 なら出さない。
 */
@Composable
fun SameFacilityDialog(
  placeName: String?,
  otherName: String?,
  onMerge: () -> Unit,
  onPickAnother: () -> Unit,
  onKeepSeparate: () -> Unit,
  onDismiss: () -> Unit,
  remaining: Int = 0,
) {
  val other = otherName ?: UNNAMED_PLACE
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("同じ施設の場所があります") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "「${placeName ?: UNNAMED_PLACE}」は、登録済みの「$other」と同じ施設のようです。",
          style = MaterialTheme.typography.bodyMedium,
        )
        if (remaining > 0) {
          Text(
            "ほかに $remaining 件あります",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
          Text("「$other」にまとめる")
        }
        Text(
          "立ち寄り・メモ・行きたいは「$other」に引き継ぎます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth()) {
          Text("別の施設を選び直す")
        }
        OutlinedButton(onClick = onKeepSeparate, modifier = Modifier.fillMaxWidth()) {
          Text("施設情報なしで、別の場所として残す")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("あとで") }
    },
  )
}
