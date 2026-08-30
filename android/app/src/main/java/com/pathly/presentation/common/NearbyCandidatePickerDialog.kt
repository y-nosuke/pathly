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
import androidx.compose.material3.HorizontalDivider
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
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult

/**
 * 座標の近くの POI 候補を Nearby 検索して、ラジオで選ぶ（任意で自分でも入力／名前でも検索する）汎用ダイアログ。
 * 立ち寄りの選び直し（[StopReassignDialog]）と、場所詳細の「Googleで情報を取得」で共用する。
 *
 * @param reloadKey これが変わると候補を取り直す（LaunchedEffect のキー）。
 * @param allowCustomName 自分で名前を入力する導線を出すか（選び直しは true）。
 * @param onSearchPredictions キーワード検索の候補取得。[onFetchPrediction] と両方あるとき、座標の候補に
 *   出ないときの**名前で検索**フォールバックを表示する（座標がずれて周辺に出ない施設用）。
 * @param onFetchPrediction 検索候補を確定して座標つきの [PlaceSearchResult] にする。
 */
@Composable
fun NearbyCandidatePickerDialog(
  latitude: Double,
  longitude: Double,
  reloadKey: Any,
  title: String,
  currentLabel: String?,
  description: String?,
  confirmLabel: String,
  allowCustomName: Boolean,
  onFetchCandidates: suspend (Double, Double) -> List<PlaceSearchResult>,
  onConfirm: (chosen: PlaceSearchResult?, customName: String?) -> Unit,
  onDismiss: () -> Unit,
  onSearchPredictions: (suspend (String) -> List<PlacePrediction>)? = null,
  onFetchPrediction: (suspend (String) -> PlaceSearchResult?)? = null,
) {
  var candidates by remember { mutableStateOf<List<PlaceSearchResult>?>(null) } // null=読込中
  var selected by remember { mutableStateOf<PlaceSearchResult?>(null) }
  var customName by remember { mutableStateOf("") }

  // 名前で検索（フォールバック）。入力欄と候補の見せ方は共通部品に任せる。
  val searchEnabled = onSearchPredictions != null && onFetchPrediction != null

  LaunchedEffect(reloadKey) {
    candidates = onFetchCandidates(latitude, longitude)
  }

  val canConfirm = selected != null || (allowCustomName && customName.isNotBlank())

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        currentLabel?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        description?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                if (allowCustomName) {
                  "近くの候補は見つかりませんでした（名前を入力して直せます）。"
                } else if (searchEnabled) {
                  "近くに施設が見つかりませんでした（下の「名前で検索」で探せます）。"
                } else {
                  "近くに施設が見つかりませんでした（オンライン・座標を確認）。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
              )
            } else {
              Column(
                modifier = Modifier
                  .heightIn(max = 200.dp)
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
                        Text(it.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
              }
            }

            if (allowCustomName) {
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

            if (searchEnabled) {
              Spacer(modifier = Modifier.height(12.dp))
              HorizontalDivider()
              Spacer(modifier = Modifier.height(8.dp))
              PlaceNameSearchField(
                onSearchPredictions = onSearchPredictions!!,
                onFetchPrediction = onFetchPrediction!!,
                onPicked = { result ->
                  // 検索で選んだ施設を候補の先頭に足して選択状態にする（そのまま紐付けできる）。
                  candidates = listOf(result) +
                    (candidates?.filterNot { it.googlePlaceId == result.googlePlaceId } ?: emptyList())
                  selected = result
                  customName = ""
                },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canConfirm,
        onClick = { onConfirm(selected, customName.trim().ifBlank { null }) },
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
