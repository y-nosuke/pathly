package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.presentation.places.PlaceKeywordSearchBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 記録画面の「検索して登録」: キーワードで Google の場所を探し、選んだ 1 件を [onSelected] で返す。
 * 登録そのものは地図の上の場所シートで行う（地図タップ・POI タップと同じシート）。
 * 探す欄と候補の一覧は場所タブの「検索して追加」と共有する（[PlaceKeywordSearchBody]）。
 */
@Composable
internal fun TrackingPlaceSearchPanel(
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult?,
  onSelected: (PlaceSearchResult) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var query by remember { mutableStateOf("") }
  var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var isFetching by remember { mutableStateOf(false) }
  var fetchFailed by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  BackHandler { onClose() }

  // 打鍵ごとには叩かない（場所タブの「検索して追加」と同じ 300ms のデバウンス）。
  LaunchedEffect(query) {
    fetchFailed = false
    if (query.isBlank()) {
      predictions = emptyList()
      isSearching = false
      return@LaunchedEffect
    }
    delay(300)
    isSearching = true
    predictions = onSearchPredictions(query)
    isSearching = false
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("trackingPlaceSearch"),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .statusBarsPadding()
        .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "検索して登録",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onClose) { Text("閉じる") }
      }

      if (fetchFailed) {
        Text(
          text = "場所の取得に失敗しました（オフライン等）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      PlaceKeywordSearchBody(
        query = query,
        predictions = predictions,
        isSearching = isSearching || isFetching,
        onQueryChange = { query = it },
        onSelectPrediction = { placeId ->
          if (isFetching) return@PlaceKeywordSearchBody
          scope.launch {
            isFetching = true
            val result = onFetchPrediction(placeId)
            isFetching = false
            if (result != null) onSelected(result) else fetchFailed = true
          }
        },
      )
    }
  }
}
