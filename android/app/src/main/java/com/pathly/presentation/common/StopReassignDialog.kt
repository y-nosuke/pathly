package com.pathly.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop

/**
 * 誤検知の訂正: 確定済みの立ち寄り（[stop]）の場所を、近くの POI 候補から選び直す／自分で入力し直す
 * ダイアログ。**この訪問だけ**付け替える（他の経路・訪問は不変）。記録画面・履歴詳細で共用する。
 */
@Composable
fun StopReassignDialog(
  stop: Stop,
  onFetchCandidates: suspend (Double, Double) -> List<PlaceSearchResult>,
  onConfirm: (chosen: PlaceSearchResult?, customName: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var candidates by remember { mutableStateOf<List<PlaceSearchResult>?>(null) } // null=読込中
  var selected by remember { mutableStateOf<PlaceSearchResult?>(null) }
  var customName by remember { mutableStateOf("") }

  LaunchedEffect(stop.id) {
    candidates = onFetchCandidates(stop.place.latitude, stop.place.longitude)
  }

  val canConfirm = selected != null || customName.isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("場所を選び直す") },
    text = {
      Column {
        Text(
          "現在: ${stop.place.name ?: stop.place.googleName ?: "未命名"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          "この訪問だけを、正しい場所に付け替えます（他の経路・訪問は変わりません）。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (val list = candidates) {
          null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("近くの候補を検索中…", style = MaterialTheme.typography.bodySmall)
          }

          else -> {
            if (list.isEmpty()) {
              Text(
                "近くの候補は見つかりませんでした（名前を入力して直せます）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
              )
            } else {
              Column(
                modifier = Modifier
                  .heightIn(max = 220.dp)
                  .verticalScroll(rememberScrollState()),
              ) {
                list.forEach { poi ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        selected = poi
                        customName = ""
                      }
                      .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    RadioButton(
                      selected = selected?.googlePlaceId == poi.googlePlaceId,
                      onClick = {
                        selected = poi
                        customName = ""
                      },
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                      Text(poi.name ?: "（名称不明）", style = MaterialTheme.typography.bodyMedium)
                      poi.category?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
              value = customName,
              onValueChange = {
                customName = it
                if (it.isNotEmpty()) selected = null
              },
              singleLine = true,
              label = { Text("自分で入力（任意）") },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canConfirm,
        onClick = { onConfirm(selected, customName.trim().ifBlank { null }) },
      ) {
        Text("この場所に直す")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
