package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.DefaultMapLocation
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.toGeoBounds
import com.pathly.presentation.common.toLatLngBounds
import com.pathly.presentation.places.PlaceEditHost
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.ManualStopHost
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.StopReassignHost

/**
 * 記録画面。地図を全面に出し、記録の操作・場所の登録・立ち寄りの手での追加・この範囲の経路を地図の上に重ねる。
 * 場所の操作の UI は [PlaceEditHost]・[ManualStopHost] がつなぐ（経路詳細・場所タブと同じ）。
 *
 * @param onOpenTrack 場所シートの訪問履歴から、その訪問のお出掛け（経路詳細）を開く。
 * @param trackOverlayViewModel 「この範囲の経路」（地図に映っている範囲の過去の経路を選んで重ねる）。記録まわりとは別の ViewModel。
 * @param mapContent 地図スロット。null（既定）は実マップ（GoogleMap）を描画する。
 *   テストは空スロット（{}）を渡し、GMS 依存の地図描画を避けてオーバーレイだけを検証する。
 */
@Composable
fun TrackingScreen(
  modifier: Modifier = Modifier,
  onRequestPermission: () -> Unit,
  onOpenTrack: (trackId: Long) -> Unit = {},
  viewModel: TrackingViewModel = hiltViewModel(),
  trackOverlayViewModel: TrackOverlayViewModel = hiltViewModel(),
  mapContent: (@Composable () -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val mapPlaceFilter by viewModel.mapPlaceFilter.collectAsStateWithLifecycle()
  val overlayState by trackOverlayViewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // 保存（確定）中はバックで抜けさせない。抜けられると「保存できていないのに終わった」ように
  // 見える。ただしこれで防げるのはバックだけで、タスクから消す・OS に落とされるのは防げない
  // （そちらは停止後に記録を復活させないことで受け止める → adr/0021）。
  BackHandler(enabled = uiState.isFinalizing) { /* 何もしない（保存が終わるまで待たせる） */ }

  // 復帰のたびに権限・電池最適化の状態を再確認（システム設定から戻ったときに反映するため）
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.checkLocationPermission()
    viewModel.checkBatteryOptimization()
    viewModel.checkLocationEnabled()
  }

  // 中断された記録の再開/完了を確認するダイアログ
  if (uiState.interruptedTrack != null) {
    InterruptedTrackDialog(
      isLocationDisabled = uiState.isLocationDisabled,
      onResume = viewModel::resumeTracking,
      onFinish = viewModel::finishInterruptedTracking,
      onOpenLocationSettings = viewModel::openLocationSettings,
    )
  }

  // 地図の1点タップで開く統一の「場所シート」（未登録の空き地点/未登録POI/登録済みの場所）。
  var placeSheetTarget by remember { mutableStateOf<PlaceSheetTarget?>(null) }
  // 停止の誤爆防止（記録中の停止だけ確認を挟む）。
  var showStopConfirm by remember { mutableStateOf(false) }
  // 手動で立ち寄りを追加する対象。起点（POI/空き地点/今ここ/登録済み）ごとに出す内容が変わる。
  var manualTarget by remember { mutableStateOf<ManualStopTarget?>(null) }
  // 手動追加で選んでいる滞在区間（到着〜出発の点インデックス）。地図に青くハイライトする。
  var manualRange by remember(manualTarget) { mutableStateOf<Pair<Int, Int>?>(null) }
  // 立ち寄りマーカーをタップして「場所を選び直す」対象（誤検知の訂正）。
  var reassignTarget by remember { mutableStateOf<Stop?>(null) }
  // 「立ち寄りを追加」モード（記録中のみ）。ONの間は地図タップ＝立ち寄り追加、OFF＝場所登録。
  var manualMode by remember { mutableStateOf(false) }
  // 地図の上の検索窓（「検索して登録」か「地域へ移動」）。null なら閉じている。
  var mapSearch by remember { mutableStateOf<MapSearchMode?>(null) }
  // 検索窓を開いたときに地図に映っていた範囲。候補はこの中を優先する（範囲の外も出る）。
  var mapSearchBias by remember { mutableStateOf<GeoBounds?>(null) }
  // 「地域へ移動」で移した地域の目印（中心のピンと名前）。別の地域を選ぶ・現在地ボタン・ピンのタップで消す。
  var regionMarker by remember { mutableStateOf<RegionMarker?>(null) }
  // シートの開き具合。地図の下パディングを合わせて、選んだ地点がシートの裏に来ないようにする。
  val placeSheetState = rememberFloatingSheetState()
  val manualSheetState = rememberFloatingSheetState(peekFraction = 0.5f)
  // 経路の一覧は畳みきれないようにする（開け閉めは経路のボタンと × で。畳んだまま閉じて線ごと消えないように）。
  // 地図を広く見られるよう、開いたときは低めにする。
  val overlaySheetState = rememberFloatingSheetState(peekFraction = 0.35f, allowHidden = false)
  // 地図を寄せる範囲（一覧の「この経路の全体を見る」・地域へ移動）。同じ範囲でも効くよう nonce で送る。
  var fitBounds by remember { mutableStateOf<LatLngBounds?>(null) }
  var fitNonce by remember { mutableIntStateOf(0) }
  fun fitTo(bounds: LatLngBounds) {
    fitBounds = bounds
    fitNonce++
  }
  // 一覧の「この経路の全体を見る」。
  TrackFocusEffect(
    focusRequest = overlayState.focusRequest,
    selected = overlayState.selected,
    onFit = { fitTo(it.toLatLngBounds()) },
    onConsume = trackOverlayViewModel::consumeFocus,
  )
  // 地図のカメラ。「この範囲の経路」を押したときに、そのとき映っている範囲を読むため画面で持つ。
  val mapCameraState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(DefaultMapLocation, 15f)
  }
  // 場所シート・手動追加のシートが出ている間は、経路の一覧は下がる（閉じれば戻る）。
  val showOverlaySheet = overlayState.isSheetOpen && placeSheetTarget == null && manualTarget == null
  // マーカーの吹き出し。シートと同時に出るので、シート側から一緒に閉じられるよう画面で持つ。
  val infoWindow = rememberMapInfoWindowState()
  // 地図で選んだ地点。可視領域へ寄せる合図（同じ地点を選び直しても効くよう nonce で送る）。
  var focusTarget by remember { mutableStateOf<LatLng?>(null) }
  var focusNonce by remember { mutableIntStateOf(0) }
  fun focusOn(latLng: LatLng) {
    focusTarget = latLng
    focusNonce++
  }
  // 記録が止まったら手動追加モードは自動で抜ける（立ち寄りを足すトラックが無い）。
  LaunchedEffect(uiState.isTracking) { if (!uiState.isTracking) manualMode = false }

  /**
   * 地図のタップ。検索窓を開いている間は、窓を閉じるだけ（地図を見たくてタップしたとみなす）。
   * 手動追加モード（記録中のみ）なら [manual] を立ち寄りを足す地点にして寄せる。それ以外は [onNormal]
   * （[focusInNormal] なら、その地点へも寄せる）。
   */
  fun onMapTap(manual: ManualStopTarget, focusInNormal: Boolean = true, onNormal: () -> Unit) {
    if (mapSearch != null) {
      mapSearch = null
      return
    }
    val adding = uiState.isTracking && manualMode
    if (adding) manualTarget = manual else onNormal()
    if (adding || focusInNormal) focusOn(LatLng(manual.latitude, manual.longitude))
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (mapContent != null) {
      mapContent()
    } else {
      TrackingMapView(
        hasPermission = uiState.hasLocationPermission,
        cameraPositionState = mapCameraState,
        track = uiState.currentTrack,
        currentLocation = uiState.currentLocation,
        overlayTracks = overlayState.selected,
        stops = uiState.stops,
        currentStop = uiState.currentStop,
        infoWindow = infoWindow,
        // 施設は確定しているので、名前と placeId を持ったまま渡す（候補の選び直しは出さない）。通常は場所シート（POI登録）。
        onPoiClick = { poi ->
          onMapTap(ManualStopTarget(poi.latLng.latitude, poi.latLng.longitude, ManualStopOrigin.Poi(poi.name, poi.placeId))) {
            placeSheetTarget = PlaceSheetTarget.NewPoi(poi)
          }
        },
        // 通常は場所シート（空き地点の登録）。
        onMapClick = { latLng ->
          onMapTap(ManualStopTarget(latLng.latitude, latLng.longitude, ManualStopOrigin.MapPoint)) {
            placeSheetTarget = PlaceSheetTarget.NewPoint(latLng)
          }
        },
        // 立ち寄りマーカー: 手動追加モード＝その場所にもう 1 件立ち寄りを足す（登録済みマーカーと同じ）／
        // 通常＝「場所を選び直す」（誤検知の訂正）。
        onStopClick = { stop ->
          val target = ManualStopTarget(
            stop.place.latitude,
            stop.place.longitude,
            ManualStopOrigin.ExistingPlace(stop.place.id, stop.place.displayName),
          )
          onMapTap(target, focusInNormal = false) { reassignTarget = stop }
        },
        modifier = Modifier.fillMaxSize(),
        // 表示 ON のとき、地図の絞り込みに合う場所だけを出す（まとまり・ラベルも絞った後で作られる）。
        registeredPlaces = if (uiState.showRegisteredPlaces) uiState.registeredPlaces.filter(mapPlaceFilter::matches) else emptyList(),
        manualHighlight = manualTarget?.let { _ ->
          val pts = uiState.currentTrack?.smoothedPoints.orEmpty()
          manualRange?.let { (start, end) ->
            if (pts.size >= 2) pts.subList(start, (end + 1).coerceAtMost(pts.size)) else emptyList()
          }
        }.orEmpty(),
        // 登録前に選んだ地点の青いピン。手動追加の地点か、検索で選んだ場所（地図の上に施設アイコンが無いこともあるので示す）。
        manualPickTarget = manualTarget?.let { LatLng(it.latitude, it.longitude) }
          ?: (placeSheetTarget as? PlaceSheetTarget.NewSearchResult)?.result?.let { LatLng(it.latitude, it.longitude) },
        // シートが出ている分だけ可視領域を狭める（ドラッグ中の逐次値ではなく段の確定値）。
        contentPadding = PaddingValues(
          bottom = when {
            manualTarget != null -> manualSheetState.heightOf(manualSheetState.detent)
            placeSheetTarget != null -> placeSheetState.heightOf(placeSheetState.detent)
            showOverlaySheet -> overlaySheetState.heightOf(overlaySheetState.detent)
            else -> 0.dp
          },
        ),
        focusTarget = focusTarget,
        focusNonce = focusNonce,
        fitBounds = fitBounds,
        fitNonce = fitNonce,
        regionMarker = regionMarker,
        onRegionMarkerClick = { regionMarker = null },
        onRecenter = { regionMarker = null },
        // 登録済みマーカーのタップ: 手動追加モード＝既存placeへ紐付け／通常＝場所シート（その場で編集）。
        onRegisteredPlaceClick = { place ->
          val target = ManualStopTarget(place.latitude, place.longitude, ManualStopOrigin.ExistingPlace(place.placeId, place.displayName))
          onMapTap(target) { placeSheetTarget = PlaceSheetTarget.Existing(place.placeId) }
        },
      )
    }

    // 検索窓を開いている間は、上端の操作（右のボタン列・左上の記録中の操作・記録中のピル）を隠す（窓と重なるため）。
    if (mapSearch == null) {
      TrackingMapButtons(
        showRegisteredPlaces = uiState.showRegisteredPlaces,
        registeredPlaces = uiState.registeredPlaces,
        mapPlaceFilter = mapPlaceFilter,
        onToggleRegisteredPlaces = viewModel::toggleShowRegisteredPlaces,
        onMapPlaceFilterChange = viewModel::setMapPlaceFilter,
        overlayActive = overlayState.isSheetOpen,
        // 立ち寄りの追加中は一覧・検索を出せない（追加のシートが優先）ので押せなくする。
        actionsEnabled = manualTarget == null,
        onToggleOverlay = {
          // もう一度押すと閉じる（登録済みの場所のトグルと同じ）。閉じると重ねた線も消える。
          if (overlayState.isSheetOpen) {
            trackOverlayViewModel.closeSheet()
          } else {
            mapCameraState.visibleGeoBounds()?.let { bounds ->
              placeSheetTarget = null
              trackOverlayViewModel.search(bounds)
              overlaySheetState.settleTo(SheetDetent.PEEK)
            }
          }
        },
        onSearchPlace = {
          placeSheetTarget = null
          mapSearchBias = mapCameraState.visibleGeoBounds()
          viewModel.startPlaceSearch()
          mapSearch = MapSearchMode.Place
        },
        onSearchRegion = {
          mapSearchBias = mapCameraState.visibleGeoBounds()
          viewModel.startPlaceSearch()
          mapSearch = MapSearchMode.Region
        },
        modifier = Modifier.align(Alignment.TopEnd),
      )
    }

    if ((uiState.isTracking || regionMarker != null) && mapSearch == null) {
      TrackingTopStartControls(
        isTracking = uiState.isTracking,
        manualMode = manualMode,
        onToggleManualMode = { manualMode = !manualMode },
        hasLocationPermission = uiState.hasLocationPermission,
        currentLocation = uiState.currentLocation,
        onAddStopHere = { loc -> manualTarget = ManualStopTarget(loc.latitude, loc.longitude, ManualStopOrigin.CurrentLocation) },
        regionMarker = regionMarker,
        onRefitRegion = { region -> fitTo(region.bounds) },
        onClearRegion = { regionMarker = null },
        modifier = Modifier.align(Alignment.TopStart),
      )
    }

    // 記録中の状態ピル（上部中央）
    if (uiState.isTracking && mapSearch == null) {
      RecordingStatusPill(
        startTime = uiState.currentTrack?.startTime,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 12.dp),
      )
    }

    // 場所を登録した直後の一時メッセージ
    uiState.placeRegisteredMessage?.let { msg ->
      PlaceRegisteredToast(
        message = msg,
        isTracking = uiState.isTracking,
        onShown = viewModel::clearPlaceRegisteredMessage,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }

    // 権限がない場合のオーバーレイ（中央）
    if (!uiState.hasLocationPermission) {
      LocationPermissionOverlay(
        onRequestPermission = onRequestPermission,
        modifier = Modifier
          .align(Alignment.Center)
          .padding(24.dp),
      )
    }

    TrackingBottomControls(
      uiState = uiState,
      onDisableBatteryOptimization = viewModel::requestDisableBatteryOptimization,
      onOpenLocationSettings = viewModel::openLocationSettings,
      onStartTracking = viewModel::startTracking,
      onStopTracking = { showStopConfirm = true },
      modifier = Modifier.align(Alignment.BottomCenter),
    )

    // 地図の1点タップで開く統一の「場所シート」。未登録の空き地点/POI＝登録、登録済み＝その場で編集。
    // 場所を削除したら「取り消す」を出す（場所一覧・経路詳細と同じ出し方）。
    PlaceEditHost(
      placeEdit = viewModel.placeEdit,
      target = placeSheetTarget,
      onDismiss = { placeSheetTarget = null },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      snackbarHostState = snackbarHostState,
      sheetState = placeSheetState,
      onOpenTrack = onOpenTrack,
      // 記録中のみ「立ち寄りに追加」。この訪問を既存 place にひも付ける手動追加へ流す。
      onAddStop = if (uiState.isTracking) {
        { item ->
          manualTarget = ManualStopTarget(
            item.place.latitude,
            item.place.longitude,
            ManualStopOrigin.ExistingPlace(item.place.id, item.displayName),
          )
        }
      } else {
        null
      },
    )

    // この範囲の経路の一覧。閉じると重ねた線も消える。
    if (showOverlaySheet) {
      TrackOverlaySheet(
        state = overlayState,
        sheetState = overlaySheetState,
        onToggle = trackOverlayViewModel::toggle,
        onFocus = trackOverlayViewModel::focus,
        onClearAll = trackOverlayViewModel::clearAll,
        onClose = trackOverlayViewModel::closeSheet,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

    // 保存（確定）中は画面全体を覆う。終わっていないのに終わったように見せない（→ adr/0021）。
    if (uiState.isFinalizing) {
      FinalizingOverlay()
    }

    // 場所の削除を取り消すスナックバー（最前面）。シート・オーバーレイの上に出す。
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding(),
    )

    ManualStopHost(
      manualStop = viewModel.manualStop,
      placeEdit = viewModel.placeEdit,
      target = manualTarget,
      points = uiState.currentTrack?.smoothedPoints.orEmpty(),
      onAdded = { manualTarget = null },
      onCancel = { manualTarget = null },
      onRangeChange = { start, end -> manualRange = start to end },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      sheetState = manualSheetState,
    )

    // 地図はそのままに上端へ検索窓を重ねる。
    mapSearch?.let { mode ->
      TrackingMapSearch(
        mode = mode,
        bias = mapSearchBias,
        onPredictPlaces = viewModel::predictPlacesNear,
        onFetchPlace = viewModel.placeEdit::fetchPlaceResult,
        onPredictRegions = viewModel::predictRegions,
        onFetchRegion = viewModel::fetchRegion,
        // 検索して登録: 候補を選んだら閉じて、その場所へ地図を寄せ、場所シートで登録する。
        onPlaceSelected = { result ->
          mapSearch = null
          placeSheetTarget = PlaceSheetTarget.NewSearchResult(result)
          focusOn(LatLng(result.latitude, result.longitude))
        },
        // 地域へ移動: 地名を選んだら閉じて、その地域の範囲が収まるように地図を移す（現在地の追従は止まる）。
        onRegionSelected = { region ->
          mapSearch = null
          fitTo(region.bounds)
          regionMarker = region
        },
        onClose = { mapSearch = null },
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }

  // 停止の確認ダイアログ（誤爆防止）。
  if (showStopConfirm) {
    StopTrackingConfirmDialog(
      onConfirm = {
        showStopConfirm = false
        viewModel.stopTracking()
      },
      onDismiss = { showStopConfirm = false },
    )
  }

  StopReassignHost(
    stop = reassignTarget,
    manualStop = viewModel.manualStop,
    placeEdit = viewModel.placeEdit,
    onDismiss = { reassignTarget = null },
    infoWindow = infoWindow,
  )
}

/** 地図の上端に重ねる検索窓（[MapSearchMode] ごとに、候補の探し方と選んだあとの動きが違う）。 */
@Composable
private fun TrackingMapSearch(
  mode: MapSearchMode,
  bias: GeoBounds?,
  onPredictPlaces: suspend (query: String, bias: GeoBounds?) -> List<PlacePrediction>,
  onFetchPlace: suspend (placeId: String) -> PlaceSearchResult?,
  onPredictRegions: suspend (query: String, bias: GeoBounds?) -> List<PlacePrediction>,
  onFetchRegion: suspend (placeId: String) -> MapRegion?,
  onPlaceSelected: (PlaceSearchResult) -> Unit,
  onRegionSelected: (RegionMarker) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (mode) {
    MapSearchMode.Place -> TrackingPlaceSearchBar(
      placeholder = "店名・場所名で検索",
      fetchFailedText = "場所の取得に失敗しました（オフライン等）",
      onSearchPredictions = { query -> onPredictPlaces(query, bias) },
      onFetchPrediction = { prediction -> onFetchPlace(prediction.placeId) },
      onSelected = onPlaceSelected,
      onClose = onClose,
      modifier = modifier,
    )

    MapSearchMode.Region -> TrackingPlaceSearchBar(
      placeholder = "地名で探して移動",
      fetchFailedText = "地域の取得に失敗しました（オフライン等）",
      onSearchPredictions = { query -> onPredictRegions(query, bias) },
      // 目印の名前は候補の文字を使う（Places から名前を引くと料金の区分が上がるため）。
      onFetchPrediction = { prediction ->
        onFetchRegion(prediction.placeId)?.let { region ->
          RegionMarker(
            name = prediction.primaryText,
            position = LatLng(region.latitude, region.longitude),
            bounds = region.bounds.toLatLngBounds(),
          )
        }
      },
      onSelected = onRegionSelected,
      onClose = onClose,
      modifier = modifier,
    )
  }
}

/** 記録画面の地図に重ねる検索窓の種類。 */
private enum class MapSearchMode {
  /** 検索して登録（施設を探して場所シートで登録する）。 */
  Place,

  /** 地域へ移動（地名を探して地図を移す）。 */
  Region,
}

/** いま地図に映っている範囲（地図の準備前は null）。 */
private fun CameraPositionState.visibleGeoBounds(): GeoBounds? = projection?.visibleRegion?.latLngBounds?.toGeoBounds()

/** 「地域へ移動」で移した地域の目印（中心と名前）。[bounds] はチップの地名から寄せ直すときの範囲（選んだときと同じ）。 */
internal data class RegionMarker(val name: String, val position: LatLng, val bounds: LatLngBounds)
