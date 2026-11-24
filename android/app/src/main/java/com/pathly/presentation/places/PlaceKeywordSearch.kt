package com.pathly.presentation.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.PlacePrediction
import com.pathly.ui.theme.PathlySpacing

/**
 * キーワードで Google の場所を探す欄と候補の一覧（「検索して追加」の中身）。
 * 場所タブの「検索して追加」と記録画面の検索で共有する（入口で探し方が変わらないように）。
 * 状態（入力・候補・検索中）と Places の呼び出しは呼び出し側が持つ。
 */
@Composable
internal fun PlaceKeywordSearchBody(
  query: String,
  predictions: List<PlacePrediction>,
  isSearching: Boolean,
  onQueryChange: (String) -> Unit,
  onSelectPrediction: (placeId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      label = { Text("店名・場所名で検索") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(PathlySpacing.sm))

    PlaceSearchPredictions(
      query = query,
      predictions = predictions,
      isSearching = isSearching,
      onSelectPrediction = onSelectPrediction,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

/**
 * キーワード検索の候補の一覧（検索中・候補なしの表示を含む）。[PlaceKeywordSearchBody] の下半分で、
 * 記録画面の地図の上の検索窓では、窓の下のカードにこれだけを出す。高さは [modifier] で決める
 * （全面なら fillMaxSize、カードなら中身に合わせて縮む）。
 */
@Composable
internal fun PlaceSearchPredictions(
  query: String,
  predictions: List<PlacePrediction>,
  isSearching: Boolean,
  onSelectPrediction: (placeId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  PlaceSearchCandidates(
    query = query,
    candidates = predictions,
    isSearching = isSearching,
    key = { it.placeId },
    modifier = modifier,
  ) { prediction ->
    PredictionRow(prediction = prediction, onClick = { onSelectPrediction(prediction.placeId) })
  }
}

/**
 * 候補の一覧の枠（検索中・候補なしの表示と、区切り線つきの行の並び）。行の描き方は [row] に任せる
 * （記録画面の検索窓は、1 つの候補を「地域へ移動」と「場所として登録」の 2 行に分けて出す）。
 */
@Composable
internal fun <T : Any> PlaceSearchCandidates(
  query: String,
  candidates: List<T>,
  isSearching: Boolean,
  key: (T) -> Any,
  modifier: Modifier = Modifier,
  row: @Composable (T) -> Unit,
) {
  when {
    isSearching -> {
      Box(modifier = modifier.padding(vertical = PathlySpacing.lg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }

    query.isNotBlank() && candidates.isEmpty() -> {
      Box(modifier = modifier.padding(vertical = PathlySpacing.lg), contentAlignment = Alignment.Center) {
        Text(
          text = "候補がありません\n（オンライン・キーワードを確認）",
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    else -> {
      LazyColumn(modifier = modifier) {
        items(candidates, key = key) { candidate ->
          row(candidate)
          HorizontalDivider()
        }
      }
    }
  }
}

/**
 * 候補の 1 行（名前と住所）。[leadingIcon] があれば頭に印を、[actionLabel] があれば右端に選ぶと何が起きるか（「登録」など）を出す。
 */
@Composable
internal fun PredictionRow(
  prediction: PlacePrediction,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
  actionLabel: String? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = PathlySpacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leadingIcon != null) {
      leadingIcon()
      Spacer(modifier = Modifier.width(PathlySpacing.md))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = prediction.primaryText,
        style = MaterialTheme.typography.bodyLarge,
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
    if (actionLabel != null) {
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = actionLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
      )
    }
  }
}
