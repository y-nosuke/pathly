package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.domain.model.WishlistFlag
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.DefaultMapLocation
import com.pathly.presentation.common.MapTopBarInset
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.SheetRestoreButtonInset
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFixedFloatingSheetState
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.toGeoBounds
import com.pathly.presentation.common.toLatLngBounds
import com.pathly.presentation.history.PlaceNameDialog
import com.pathly.presentation.history.RenameTrackDialog
import com.pathly.presentation.history.SameFacilityReview
import com.pathly.presentation.history.SameFacilityReviewDialog
import com.pathly.presentation.history.StopNoteDialog
import com.pathly.presentation.places.PLACE_SHEET_PEEK_FRACTION
import com.pathly.presentation.places.PlaceEditHost
import com.pathly.presentation.places.PlaceEditUi
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.LiveStopActions
import com.pathly.presentation.stops.ManualStopHost
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.StopDurationSheet
import com.pathly.presentation.stops.StopEditActions
import com.pathly.presentation.stops.StopReassignHost
import com.pathly.presentation.stops.isLive
import com.pathly.ui.theme.PathlySpacing
import com.pathly.util.DateFormatters
import kotlinx.coroutines.launch

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
  // 電池の最適化の帯を押したとき（設定タブの「電池の最適化」へ）。
  onOpenSettings: () -> Unit = {},
  viewModel: TrackingViewModel = hiltViewModel(),
  trackOverlayViewModel: TrackOverlayViewModel = hiltViewModel(),
  mapContent: (@Composable () -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val mapPlaceFilter by viewModel.mapPlaceFilter.collectAsStateWithLifecycle()
  val overlayState by trackOverlayViewModel.uiState.collectAsStateWithLifecycle()
  val regionMarkers by viewModel.regionMarkers.state.collectAsStateWithLifecycle()
  val unresolvedCount by viewModel.stopEdit.unresolvedCount.collectAsStateWithLifecycle()
  val sameFacilityPending by viewModel.stopEdit.sameFacilityPending.collectAsStateWithLifecycle()
  val sameFacilityDialogOpen by viewModel.stopEdit.sameFacilityDialogOpen.collectAsStateWithLifecycle()
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
  // 記録のシートの立ち寄りの行から開く編集（経路詳細と同じ）: 場所の名前・訪問のメモ・滞在時間・経路の名前。
  var editingStop by remember { mutableStateOf<Stop?>(null) }
  var editingNoteStop by remember { mutableStateOf<Stop?>(null) }
  var durationEditStop by remember { mutableStateOf<Stop?>(null) }
  // 滞在時間の編集で選んでいる区間（到着〜出発の点インデックス）。地図に青くハイライトする。
  var durationRange by remember(durationEditStop) { mutableStateOf<Pair<Int, Int>?>(null) }
  var renamingTrack by remember { mutableStateOf(false) }
  // 滞在区間を選ぶ軌跡の点（手動追加・滞在時間の編集）。記録中は点が増え続けるので、シートを開いた時点の点で固定する
  // （そのまま渡すと、位置の更新のたびに選んでいた区間が初めの位置へ戻る）。
  val manualPoints = remember(manualTarget) { uiState.currentTrack?.smoothedPoints.orEmpty() }
  val durationPoints = remember(durationEditStop) { uiState.currentTrack?.smoothedPoints.orEmpty() }
  // 「立ち寄りを追加」モード（記録中のみ）。ONの間は地図タップ＝立ち寄り追加、OFF＝場所登録。
  var manualMode by remember { mutableStateOf(false) }
  // 地図の上の検索窓（施設の登録と地域への移動を 1 つの窓で）を開いているか。
  var mapSearchOpen by remember { mutableStateOf(false) }
  // 検索窓を開いたときに地図に映っていた範囲。候補はこの中を優先する（範囲の外も出る）。
  var mapSearchBias by remember { mutableStateOf<GeoBounds?>(null) }
  // シートの開き具合。地図の下パディングを合わせて、選んだ地点がシートの裏に来ないようにする。
  val placeSheetState = rememberFloatingSheetState(peekFraction = PLACE_SHEET_PEEK_FRACTION)
  val manualSheetState = rememberFloatingSheetState(peekFraction = 0.5f)
  val durationSheetState = rememberFloatingSheetState(peekFraction = 0.5f)
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

  // 地域の目印（チップ・ピン）からその地域の範囲へ寄せ直す（選んだときと同じ合わせ方）。
  fun refitRegion(region: RegionMarker) {
    fitTo(region.bounds.toLatLngBounds())
    viewModel.regionMarkers.focus(region.placeId)
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
  val showOverlaySheet = overlayState.isSheetOpen && placeSheetTarget == null && manualTarget == null && durationEditStop == null
  // 下の記録のシート（記録中は情報と脇役の操作と立ち寄りの一覧、停止中は小さなシート）。ほかのシートが出ている間は下がる。
  // 高さが記録中と停止中で違うので、切り替わると開いた状態で作り直す（記録を始めたらシートが開く）。
  // 記録中は、立ち寄りがあればハーフで新しい 2 件ほどが見える高さにし、上へ引くと一覧が広がる（停止中は広げない）。
  val showRecordSheet = uiState.hasLocationPermission && placeSheetTarget == null && manualTarget == null &&
    durationEditStop == null && !showOverlaySheet
  val hasRecordingStops = uiState.stops.isNotEmpty() || uiState.currentStop != null
  // 出ているときだけの「やること」の帯の数（その分だけハーフを高くし、立ち寄りの行を押し出さない）。
  val todoCount = (if (unresolvedCount > 0) 1 else 0) + (if (sameFacilityPending.isNotEmpty()) 1 else 0)
  // 記録の状態（記録中/停止中）ごとの最後の段。最初の立ち寄り・やることの帯で高さが変わって作り直しても、同じ記録の
  // あいだは畳んだまま・広げたままにする（勝手に開いたり縮んだりしない）。記録を始めた・止めたときはハーフで開く。
  var recordSheetDetent by remember { mutableStateOf<Pair<Boolean, SheetDetent>?>(null) }
  val recordSheetState = rememberFixedFloatingSheetState(
    initialDetent = recordSheetDetent?.takeIf { it.first == uiState.isTracking }?.second ?: SheetDetent.PEEK,
    height = when {
      !uiState.isTracking -> STOPPED_SHEET_HEIGHT
      hasRecordingStops -> RECORDING_SHEET_WITH_STOPS_HEIGHT + RECORD_SHEET_TODO_HEIGHT * todoCount
      else -> RECORDING_SHEET_HEIGHT + RECORD_SHEET_TODO_HEIGHT * todoCount
    },
    fullFraction = if (uiState.isTracking && hasRecordingStops) RECORDING_SHEET_FULL_FRACTION else null,
  )
  LaunchedEffect(recordSheetState, uiState.isTracking) {
    snapshotFlow { recordSheetState.detent }.collect { detent ->
      recordSheetDetent = uiState.isTracking to detent
    }
  }
  // いま地図の下に出ているシート（地図の下の余白・右下の操作の位置を合わせる）。
  val activeSheet = when {
    manualTarget != null -> manualSheetState
    durationEditStop != null -> durationSheetState
    placeSheetTarget != null -> placeSheetState
    showOverlaySheet -> overlaySheetState
    showRecordSheet -> recordSheetState
    else -> null
  }
  // 停止中に現在地を取りに行っている間（上の下に出す）。
  var isLocating by remember { mutableStateOf(false) }
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
  // システムバックは、画面（アプリ）を抜ける前に、出ているものを 1 つずつ閉じる: 立ち寄りを足すシート → 地図で指すモード。
  // （場所シートは自分でバックを受ける。）
  BackHandler(enabled = manualTarget == null && manualMode) { manualMode = false }
  BackHandler(enabled = manualTarget != null) { manualTarget = null }
  // 滞在時間の編集中のバックは、画面を抜けるのではなく編集をやめる。
  BackHandler(enabled = durationEditStop != null) { durationEditStop = null }
  // 記録が止まったら、記録中の経路の立ち寄りの編集はやめる（記録のシートが停止中に変わる）。
  LaunchedEffect(uiState.isTracking) {
    if (!uiState.isTracking) {
      durationEditStop = null
      editingStop = null
      editingNoteStop = null
      renamingTrack = false
    }
  }
  // 立ち寄りの削除は確認を出さずにすぐ消し、スナックバーの「取り消す」で戻せる（経路詳細と同じ）。
  val scope = rememberCoroutineScope()
  fun deleteStopWithUndo(stop: Stop) {
    viewModel.stopEdit.deleteStops(listOf(stop.id))
    scope.launch {
      val result = snackbarHostState.showSnackbar(message = "1件を削除しました", actionLabel = "取り消す", duration = SnackbarDuration.Short)
      if (result == SnackbarResult.ActionPerformed) viewModel.stopEdit.undoStopChange()
    }
  }

  /**
   * 地図のタップ。検索窓を開いている間は、窓を閉じるだけ（地図を見たくてタップしたとみなす）。
   * 手動追加モード（記録中のみ）なら [manual] を立ち寄りを足す地点にして寄せる。それ以外は [onNormal]
   * （[focusInNormal] なら、その地点へも寄せる）。
   */
  fun onMapTap(manual: ManualStopTarget, focusInNormal: Boolean = true, onNormal: () -> Unit) {
    if (mapSearchOpen) {
      mapSearchOpen = false
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
        // 手動追加・滞在時間の編集で選んでいる滞在区間。
        manualHighlight = when {
          manualTarget != null -> manualRange?.let { range -> manualPoints to range }
          durationEditStop != null -> durationRange?.let { range -> durationPoints to range }
          else -> null
        }?.let { (pts, range) ->
          val (start, end) = range
          if (pts.size >= 2) pts.subList(start, (end + 1).coerceAtMost(pts.size)) else emptyList()
        }.orEmpty(),
        // 登録前に選んだ地点の青いピン。手動追加の地点か、検索で選んだ場所（地図の上に施設アイコンが無いこともあるので示す）。
        manualPickTarget = manualTarget?.let { LatLng(it.latitude, it.longitude) }
          ?: (placeSheetTarget as? PlaceSheetTarget.NewSearchResult)?.result?.let { LatLng(it.latitude, it.longitude) },
        // シートが出ている分だけ可視領域を狭める（ドラッグ中の逐次値ではなく段の確定値）。上は検索窓の分を空け、
        // マーカーを押して地図が寄ったときに吹き出しが検索窓に被らないようにする（ほかの地図の画面と同じ）。
        contentPadding = PaddingValues(top = MapTopBarInset, bottom = activeSheet?.let { it.heightOf(it.detent) } ?: 0.dp),
        // 右下の現在地ボタンはシートの上端に沿わせる。畳んだシートは戻すボタンの上。
        controlsBottomInset = activeSheet?.let {
          if (it.detent == SheetDetent.HIDDEN) SheetRestoreButtonInset else it.heightOf(it.detent)
        } ?: 0.dp,
        onLocatingChange = { isLocating = it },
        focusTarget = focusTarget,
        focusNonce = focusNonce,
        fitBounds = fitBounds,
        fitNonce = fitNonce,
        // 地域の目印のタップは、チップの地名と同じくその地域へ寄せ直す（外すのはチップの ×）。
        regionMarkers = regionMarkers.markers,
        onRegionMarkerClick = { region -> refitRegion(region) },
        onFitAllRegions = if (regionMarkers.markers.size >= 2) {
          {
            regionMarkers.markers.map { it.bounds }.toLatLngBounds()?.let(::fitTo)
            viewModel.regionMarkers.unfocus()
          }
        } else {
          null
        },
        // 登録済みマーカーのタップ: 手動追加モード＝既存placeへ紐付け／通常＝場所シート（その場で編集）。
        onRegisteredPlaceClick = { place ->
          val target = ManualStopTarget(place.latitude, place.longitude, ManualStopOrigin.ExistingPlace(place.placeId, place.displayName))
          onMapTap(target) { placeSheetTarget = PlaceSheetTarget.Existing(place.placeId) }
        },
      )
    }

    // 上の区画（検索の入口・レイヤー）と上の下（今の状態・1 行の帯）。検索窓を開いている間は窓と重なるので隠す。
    if (!mapSearchOpen) {
      TrackingTopBar(
        showRegisteredPlaces = uiState.showRegisteredPlaces,
        registeredPlaces = uiState.registeredPlaces,
        mapPlaceFilter = mapPlaceFilter,
        onToggleRegisteredPlaces = viewModel::toggleShowRegisteredPlaces,
        onMapPlaceFilterChange = viewModel::setMapPlaceFilter,
        overlayActive = overlayState.isSheetOpen,
        // 立ち寄りの追加中は経路の一覧を出せない（追加のシートが優先）ので押せなくする。検索窓は地域への移動だけになる。
        overlayEnabled = manualTarget == null,
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
        onSearch = {
          mapSearchBias = mapCameraState.visibleGeoBounds()
          viewModel.startPlaceSearch()
          mapSearchOpen = true
        },
        modifier = Modifier.align(Alignment.TopCenter),
      ) {
        // 今の状態のピル（記録中・現在地を取得中）を左寄せで 1 行に。
        Row(
          horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (uiState.isTracking) RecordingStatusPill(startTime = uiState.currentTrack?.startTime)
          if (isLocating) LocatingPill()
        }
        // 「地域へ移動」で出した目印の地名のチップ（左上に縦に並べ、6 つ目からは 1 つにまとめる）。
        // 地名でその地域の範囲へ寄せ直し、× でその目印を外す（地図は動かさない）。
        RegionMarkerChips(
          markers = regionMarkers.markers,
          focusedPlaceId = regionMarkers.focusedPlaceId,
          onRefit = { region -> refitRegion(region) },
          onClear = { region -> viewModel.regionMarkers.remove(region.placeId) },
          onClearAll = viewModel.regionMarkers::clear,
        )
        // 場所を登録した直後の一時メッセージ
        uiState.placeRegisteredMessage?.let { msg ->
          PlaceRegisteredToast(message = msg, onShown = viewModel::clearPlaceRegisteredMessage)
        }
        TrackingNotices(
          uiState = uiState,
          onOpenBatterySettings = onOpenSettings,
          onOpenLocationSettings = viewModel::openLocationSettings,
        )
      }
    }

    // 権限がない場合のオーバーレイ（中央）
    if (!uiState.hasLocationPermission) {
      LocationPermissionOverlay(
        onRequestPermission = onRequestPermission,
        modifier = Modifier
          .align(Alignment.Center)
          .padding(PathlySpacing.xl),
      )
    }

    // 下の記録のシート。主役の開始・停止もシートの中にある（畳むと戻すボタンだけが残る → adr/0044）。
    if (showRecordSheet) {
      TrackingRecordSheet(
        uiState = uiState,
        sheetState = recordSheetState,
        manualMode = manualMode,
        onToggleManualMode = { manualMode = !manualMode },
        onAddStopHere = { loc -> manualTarget = ManualStopTarget(loc.latitude, loc.longitude, ManualStopOrigin.CurrentLocation) },
        onStartTracking = viewModel::startTracking,
        onStopTracking = { showStopConfirm = true },
        todos = RecordSheetTodos(
          unresolvedCount = unresolvedCount,
          onResolveNames = viewModel.stopEdit::resolveNames,
          sameFacilityPendingCount = sameFacilityPending.size,
          onOpenSameFacility = viewModel.stopEdit::openSameFacilityDialog,
        ),
        onRenameTrack = { renamingTrack = true },
        rowActions = RecordingStopRowActions(
          onFocus = { stop -> focusOn(LatLng(stop.place.latitude, stop.place.longitude)) },
          onToggleWishlist = { stop -> viewModel.stopEdit.toggleWishlist(stop.place.id) },
          onEditName = { stop -> editingStop = stop },
          onEditNote = { stop -> editingNoteStop = stop },
          // 滞在時間の編集・場所の選び直しは、対象を地図に出してから始める（地図を見ながら直せるように）。
          onEditDuration = { stop ->
            focusOn(LatLng(stop.place.latitude, stop.place.longitude))
            durationEditStop = stop
          },
          canEditDuration = (uiState.currentTrack?.smoothedPoints?.size ?: 0) >= 2,
          onReassign = { stop ->
            focusOn(LatLng(stop.place.latitude, stop.place.longitude))
            reassignTarget = stop
          },
          onDelete = ::deleteStopWithUndo,
          live = LiveStopActions(
            onEditNote = { stop -> editingNoteStop = stop },
            onEditName = { stop -> editingStop = stop },
            onEditArrival = { stop ->
              focusOn(LatLng(stop.place.latitude, stop.place.longitude))
              durationEditStop = stop
            },
            // 場所の選び直しは「今ここを立ち寄り」と同じ候補のシートで（到着はそのまま、場所だけ変わる）。
            onReassign = { stop ->
              manualTarget = ManualStopTarget(stop.place.latitude, stop.place.longitude, ManualStopOrigin.CurrentLocation)
            },
            onDismiss = { viewModel.stopEdit.dismissLiveStop() },
          ),
        ),
        wishlistFlags = remember(uiState.registeredPlaces) {
          uiState.registeredPlaces.associate { it.placeId to WishlistFlag.of(it.isWishlisted, it.fulfilledWishCount) }
        },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

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

    // 滞在時間の編集: 地図を見ながら滞在区間を調整する（経路詳細と同じシート）。
    durationEditStop?.let { stop ->
      val points = durationPoints
      if (points.size >= 2) {
        CloseInfoWindowWithSheet(infoWindow)
        StopDurationSheet(
          stop = stop,
          points = points,
          onConfirm = { arrivalIdx, departureIdx ->
            if (stop.isLive) {
              viewModel.stopEdit.updateLiveStopArrival(points[arrivalIdx].timestamp)
            } else {
              viewModel.stopEdit.updateStopDuration(stop.id, points[arrivalIdx].timestamp, points[departureIdx].timestamp)
            }
            durationEditStop = null
          },
          arrivalOnly = stop.isLive,
          onCancel = { durationEditStop = null },
          onRangeChange = { start, end -> durationRange = start to end },
          sheetState = durationSheetState,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
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
      points = manualPoints,
      onAdded = { manualTarget = null },
      onCancel = { manualTarget = null },
      onRangeChange = { start, end -> manualRange = start to end },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      sheetState = manualSheetState,
      liveStop = uiState.currentStop,
    )

    // 地図はそのままに上端へ検索窓を重ねる。
    if (mapSearchOpen) {
      TrackingMapSearch(
        registerEnabled = manualTarget == null,
        bias = mapSearchBias,
        onPredictPlaces = viewModel::predictPlacesNear,
        onFetchPlace = viewModel.placeEdit::fetchPlaceResult,
        onPredictRegions = viewModel::predictRegions,
        onFetchRegion = viewModel::fetchRegion,
        regionMarkers = regionMarkers,
        // 登録: 候補を選んだら閉じて、その場所へ地図を寄せ、場所シートで登録する。
        onPlaceSelected = { result ->
          mapSearchOpen = false
          placeSheetTarget = PlaceSheetTarget.NewSearchResult(result)
          focusOn(LatLng(result.latitude, result.longitude))
        },
        // 地域へ移動: 地名を選んだら閉じて、目印を足し、その地域の範囲が収まるように地図を移す（現在地の追従は止まる）。
        onRegionSelected = { region ->
          mapSearchOpen = false
          viewModel.regionMarkers.show(region)
          fitTo(region.bounds.toLatLngBounds())
        },
        onClose = { mapSearchOpen = false },
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

  TrackingStopDialogs(
    track = uiState.currentTrack,
    stops = uiState.stops,
    editingStop = editingStop,
    editingNoteStop = editingNoteStop,
    renamingTrack = renamingTrack,
    stopEdit = viewModel.stopEdit,
    sameFacility = viewModel.stopEdit.sameFacilityReview(sameFacilityPending, sameFacilityDialogOpen),
    placeEdit = viewModel.placeEdit,
    onRenameTrack = viewModel::renameTrack,
    onPickAnotherFacility = { placeId -> placeSheetTarget = PlaceSheetTarget.Existing(placeId, openLinkPicker = true) },
    onDismissEditingStop = { editingStop = null },
    onDismissEditingNote = { editingNoteStop = null },
    onDismissRename = { renamingTrack = false },
  )

  StopReassignHost(
    stop = reassignTarget,
    manualStop = viewModel.manualStop,
    placeEdit = viewModel.placeEdit,
    onDismiss = { reassignTarget = null },
    infoWindow = infoWindow,
  )
}

/**
 * 地図の上端に重ねる検索窓。施設と地域の候補を一緒に出し、選んだ行の動き（[MapSearchAction]）で分ける:
 * 登録 → 場所の詳細を引いて場所シート、地域へ移動 → 範囲を引いて目印。
 * [registerEnabled] が false（立ち寄りの追加中）なら、候補を地域に絞って地域へ移動だけにする。
 */
@Composable
private fun TrackingMapSearch(
  registerEnabled: Boolean,
  bias: GeoBounds?,
  onPredictPlaces: suspend (query: String, bias: GeoBounds?) -> List<PlacePrediction>,
  onFetchPlace: suspend (placeId: String) -> PlaceSearchResult?,
  onPredictRegions: suspend (query: String, bias: GeoBounds?) -> List<PlacePrediction>,
  onFetchRegion: suspend (placeId: String) -> MapRegion?,
  regionMarkers: RegionMarkersState,
  onPlaceSelected: (PlaceSearchResult) -> Unit,
  onRegionSelected: (RegionMarker) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TrackingPlaceSearchBar(
    placeholder = if (registerEnabled) "場所・地域を検索" else "地域を検索",
    registerEnabled = registerEnabled,
    // 施設と地域を 1 回の Autocomplete で取る（2 回に分けると、選ばずに閉じたときの料金が倍になる）。
    onSearchPredictions = { query -> if (registerEnabled) onPredictPlaces(query, bias) else onPredictRegions(query, bias) },
    onFetchCandidate = { candidate ->
      val prediction = candidate.prediction
      when (candidate.action) {
        MapSearchAction.Register -> onFetchPlace(prediction.placeId)?.let(MapSearchSelection::Place)

        // もう出している地域は、引き直さずに覚えている範囲へ移す。
        // 目印の名前は候補の文字を使う（Places から名前を引くと料金の区分が上がるため）。
        MapSearchAction.MoveToRegion -> (
          regionMarkers.find(prediction.placeId) ?: onFetchRegion(prediction.placeId)?.let { region ->
            RegionMarker(
              placeId = prediction.placeId,
              name = prediction.primaryText,
              latitude = region.latitude,
              longitude = region.longitude,
              bounds = region.bounds,
            )
          }
          )?.let(MapSearchSelection::Region)
      }
    },
    onSelected = { selection ->
      when (selection) {
        is MapSearchSelection.Place -> onPlaceSelected(selection.result)
        is MapSearchSelection.Region -> onRegionSelected(selection.marker)
      }
    },
    onClose = onClose,
    modifier = modifier,
  )
}

/** 検索窓で選んだもの（引いた結果）。 */
private sealed interface MapSearchSelection {
  data class Place(val result: PlaceSearchResult) : MapSearchSelection

  data class Region(val marker: RegionMarker) : MapSearchSelection
}

/** 記録のシートの立ち寄りの行・経路の名前から開くダイアログ（経路詳細と同じもの）。 */
@Composable
private fun TrackingStopDialogs(
  track: GpsTrack?,
  stops: List<Stop>,
  editingStop: Stop?,
  editingNoteStop: Stop?,
  renamingTrack: Boolean,
  stopEdit: StopEditActions,
  sameFacility: SameFacilityReview,
  placeEdit: PlaceEditUi,
  onRenameTrack: (String) -> Unit,
  onPickAnotherFacility: (placeId: Long) -> Unit,
  onDismissEditingStop: () -> Unit,
  onDismissEditingNote: () -> Unit,
  onDismissRename: () -> Unit,
) {
  SameFacilityReviewDialog(sameFacility = sameFacility, stops = stops, placeEdit = placeEdit, onPickAnother = onPickAnotherFacility)
  if (renamingTrack && track != null) {
    RenameTrackDialog(
      initialName = track.name.orEmpty(),
      defaultName = DateFormatters.date(track.startTime),
      onDismiss = onDismissRename,
      onConfirm = { name ->
        onRenameTrack(name)
        onDismissRename()
      },
    )
  }
  editingStop?.let { stop ->
    PlaceNameDialog(
      stop = stop,
      onDismiss = onDismissEditingStop,
      onConfirm = { name ->
        stopEdit.updatePlaceName(stop.place.id, name)
        onDismissEditingStop()
      },
    )
  }
  editingNoteStop?.let { stop ->
    StopNoteDialog(
      stop = stop,
      onDismiss = onDismissEditingNote,
      onConfirm = { note ->
        stopEdit.updateNote(stop, note)
        onDismissEditingNote()
      },
    )
  }
}

/** 記録中でまだ立ち寄りが無いときの記録のシートの高さ（つまみ・経路の名前・情報の段と停止・脇役の操作の段・「まだ立ち寄りはありません」）。 */
private val RECORDING_SHEET_HEIGHT = 280.dp

/** 記録中で立ち寄りがあるときの、記録のシートのハーフの高さ（上の段に加えて、新しい立ち寄りが 2 件ほど見える）。 */
private val RECORDING_SHEET_WITH_STOPS_HEIGHT = 376.dp

/** 記録中で立ち寄りがあるときの、記録のシートのフルの高さ（画面に対する割合）。上の区画（検索・ピル・案内の帯）は隠さない高さにとどめる。 */
private const val RECORDING_SHEET_FULL_FRACTION = 0.6f

/** 記録のシートの「やること」の帯 1 本ぶんの高さ（帯と上の余白）。出ている数だけハーフを高くする。 */
private val RECORD_SHEET_TODO_HEIGHT = 52.dp

/** 停止中の記録のシートの高さ（つまみと「記録していません」・開始の丸）。 */
private val STOPPED_SHEET_HEIGHT = 128.dp

/** いま地図に映っている範囲（地図の準備前は null）。 */
private fun CameraPositionState.visibleGeoBounds(): GeoBounds? = projection?.visibleRegion?.latLngBounds?.toGeoBounds()
