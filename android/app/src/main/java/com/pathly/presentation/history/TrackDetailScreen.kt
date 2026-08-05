package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.TrackSmoother
import com.pathly.presentation.places.RegisterPlaceFromPoiDialog
import com.pathly.ui.theme.TrackLineOrange
import com.pathly.util.DateFormatters
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.cos
import kotlin.math.roundToInt

private val tuningSheetPeekHeight = 360.dp
private val rawTrackColor = Color(0x66424242)

// 手動追加のハイライト（選択した滞在区間）。軌跡（オレンジ）・立ち寄り（紫）と見分ける青。
private val manualHighlightColor = Color(0xFF1E88E5)

// 既存の立ち寄りの滞在区間ハイライト（常時表示）。オレンジ/赤の軌跡に負けないよう、濃い青の太い帯を
// 下に敷く（軌跡は上に細く残る）。薄い青だと軌跡に埋もれて見えないため不透明寄りの濃色にする。
private val stopSegmentColor = Color(0xF00D47A1)

/** 立ち寄りの到着〜出発に対応する軌跡点（滞在区間）。時刻で範囲を切り出す（両端含む）。 */
private fun stopSegmentPoints(points: List<GpsPoint>, stop: Stop): List<GpsPoint> = points.filter { !it.timestamp.before(stop.arrivalTime) && !it.timestamp.after(stop.departureTime) }

// 手動追加で最初に仮置きする滞在時間（最寄り点からこの範囲を既定で選ぶ。あとで調整可）。
private const val DEFAULT_MANUAL_STAY_MILLIS = 3 * 60 * 1000L

/** 指した地点に最も近い軌跡点の添字（緯度補正した平面近似で十分）。 */
private fun nearestPointIndex(points: List<GpsPoint>, lat: Double, lng: Double): Int {
  if (points.isEmpty()) return 0
  var best = 0
  var bestD = Double.MAX_VALUE
  points.forEachIndexed { i, p ->
    val dLat = p.latitude - lat
    val dLng = (p.longitude - lng) * cos(Math.toRadians(lat))
    val d = dLat * dLat + dLng * dLng
    if (d < bestD) {
      bestD = d
      best = i
    }
  }
  return best
}

/** 到着点から既定の滞在時間ぶん先まで進めた出発点の添字（末尾で頭打ち）。 */
private fun defaultDepartureIndex(points: List<GpsPoint>, arrivalIdx: Int): Int {
  if (points.isEmpty()) return arrivalIdx
  val arrivalMillis = points[arrivalIdx].timestamp.time
  var idx = arrivalIdx
  while (idx + 1 < points.size && points[idx + 1].timestamp.time - arrivalMillis < DEFAULT_MANUAL_STAY_MILLIS) {
    idx++
  }
  return idx
}

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
  onEditStopNote: (stopId: Long, note: String?) -> Unit = { _, _ -> },
  onResolveNames: () -> Unit = {},
  onReanalyze: () -> Unit = {},
  reanalyzeCandidates: List<StopCandidate>? = null,
  onAddStops: (List<StopCandidate>) -> Unit = {},
  onDismissReanalyze: () -> Unit = {},
  onDeleteStops: (stopIds: List<Long>) -> Unit = {},
  onUndoDeletion: () -> Unit = {},
  onAddManualStop: (lat: Double, lng: Double, arrival: Date, departure: Date, name: String?, googlePlaceId: String?) -> Unit =
    { _, _, _, _, _, _ -> },
  onMessageShown: () -> Unit = {},
  onRegisterPlace: (lat: Double, lng: Double, name: String, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?) -> Unit =
    { _, _, _, _, _, _, _ -> },
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult? = { null },
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
  // メモ編集中の立ち寄り（stop 単位。名前編集 [editingStop] とは別ダイアログ）。
  var editingNoteStop by remember { mutableStateOf<Stop?>(null) }

  // 複数選択削除。長押しで選択モードに入り、チェックで選ぶ。
  var selectionMode by remember { mutableStateOf(false) }
  val selectedStopIds = remember { mutableStateListOf<Long>() }

  // 削除などで stops が変わったら、消えた ID を選択から外す。空になったら選択モードを抜ける。
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

  // 再解析の候補選択モード（候補があるあいだ）。地図にオレンジのピンで位置を見せ、
  // 地図の上に**高さ固定のオーバーレイ**で候補を出す（シートは畳んで地図を常に見せる）。
  val candidateMode = reanalyzeCandidates?.isNotEmpty() == true
  val selectedCandidates = remember(reanalyzeCandidates) {
    mutableStateListOf<Int>().apply { reanalyzeCandidates?.let { addAll(it.indices) } }
  }
  // 候補モード中のシステムバックは、画面を抜けるのではなく候補選択をやめる。
  BackHandler(enabled = candidateMode) { onDismissReanalyze() }
  // 候補オーバーレイの高さ（画面の約42%）。地図はこの分だけ下を空けてピンを隠さない。
  val candidateOverlayHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp

  // 手動追加モード。地図の通常タップ（POI はそのままタップ）で地点を指し、最寄り軌跡点から
  // 到着/出発を仮置きしてレンジで微調整する。滞在区間は地図に青くハイライトして見せる。
  val manualPoints = track.smoothedPoints
  var manualMode by remember { mutableStateOf(false) }
  var manualPick by remember { mutableStateOf<ManualPick?>(null) }
  val manualLastIdx = (manualPoints.size - 1).coerceAtLeast(0)
  var manualArrivalIdx by remember(manualPick) {
    mutableIntStateOf(
      manualPick?.let { nearestPointIndex(manualPoints, it.latLng.latitude, it.latLng.longitude) } ?: 0,
    )
  }
  var manualDepartureIdx by remember(manualPick) {
    mutableIntStateOf(defaultDepartureIndex(manualPoints, manualArrivalIdx))
  }
  var manualName by remember(manualPick) { mutableStateOf(manualPick?.name ?: "") }
  val exitManual: () -> Unit = {
    manualMode = false
    manualPick = null
  }
  // バックは2段階で戻す: 地点確定後は地点選択（マップ）に戻すだけ、地点選択中は追加モードを抜ける。
  BackHandler(enabled = manualMode) {
    if (manualPick != null) manualPick = null else exitManual()
  }
  // 手動追加オーバーレイ（地点確定後）の高さ。地点を指す前は下部に細い案内だけ出す。
  val manualOverlayHeight = (LocalConfiguration.current.screenHeightDp * 0.46f).dp

  // 地図に描く点列。調整モードではスライダーの値で補正する。
  val displayPoints = remember(track, tuningMode, tuningParams) {
    if (tuningMode) TrackSmoother.smooth(track.points, tuningParams) else track.smoothedPoints
  }
  // 立ち寄りの滞在区間（到着〜出発の軌跡点）。全件を軌跡の下に常時ハイライトする。
  // 補正調整・候補選択・手動追加のときは、そちらのハイライトと干渉しないよう出さない。
  val stopSegments = remember(track.smoothedPoints, stops, tuningMode, candidateMode, manualMode) {
    if (tuningMode || candidateMode || manualMode) {
      emptyList()
    } else {
      stops.mapNotNull { stop -> stopSegmentPoints(track.smoothedPoints, stop).takeIf { it.size >= 2 } }
    }
  }
  // 既定のピーク（＝ハーフ表示）は画面の約45%。ここが「地図＋一覧」を同時に見る状態で、
  // 立ち寄りを連続タップして地図で確認できる。上までドラッグすると全画面一覧（Expanded）、
  // 下スワイプで全画面地図（Hidden）になる（標準ボトムシートの2段スナップ＋隠す）。
  val peek = when {
    tuningMode -> tuningSheetPeekHeight
    // 候補・手動追加モードではシートを畳み、地図＋オーバーレイに専念する。
    candidateMode || manualMode -> 0.dp
    else -> (LocalConfiguration.current.screenHeightDp * 0.45f).dp
  }
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
      } else if (candidateMode || manualMode) {
        // 候補選択・手動追加は地図上のオーバーレイで行うのでシートは空にする（ピーク 0 で畳む）。
        Spacer(Modifier.height(1.dp))
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
          onEditStopNote = { editingNoteStop = it },
          onDeleteStop = { deleteWithUndo(listOf(it.id)) },
          onResolveNames = onResolveNames,
          onReanalyze = onReanalyze,
          onManualAdd = {
            manualPick = null
            manualMode = true
          },
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
          // 手動追加で選んだ滞在区間（到着〜出発）の点列。地図に青くハイライトする。
          val manualHighlight = if (manualMode && manualPick != null && manualPoints.size >= 2) {
            manualPoints.subList(manualArrivalIdx, (manualDepartureIdx + 1).coerceAtMost(manualPoints.size))
          } else {
            emptyList()
          }
          TrackMapView(
            track = track,
            displayPoints = displayPoints,
            stops = stops,
            candidates = if (candidateMode && !manualMode) reanalyzeCandidates.orEmpty() else emptyList(),
            showRawOverlay = tuningMode,
            manualPickTarget = if (manualMode) manualPick?.latLng else null,
            highlightPoints = manualHighlight,
            stopSegments = stopSegments,
            focusTarget = focusTarget,
            focusNonce = focusNonce,
            // 候補・手動追加モードは下のオーバーレイ分だけ空け、フォーカスがピンをその裏に隠さないようにする。
            contentPadding = PaddingValues(
              bottom = when {
                candidateMode -> candidateOverlayHeight
                manualMode && manualPick != null -> manualOverlayHeight
                manualMode -> 96.dp
                sheetHidden -> 0.dp
                else -> peek
              },
            ),
            onPoiClick = { poi ->
              if (manualMode) {
                manualPick = ManualPick(poi.latLng, poi.name, poi.placeId)
                focusTarget = poi.latLng
                focusNonce++
              } else {
                poiTarget = poi
              }
            },
            onMapClick = { latLng ->
              if (manualMode) {
                manualPick = ManualPick(latLng, null, null)
                focusTarget = latLng
                focusNonce++
              }
            },
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

      // シートを隠しているときは、下部中央に戻すボタンを出す（候補・手動追加モードでは出さない）。
      if (sheetHidden && !tuningMode && !candidateMode && !manualMode) {
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

      // 再解析の候補は、地図を常に見せたまま選べるよう下部の固定オーバーレイに出す。
      if (candidateMode) {
        CandidateOverlay(
          candidates = reanalyzeCandidates.orEmpty(),
          selectedIndices = selectedCandidates,
          height = candidateOverlayHeight,
          modifier = Modifier.align(Alignment.BottomCenter),
          onToggle = { index ->
            if (index in selectedCandidates) selectedCandidates.remove(index) else selectedCandidates.add(index)
          },
          onFocus = { candidate ->
            focusTarget = LatLng(candidate.detected.latitude, candidate.detected.longitude)
            focusNonce++
          },
          onConfirm = {
            val picked = selectedCandidates.sorted().mapNotNull { reanalyzeCandidates?.getOrNull(it) }
            onAddStops(picked)
          },
          onCancel = onDismissReanalyze,
        )
      }

      // 手動追加: 地点を指す前は案内、指したあとは入力＋区間調整のオーバーレイを出す。
      if (manualMode) {
        val pick = manualPick
        if (pick == null) {
          ManualPickPrompt(
            modifier = Modifier.align(Alignment.BottomCenter),
            onCancel = exitManual,
          )
        } else if (manualPoints.size >= 2) {
          val arrival = manualPoints[manualArrivalIdx].timestamp
          val departure = manualPoints[manualDepartureIdx].timestamp
          ManualAddOverlay(
            name = manualName,
            onNameChange = { manualName = it },
            arrivalTime = arrival,
            departureTime = departure,
            arrivalIdx = manualArrivalIdx,
            departureIdx = manualDepartureIdx,
            lastIdx = manualLastIdx,
            height = manualOverlayHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
            onRangeChange = { start, end ->
              manualArrivalIdx = start
              manualDepartureIdx = end
            },
            onConfirm = {
              val finalName = manualName.trim().ifBlank { null }
              // 名前を POI 名から変えたら googlePlaceId は使わない（別名で解決記録を焼き込まない）。
              val googleId = pick.googlePlaceId?.takeIf { finalName == pick.name }
              onAddManualStop(pick.latLng.latitude, pick.latLng.longitude, arrival, departure, finalName, googleId)
              exitManual()
              scope.launch { snackbarHostState.showSnackbar("立ち寄りを追加しました") }
            },
            // 編集からのキャンセルは追加モードを抜けず、地点選択（マップ）に戻すだけ。
            onCancel = { manualPick = null },
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

  editingNoteStop?.let { stop ->
    StopNoteDialog(
      stop = stop,
      onDismiss = { editingNoteStop = null },
      onConfirm = { note ->
        onEditStopNote(stop.id, note)
        editingNoteStop = null
      },
    )
  }

  // 候補ゼロのときだけダイアログで知らせる（候補があればシート＋地図で選ばせる）。
  if (reanalyzeCandidates?.isEmpty() == true) {
    AlertDialog(
      onDismissRequest = onDismissReanalyze,
      title = { Text("再解析") },
      text = { Text("一覧に無い立ち寄りは見つかりませんでした。") },
      confirmButton = { TextButton(onClick = onDismissReanalyze) { Text("閉じる") } },
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
      onFetchDetails = onFetchPoiDetails,
      onRegister = { name, wishlist, priority, memo ->
        onRegisterPlace(poi.latLng.latitude, poi.latLng.longitude, name, wishlist, priority, memo, poi.placeId)
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

/** 立ち寄り（訪問）のメモを編集するダイアログ。stop 単位・複数行入力。空で保存すると消える。 */
@Composable
private fun StopNoteDialog(
  stop: Stop,
  onDismiss: () -> Unit,
  onConfirm: (String?) -> Unit,
) {
  var text by remember(stop.id) { mutableStateOf(stop.note ?: "") }
  // 開いたらすぐ入力できるよう、メモ欄にフォーカスして IME を出す。
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("メモ") },
    text = {
      Column {
        Text(
          text = stop.place.displayName,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          placeholder = { Text("この立ち寄りのメモ（例: 限定パフェが美味しかった）") },
          minLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

/**
 * 再解析の候補（一覧に無い立ち寄り）を選択して追加する**下部オーバーレイ**。
 * 高さを [height] に固定し、地図の上に重ねる（地図は常に見えたまま）。リストは
 * このカード内で独立スクロールするので、全画面にしなくても下の候補まで届く。
 * 行タップ（[onFocus]）で地図をその候補へ寄せ、名前＋位置で確かめてから選べる。
 */
@Composable
private fun CandidateOverlay(
  candidates: List<StopCandidate>,
  selectedIndices: List<Int>,
  height: Dp,
  onToggle: (Int) -> Unit,
  onFocus: (StopCandidate) -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(height),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 固定ヘッダー: 説明と操作。スクロールから外して常に押せるようにする。
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "一覧に無い立ち寄り ${candidates.size}件",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "オレンジのピンが候補です。行をタップすると地図が寄ります。追加するものを選んでください。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      // 候補リスト（このカード内でスクロール）。
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp),
      ) {
        candidates.forEachIndexed { index, candidate ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onFocus(candidate) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = index in selectedIndices, onCheckedChange = { onToggle(index) })
            Column(modifier = Modifier.padding(start = 4.dp)) {
              Text(
                text = candidate.name ?: "名称未取得",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = "${DateFormatters.TIME_FORMAT.format(candidate.detected.arrivalTime)} – " +
                  "${DateFormatters.TIME_FORMAT.format(candidate.detected.departureTime)} ・ 滞在${candidate.detected.durationMinutes}分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      // 固定フッター: キャンセル／追加。
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
        Button(
          onClick = onConfirm,
          enabled = selectedIndices.isNotEmpty(),
          modifier = Modifier.weight(1f),
        ) { Text("追加（${selectedIndices.size}件）") }
      }
    }
  }
}

/** 手動追加で指した地点（座標＋POI由来の名前/ID）。空きタップは name/googlePlaceId が null。 */
private data class ManualPick(
  val latLng: LatLng,
  val name: String?,
  val googlePlaceId: String?,
)

/** 手動追加モードで地点を指す前に出す案内バー（下部・細め）。 */
@Composable
private fun ManualPickPrompt(
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "立ち寄った地点を地図でタップ（施設はタップで名前も入ります）",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onCancel) { Text("キャンセル") }
    }
  }
}

/**
 * 手動追加で指した地点を立ち寄りとして登録する**下部オーバーレイ**。名前と、滞在した区間
 * （到着〜出発）を調整する。区間は地図に青くハイライトされ、経路のどこに対応するかが見える。
 * 調整は**スライダー（粗）＋ ＋/−（1点ずつの微調整）**の2段構え。長い経路でも精密に合わせられる。
 * 高さを [height] に固定し、地図は上に見えたまま。キャンセル/追加は下部に常に見える。
 */
@Composable
private fun ManualAddOverlay(
  name: String,
  onNameChange: (String) -> Unit,
  arrivalTime: Date,
  departureTime: Date,
  arrivalIdx: Int,
  departureIdx: Int,
  lastIdx: Int,
  height: Dp,
  onRangeChange: (start: Int, end: Int) -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val durationMinutes = ((departureTime.time - arrivalTime.time) / 1000 / 60).toInt()
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(height),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 本文はスクロール（小さい画面でもボタンが隠れないよう、フッターは下に固定する）。
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "手動で立ち寄りを追加",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
          value = name,
          onValueChange = onNameChange,
          label = { Text("名前（任意）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = "滞在${durationMinutes}分（青いハイライトが滞在区間）",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "スライダーで大まかに、＋/− で1点ずつ微調整できます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RangeSlider(
          value = arrivalIdx.toFloat()..departureIdx.toFloat(),
          onValueChange = { range ->
            val start = range.start.roundToInt().coerceIn(0, lastIdx)
            val end = range.endInclusive.roundToInt().coerceIn(start, lastIdx)
            onRangeChange(start, end)
          },
          valueRange = 0f..lastIdx.toFloat(),
        )
        // 微調整（1点ずつ）。到着は出発を超えられず、出発は到着を下回れない。
        StepperRow(
          label = "到着",
          time = arrivalTime,
          minusEnabled = arrivalIdx > 0,
          plusEnabled = arrivalIdx < departureIdx,
          onMinus = { onRangeChange((arrivalIdx - 1).coerceAtLeast(0), departureIdx) },
          onPlus = { onRangeChange((arrivalIdx + 1).coerceAtMost(departureIdx), departureIdx) },
        )
        StepperRow(
          label = "出発",
          time = departureTime,
          minusEnabled = departureIdx > arrivalIdx,
          plusEnabled = departureIdx < lastIdx,
          onMinus = { onRangeChange(arrivalIdx, (departureIdx - 1).coerceAtLeast(arrivalIdx)) },
          onPlus = { onRangeChange(arrivalIdx, (departureIdx + 1).coerceAtMost(lastIdx)) },
        )
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("追加") }
      }
    }
  }
}

/** 到着／出発を1点ずつ微調整する行（時刻表示＋ −/＋）。 */
@Composable
private fun StepperRow(
  label: String,
  time: Date,
  minusEnabled: Boolean,
  plusEnabled: Boolean,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = DateFormatters.TIME_FORMAT.format(time),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(start = 12.dp),
    )
    Spacer(modifier = Modifier.weight(1f))
    OutlinedButton(
      onClick = onMinus,
      enabled = minusEnabled,
      shape = CircleShape,
      contentPadding = PaddingValues(0.dp),
      modifier = Modifier.size(40.dp),
    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
    Spacer(modifier = Modifier.width(8.dp))
    OutlinedButton(
      onClick = onPlus,
      enabled = plusEnabled,
      shape = CircleShape,
      contentPadding = PaddingValues(0.dp),
      modifier = Modifier.size(40.dp),
    ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackDetailSheet(
  track: GpsTrack,
  stops: List<Stop>,
  unresolvedCount: Int,
  selectionMode: Boolean,
  selectedStopIds: List<Long>,
  onFocusStop: (Stop) -> Unit,
  onEditStop: (Stop) -> Unit,
  onEditStopNote: (Stop) -> Unit,
  onDeleteStop: (Stop) -> Unit,
  onResolveNames: () -> Unit,
  onReanalyze: () -> Unit,
  onManualAdd: () -> Unit,
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
      // チップが増えても収まるよう、幅が足りなければ次の行に折り返す（FlowRow）。
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // 再解析: 一覧に無い立ち寄りを検出して、選択式で追加する（非破壊）。
        if (track.points.size >= 2 && !track.isActive) {
          ActionChip(
            text = "再解析",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onReanalyze,
          )
          // 手動で追加: 検出に頼らず、地図で指した地点を立ち寄りとして足す（完全手動）。
          ActionChip(
            text = "手動で追加",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onManualAdd,
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
          onEditNote = { onEditStopNote(stop) },
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
  onEditNote: () -> Unit,
  onDelete: () -> Unit,
  onToggleSelect: () -> Unit,
  onEnterSelection: () -> Unit,
) {
  val title = stop.place.displayName
  val note = stop.note?.takeIf { it.isNotBlank() }
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
      // メモがあれば時刻の下にインライン表示（長文は省略）。
      if (note != null) {
        Text(
          text = note,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
    if (!selectionMode) {
      TextButton(onClick = onEditNote) { Text(if (note != null) "メモ" else "＋メモ") }
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
  candidates: List<StopCandidate> = emptyList(),
  showRawOverlay: Boolean = false,
  manualPickTarget: LatLng? = null,
  highlightPoints: List<GpsPoint> = emptyList(),
  stopSegments: List<List<GpsPoint>> = emptyList(),
  focusTarget: LatLng? = null,
  focusNonce: Int = 0,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  onPoiClick: (PointOfInterest) -> Unit = {},
  onMapClick: (LatLng) -> Unit = {},
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
    onMapClick = onMapClick,
  ) {
    // 調整モードでは生データを灰色で重ねて見比べる
    if (showRawOverlay && track.points.size >= 2) {
      Polyline(
        points = track.points.map { LatLng(it.latitude, it.longitude) },
        color = rawTrackColor,
        width = 10f,
      )
    }

    // 立ち寄りの滞在区間を軌跡の「下」に半透明の帯で常時表示する（経路のどこで滞在したかが見える）。
    // 軌跡ポリラインより先に描くことで、オレンジの軌跡が帯の上に残って潰れない。
    stopSegments.forEach { segment ->
      if (segment.size >= 2) {
        Polyline(
          points = segment.map { LatLng(it.latitude, it.longitude) },
          color = stopSegmentColor,
          width = 26f,
        )
      }
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
        title = stop.place.name ?: stop.place.googleName ?: "立ち寄り",
        snippet = "${DateFormatters.SHORT_TIME_FORMAT.format(stop.arrivalTime)} ・ 滞在${stop.durationMinutes}分",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
      )
    }

    // 再解析の候補（オレンジのピン）。既存（紫）と見分けられるようにする。
    candidates.forEachIndexed { index, candidate ->
      val d = candidate.detected
      val candidateMarkerState = remember(index, d.latitude, d.longitude) {
        MarkerState(position = LatLng(d.latitude, d.longitude))
      }
      Marker(
        state = candidateMarkerState,
        title = candidate.name ?: "候補（名称未取得）",
        snippet = "${DateFormatters.SHORT_TIME_FORMAT.format(d.arrivalTime)} ・ 滞在${d.durationMinutes}分",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
      )
    }

    // 手動追加: 選んだ滞在区間を青い太線でハイライトし、経路のどこかを見せる。
    if (highlightPoints.size >= 2) {
      Polyline(
        points = highlightPoints.map { LatLng(it.latitude, it.longitude) },
        color = manualHighlightColor,
        width = 14f,
      )
    }

    // 手動追加で指した地点（青いピン）。
    manualPickTarget?.let { target ->
      val pickMarkerState = remember(target) { MarkerState(position = target) }
      Marker(
        state = pickMarkerState,
        title = "追加する地点",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
      )
    }
  }
}
