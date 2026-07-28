package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PointOfInterest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.BuildConfig
import com.pathly.R
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.Stop
import com.pathly.domain.model.TrackSmoother
import com.pathly.presentation.places.RegisterPlaceFromPoiDialog
import com.pathly.ui.theme.TrackLineOrange
import com.pathly.util.DateFormatters
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val tuningSheetPeekHeight = 360.dp
private val rawTrackColor = Color(0x66424242)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
  track: GpsTrack,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  stops: List<Stop> = emptyList(),
  unresolvedCount: Int = 0,
  message: String? = null,
  onEditPlaceName: (placeId: Long, name: String) -> Unit = { _, _ -> },
  onResolveNames: () -> Unit = {},
  onReanalyze: () -> Unit = {},
  onDeleteStops: (stopIds: List<Long>) -> Unit = {},
  onUndoDeletion: () -> Unit = {},
  onMessageShown: () -> Unit = {},
  onRegisterPlace: (lat: Double, lng: Double, name: String, wishlist: Boolean) -> Unit = { _, _, _, _ -> },
  // 地図スロット。null（既定）は実マップ（GoogleMap）を描画する。
  // テストは空スロット（{}）を渡し、GMS 依存の地図描画を避けてシート・オーバーレイだけを検証する。
  mapContent: (@Composable () -> Unit)? = null,
) {
  var poiTarget by remember { mutableStateOf<PointOfInterest?>(null) }
  // 下にスワイプでシートを隠せる（地図を全画面化）。戻すボタンで復帰する。
  val sheetState = rememberStandardBottomSheetState(
    initialValue = SheetValue.PartiallyExpanded,
    skipHiddenState = false,
  )
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
  val scope = rememberCoroutineScope()
  var tuningMode by remember { mutableStateOf(false) }
  var tuningParams by remember { mutableStateOf(SmoothingParams()) }
  var editingStop by remember { mutableStateOf<Stop?>(null) }

  // 複数選択削除。長押しで選択モードに入り、チェックで選ぶ。
  var selectionMode by remember { mutableStateOf(false) }
  val selectedStopIds = remember { mutableStateListOf<Long>() }

  // 再解析などで stops が変わったら、消えた ID を選択から外す。空になったら選択モードを抜ける。
  LaunchedEffect(stops) {
    val ids = stops.mapTo(HashSet()) { it.id }
    selectedStopIds.retainAll(ids)
    if (selectedStopIds.isEmpty()) selectionMode = false
  }

  // 選択モード中のシステムバックは、画面を抜けるのではなく選択を解除する。
  BackHandler(enabled = selectionMode) {
    selectionMode = false
    selectedStopIds.clear()
  }

  // 削除は確認ダイアログを出さず即時実行し、スナックバーの「取り消す」で元に戻せる。
  val snackbarHostState = remember { SnackbarHostState() }
  val deleteWithUndo: (List<Long>) -> Unit = { ids ->
    if (ids.isNotEmpty()) {
      onDeleteStops(ids)
      scope.launch {
        val result = snackbarHostState.showSnackbar(
          message = "${ids.size}件を削除しました",
          actionLabel = "取り消す",
          duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDeletion()
      }
    }
  }

  // タップした立ち寄りへ地図をフォーカスする（nonce で同じ場所の再タップにも反応）。
  var focusTarget by remember { mutableStateOf<LatLng?>(null) }
  var focusNonce by remember { mutableIntStateOf(0) }

  // 地図に描く点列。調整モードではスライダーの値で補正する。
  val displayPoints = remember(track, tuningMode, tuningParams) {
    if (tuningMode) TrackSmoother.smooth(track.points, tuningParams) else track.smoothedPoints
  }
  // 既定のピーク（＝ハーフ表示）は画面の約45%。ここが「地図＋一覧」を同時に見る状態で、
  // 立ち寄りを連続タップして地図で確認できる。上までドラッグすると全画面一覧（Expanded）、
  // 下スワイプで全画面地図（Hidden）になる（標準ボトムシートの2段スナップ＋隠す）。
  val peek = if (tuningMode) tuningSheetPeekHeight else (LocalConfiguration.current.screenHeightDp * 0.45f).dp
  val sheetHidden = sheetState.currentValue == SheetValue.Hidden

  BottomSheetScaffold(
    modifier = modifier.fillMaxSize(),
    scaffoldState = scaffoldState,
    sheetPeekHeight = peek,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    sheetContent = {
      if (tuningMode) {
        TuningSheet(
          track = track,
          params = tuningParams,
          onParamsChange = { tuningParams = it },
        )
      } else {
        TrackDetailSheet(
          track = track,
          stops = stops,
          unresolvedCount = unresolvedCount,
          selectionMode = selectionMode,
          selectedStopIds = selectedStopIds,
          // 全画面（Expanded）まで伸ばせるよう高さいっぱいにする。ハーフ表示はピークが担う。
          // タップでは畳まず地図だけ動かし、連続タップで確認できるようにする。
          modifier = Modifier.fillMaxHeight(),
          onFocusStop = {
            focusTarget = LatLng(it.place.latitude, it.place.longitude)
            focusNonce++
          },
          onEditStop = { editingStop = it },
          onDeleteStop = { deleteWithUndo(listOf(it.id)) },
          onResolveNames = onResolveNames,
          onReanalyze = onReanalyze,
          onEnterSelection = { stop ->
            selectionMode = true
            if (stop.id !in selectedStopIds) selectedStopIds.add(stop.id)
          },
          onToggleSelect = { stop ->
            if (stop.id in selectedStopIds) selectedStopIds.remove(stop.id) else selectedStopIds.add(stop.id)
          },
          onSelectAll = {
            val allSelected = selectedStopIds.size == stops.size
            selectedStopIds.clear()
            if (!allSelected) selectedStopIds.addAll(stops.map { it.id })
          },
          onCancelSelection = {
            selectionMode = false
            selectedStopIds.clear()
          },
          onDeleteSelected = {
            deleteWithUndo(selectedStopIds.toList())
            selectionMode = false
            selectedStopIds.clear()
          },
        )
      }
    },
  ) { innerPadding ->
    Box(
      // 地図はシートの下まで敷き詰める（下スワイプで隠したとき黒帯が残らないよう、
      // ボトムのインセットは効かせない）。上端のみステータスバー分を空ける。
      modifier = Modifier
        .fillMaxSize()
        .padding(top = innerPadding.calculateTopPadding()),
    ) {
      if (track.points.isNotEmpty()) {
        if (mapContent != null) {
          mapContent()
        } else {
          TrackMapView(
            track = track,
            displayPoints = displayPoints,
            stops = stops,
            showRawOverlay = tuningMode,
            focusTarget = focusTarget,
            focusNonce = focusNonce,
            contentPadding = PaddingValues(bottom = if (sheetHidden) 0.dp else peek),
            onPoiClick = { poiTarget = it },
            modifier = Modifier.fillMaxSize(),
          )
        }
      } else {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "GPSデータがありません",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          onClick = onBackClick,
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 4.dp,
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = "戻る",
            modifier = Modifier.padding(8.dp),
          )
        }

        // 補正の調整はデバッグビルドのみ
        if (BuildConfig.DEBUG && track.points.isNotEmpty()) {
          Surface(
            onClick = { tuningMode = !tuningMode },
            shape = CircleShape,
            color = if (tuningMode) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.surface
            },
            shadowElevation = 4.dp,
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_tune),
              contentDescription = "補正を調整",
              tint = if (tuningMode) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                MaterialTheme.colorScheme.onSurface
              },
              modifier = Modifier.padding(8.dp),
            )
          }
        }
      }

      // シートを隠しているときは、下部中央に戻すボタンを出す。
      if (sheetHidden && !tuningMode) {
        Surface(
          onClick = { scope.launch { sheetState.partialExpand() } },
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 4.dp,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp),
        ) {
          Text(
            text = "▲ 詳細を表示",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          )
        }
      }
    }
  }

  editingStop?.let { stop ->
    PlaceNameDialog(
      stop = stop,
      onDismiss = { editingStop = null },
      onConfirm = { name ->
        onEditPlaceName(stop.place.id, name)
        editingStop = null
      },
    )
  }

  message?.let { text ->
    AlertDialog(
      onDismissRequest = onMessageShown,
      confirmButton = { TextButton(onClick = onMessageShown) { Text("OK") } },
      text = { Text(text) },
    )
  }

  poiTarget?.let { poi ->
    RegisterPlaceFromPoiDialog(
      poi = poi,
      onDismiss = { poiTarget = null },
      onRegister = { name, wishlist ->
        onRegisterPlace(poi.latLng.latitude, poi.latLng.longitude, name, wishlist)
        poiTarget = null
      },
    )
  }
}

@Composable
private fun PlaceNameDialog(
  stop: Stop,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember(stop.id) { mutableStateOf(stop.place.name ?: "") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("場所の名前") },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("例: スターバックス ◯◯店") },
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

@Composable
private fun TrackDetailSheet(
  track: GpsTrack,
  stops: List<Stop>,
  unresolvedCount: Int,
  selectionMode: Boolean,
  selectedStopIds: List<Long>,
  onFocusStop: (Stop) -> Unit,
  onEditStop: (Stop) -> Unit,
  onDeleteStop: (Stop) -> Unit,
  onResolveNames: () -> Unit,
  onReanalyze: () -> Unit,
  onEnterSelection: (Stop) -> Unit,
  onToggleSelect: (Stop) -> Unit,
  onSelectAll: () -> Unit,
  onCancelSelection: () -> Unit,
  onDeleteSelected: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    // 選択モードのアクションはスクロールから外し、常に上部に固定する
    // （選択後に削除まで戻る手間をなくす）。
    if (selectionMode) {
      SelectionBar(
        selectedCount = selectedStopIds.size,
        totalCount = stops.size,
        onSelectAll = onSelectAll,
        onDelete = onDeleteSelected,
        onCancel = onCancelSelection,
      )
    }
    Column(
      // 固定ヘッダー（選択バー）の下で、残りの高さいっぱいにスクロールさせる。
      modifier = Modifier
        .weight(1f, fill = false)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = DateFormatters.DATE_FORMAT.format(track.startTime),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        if (track.isActive) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "● 記録中",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }
        }
      }

      val startText = DateFormatters.TIME_FORMAT.format(track.startTime)
      val endTime = track.endTime
      val subtitle = if (endTime != null) {
        val durationMinutes = ((endTime.time - track.startTime.time) / 1000 / 60).toInt()
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        val duration = if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
        "$startText – ${DateFormatters.TIME_FORMAT.format(endTime)} ・ $duration"
      } else {
        startText
      }
      Text(
        text = subtitle + if (stops.isNotEmpty()) " ・ 立ち寄り${stops.size}件" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // 操作ボタンは上部（ピーク内）に置いて常に押せるようにする。
      if (track.points.size >= 2 && !track.isActive) {
        ActionChip(
          text = "再解析",
          container = MaterialTheme.colorScheme.tertiaryContainer,
          content = MaterialTheme.colorScheme.onTertiaryContainer,
          onClick = onReanalyze,
        )
      }
      if (unresolvedCount > 0) {
        ActionChip(
          text = "場所を取得（未取得 ${unresolvedCount}件）",
          container = MaterialTheme.colorScheme.secondaryContainer,
          content = MaterialTheme.colorScheme.onSecondaryContainer,
          onClick = onResolveNames,
        )
      }

      val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        StatTile(
          value = "${distanceKm}km",
          label = "移動距離",
          modifier = Modifier.weight(1f),
        )
        StatTile(
          value = "${track.points.size}",
          label = "地点数",
          modifier = Modifier.weight(1f),
        )
      }

      stops.forEach { stop ->
        StopRow(
          stop = stop,
          selectionMode = selectionMode,
          selected = stop.id in selectedStopIds,
          onFocus = { onFocusStop(stop) },
          onEdit = { onEditStop(stop) },
          onDelete = { onDeleteStop(stop) },
          onToggleSelect = { onToggleSelect(stop) },
          onEnterSelection = { onEnterSelection(stop) },
        )
      }
    }
  }
}

@Composable
private fun SelectionBar(
  selectedCount: Int,
  totalCount: Int,
  onSelectAll: () -> Unit,
  onDelete: () -> Unit,
  onCancel: () -> Unit,
) {
  // 固定ヘッダー。地図・一覧と区別できるよう淡い背景を敷く。
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "${selectedCount}件選択中",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectAll) {
          Text(if (selectedCount == totalCount && totalCount > 0) "全解除" else "全選択")
        }
        TextButton(onClick = onDelete, enabled = selectedCount > 0) {
          Text("削除", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onCancel) { Text("キャンセル") }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopRow(
  stop: Stop,
  selectionMode: Boolean,
  selected: Boolean,
  onFocus: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onToggleSelect: () -> Unit,
  onEnterSelection: () -> Unit,
) {
  val title = stop.place.name
    ?: "%.5f, %.5f".format(stop.place.latitude, stop.place.longitude)
  Row(
    // 通常はタップでフォーカス、長押しで選択モード。選択モード中はタップで選択トグル。
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = { if (selectionMode) onToggleSelect() else onFocus() },
        onLongClick = { if (!selectionMode) onEnterSelection() },
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (selectionMode) {
      Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
    }
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "${DateFormatters.SHORT_TIME_FORMAT.format(stop.arrivalTime)}" +
          " – ${DateFormatters.SHORT_TIME_FORMAT.format(stop.departureTime)}" +
          " ・ 滞在${stop.durationMinutes}分",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (!selectionMode) {
      TextButton(onClick = onEdit) { Text("編集") }
      TextButton(onClick = onDelete) {
        Text("削除", color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

@Composable
private fun TuningSheet(
  track: GpsTrack,
  params: SmoothingParams,
  onParamsChange: (SmoothingParams) -> Unit,
  modifier: Modifier = Modifier,
) {
  val smoothed = remember(track, params) { TrackSmoother.smooth(track.points, params) }
  val rawKm = (TrackSmoother.totalDistanceMeters(track.points) / 1000.0 * 100).roundToInt() / 100.0
  val smKm = (TrackSmoother.totalDistanceMeters(smoothed) / 1000.0 * 100).roundToInt() / 100.0
  val rawTurn = TrackSmoother.totalTurningDegrees(track.points).roundToInt()
  val smTurn = TrackSmoother.totalTurningDegrees(smoothed).roundToInt()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "補正の調整（デバッグ）",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "距離: 生 ${rawKm}km → 補正 ${smKm}km",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "曲がり角合計: 生 $rawTurn° → 補正 $smTurn°（小さいほど滑らか）",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ParamSlider(
      label = "速度上限（ジャンプ除外）: ${params.maxSpeedMps.roundToInt()} m/s",
      value = params.maxSpeedMps.toFloat(),
      valueRange = 10f..100f,
      onChange = { onParamsChange(params.copy(maxSpeedMps = it.toDouble())) },
    )
    ParamSlider(
      label = "平滑窓: ${params.window}",
      value = params.window.toFloat(),
      valueRange = 1f..15f,
      onChange = {
        val window = it.roundToInt().let { v -> if (v % 2 == 0) v + 1 else v }.coerceIn(1, 15)
        onParamsChange(params.copy(window = window))
      },
    )

    Text(
      text = "MAX_SPEED=${params.maxSpeedMps.roundToInt()}, WINDOW=${params.window}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

@Composable
private fun ParamSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
    )
    Slider(
      value = value,
      onValueChange = onChange,
      valueRange = valueRange,
    )
  }
}

@Composable
private fun ActionChip(
  text: String,
  container: Color,
  content: Color,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = container,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = content,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
  }
}

@Composable
private fun StatTile(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun TrackMapView(
  track: GpsTrack,
  displayPoints: List<GpsPoint>,
  modifier: Modifier = Modifier,
  stops: List<Stop> = emptyList(),
  showRawOverlay: Boolean = false,
  focusTarget: LatLng? = null,
  focusNonce: Int = 0,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  onPoiClick: (PointOfInterest) -> Unit = {},
) {
  val cameraPositionState = rememberCameraPositionState()
  val defaultPosition = LatLng(35.6762, 139.6503) // Tokyo Station as default

  // カメラ範囲はトラック読み込み時に一度だけ合わせる（スライダー操作では動かさない）
  LaunchedEffect(track.id) {
    val pts = track.smoothedPoints
    if (pts.isNotEmpty()) {
      val boundsBuilder = LatLngBounds.Builder()
      pts.forEach { point ->
        boundsBuilder.include(LatLng(point.latitude, point.longitude))
      }
      cameraPositionState.animate(
        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
      )
    } else {
      cameraPositionState.position = CameraPosition.fromLatLngZoom(defaultPosition, 12f)
    }
  }

  // 立ち寄り一覧のタップで、その場所へズーム＆センタリングする。
  LaunchedEffect(focusNonce) {
    val target = focusTarget ?: return@LaunchedEffect
    if (focusNonce == 0) return@LaunchedEffect
    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 17f))
  }

  GoogleMap(
    modifier = modifier,
    cameraPositionState = cameraPositionState,
    contentPadding = contentPadding,
    properties = MapProperties(
      mapType = MapType.NORMAL,
      isMyLocationEnabled = false,
    ),
    uiSettings = MapUiSettings(
      zoomControlsEnabled = false,
      compassEnabled = false,
      myLocationButtonEnabled = false,
      mapToolbarEnabled = false,
      zoomGesturesEnabled = true,
      scrollGesturesEnabled = true,
    ),
    onPOIClick = onPoiClick,
  ) {
    // 調整モードでは生データを灰色で重ねて見比べる
    if (showRawOverlay && track.points.size >= 2) {
      Polyline(
        points = track.points.map { LatLng(it.latitude, it.longitude) },
        color = rawTrackColor,
        width = 10f,
      )
    }

    if (displayPoints.size >= 2) {
      Polyline(
        points = displayPoints.map { LatLng(it.latitude, it.longitude) },
        color = TrackLineOrange,
        width = 6f,
      )

      val startPoint = displayPoints.first()
      val startMarkerState = remember(startPoint) {
        MarkerState(position = LatLng(startPoint.latitude, startPoint.longitude))
      }
      Marker(
        state = startMarkerState,
        title = "開始",
        snippet = "記録開始地点 - ${DateFormatters.TIME_FORMAT.format(track.startTime)}",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
      )

      val endPoint = displayPoints.last()
      val endMarkerState = remember(endPoint) {
        MarkerState(position = LatLng(endPoint.latitude, endPoint.longitude))
      }
      Marker(
        state = endMarkerState,
        title = if (track.isActive) "現在地" else "終了",
        snippet = track.endTime?.let {
          "記録終了地点 - ${DateFormatters.TIME_FORMAT.format(it)}"
        } ?: "記録中の最新地点",
        icon = BitmapDescriptorFactory.defaultMarker(
          if (track.isActive) BitmapDescriptorFactory.HUE_BLUE else BitmapDescriptorFactory.HUE_RED,
        ),
      )
    }

    // 立ち寄り場所（紫のピン）
    stops.forEach { stop ->
      val stopMarkerState = remember(stop.id, stop.place.latitude, stop.place.longitude) {
        MarkerState(position = LatLng(stop.place.latitude, stop.place.longitude))
      }
      Marker(
        state = stopMarkerState,
        title = stop.place.name ?: "立ち寄り",
        snippet = "${DateFormatters.SHORT_TIME_FORMAT.format(stop.arrivalTime)} ・ 滞在${stop.durationMinutes}分",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
      )
    }
  }
}
