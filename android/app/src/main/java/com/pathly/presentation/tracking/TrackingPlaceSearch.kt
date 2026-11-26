package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.PlacePrediction
import com.pathly.presentation.places.PlaceSearchCandidates
import com.pathly.presentation.places.PredictionRow
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 記録画面の検索窓の候補を選んだときの動き。 */
internal enum class MapSearchAction {
  /** 場所として登録する（寄せて場所シート）。「場所」で探した候補（施設も地域も）。 */
  Register,

  /** 地域へ移動する（範囲に合わせて地図を移し、目印とチップ）。「地域」で探した候補。 */
  MoveToRegion,
}

/**
 * 記録画面の検索窓で何を探すか（窓の下の「場所｜地域」の切り替え）。窓を開くたびに [Place] から始める。
 * 立ち寄りの追加中は登録できないので、切り替えを出さずに [Region] だけ。
 */
internal enum class MapSearchMode(val label: String, val placeholder: String) {
  /** 種類で絞らずに探し、選ぶと場所として登録する（地域が出たら「場所として登録」）。 */
  Place("場所", "場所を検索"),

  /** 地域に絞って探し、選ぶと地域へ移動する。 */
  Region("地域", "地域を検索"),
}

/** 検索窓の候補の 1 行。 */
internal data class MapSearchCandidate(
  val prediction: PlacePrediction,
  val action: MapSearchAction,
) {
  val key: String get() = "${prediction.placeId}/$action"
}

/**
 * Autocomplete の候補を検索窓の行にする（関連順のまま、候補ごとに 1 行）。[mode] が「場所」なら登録（地域の候補は「場所として登録」）、
 * 「地域」なら地域へ移動。
 */
internal fun List<PlacePrediction>.toMapSearchCandidates(mode: MapSearchMode): List<MapSearchCandidate> = map { prediction ->
  MapSearchCandidate(
    prediction,
    when (mode) {
      MapSearchMode.Place -> MapSearchAction.Register
      MapSearchMode.Region -> MapSearchAction.MoveToRegion
    },
  )
}

/**
 * 記録画面の地図に重ねる検索窓: 地図はそのままに、上端に検索窓を重ね、候補は窓の下のカードに出す。
 * 窓の下の「場所｜地域」で探すものを切り替え（[MapSearchMode]。開くたびに「場所」から）、切り替えたら打ってある言葉で探し直す。
 * [registerEnabled] が false（立ち寄りの追加中）なら切り替えを出さず「地域」だけ。行ごとに選ぶと何が起きるかを右端に書く。
 * 選んだ行を [onFetchCandidate] で確定し、取れたら [onSelected] で返す（取れなければ取得に失敗した旨を出す）。
 * 候補の一覧の枠は場所タブの「検索して追加」と共有する（[PlaceSearchCandidates]）。
 * 地図をタップしたとき・戻る・× で閉じる（閉じる処理は呼び出し側の [onClose]）。
 */
@Composable
internal fun <T : Any> TrackingPlaceSearchBar(
  registerEnabled: Boolean,
  onSearchPredictions: suspend (query: String, mode: MapSearchMode) -> List<PlacePrediction>,
  onFetchCandidate: suspend (candidate: MapSearchCandidate) -> T?,
  onSelected: (T) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var query by remember { mutableStateOf("") }
  var selectedMode by remember { mutableStateOf(MapSearchMode.Place) }
  val mode = if (registerEnabled) selectedMode else MapSearchMode.Region
  var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
  // 候補は探したときの探し方で行にする（切り替えた直後に、前の候補を新しい探し方の行で出さないように）。
  var predictionsMode by remember { mutableStateOf(mode) }
  val candidates = remember(predictions, predictionsMode) { predictions.toMapSearchCandidates(predictionsMode) }
  // 窓を開いている間に立ち寄りの追加が終わると探し方が変わるので、打ち直しを待たずに新しいほうで探す。
  val searchPredictions by rememberUpdatedState(onSearchPredictions)
  var isSearching by remember { mutableStateOf(false) }
  var isFetching by remember { mutableStateOf(false) }
  var fetchFailed by remember { mutableStateOf<MapSearchAction?>(null) }
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
  // 切り替えたときも、打ってある言葉で探し直す（デバウンスは打鍵のときと同じ）。
  LaunchedEffect(query, mode) {
    fetchFailed = null
    if (query.isBlank()) {
      predictions = emptyList()
      isSearching = false
      return@LaunchedEffect
    }
    delay(300)
    isSearching = true
    val searchedMode = mode
    predictions = searchPredictions(query, searchedMode)
    predictionsMode = searchedMode
    isSearching = false
  }

  Column(
    modifier = modifier
      .statusBarsPadding()
      .padding(PathlySpacing.md)
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
            .padding(start = PathlySpacing.lg)
            .size(20.dp),
        )
        TextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(mode.placeholder) },
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

    // 探すものの切り替え（立ち寄りの追加中は地域だけなので出さない）。
    if (registerEnabled) {
      MapSearchModeChips(
        selected = selectedMode,
        onSelect = { selectedMode = it },
        modifier = Modifier.padding(top = PathlySpacing.sm),
      )
    }

    // 何か打ってから候補のカードを出す（空のうちは地図を隠さない）。
    if (query.isNotBlank() || fetchFailed != null) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = PathlyElevation.floating,
        modifier = Modifier
          .padding(top = PathlySpacing.sm)
          .fillMaxWidth()
          .heightIn(max = maxCardHeight),
      ) {
        Column(modifier = Modifier.padding(horizontal = PathlySpacing.lg)) {
          fetchFailed?.let { action ->
            Text(
              text = fetchFailedText(action),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = PathlySpacing.sm),
            )
          }
          PlaceSearchCandidates(
            query = query,
            candidates = candidates,
            isSearching = isSearching || isFetching,
            key = { it.key },
            modifier = Modifier.fillMaxWidth(),
          ) { candidate ->
            MapSearchCandidateRow(
              candidate = candidate,
              onClick = {
                if (!isFetching) {
                  scope.launch {
                    isFetching = true
                    val result = onFetchCandidate(candidate)
                    isFetching = false
                    if (result != null) {
                      keyboard?.hide()
                      onSelected(result)
                    } else {
                      fetchFailed = candidate.action
                    }
                  }
                }
              },
            )
          }
        }
      }
    }
  }
}

/** 窓の下の「場所｜地域」の切り替え。地図の上に重ねるので、チップは地の色と影を付ける（地名のチップと同じ見た目）。 */
@Composable
private fun MapSearchModeChips(
  selected: MapSearchMode,
  onSelect: (MapSearchMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
  ) {
    MapSearchMode.entries.forEach { mode ->
      val isSelected = mode == selected
      Surface(
        shape = PathlyShapes.pill,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shadowElevation = PathlyElevation.floating,
        modifier = Modifier
          .testTag("mapSearchMode_$mode")
          .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(mode) }),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = PathlySpacing.md, vertical = PathlySpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            painter = painterResource(
              when (mode) {
                MapSearchMode.Place -> R.drawable.ic_place
                MapSearchMode.Region -> R.drawable.ic_public
              },
            ),
            contentDescription = null,
            tint = when (mode) {
              MapSearchMode.Place -> LocalContentColor.current
              MapSearchMode.Region -> PathlyMapColors.Region
            },
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(PathlySpacing.sm))
          Text(text = mode.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
      }
    }
  }
}

/** 候補の 1 行。頭の印（地域へ移動は地球、登録はピン）と、右端に選ぶと何が起きるか。 */
@Composable
private fun MapSearchCandidateRow(
  candidate: MapSearchCandidate,
  onClick: () -> Unit,
) {
  val (icon, tint, label) = when (candidate.action) {
    MapSearchAction.MoveToRegion -> Triple(R.drawable.ic_public, PathlyMapColors.Region, "地域へ移動")

    MapSearchAction.Register -> Triple(
      R.drawable.ic_place,
      MaterialTheme.colorScheme.onSurfaceVariant,
      // 地域の候補は、地域へ移動との違いが分かるように「場所として」を添える。
      if (candidate.prediction.isRegion) "場所として登録" else "登録",
    )
  }
  PredictionRow(
    prediction = candidate.prediction,
    onClick = onClick,
    leadingIcon = {
      Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    },
    actionLabel = label,
    modifier = Modifier.testTag("mapSearchCandidate_${candidate.action}"),
  )
}

private fun fetchFailedText(action: MapSearchAction): String = when (action) {
  MapSearchAction.Register -> "場所の取得に失敗しました（オフライン等）"
  MapSearchAction.MoveToRegion -> "地域の取得に失敗しました（オフライン等）"
}
