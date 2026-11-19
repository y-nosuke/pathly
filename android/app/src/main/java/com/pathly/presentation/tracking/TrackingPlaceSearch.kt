package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.PlacePrediction
import com.pathly.presentation.places.PlaceSearchPredictions
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 記録画面の地図に重ねる検索窓: 地図はそのままに、上端に検索窓を重ね、候補は窓の下のカードに出す。
 * 「検索して登録」（施設を探して場所シートで登録）と「地域へ移動」（地名を探して地図を移す）で共有する。
 * 選んだ候補を [onFetchPrediction] で確定し、取れたら [onSelected] で返す（取れなければ [fetchFailedText] を出す）。
 * [selectionError] が文言を返す候補は確定せず（取得もしない）、その文言を窓に出す（地域の目印の上限など）。
 * 候補の一覧は場所タブの「検索して追加」と共有する（[PlaceSearchPredictions]）。
 * 地図をタップしたとき・戻る・× で閉じる（閉じる処理は呼び出し側の [onClose]）。
 */
@Composable
internal fun <T : Any> TrackingPlaceSearchBar(
  placeholder: String,
  fetchFailedText: String,
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (prediction: PlacePrediction) -> T?,
  selectionError: (prediction: PlacePrediction) -> String? = { null },
  onSelected: (T) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var query by remember { mutableStateOf("") }
  var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var isFetching by remember { mutableStateOf(false) }
  // 窓に出す失敗の文言（取得の失敗・選べない候補）。打ち直すと消す。
  var errorText by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  // 候補のカードは画面の半分ほどまで。その下は地図が見えるようにする。
  val maxCardHeight = with(LocalDensity.current) {
    (LocalWindowInfo.current.containerSize.height * 0.45f).toDp()
  }

  BackHandler { onClose() }

  // 開いたらすぐ打てるようにする。
  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  // 打鍵ごとには叩かない（場所タブの「検索して追加」と同じ 300ms のデバウンス）。
  LaunchedEffect(query) {
    errorText = null
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

  Column(
    modifier = modifier
      .statusBarsPadding()
      .padding(12.dp)
      .fillMaxWidth()
      .testTag("trackingPlaceSearch"),
  ) {
    Surface(
      shape = PathlyShapes.pill,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = PathlyElevation.floating,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painter = painterResource(R.drawable.ic_search),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .padding(start = 16.dp)
            .size(20.dp),
        )
        TextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(placeholder) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          // 確定キーは候補を選ぶまでの間、キーボードを下げて地図を見せるだけ。
          keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
          ),
          modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester)
            .testTag("trackingPlaceSearchField"),
        )
        IconButton(onClick = onClose) {
          Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "検索を閉じる")
        }
      }
    }

    // 何か打ってから候補のカードを出す（空のうちは地図を隠さない）。
    if (query.isNotBlank() || errorText != null) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = PathlyElevation.floating,
        modifier = Modifier
          .padding(top = 8.dp)
          .fillMaxWidth()
          .heightIn(max = maxCardHeight),
      ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          errorText?.let { text ->
            Text(
              text = text,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
          PlaceSearchPredictions(
            query = query,
            predictions = predictions,
            isSearching = isSearching || isFetching,
            onSelectPrediction = { placeId ->
              if (isFetching) return@PlaceSearchPredictions
              val prediction = predictions.firstOrNull { it.placeId == placeId } ?: return@PlaceSearchPredictions
              selectionError(prediction)?.let { message ->
                errorText = message
                return@PlaceSearchPredictions
              }
              scope.launch {
                isFetching = true
                val result = onFetchPrediction(prediction)
                isFetching = false
                if (result != null) {
                  keyboard?.hide()
                  onSelected(result)
                } else {
                  errorText = fetchFailedText
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
