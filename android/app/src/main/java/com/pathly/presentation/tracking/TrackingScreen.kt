package com.pathly.presentation.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.ManualStopOrigin
import com.pathly.presentation.common.ManualStopSheet
import com.pathly.presentation.common.ManualStopTarget
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.presentation.common.RouteMapContent
import com.pathly.presentation.common.StopReassignDialog
import com.pathly.presentation.common.stopSegmentPoints
import com.pathly.presentation.places.PlaceActionSheet
import com.pathly.presentation.places.PlaceSheetTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

/** 手動追加のハイライト（選択した滞在区間）。軌跡・立ち寄りと見分ける青。経路詳細と揃える。 */
private val manualHighlightColor = Color(0xFF1E88E5)

@Composable
fun TrackingScreen(
  modifier: Modifier = Modifier,
  onRequestPermission: () -> Unit,
  onOpenPlaceDetail: (placeId: Long) -> Unit = {},
  viewModel: TrackingViewModel = hiltViewModel(),
  // 地図スロット。null（既定）は実マップ（GoogleMap）を描画する。
  // テストは空スロット（{}）を渡し、GMS 依存の地図描画を避けてオーバーレイだけを検証する。
  mapContent: (@Composable () -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // 復帰のたびに権限・電池最適化の状態を再確認（システム設定から戻ったときに反映するため）
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.checkLocationPermission()
    viewModel.checkBatteryOptimization()
  }

  // 中断された記録の再開/完了を確認するダイアログ
  if (uiState.interruptedTrack != null) {
    AlertDialog(
      onDismissRequest = viewModel::finishInterruptedTracking,
      title = { Text("前回の記録が中断されています") },
      text = { Text("アプリの更新などで記録が中断されました。続けて記録を再開しますか？") },
      confirmButton = {
        TextButton(onClick = viewModel::resumeTracking) {
          Text("再開する")
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::finishInterruptedTracking) {
          Text("完了にする")
        }
      },
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
  // 記録が止まったら手動追加モードは自動で抜ける（立ち寄りを足すトラックが無い）。
  LaunchedEffect(uiState.isTracking) { if (!uiState.isTracking) manualMode = false }
  val scope = rememberCoroutineScope()

  Box(modifier = modifier.fillMaxSize()) {
    if (mapContent != null) {
      mapContent()
    } else {
      TrackingMapView(
        hasPermission = uiState.hasLocationPermission,
        track = uiState.currentTrack,
        currentLocation = uiState.currentLocation,
        stops = uiState.stops,
        currentStop = uiState.currentStop,
        // 手動追加モード（記録中のみ）: POIタップも立ち寄り追加に。通常は統一の場所シート（POI登録）。
        onPoiClick = { poi ->
          if (uiState.isTracking && manualMode) {
            // 施設は確定しているので、名前と placeId を持ったまま渡す（候補の選び直しは出さない）。
            manualTarget = ManualStopTarget(
              poi.latLng.latitude,
              poi.latLng.longitude,
              ManualStopOrigin.Poi(poi.name, poi.placeId),
            )
          } else {
            placeSheetTarget = PlaceSheetTarget.NewPoi(poi)
          }
        },
        // 手動追加モード＝立ち寄り追加、それ以外（通常/平常時）＝場所シート（空き地点の登録）。
        onMapClick = { latLng ->
          if (uiState.isTracking && manualMode) {
            manualTarget = ManualStopTarget(latLng.latitude, latLng.longitude, ManualStopOrigin.MapPoint)
          } else {
            placeSheetTarget = PlaceSheetTarget.NewPoint(latLng)
          }
        },
        // 立ち寄りマーカーのタップで「場所を選び直す」（誤検知の訂正）。
        onStopClick = { reassignTarget = it },
        modifier = Modifier.fillMaxSize(),
        registeredPlaces = if (uiState.showRegisteredPlaces) uiState.registeredPlaces else emptyList(),
        // 登録済みマーカーのタップ: 手動追加モード＝既存placeへ紐付け／通常＝場所シート（その場で編集）。
        manualHighlight = manualTarget?.let { _ ->
          val pts = uiState.currentTrack?.smoothedPoints.orEmpty()
          manualRange?.let { (start, end) ->
            if (pts.size >= 2) pts.subList(start, (end + 1).coerceAtMost(pts.size)) else emptyList()
          }
        }.orEmpty(),
        manualPickTarget = manualTarget?.let { LatLng(it.latitude, it.longitude) },
        onRegisteredPlaceClick = { place ->
          if (uiState.isTracking && manualMode) {
            manualTarget = ManualStopTarget(
              place.latitude,
              place.longitude,
              ManualStopOrigin.ExistingPlace(place.placeId, place.displayName),
            )
          } else {
            placeSheetTarget = PlaceSheetTarget.Existing(place.placeId)
          }
        },
      )
    }

    // 登録済みの場所を地図に出すトグル（記録画面・画面別）。上部右に置く。
    Surface(
      onClick = { viewModel.toggleShowRegisteredPlaces() },
      shape = CircleShape,
      color = if (uiState.showRegisteredPlaces) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .statusBarsPadding()
        .padding(12.dp),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_place),
        contentDescription = if (uiState.showRegisteredPlaces) "登録済みの場所を隠す" else "登録済みの場所を表示",
        tint = if (uiState.showRegisteredPlaces) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
      )
    }

    // 記録中の左上操作（縦積み）: 「立ち寄りを追加」モード切替 と 「今ここ」ショートカット。
    if (uiState.isTracking) {
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .statusBarsPadding()
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // 「立ち寄りを追加」モード（ON=地図タップが立ち寄り追加／OFF=場所登録）。
        Surface(
          onClick = { manualMode = !manualMode },
          shape = RoundedCornerShape(20.dp),
          color = if (manualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
          shadowElevation = 4.dp,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_place),
              contentDescription = null,
              tint = if (manualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (manualMode) "立ち寄り追加中（地図をタップ）" else "立ち寄りを追加",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
              color = if (manualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        // 「今ここ」を立ち寄りに追加（現在地あり）。
        if (uiState.hasLocationPermission) {
          val loc = uiState.currentLocation
          Surface(
            // 地点を指していないので、近くの施設候補から選ばせる。
            onClick = {
              loc?.let { manualTarget = ManualStopTarget(it.latitude, it.longitude, ManualStopOrigin.CurrentLocation) }
            },
            enabled = loc != null,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                painter = painterResource(R.drawable.ic_place),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "今ここを立ち寄り",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
      }
    }

    // 記録中の状態ピル（上部中央）
    if (uiState.isTracking) {
      RecordingStatusPill(
        startTime = uiState.currentTrack?.startTime,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 12.dp),
      )
    }

    // 場所を登録した直後の一時メッセージ
    uiState.placeRegisteredMessage?.let { msg ->
      LaunchedEffect(msg) {
        delay(2000)
        viewModel.clearPlaceRegisteredMessage()
      }
      Surface(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = if (uiState.isTracking) 56.dp else 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp,
      ) {
        Text(
          text = msg,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
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

    // 下部コントロール：電池最適化の案内＋統計カード（記録中）＋エラー＋記録ボタン
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 電池の最適化が有効なままだとバックグラウンド記録が止まりやすいので案内する
      if (uiState.hasLocationPermission && !uiState.isIgnoringBatteryOptimizations) {
        BatteryOptimizationCard(
          onDisable = viewModel::requestDisableBatteryOptimization,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (uiState.isTracking) {
        // 「立ち寄り中」は地図上のマーカー（滞在時間ラベル付き）で示すので、下部カードは出さない。
        TrackingStatsCard(
          track = uiState.currentTrack,
          locationCount = uiState.locationCount,
        )
      }

      uiState.errorMessage?.let { error ->
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
          ),
        ) {
          Text(
            text = error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
        }
      }

      if (uiState.hasLocationPermission) {
        RecordFab(
          isTracking = uiState.isTracking,
          onStartTracking = viewModel::startTracking,
          onStopTracking = { showStopConfirm = true },
        )
      }
    }

    // 地図の1点タップで開く統一の「場所シート」。未登録の空き地点/POI＝登録、登録済み＝その場で編集。
    placeSheetTarget?.let { target ->
      PlaceActionSheet(
        target = target,
        onDismiss = { placeSheetTarget = null },
        onFetchPoiDetails = viewModel::fetchPoiDetails,
        onLoadPlace = viewModel::loadPlace,
        // POI か空き地点か、近接確認が要るかの判断は ViewModel（PlaceEditUseCase）が持つ。
        onRegisterNew = viewModel::registerPlaceWithNearbyCheck,
        onSaveExisting = { item, name, note, wishlist, priority, visited ->
          viewModel.savePlaceEdits(item, name, note, wishlist, priority, visited)
        },
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
        onOpenDetail = onOpenPlaceDetail,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

    manualTarget?.let { target ->
      ManualStopSheet(
        origin = target.origin,
        latitude = target.latitude,
        longitude = target.longitude,
        points = uiState.currentTrack?.smoothedPoints.orEmpty(),
        onFetchCandidates = viewModel::nearbyPois,
        onConfirm = { input ->
          val origin = target.origin
          if (origin is ManualStopOrigin.ExistingPlace) {
            viewModel.addManualStopForPlace(origin.placeId, input.arrivalTime, input.departureTime)
          } else {
            viewModel.addManualStopWithNearbyCheck(
              input.latitude,
              input.longitude,
              input.arrivalTime,
              input.departureTime,
              input.name,
              input.googlePlaceId,
            )
          }
          manualTarget = null
        },
        onCancel = { manualTarget = null },
        onRangeChange = { start, end -> manualRange = start to end },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }

  // 停止の確認ダイアログ（誤爆防止）。
  if (showStopConfirm) {
    AlertDialog(
      onDismissRequest = { showStopConfirm = false },
      title = { Text("記録を停止しますか？") },
      text = { Text("記録を停止して履歴に保存します。") },
      confirmButton = {
        TextButton(onClick = {
          showStopConfirm = false
          viewModel.stopTracking()
        }) {
          Text("停止する")
        }
      },
      dismissButton = {
        TextButton(onClick = { showStopConfirm = false }) { Text("続ける") }
      },
    )
  }

  // 場所登録の近接確認: 近くの既存に紐付け／新規で登録。
  uiState.nearbyRegisterPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      onLink = viewModel::confirmNearbyLink,
      onCreateNew = viewModel::confirmNearbyNew,
      onDismiss = viewModel::dismissNearbyPrompt,
    )
  }

  // 近接確認: 近くに既存があれば紐付け／新規を選ぶ。
  uiState.nearbyStopPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      onLink = viewModel::confirmNearbyStopLink,
      onCreateNew = viewModel::confirmNearbyStopNew,
      onDismiss = viewModel::dismissNearbyStopPrompt,
    )
  }

  reassignTarget?.let { stop ->
    StopReassignDialog(
      stop = stop,
      onFetchCandidates = viewModel::nearbyPois,
      onConfirm = { chosen, customName ->
        viewModel.reassignStop(stop.id, chosen, customName)
        reassignTarget = null
      },
      onDismiss = { reassignTarget = null },
    )
  }
}

@Composable
private fun TrackingMapView(
  hasPermission: Boolean,
  track: GpsTrack?,
  currentLocation: LocationInfo?,
  stops: List<Stop>,
  currentStop: Stop?,
  onPoiClick: (PointOfInterest) -> Unit,
  onMapClick: (LatLng) -> Unit,
  onStopClick: (Stop) -> Unit,
  modifier: Modifier = Modifier,
  registeredPlaces: List<RegisteredPlace> = emptyList(),
  onRegisteredPlaceClick: (RegisteredPlace) -> Unit = {},
  // 手動追加で選んでいる滞在区間（青くハイライト）と、指した地点（青いピン）。
  manualHighlight: List<GpsPoint> = emptyList(),
  manualPickTarget: LatLng? = null,
) {
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(LatLng(35.6762, 139.6503), 15f)
  }
  var followUser by remember { mutableStateOf(true) }
  var isLocating by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

  fun applyCamera(latitude: Double, longitude: Double, animate: Boolean) {
    val update = CameraUpdateFactory.newLatLng(LatLng(latitude, longitude))
    if (animate) {
      scope.launch { cameraPositionState.animate(update) }
    } else {
      // 初回は横断的なアニメーションを避けるため瞬時に移動する
      cameraPositionState.move(update)
    }
  }

  // 現在地へセンタリングして追従を再開する。
  // 記録中はサービスの現在地、停止中は単発取得した現在地を使う（取得中はローディング表示）。
  fun centerOnCurrentLocation(animate: Boolean) {
    followUser = true
    val known = currentLocation
    if (known != null) {
      applyCamera(known.latitude, known.longitude, animate)
      return
    }
    if (!hasPermission) return
    isLocating = true
    try {
      fusedClient.getCurrentLocation(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        CancellationTokenSource().token,
      ).addOnCompleteListener { task ->
        isLocating = false
        val location = if (task.isSuccessful) task.result else null
        location?.let { applyCamera(it.latitude, it.longitude, animate) }
      }
    } catch (e: SecurityException) {
      isLocating = false
    }
  }

  // 画面表示時（権限取得時）に一度、現在地へセンタリングする（初回は瞬時に移動）
  LaunchedEffect(hasPermission) {
    if (hasPermission) {
      centerOnCurrentLocation(animate = false)
    }
  }

  // ユーザーが地図を手で操作したら追従を止める（現在地ボタンで再センター可能）
  LaunchedEffect(cameraPositionState) {
    snapshotFlow { cameraPositionState.cameraMoveStartedReason }
      .collect { reason ->
        if (reason == CameraMoveStartedReason.GESTURE) {
          followUser = false
        }
      }
  }

  // 追従中のみ現在地へカメラを移動
  LaunchedEffect(currentLocation, followUser) {
    if (followUser) {
      currentLocation?.let { loc ->
        cameraPositionState.animate(
          CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)),
        )
      }
    }
  }

  Box(modifier = modifier) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      properties = MapProperties(
        mapType = MapType.NORMAL,
        isMyLocationEnabled = hasPermission,
      ),
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        // 標準ボタンは追従状態を反映しないため無効化し、自作ボタンを使う
        myLocationButtonEnabled = false,
        mapToolbarEnabled = false,
        compassEnabled = false,
      ),
      // 施設アイコン（POI）をタップしたら場所登録ダイアログを開く
      onPOIClick = onPoiClick,
      // 空きスペースのタップは手動立ち寄り追加に使う（呼び出し側で記録中のみ受ける）
      onMapClick = onMapClick,
    ) {
      val displayPoints = track?.smoothedPoints.orEmpty()
      if (track != null && displayPoints.size >= 2) {
        // 確定済み立ち寄りの滞在区間（帯）。詳細画面と同じ描画。
        val stopSegments = remember(displayPoints, stops) {
          stops.mapNotNull { s ->
            stopSegmentPoints(displayPoints, s.arrivalTime, s.departureTime).takeIf { it.size >= 2 }
          }
        }
        // 立ち寄り中（ライブ）の滞在区間。
        val currentSeg = remember(displayPoints, currentStop) {
          currentStop?.let { stopSegmentPoints(displayPoints, it.arrivalTime, it.departureTime) }.orEmpty()
        }
        RouteMapContent(
          track = track,
          displayPoints = displayPoints,
          stops = stops,
          stopSegments = stopSegments,
          currentStop = currentStop,
          currentStopSegment = currentSeg,
          // 記録中は端末の現在地ドットがあるので、終了/現在地マーカーは重複を避けて出さない。
          showEndMarker = false,
          onStopClick = onStopClick,
        )
      }
      // 手動追加: 選んだ滞在区間を青い太線で見せる（経路詳細と同じ描き分け）。
      if (manualHighlight.size >= 2) {
        Polyline(
          points = manualHighlight.map { LatLng(it.latitude, it.longitude) },
          color = manualHighlightColor,
          width = 14f,
        )
      }
      manualPickTarget?.let { pick ->
        val pickMarkerState = remember(pick) { MarkerState(position = pick) }
        Marker(
          state = pickMarkerState,
          title = "追加する地点",
          icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
        )
      }
      // 登録済みの場所（トグルON）。トラック未確定でも出せるよう、経路描画とは独立して描く。
      // ただし、この経路の立ち寄り（確定＝紫番号／滞在中＝ティール）と同じ場所は二重に出さない
      // （登録済みピンが立ち寄りマーカーを覆って分かりにくくなるのを防ぐ）。
      val stopPlaceIds = remember(stops, currentStop) {
        (stops.map { it.place.id } + listOfNotNull(currentStop?.place?.id)).toSet()
      }
      RegisteredPlaceMarkers(registeredPlaces.filter { it.placeId !in stopPlaceIds }, onRegisteredPlaceClick)
    }

    // 現在地ボタン：追従中は活性（色付き）、固定中は非活性（グレー）。タップで追従再開＆リセンター。
    if (hasPermission) {
      FollowLocationButton(
        following = followUser,
        onClick = { centerOnCurrentLocation(animate = true) },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(12.dp),
      )
    }

    // 現在地を取得中のローディング表示（上部中央）
    if (isLocating) {
      Surface(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "現在地を取得中…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun FollowLocationButton(
  following: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.size(44.dp),
    shape = CircleShape,
    color = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    shadowElevation = 4.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painter = painterResource(R.drawable.ic_my_location),
        contentDescription = if (following) "現在地に追従中" else "現在地へ移動",
        tint = if (following) {
          MaterialTheme.colorScheme.onPrimary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun RecordingStatusPill(
  startTime: Date?,
  modifier: Modifier = Modifier,
) {
  var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(startTime) {
    while (true) {
      nowMs = System.currentTimeMillis()
      delay(1000)
    }
  }
  val elapsedText = startTime?.let { formatElapsed(nowMs - it.time) } ?: "00:00"

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.errorContainer,
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "● 記録中",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onErrorContainer,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = elapsedText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
  }
}

@Composable
private fun TrackingStatsCard(
  track: GpsTrack?,
  locationCount: Int,
  modifier: Modifier = Modifier,
) {
  val distanceKm = ((track?.totalDistanceMeters ?: 0.0) / 1000.0 * 100).roundToInt() / 100.0
  val pointCount = track?.points?.size ?: locationCount

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
      StatColumn(value = "${distanceKm}km", label = "移動距離")
      StatColumn(value = "$pointCount", label = "地点数")
    }
  }
}

@Composable
private fun StatColumn(
  value: String,
  label: String,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
private fun RecordFab(
  isTracking: Boolean,
  onStartTracking: () -> Unit,
  onStopTracking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FloatingActionButton(
    onClick = if (isTracking) onStopTracking else onStartTracking,
    modifier = modifier.size(72.dp),
    shape = CircleShape,
    containerColor = if (isTracking) {
      MaterialTheme.colorScheme.error
    } else {
      MaterialTheme.colorScheme.primary
    },
  ) {
    Icon(
      painter = painterResource(
        if (isTracking) R.drawable.ic_stop else R.drawable.ic_play_arrow,
      ),
      contentDescription = if (isTracking) "記録停止" else "記録開始",
      modifier = Modifier.size(32.dp),
    )
  }
}

@Composable
private fun LocationPermissionOverlay(
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface,
    ),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "位置情報の権限が必要です",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "GPS記録機能を使用するため、位置情報へのアクセスを許可してください。",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onRequestPermission) {
        Text("位置情報を許可")
      }
    }
  }
}

@Composable
private fun BatteryOptimizationCard(
  onDisable: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    shadowElevation = 4.dp,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "バックグラウンド記録を安定させる",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Text(
        text = "電池の最適化を無効にすると、アプリを閉じても記録が止まりにくくなります。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Button(onClick = onDisable) {
        Text("電池の最適化を無効にする")
      }
    }
  }
}

private fun formatElapsed(millis: Long): String {
  val totalSeconds = (millis / 1000).coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%02d:%02d".format(minutes, seconds)
  }
}
