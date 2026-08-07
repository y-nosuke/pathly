package com.pathly.presentation.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.RouteMapContent
import com.pathly.presentation.common.StopReassignDialog
import com.pathly.presentation.common.stopSegmentPoints
import com.pathly.presentation.places.RegisterPlaceFromPoiDialog
import com.pathly.util.DateFormatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun TrackingScreen(
  modifier: Modifier = Modifier,
  onRequestPermission: () -> Unit,
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

  var poiTarget by remember { mutableStateOf<PointOfInterest?>(null) }
  // 停止の誤爆防止（記録中の停止だけ確認を挟む）。
  var showStopConfirm by remember { mutableStateOf(false) }
  // 手動で立ち寄りを追加する対象地点（「今ここ」または地図タップ）。非nullで確認ダイアログを出す。
  var manualTarget by remember { mutableStateOf<LatLng?>(null) }
  // 立ち寄りマーカーをタップして「場所を選び直す」対象（誤検知の訂正）。
  var reassignTarget by remember { mutableStateOf<Stop?>(null) }

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
        onPoiClick = { poiTarget = it },
        // 記録中は地図の空きタップで、その地点を手動立ち寄りとして追加できる。
        onMapClick = { latLng -> if (uiState.isTracking) manualTarget = latLng },
        // 立ち寄りマーカーのタップで「場所を選び直す」（誤検知の訂正）。
        onStopClick = { reassignTarget = it },
        modifier = Modifier.fillMaxSize(),
      )
    }

    // 「今ここ」を立ち寄りに追加（記録中・現在地あり）。地図左上に置いて下部の操作と干渉させない。
    if (uiState.isTracking && uiState.hasLocationPermission) {
      val loc = uiState.currentLocation
      Surface(
        onClick = { loc?.let { manualTarget = LatLng(it.latitude, it.longitude) } },
        enabled = loc != null,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(12.dp),
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

  poiTarget?.let { poi ->
    RegisterPlaceFromPoiDialog(
      poi = poi,
      onDismiss = { poiTarget = null },
      onFetchDetails = viewModel::fetchPoiDetails,
      onRegister = { name, wishlist, priority, memo ->
        viewModel.registerPlace(
          poi.latLng.latitude,
          poi.latLng.longitude,
          name,
          wishlist,
          priority,
          memo,
          poi.placeId,
        )
        poiTarget = null
      },
    )
  }

  manualTarget?.let { target ->
    ManualStopDialog(
      target = target,
      points = uiState.currentTrack?.smoothedPoints.orEmpty(),
      onFetchCandidates = viewModel::nearbyPois,
      onConfirm = { lat, lng, arrival, departure, name, googlePlaceId ->
        viewModel.addManualStop(lat, lng, arrival, departure, name, googlePlaceId)
        manualTarget = null
      },
      onDismiss = { manualTarget = null },
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

/**
 * 記録中に手動で立ち寄りを追加する確認ダイアログ。到着/出発は近傍の軌跡点から自動導出して表示し、
 * 名前は「近くのPOI候補から選ぶ／自分で入力／名前なし」から決める（最寄り1件の自動命名で
 * 隣の別施設に化けるのを避けるため、候補はユーザーが選ぶ）。
 */
@Composable
private fun ManualStopDialog(
  target: LatLng,
  points: List<GpsPoint>,
  onFetchCandidates: suspend (Double, Double) -> List<PlaceSearchResult>,
  onConfirm: (lat: Double, lng: Double, arrival: Date, departure: Date, name: String?, googlePlaceId: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val (arrival, departure) = remember(target, points) {
    deriveStopWindow(points, target.latitude, target.longitude)
  }
  val durationMinutes = ((departure.time - arrival.time) / 1000 / 60).toInt()

  var candidates by remember { mutableStateOf<List<PlaceSearchResult>?>(null) } // null=読込中
  var selected by remember { mutableStateOf<PlaceSearchResult?>(null) }
  var customName by remember { mutableStateOf("") }

  LaunchedEffect(target) {
    candidates = onFetchCandidates(target.latitude, target.longitude)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("立ち寄りを追加") },
    text = {
      Column {
        val window = if (durationMinutes > 0) {
          "${DateFormatters.SHORT_TIME_FORMAT.format(arrival)}–" +
            "${DateFormatters.SHORT_TIME_FORMAT.format(departure)} ・ 滞在${durationMinutes}分"
        } else {
          "この地点付近（滞在時間は軌跡から推定）"
        }
        Text(text = window, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Text("名前", style = MaterialTheme.typography.labelLarge)
        when (val list = candidates) {
          null -> Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("近くの候補を検索中…", style = MaterialTheme.typography.bodySmall)
          }

          else -> {
            if (list.isEmpty()) {
              Text(
                "近くの候補は見つかりませんでした（名前を入力するか、名前なしで追加できます）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
              )
            } else {
              Column(
                modifier = Modifier
                  .heightIn(max = 220.dp)
                  .verticalScroll(rememberScrollState()),
              ) {
                list.forEach { poi ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        selected = poi
                        customName = ""
                      }
                      .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    RadioButton(
                      selected = selected?.googlePlaceId == poi.googlePlaceId,
                      onClick = {
                        selected = poi
                        customName = ""
                      },
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                      Text(poi.name ?: "（名称不明）", style = MaterialTheme.typography.bodyMedium)
                      poi.category?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
              value = customName,
              onValueChange = {
                customName = it
                if (it.isNotEmpty()) selected = null
              },
              singleLine = true,
              label = { Text("自分で入力（任意）") },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val picked = selected
        val name = picked?.name ?: customName.trim().ifBlank { null }
        // 候補を選んだときは place を候補の Google 座標で保存（他経路と統一）。無ければタップ地点。
        val lat = picked?.latitude ?: target.latitude
        val lng = picked?.longitude ?: target.longitude
        onConfirm(lat, lng, arrival, departure, name, picked?.googlePlaceId)
      }) {
        // 名前が決まっていなければ「名前なしで追加」を明示する。
        Text(if (selected == null && customName.isBlank()) "名前なしで追加" else "追加")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

/** 手動追加の到着/出発を、指した地点の近傍の軌跡点から推定する（両端の時刻）。近傍が無ければ最寄り点。 */
private fun deriveStopWindow(points: List<GpsPoint>, lat: Double, lng: Double): Pair<Date, Date> {
  if (points.isEmpty()) return Date() to Date()
  val near = points.filter { distanceMeters(it.latitude, it.longitude, lat, lng) <= 60.0 }
  if (near.isNotEmpty()) {
    val times = near.map { it.timestamp }
    return times.min() to times.max()
  }
  val nearest = points.minByOrNull { distanceMeters(it.latitude, it.longitude, lat, lng) }!!
  return nearest.timestamp to nearest.timestamp
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val earthRadius = 6371000.0
  val dLat = Math.toRadians(lat2 - lat1)
  val dLon = Math.toRadians(lon2 - lon1)
  val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
    kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
    kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
  return earthRadius * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
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
