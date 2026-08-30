package com.pathly.presentation.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PointOfInterest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.TrackSegments
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.MarkerCandidateOrange
import com.pathly.presentation.common.MarkerPickBlue
import com.pathly.presentation.common.RouteMapContent
import com.pathly.util.DateFormatters

// 経路詳細の地図。軌跡・立ち寄り・候補・手動追加のハイライトを重ねて描く。

/**
 * 補正の調整モードで、比較用に重ねる生データの色。
 *
 * **灰色は使えない**（欠落を結ぶ破線が灰色で、意味が違う線が同じ色になるため → adr/0022）。
 * 半透明の紫にして、補正後（橙）とも欠落（灰）とも分ける。
 */
private val rawTrackColor = Color(0x665E35B1)

/** 手動追加のハイライト（選択した滞在区間）。軌跡（オレンジ）・立ち寄り（紫）と見分ける青。 */
private val manualHighlightColor = Color(0xFF1E88E5)

@Composable
internal fun TrackMapView(
  track: GpsTrack,
  displayPoints: List<GpsPoint>,
  // 吹き出しの開閉。シートと一緒に閉じられるよう、状態は画面側が持つ。
  infoWindow: MapInfoWindowState,
  modifier: Modifier = Modifier,
  stops: List<Stop> = emptyList(),
  currentStop: Stop? = null,
  currentStopSegment: List<GpsPoint> = emptyList(),
  candidates: List<StopCandidate> = emptyList(),
  showRawOverlay: Boolean = false,
  manualPickTarget: LatLng? = null,
  highlightPoints: List<GpsPoint> = emptyList(),
  stopSegments: List<List<GpsPoint>> = emptyList(),
  registeredPlaces: List<RegisteredPlace> = emptyList(),
  focusTarget: LatLng? = null,
  focusNonce: Int = 0,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  onPoiClick: (PointOfInterest) -> Unit = {},
  onMapClick: (LatLng) -> Unit = {},
  onStopClick: (Stop) -> Unit = {},
  onRegisteredPlaceClick: (RegisteredPlace) -> Unit = {},
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
    // 調整モードでは生データを紫で重ねて見比べる
    if (showRawOverlay && track.points.size >= 2) {
      // 生データも補正後と同じく、途切れた区間はまたがずに引く（見比べる前提が揃う）。
      val rawSegments = remember(track.points) { TrackSegments.split(track.points) }
      rawSegments.forEach { segment ->
        if (segment.size >= 2) {
          Polyline(
            points = segment.map { LatLng(it.latitude, it.longitude) },
            color = rawTrackColor,
            width = 10f,
          )
        }
      }
    }

    // 軌跡・帯・開始/終了/立ち寄りマーカーは記録画面と共通の描画にまとめる（見た目統一）。
    RouteMapContent(
      track = track,
      displayPoints = displayPoints,
      infoWindow = infoWindow,
      stops = stops,
      stopSegments = stopSegments,
      currentStop = currentStop,
      currentStopSegment = currentStopSegment,
      registeredPlaces = registeredPlaces,
      onStopClick = onStopClick,
      onRegisteredPlaceClick = onRegisteredPlaceClick,
    )

    // 再解析の候補（橙のピン）。まだ追加していない＝これから決めるものなので、確定した立ち寄り
    // （紫の丸）とは形で見分ける。
    // 候補も1件ずつ独立させる（立ち寄りマーカーと同じ理由。位置で使い回すと MarkerState が
    // 差し替わってピンが消える）。並びの位置ではなく座標で識別する。
    candidates.forEach { candidate ->
      val d = candidate.detected
      key(d.latitude, d.longitude, d.arrivalTime) {
        val candidateMarkerState = remember(d.latitude, d.longitude) {
          MarkerState(position = LatLng(d.latitude, d.longitude))
        }
        MapMarker(
          "candidate",
          d.latitude,
          d.longitude,
          infoWindow = infoWindow,
          state = candidateMarkerState,
          title = candidate.name ?: "候補（名称未取得）",
          snippet = "${DateFormatters.shortTime(d.arrivalTime)} ・ 滞在${d.durationMinutes}分",
        ) {
          MapPinMarker(bg = MarkerCandidateOrange, glyph = R.drawable.ic_help)
        }
      }
    }

    // 手動追加: 選んだ滞在区間を青い太線でハイライトし、経路のどこかを見せる。
    if (highlightPoints.size >= 2) {
      Polyline(
        points = highlightPoints.map { LatLng(it.latitude, it.longitude) },
        color = manualHighlightColor,
        width = 14f,
      )
    }

    // 手動追加で指した地点（青いピン）。いま自分で置いている最中のもの。
    manualPickTarget?.let { target ->
      val pickMarkerState = remember(target) { MarkerState(position = target) }
      MapMarker(
        "manual-pick",
        target.latitude,
        target.longitude,
        infoWindow = infoWindow,
        state = pickMarkerState,
        title = "追加する地点",
      ) {
        MapPinMarker(bg = MarkerPickBlue, glyph = R.drawable.ic_add)
      }
    }
  }
}
