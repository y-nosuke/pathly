package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.MapControls
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.MapPlaceFilterButton
import com.pathly.presentation.common.MapRoundButton
import com.pathly.presentation.common.NearbyConfirmPurpose
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.presentation.common.RouteMapContent
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.rememberMarkerIconCache
import com.pathly.presentation.common.stopSegmentPoints
import com.pathly.presentation.places.PlaceActionSheet
import com.pathly.presentation.places.PlaceDeleteUndoEffect
import com.pathly.presentation.places.PlaceMergeActions
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopSheet
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.StopReassignDialog
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlyShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

// 記録画面の地図（記録中の経路・立ち寄り・重ねた過去の経路・登録済みの場所・地域の目印）と右下の現在地ボタン。

// 「この経路の全体を見る」で寄せたとき、線の端が画面の縁やボタンに掛からないようにする余白（px）。
private const val FIT_PADDING_PX = 160

@Composable
internal fun TrackingMapView(
  cameraPositionState: CameraPositionState,
  hasPermission: Boolean,
  track: GpsTrack?,
  currentLocation: LocationInfo?,
  stops: List<Stop>,
  currentStop: Stop?,
  // 吹き出しの開閉。シートと一緒に閉じられるよう、状態は画面側が持つ。
  infoWindow: MapInfoWindowState,
  onPoiClick: (PointOfInterest) -> Unit,
  onMapClick: (LatLng) -> Unit,
  onStopClick: (Stop) -> Unit,
  modifier: Modifier = Modifier,
  registeredPlaces: List<RegisteredPlace> = emptyList(),
  onRegisteredPlaceClick: (RegisteredPlace) -> Unit = {},
  // 手動追加で選んでいる滞在区間（青くハイライト）と、指した地点（青いピン）。
  manualHighlight: List<GpsPoint> = emptyList(),
  manualPickTarget: LatLng? = null,
  // シートが出ている分だけ地図の可視領域を狭める（下に隠れる地点を作らない）。
  contentPadding: PaddingValues = PaddingValues(0.dp),
  // 地図で場所を選んだときに、その地点を可視領域へ寄せる。同じ地点を選び直しても
  // 効くよう、実行の合図は [focusNonce] の変化で受ける（経路詳細と同じ仕組み）。
  focusTarget: LatLng? = null,
  focusNonce: Int = 0,
  // 一覧から選んで重ねている過去の経路（色分け）。
  overlayTracks: List<OverlayTrack> = emptyList(),
  // 範囲の経路の一覧で「この経路の全体を見る」を押したとき、その範囲が全部入るまで寄せる（[fitNonce] の変化が合図）。
  fitBounds: LatLngBounds? = null,
  fitNonce: Int = 0,
  // 「地域へ移動」で出した地域の目印（複数）。タップ（[onRegionMarkerClick]）でその地域へ寄せ直すのは画面側。
  regionMarkers: List<RegionMarker> = emptyList(),
  onRegionMarkerClick: (RegionMarker) -> Unit = {},
  // 右下の地図の操作をシートの上端に沿わせる高さ（いま出ているシートの高さ）。
  controlsBottomInset: Dp = 0.dp,
  // 停止中に現在地を 1 回取りに行っている間 true（画面が上の下に「取得中」を出す）。
  onLocatingChange: (Boolean) -> Unit = {},
) {
  var followUser by remember { mutableStateOf(true) }
  var isLocating by remember { mutableStateOf(false) }
  LaunchedEffect(isLocating) { onLocatingChange(isLocating) }
  // 取得中に地図が外れたら（権限を外したなど）、画面の「取得中」が残らないよう戻す。
  DisposableEffect(Unit) { onDispose { onLocatingChange(false) } }

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

  // 地図で場所を選んだら、そこへ寄せる。関心が現在地から選んだ場所へ移っているので
  // 追従も止める（現在地ボタンで戻せる）。
  LaunchedEffect(focusNonce) {
    if (focusNonce == 0) return@LaunchedEffect
    focusTarget?.let {
      followUser = false
      cameraPositionState.animate(CameraUpdateFactory.newLatLng(it))
    }
  }

  LaunchedEffect(fitNonce) {
    if (fitNonce == 0) return@LaunchedEffect
    fitBounds?.let {
      // 追従したままだと次の現在地の更新で引き戻されるので止める（現在地ボタンで戻せる）。
      followUser = false
      cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(it, FIT_PADDING_PX))
    }
  }

  Box(modifier = modifier) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = contentPadding,
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
      // 施設アイコン（POI）をタップしたら場所シートを開く
      onPOIClick = onPoiClick,
      // 空きスペースのタップは手動立ち寄り追加に使う（呼び出し側で記録中のみ受ける）
      onMapClick = onMapClick,
    ) {
      // 過去の経路は記録中の経路の下に描く。
      TrackOverlayContent(overlayTracks, infoWindow)
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
          infoWindow = infoWindow,
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
          color = PathlyMapColors.ManualHighlight,
          width = 14f,
        )
      }
      // 手動追加で指した地点（青いピン）。いま自分で置いている最中のもの。
      manualPickTarget?.let { pick ->
        val pickMarkerState = remember(pick) { MarkerState(position = pick) }
        MapMarker(
          "manual-pick",
          pick.latitude,
          pick.longitude,
          infoWindow = infoWindow,
          state = pickMarkerState,
          title = "追加する地点",
        ) {
          MapPinMarker(bg = PathlyMapColors.Picked, glyph = R.drawable.ic_add)
        }
      }
      // 登録済みの場所（トグルON）。トラック未確定でも出せるよう、経路描画とは独立して描く。
      // ただし、この経路の立ち寄り（確定＝紫番号／滞在中＝ティール）と同じ場所は二重に出さない
      // （登録済みピンが立ち寄りマーカーを覆って分かりにくくなるのを防ぐ）。
      val stopPlaceIds = remember(stops, currentStop) {
        (stops.map { it.place.id } + listOfNotNull(currentStop?.place?.id)).toSet()
      }
      // まとまりをタップして寄ったら追従を止める。止めないと次の現在地の更新でカメラが引き戻される
      // （寄せたのはジェスチャーではないので、手で動かしたときの判定には掛からない）。
      RegisteredPlaceMarkers(
        registeredPlaces.filter { it.placeId !in stopPlaceIds },
        infoWindow,
        onClusterExpand = { followUser = false },
        onRegisteredPlaceClick = onRegisteredPlaceClick,
      )
      // 地域へ移動の目印: 地域の中心にピンと名前。見ている地図のどこがその地名かを示す（境界は描かない）。
      val regionIcons = rememberMarkerIconCache()
      regionMarkers.forEach { region ->
        key(region.placeId) {
          val icon = remember(regionIcons, region.name) {
            // 名前はピンの上の札にする（ピンの下に書くと、地図の地名の文字と重なって読めない）。
            regionIcons.createLabeled(null) { RegionMarkerBadge(region.name) }
          }
          val state = remember(region) { MarkerState(position = LatLng(region.latitude, region.longitude)) }
          MapMarker(
            icon = icon.icon,
            infoWindow = infoWindow,
            state = state,
            anchor = icon.anchor,
            onClick = { onRegionMarkerClick(region) },
          )
        }
      }
    }

    // 右下の地図の操作。現在地ボタン：追従中は塗り、手で動かした後は白。タップで追従再開＆リセンター。
    if (hasPermission) {
      MapControls(
        bottomInset = controlsBottomInset,
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {
        MapRoundButton(
          icon = R.drawable.ic_my_location,
          contentDescription = if (followUser) "現在地に追従中" else "現在地へ移動",
          active = followUser,
          onClick = {
            centerOnCurrentLocation(animate = true)
          },
        )
      }
    }
  }
}

/** 地域の目印: 藍色の札（地名）の下にピン。札は地図の地名の文字と見分けられるよう塗りつぶす。 */
@Composable
private fun RegionMarkerBadge(name: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = name,
      color = PathlyMapColors.OnMarker,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .widthIn(max = 200.dp)
        .background(PathlyMapColors.Region, RoundedCornerShape(8.dp))
        .border(1.dp, PathlyMapColors.OnMarker, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
    )
    Spacer(modifier = Modifier.height(2.dp))
    MapPinMarker(bg = PathlyMapColors.Region, glyph = R.drawable.ic_public)
  }
}
