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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 打鍵ごとに Places を叩かないための待ち時間。 */
private const val SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * 施設を**名前で探して選ぶ**入力欄。座標の近くの候補（Nearby）に出ない施設を拾うための共通部品。
 *
 * 座標だけを頼りにすると、代表点が敷地の中心にある広い施設・地図に POI が出ていない場所は
 * どうやっても選べない。立ち寄りの追加（[com.pathly.presentation.stops.ManualStopSheet]）、
 * 立ち寄りの選び直し・場所詳細の紐付け（[NearbyCandidatePickerDialog]）で共用する。
 *
 * 選ばれた施設は座標つきの [PlaceSearchResult] にしてから [onPicked] に渡す（呼び出し側は
 * 施設の座標をそのまま使える）。選び終えたら入力欄と候補は自分で片付ける。
 */
@Composable
fun PlaceNameSearchField(
  onSearchPredictions: suspend (String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (String) -> PlaceSearchResult?,
  onPicked: (PlaceSearchResult) -> Unit,
  modifier: Modifier = Modifier,
  heading: String? = "見つからない場合は名前で検索",
) {
  var query by remember { mutableStateOf("") }
  var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
  var searching by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  // キーワードのデバウンス検索（打鍵ごとに叩かない）。
  LaunchedEffect(query) {
    if (query.isBlank()) {
      predictions = emptyList()
      searching = false
      return@LaunchedEffect
    }
    delay(SEARCH_DEBOUNCE_MILLIS)
    searching = true
    predictions = onSearchPredictions(query)
    searching = false
  }

  Column(modifier = modifier) {
    heading?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
    }
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      singleLine = true,
      label = { Text("店名・場所名で検索") },
      modifier = Modifier.fillMaxWidth(),
    )
    if (searching) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("検索中…", style = MaterialTheme.typography.bodySmall)
      }
    }
    if (predictions.isNotEmpty()) {
      Column(
        modifier = Modifier
          .heightIn(max = 160.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        predictions.forEach { prediction ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                scope.launch {
                  val result = onFetchPrediction(prediction.placeId) ?: return@launch
                  onPicked(result)
                  query = ""
                  predictions = emptyList()
                }
              }
              .padding(vertical = 8.dp),
          ) {
            Text(
              text = prediction.primaryText,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (prediction.secondaryText.isNotBlank()) {
              Text(
                text = prediction.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}
