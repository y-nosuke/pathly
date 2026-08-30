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
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.pathly.BuildConfig
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.TrackSmoother
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.stopSegmentPoints
import com.pathly.presentation.places.PlaceActionSheet
import com.pathly.presentation.places.PlaceDeleteUndo
import com.pathly.presentation.places.PlaceDeleteUndoEffect
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopSheet
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.StopDurationSheet
import com.pathly.presentation.stops.StopReassignDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
  // 同じ場所への複数の訪問を1件にまとめる（→ adr/0024）。判定は ViewModel/Repository 側。
  onMergeStops: (stopIds: List<Long>) -> Unit = {},
  // 滞在期間の手直し。GPS 点・補正後の点は触らない。
  onUpdateStopDuration: (stopId: Long, arrival: Date, departure: Date) -> Unit = { _, _, _ -> },
  onUndoStopChange: () -> Unit = {},
  // 手動での立ち寄り追加。近くに既存があるかの判断は ViewModel（AddManualStopUseCase）側で行う。
  onAddManualStop: (lat: Double, lng: Double, arrival: Date, departure: Date, name: String?, googlePlaceId: String?, googleName: String?) -> Unit =
    { _, _, _, _, _, _, _ -> },
  // 近接確認の保留状態（非nullで確認ダイアログを出す）とその選択。
  nearbyStopPrompt: NearbyStopPrompt? = null,
  onConfirmNearbyStopLink: () -> Unit = {},
  onConfirmNearbyStopNew: () -> Unit = {},
  onDismissNearbyStopPrompt: () -> Unit = {},
  onMessageShown: () -> Unit = {},
  // 空き地点/POI の登録。近くに既存があるかの判断は ViewModel（PlaceEditUseCase）側で行う。
  onRegisterPlace: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit =
    { _, _, _, _, _, _, _, _, _ -> },
  // 近接確認の保留状態（非nullで確認ダイアログを出す）とその選択。
  nearbyRegisterPrompt: NearbyRegisterPrompt? = null,
  onConfirmNearbyLink: () -> Unit = {},
  onConfirmNearbyNew: () -> Unit = {},
  onDismissNearbyPrompt: () -> Unit = {},
  // 統一シートで登録済みマーカーをその場で編集するため、単一 place を購読・保存・削除する。
  onObservePlace: (placeId: Long) -> Flow<PlaceListItem?> = { emptyFlow() },
  onObserveVisits: (placeId: Long) -> Flow<List<PlaceVisit>> = { emptyFlow() },
  onSavePlaceEdits: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean, link: PlaceSearchResult?) -> Unit = { _, _, _, _, _, _, _ -> },
  onDeletePlace: (placeId: Long) -> Unit = {},
  // 場所を削除した直後の取り消し待ち（スナックバーで「取り消す」を出す）。
  placeDeleteUndo: PlaceDeleteUndo = PlaceDeleteUndo(),
  onUndoPlaceDelete: () -> Unit = {},
  onPlaceDeleteUndoShown: () -> Unit = {},
  // 場所シートの訪問履歴から、その訪問のお出掛け（別の経路詳細）を開く。
  onOpenTrack: (trackId: Long) -> Unit = {},
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult? = { null },
  // 誤検知の選び直し／Google 施設の紐付け用: 座標の近くの POI 候補を取得／この訪問だけ付け替える。
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult> = { _, _ -> emptyList() },
  // 座標がずれて周辺に出ない施設用: 名前で検索するフォールバック。
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction> = { emptyList() },
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult? = { null },
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
  // 場所シートの開き具合。地図の下パディングを合わせて、選んだ地点が裏に来ないようにする。
  val placeSheetState = rememberFloatingSheetState()
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
  // 滞在期間を編集中の立ち寄り。地図を見ながら調整するので、ダイアログではなくシートで出す。
  var durationEditStop by remember { mutableStateOf<Stop?>(null) }
  // 編集中の滞在区間（到着〜出発の点インデックス）。手動追加と同じく地図に青くハイライトする。
  var durationRange by remember(durationEditStop) { mutableStateOf<Pair<Int, Int>?>(null) }
  // 期間編集シートの開き具合。本体シートを畳んでいても開いた状態で出したいので別に持つ。
  val durationSheetState = rememberFloatingSheetState(peekFraction = 0.5f)
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
  // 場所そのものの削除（場所シート）も同じスナックバーで取り消せるようにする。
  PlaceDeleteUndoEffect(placeDeleteUndo, snackbarHostState, onUndoPlaceDelete, onPlaceDeleteUndoShown)
  val deleteWithUndo: (List<Long>) -> Unit = { ids ->
    if (ids.isNotEmpty()) {
      onDeleteStops(ids)
      scope.launch {
        val result = snackbarHostState.showSnackbar(
          message = "${ids.size}件を削除しました",
          actionLabel = "取り消す",
          duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoStopChange()
      }
    }
  }

  // 統合も削除と同じ流儀: 確認は出さず即時実行し、スナックバーの「取り消す」で戻せる。
  val mergeWithUndo: (List<Long>) -> Unit = { ids ->
    if (ids.size >= 2) {
      onMergeStops(ids)
      scope.launch {
        val result = snackbarHostState.showSnackbar(
          message = "${ids.size}件をまとめました",
          actionLabel = "取り消す",
          duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoStopChange()
      }
    }
  }

  // タップした立ち寄りへ地図をフォーカスする（nonce で同じ場所の再タップにも反応）。
  var focusTarget by remember { mutableStateOf<LatLng?>(null) }
  var focusNonce by remember { mutableIntStateOf(0) }

  // 立ち寄りへ地図を寄せ、一覧側の強調も合わせる。行タップだけでなく**編集を始めるとき**にも使う。
  // 直す対象が画面の外にあると、地図のハイライトを見ながら期間を調整できないため。
  val focusOnStop: (Stop) -> Unit = { stop ->
    highlightedStopId = stop.id
    focusTarget = LatLng(stop.place.latitude, stop.place.longitude)
    focusNonce++
  }

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
  // マーカーの吹き出し。シートと同時に出るので、シート側から一緒に閉じられるよう画面で持つ。
  val infoWindow = rememberMapInfoWindowState()
  var manualMode by remember { mutableStateOf(false) }
  var manualPick by remember { mutableStateOf<ManualStopTarget?>(null) }
  // 手動追加で選んでいる滞在区間（到着〜出発の点インデックス）。地図に青くハイライトする。
  var manualRange by remember(manualPick) { mutableStateOf<Pair<Int, Int>?>(null) }
  val exitManual: () -> Unit = {
    manualMode = false
    manualPick = null
  }
  // 期間の編集中のシステムバックは、画面を抜けるのではなく編集をやめる。
  BackHandler(enabled = durationEditStop != null) { durationEditStop = null }

  // バックは2段階で戻す: 地点確定後は地点選択（マップ）に戻すだけ、地点選択中は追加モードを抜ける。
  BackHandler(enabled = manualMode) {
    if (manualPick != null) manualPick = null else exitManual()
  }

  // 地図に描く点列。調整モードではスライダーの値で補正する。
  val displayPoints = remember(track, tuningMode, tuningParams) {
    if (tuningMode) TrackSmoother.smooth(track.points, tuningParams) else track.smoothedPoints
  }
  // 立ち寄りの滞在区間（到着〜出発の軌跡点）。全件を軌跡の下に常時ハイライトする。
  // 補正調整・候補選択・手動追加のときは、そちらのハイライトと干渉しないよう出さない。
  val durationEditMode = durationEditStop != null
  val stopSegments = remember(track.smoothedPoints, stops, tuningMode, candidateMode, manualMode, durationEditMode) {
    if (tuningMode || candidateMode || manualMode || durationEditMode) {
      emptyList()
    } else {
      stops.mapNotNull { stop ->
        stopSegmentPoints(track.smoothedPoints, stop.arrivalTime, stop.departureTime).takeIf { it.size >= 2 }
      }
    }
  }
  // 立ち寄り中（ライブ）の滞在区間。通常表示のときだけ（補正調整・候補・手動追加中は出さない）。
  val currentStopSegment = remember(track.smoothedPoints, currentStop, tuningMode, candidateMode, manualMode, durationEditMode) {
    if (tuningMode || candidateMode || manualMode || durationEditMode) {
      emptyList()
    } else {
      currentStop?.let { stopSegmentPoints(track.smoothedPoints, it.arrivalTime, it.departureTime) }.orEmpty()
    }
  }
  // 記録中の地図に出すライブ立ち寄り（通常表示のときだけ）。
  val liveCurrentStop = if (tuningMode || candidateMode || manualMode || durationEditMode) null else currentStop
  // シートが隠れているか（復帰ボタン表示・地図の下パディング判定に使う）。
  val sheetHidden = detent == SheetDetent.HIDDEN

  Box(modifier = modifier.fillMaxSize()) {
    // ---- 地図（全面／edge-to-edge。シートは地図の上に重ねる）----
    if (track.points.isNotEmpty()) {
      if (mapContent != null) {
        mapContent()
      } else {
        // 手動追加・期間編集で選んでいる滞在区間（到着〜出発）の点列。地図に青くハイライトする。
        val editingRange = when {
          manualMode && manualPick != null -> manualRange
          durationEditMode -> durationRange
          else -> null
        }
        val rangeHighlight = if (editingRange != null && manualPoints.size >= 2) {
          val (start, end) = editingRange
          manualPoints.subList(
            start.coerceIn(0, manualPoints.lastIndex),
            (end + 1).coerceIn(1, manualPoints.size),
          )
        } else {
          emptyList()
        }
        TrackMapView(
          track = track,
          displayPoints = displayPoints,
          infoWindow = infoWindow,
          stops = stops,
          currentStop = liveCurrentStop,
          currentStopSegment = currentStopSegment,
          candidates = if (candidateMode && !manualMode) reanalyzeCandidates.orEmpty() else emptyList(),
          showRawOverlay = tuningMode,
          manualPickTarget = if (manualMode) manualPick?.let { LatLng(it.latitude, it.longitude) } else null,
          highlightPoints = rangeHighlight,
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
              manualMode && manualPick != null -> sheetState.heightOf(SheetDetent.PEEK)
              manualMode -> 96.dp
              durationEditMode -> durationSheetState.heightOf(durationSheetState.detent)
              tuningMode -> tuningSheetPeekHeight
              // 場所シートを開いている間は立ち寄り一覧を引っ込めるので、そちらの高さで空ける。
              placeSheetTarget != null -> placeSheetState.heightOf(placeSheetState.detent)
              else -> sheetState.heightOf(detent)
            },
          ),
          onPoiClick = { poi ->
            if (manualMode) {
              manualPick = ManualStopTarget(poi.latLng.latitude, poi.latLng.longitude, ManualStopOrigin.Poi(poi.name, poi.placeId))
              focusTarget = poi.latLng
              focusNonce++
            } else {
              placeSheetTarget = PlaceSheetTarget.NewPoi(poi)
            }
            focusTarget = poi.latLng
            focusNonce++
          },
          onMapClick = { latLng ->
            if (manualMode) {
              manualPick = ManualStopTarget(latLng.latitude, latLng.longitude, ManualStopOrigin.MapPoint)
              focusTarget = latLng
              focusNonce++
            } else {
              placeSheetTarget = PlaceSheetTarget.NewPoint(latLng)
            }
            focusTarget = latLng
            focusNonce++
          },
          // 地図の立ち寄りピンをタップ → 一覧の該当行を強調＆スクロール＋「選び直し」を出す（記録画面と統一）。
          onStopClick = { stop ->
            focusOnStop(stop)
            if (detent == SheetDetent.HIDDEN) sheetState.settleTo(SheetDetent.PEEK)
            reassignTarget = stop
          },
          // 登録済みマーカーのタップ: 手動追加モード＝既存placeへ紐付け（滞在調整ありのオーバーレイ）／
          // 通常モード＝場所シートでその場編集（詳細も開ける）。
          onRegisteredPlaceClick = { place ->
            if (manualMode) {
              manualPick = ManualStopTarget(
                place.latitude,
                place.longitude,
                ManualStopOrigin.ExistingPlace(place.placeId, place.displayName),
              )
              focusTarget = LatLng(place.latitude, place.longitude)
              focusNonce++
            } else {
              placeSheetTarget = PlaceSheetTarget.Existing(place.placeId)
            }
            focusTarget = LatLng(place.latitude, place.longitude)
            focusNonce++
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
    if (!tuningMode && !candidateMode && !manualMode && !durationEditMode && placeSheetTarget == null) {
      FloatingSheet(
        state = sheetState,
        modifier = Modifier.align(Alignment.BottomCenter),
        // 隠しているときは、下部中央に復帰ボタンが出る。
        restoreLabel = "▲ 詳細を表示",
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
          onFocusStop = focusOnStop,
          onEditStop = { editingStop = it },
          onEditStopNote = { editingNoteStop = it },
          // 期間の編集・場所の選び直しは、対象を地図に出してから始める（地図を見ながら直せるように）。
          onEditStopDuration = {
            focusOnStop(it)
            durationEditStop = it
          },
          canEditDuration = manualPoints.size >= 2,
          onReassignStop = {
            focusOnStop(it)
            reassignTarget = it
          },
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
          onMergeSelected = {
            mergeWithUndo(selectedStopIds.toList())
            selectionMode = false
            selectedStopIds.clear()
          },
        )
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
      } else {
        CloseInfoWindowWithSheet(infoWindow)
        ManualStopSheet(
          origin = pick.origin,
          latitude = pick.latitude,
          longitude = pick.longitude,
          points = manualPoints,
          onFetchCandidates = onFetchNearbyPois,
          onSearchPredictions = onSearchPredictions,
          onFetchPrediction = onFetchPrediction,
          onConfirm = { input ->
            val origin = pick.origin
            if (origin is ManualStopOrigin.ExistingPlace) {
              onAddManualStopForPlace(origin.placeId, input.arrivalTime, input.departureTime)
            } else {
              onAddManualStop(
                input.latitude,
                input.longitude,
                input.arrivalTime,
                input.departureTime,
                input.name,
                input.googlePlaceId,
                input.googleName,
              )
            }
            exitManual()
          },
          onCancel = { manualPick = null },
          onRangeChange = { start, end -> manualRange = start to end },
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }

    // 期間の編集: 地図を見ながら滞在区間を調整する（手動追加と同じレンジUI）。
    durationEditStop?.let { stop ->
      CloseInfoWindowWithSheet(infoWindow)
      StopDurationSheet(
        stop = stop,
        points = manualPoints,
        onConfirm = { arrivalIdx, departureIdx ->
          onUpdateStopDuration(
            stop.id,
            manualPoints[arrivalIdx].timestamp,
            manualPoints[departureIdx].timestamp,
          )
          durationEditStop = null
        },
        onCancel = { durationEditStop = null },
        onRangeChange = { start, end -> durationRange = start to end },
        sheetState = durationSheetState,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
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
      CloseInfoWindowWithSheet(infoWindow)
      PlaceActionSheet(
        target = target,
        onDismiss = { placeSheetTarget = null },
        onFetchPoiDetails = onFetchPoiDetails,
        onObservePlace = onObservePlace,
        onObserveVisits = onObserveVisits,
        onRegisterNew = onRegisterPlace,
        onSaveExisting = onSavePlaceEdits,
        onDeletePlace = onDeletePlace,
        onOpenTrack = onOpenTrack,
        onFetchNearbyPois = onFetchNearbyPois,
        onSearchPredictions = onSearchPredictions,
        onFetchPrediction = onFetchPrediction,
        modifier = Modifier.align(Alignment.BottomCenter),
        sheetState = placeSheetState,
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
    CloseInfoWindowWithSheet(infoWindow)
    StopReassignDialog(
      stop = stop,
      onFetchCandidates = onFetchNearbyPois,
      onSearchPredictions = onSearchPredictions,
      onFetchPrediction = onFetchPrediction,
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
