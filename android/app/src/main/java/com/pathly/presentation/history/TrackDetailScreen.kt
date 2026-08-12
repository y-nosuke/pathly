package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.pathly.BuildConfig
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.TrackSmoother
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.StopReassignDialog
import com.pathly.presentation.common.defaultDepartureIndex
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.nearestPointIndex
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.stopSegmentPoints
import com.pathly.presentation.places.PlaceActionSheet
import com.pathly.presentation.places.PlaceSheetTarget
import kotlinx.coroutines.launch
import java.util.Date

private val tuningSheetPeekHeight = 360.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
  track: GpsTrack,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  stops: List<Stop> = emptyList(),
  // 記録中に開いたときの「立ち寄り中（ライブ）」。地図にだけ出す（一覧・保存には出さない）。
  currentStop: Stop? = null,
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
  // 手動での立ち寄り追加。近くに既存があるかの判断は ViewModel（AddManualStopUseCase）側で行う。
  onAddManualStop: (lat: Double, lng: Double, arrival: Date, departure: Date, name: String?, googlePlaceId: String?) -> Unit =
    { _, _, _, _, _, _ -> },
  // 近接確認の保留状態（非nullで確認ダイアログを出す）とその選択。
  nearbyStopPrompt: NearbyStopPrompt? = null,
  onConfirmNearbyStopLink: () -> Unit = {},
  onConfirmNearbyStopNew: () -> Unit = {},
  onDismissNearbyStopPrompt: () -> Unit = {},
  // 登録済みマーカー（通常モード）タップ後、シートの「詳細を開く」でその場所の詳細を開く。
  onOpenPlaceDetail: (placeId: Long) -> Unit = {},
  onMessageShown: () -> Unit = {},
  // 空き地点/POI の登録。近くに既存があるかの判断は ViewModel（PlaceEditUseCase）側で行う。
  onRegisterPlace: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?) -> Unit =
    { _, _, _, _, _, _, _ -> },
  // 近接確認の保留状態（非nullで確認ダイアログを出す）とその選択。
  nearbyRegisterPrompt: NearbyRegisterPrompt? = null,
  onConfirmNearbyLink: () -> Unit = {},
  onConfirmNearbyNew: () -> Unit = {},
  onDismissNearbyPrompt: () -> Unit = {},
  // 統一シートで登録済みマーカーをその場で編集するため、単一 place を取得・保存する。
  onLoadPlace: suspend (placeId: Long) -> PlaceListItem? = { null },
  onSavePlaceEdits: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean) -> Unit = { _, _, _, _, _, _ -> },
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult? = { null },
  // 誤検知の選び直し用: 座標の近くの POI 候補を取得／この訪問だけ付け替える。
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult> = { _, _ -> emptyList() },
  onReassignStop: (stopId: Long, chosen: PlaceSearchResult?, customName: String?) -> Unit = { _, _, _ -> },
  // 登録済みの場所の地図表示（画面別トグル）。
  registeredPlaces: List<RegisteredPlace> = emptyList(),
  showRegisteredPlaces: Boolean = false,
  onToggleRegisteredPlaces: () -> Unit = {},
  // 手動追加モードで登録済みマーカーをタップ → その既存 place にこの訪問を紐付ける（②）。
  onAddManualStopForPlace: (placeId: Long, arrival: Date, departure: Date) -> Unit = { _, _, _ -> },
  // 地図スロット。null（既定）は実マップ（GoogleMap）を描画する。
  // テストは空スロット（{}）を渡し、GMS 依存の地図描画を避けてシート・オーバーレイだけを検証する。
  mapContent: (@Composable () -> Unit)? = null,
) {
  // 通常モードの地図タップで開く統一の「場所シート」（未登録の空き地点/POI＝登録、登録済み＝その場で編集）。
  var placeSheetTarget by remember { mutableStateOf<PlaceSheetTarget?>(null) }
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  // 画面高は LocalWindowInfo から取る（Configuration.screenHeightDp は非推奨で、
  // 分割画面などコンテナ実サイズと食い違う）。containerSize は px なので dp に直す。
  val screenHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
  var tuningMode by remember { mutableStateOf(false) }
  var tuningParams by remember { mutableStateOf(SmoothingParams()) }
  // デバッグビルドのみ: 生GPS点の付随情報（provider/各種精度/MSL/extras等）を確認するダイアログ。
  var debugInfoOpen by remember { mutableStateOf(false) }
  var editingStop by remember { mutableStateOf<Stop?>(null) }
  // メモ編集中の立ち寄り（stop 単位。名前編集 [editingStop] とは別ダイアログ）。
  var editingNoteStop by remember { mutableStateOf<Stop?>(null) }
  // 「場所を選び直す」対象の立ち寄り（誤検知の訂正・この訪問だけ付け替え）。
  var reassignTarget by remember { mutableStateOf<Stop?>(null) }
  // 地図↔一覧の連動用: 選択中の立ち寄り。地図のピン/一覧の行どちらから選んでも相互に強調・スクロールする。
  var highlightedStopId by remember { mutableStateOf<Long?>(null) }

  // 複数選択削除。長押しで選択モードに入り、チェックで選ぶ。
  var selectionMode by remember { mutableStateOf(false) }
  val selectedStopIds = remember { mutableStateListOf<Long>() }

  // --- 自前フローティングシートの開閉（地図の上に重ねる。標準ボトムシートは使わない）---
  // 3段階: 隠す（全画面地図）/ ハーフ（地図＋一覧を同時に）/ フル（一覧を大きく、地図は上に少し覗く）。
  // つまみ／上部のドラッグでシートだけを開閉し、一覧（LazyColumn）は独立してスクロールする。
  val sheetState = rememberFloatingSheetState(peekFraction = 0.45f, fullFraction = 0.92f)
  val detent = sheetState.detent

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
  val candidateOverlayHeight = screenHeightDp * 0.42f

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
  val manualOverlayHeight = screenHeightDp * 0.46f

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
      stops.mapNotNull { stop ->
        stopSegmentPoints(track.smoothedPoints, stop.arrivalTime, stop.departureTime).takeIf { it.size >= 2 }
      }
    }
  }
  // 立ち寄り中（ライブ）の滞在区間。通常表示のときだけ（補正調整・候補・手動追加中は出さない）。
  val currentStopSegment = remember(track.smoothedPoints, currentStop, tuningMode, candidateMode, manualMode) {
    if (tuningMode || candidateMode || manualMode) {
      emptyList()
    } else {
      currentStop?.let { stopSegmentPoints(track.smoothedPoints, it.arrivalTime, it.departureTime) }.orEmpty()
    }
  }
  // 記録中の地図に出すライブ立ち寄り（通常表示のときだけ）。
  val liveCurrentStop = if (tuningMode || candidateMode || manualMode) null else currentStop
  // シートが隠れているか（復帰ボタン表示・地図の下パディング判定に使う）。
  val sheetHidden = detent == SheetDetent.HIDDEN

  Box(modifier = modifier.fillMaxSize()) {
    // ---- 地図（全面／edge-to-edge。シートは地図の上に重ねる）----
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
          currentStop = liveCurrentStop,
          currentStopSegment = currentStopSegment,
          candidates = if (candidateMode && !manualMode) reanalyzeCandidates.orEmpty() else emptyList(),
          showRawOverlay = tuningMode,
          manualPickTarget = if (manualMode) manualPick?.latLng else null,
          highlightPoints = manualHighlight,
          stopSegments = stopSegments,
          // この経路の立ち寄り（確定＝紫番号／滞在中＝ティール）と同じ場所は登録済みピンを二重に出さない。
          registeredPlaces = if (showRegisteredPlaces) {
            val stopPlaceIds = (stops.map { it.place.id } + listOfNotNull(liveCurrentStop?.place?.id)).toSet()
            registeredPlaces.filter { it.placeId !in stopPlaceIds }
          } else {
            emptyList()
          },
          focusTarget = focusTarget,
          focusNonce = focusNonce,
          // フォーカスがピンをシート/オーバーレイの裏に隠さないよう、下端を空ける。
          // 通常モードは現在の段（ハーフ/フル）の高さ分。ドラッグ中の逐次値ではなく確定値を使う。
          contentPadding = PaddingValues(
            bottom = when {
              candidateMode -> candidateOverlayHeight
              manualMode && manualPick != null -> manualOverlayHeight
              manualMode -> 96.dp
              tuningMode -> tuningSheetPeekHeight
              else -> sheetState.heightOf(detent)
            },
          ),
          onPoiClick = { poi ->
            if (manualMode) {
              manualPick = ManualPick(poi.latLng, poi.name, poi.placeId)
              focusTarget = poi.latLng
              focusNonce++
            } else {
              placeSheetTarget = PlaceSheetTarget.NewPoi(poi)
            }
          },
          onMapClick = { latLng ->
            if (manualMode) {
              manualPick = ManualPick(latLng, null, null)
              focusTarget = latLng
              focusNonce++
            } else {
              placeSheetTarget = PlaceSheetTarget.NewPoint(latLng)
            }
          },
          // 地図の立ち寄りピンをタップ → 一覧の該当行を強調＆スクロール＋「選び直し」を出す（記録画面と統一）。
          onStopClick = { stop ->
            highlightedStopId = stop.id
            focusTarget = LatLng(stop.place.latitude, stop.place.longitude)
            focusNonce++
            if (detent == SheetDetent.HIDDEN) sheetState.settleTo(SheetDetent.PEEK)
            reassignTarget = stop
          },
          // 登録済みマーカーのタップ: 手動追加モード＝既存placeへ紐付け（滞在調整ありのオーバーレイ）／
          // 通常モード＝場所シートでその場編集（詳細も開ける）。
          onRegisteredPlaceClick = { place ->
            if (manualMode) {
              manualPick = ManualPick(
                LatLng(place.latitude, place.longitude),
                place.displayName,
                null,
                existingPlaceId = place.placeId,
              )
              focusTarget = LatLng(place.latitude, place.longitude)
              focusNonce++
            } else {
              placeSheetTarget = PlaceSheetTarget.Existing(place.placeId)
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

    // ---- 上部の丸ボタン（戻る／調整）。ソリッドバーは置かず地図の上に浮かせる ----
    Row(
      modifier = Modifier
        .statusBarsPadding()
        .padding(12.dp),
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

      // 登録済みの場所を地図に出すトグル（ONで既存placeをアンバーのピンで表示）。
      Surface(
        onClick = onToggleRegisteredPlaces,
        shape = CircleShape,
        color = if (showRegisteredPlaces) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_place),
          contentDescription = if (showRegisteredPlaces) "登録済みの場所を隠す" else "登録済みの場所を表示",
          tint = if (showRegisteredPlaces) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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

      // GPS点の付随情報を確認するデバッグダイアログを開くボタン（デバッグビルドのみ）。
      if (BuildConfig.DEBUG && track.points.isNotEmpty()) {
        Surface(
          onClick = { debugInfoOpen = true },
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 4.dp,
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_list),
            contentDescription = "GPS詳細（デバッグ）",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp),
          )
        }
      }
    }

    // ---- 通常モードのフローティングシート（つまみで開閉・一覧は独立スクロール）----
    if (!tuningMode && !candidateMode && !manualMode) {
      if (sheetHidden) {
        // 隠しているときは、下部中央に復帰ボタンを出す。
        Surface(
          onClick = { sheetState.settleTo(SheetDetent.PEEK) },
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 4.dp,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        ) {
          Text(
            text = "▲ 詳細を表示",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          )
        }
      } else {
        FloatingSheet(
          state = sheetState,
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          TrackDetailSheet(
            track = track,
            stops = stops,
            unresolvedCount = unresolvedCount,
            selectionMode = selectionMode,
            selectedStopIds = selectedStopIds,
            highlightedStopId = highlightedStopId,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            onFocusStop = {
              highlightedStopId = it.id
              focusTarget = LatLng(it.place.latitude, it.place.longitude)
              focusNonce++
            },
            onEditStop = { editingStop = it },
            onEditStopNote = { editingNoteStop = it },
            onReassignStop = { reassignTarget = it },
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
      }
    }

    // ---- 補正の調整（デバッグ）: 地図の上に固定高さのパネルで出す ----
    if (tuningMode) {
      Surface(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(tuningSheetPeekHeight),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        ) {
          TuningSheet(
            track = track,
            params = tuningParams,
            onParamsChange = { tuningParams = it },
          )
        }
      }
    }

    // 再解析の候補は、地図を常に見せたまま選べるよう下部の固定オーバーレイに出す。
    if (candidateMode) {
      CandidateOverlay(
        candidates = reanalyzeCandidates.orEmpty(),
        selectedIndices = selectedCandidates,
        height = candidateOverlayHeight,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding(),
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
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding(),
          onCancel = exitManual,
        )
      } else if (manualPoints.size >= 2) {
        val arrival = manualPoints[manualArrivalIdx].timestamp
        val departure = manualPoints[manualDepartureIdx].timestamp
        ManualAddOverlay(
          name = manualName,
          onNameChange = { manualName = it },
          // 登録済みマーカーから開いたときは、その場所へ紐付ける（名前欄の代わりに場所名を出す）。
          linkedPlaceName = pick.existingPlaceId?.let { pick.name ?: "登録済みの場所" },
          arrivalTime = arrival,
          departureTime = departure,
          arrivalIdx = manualArrivalIdx,
          departureIdx = manualDepartureIdx,
          lastIdx = manualLastIdx,
          height = manualOverlayHeight,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding(),
          onRangeChange = { start, end ->
            manualArrivalIdx = start
            manualDepartureIdx = end
          },
          onConfirm = {
            val existingPlaceId = pick.existingPlaceId
            val finalName = manualName.trim().ifBlank { null }
            // 名前を POI 名から変えたら googlePlaceId は使わない（別名で解決記録を焼き込まない）。
            val googleId = pick.googlePlaceId?.takeIf { finalName == pick.name }
            // 登録済みマーカーから開いたときだけ既存 place へ紐付ける。それ以外は
            // 近接確認の要否も含めて ViewModel（AddManualStopUseCase）が判断する。
            if (existingPlaceId != null) {
              onAddManualStopForPlace(existingPlaceId, arrival, departure)
            } else {
              onAddManualStop(pick.latLng.latitude, pick.latLng.longitude, arrival, departure, finalName, googleId)
            }
            exitManual()
          },
          // 編集からのキャンセルは追加モードを抜けず、地点選択（マップ）に戻すだけ。
          onCancel = { manualPick = null },
        )
      }
    }

    // スナックバー（最前面）。シート/オーバーレイの上に出す。
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding(),
    )

    // 通常モードの地図タップで開く統一の「場所シート」。立ち寄り追加は手動追加モード（到着/出発の調整あり）に任せるため出さない。
    placeSheetTarget?.let { target ->
      PlaceActionSheet(
        target = target,
        onDismiss = { placeSheetTarget = null },
        onFetchPoiDetails = onFetchPoiDetails,
        onLoadPlace = onLoadPlace,
        onRegisterNew = onRegisterPlace,
        onSaveExisting = onSavePlaceEdits,
        onOpenDetail = onOpenPlaceDetail,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
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

  reassignTarget?.let { stop ->
    StopReassignDialog(
      stop = stop,
      onFetchCandidates = onFetchNearbyPois,
      onConfirm = { chosen, customName ->
        onReassignStop(stop.id, chosen, customName)
        reassignTarget = null
      },
      onDismiss = { reassignTarget = null },
    )
  }

  // 近接確認: 近くに既存があれば紐付け／新規を選ぶ。
  nearbyStopPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      onLink = onConfirmNearbyStopLink,
      onCreateNew = onConfirmNearbyStopNew,
      onDismiss = onDismissNearbyStopPrompt,
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

  // 「登録しました／登録済みです」などの一時メッセージは、削除の取り消しと同じ下部スナックバーで統一する。
  LaunchedEffect(message) {
    val text = message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
    onMessageShown()
  }

  // 空き地点登録の近接確認: 近くの既存に紐付け／新規で登録。
  nearbyRegisterPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      onLink = onConfirmNearbyLink,
      onCreateNew = onConfirmNearbyNew,
      onDismiss = onDismissNearbyPrompt,
    )
  }

  if (debugInfoOpen) {
    GpsDebugDialog(points = track.points, onDismiss = { debugInfoOpen = false })
  }
}

/** 手動追加で指した地点（座標＋POI由来の名前/ID）。空きタップは name/googlePlaceId が null。 */
private data class ManualPick(
  val latLng: LatLng,
  val name: String?,
  val googlePlaceId: String?,
  // 登録済みマーカーから選んだ場合の既存 placeId。非nullなら新規placeを作らずここへ紐付ける（②）。
  val existingPlaceId: Long? = null,
)
