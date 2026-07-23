package com.pathly.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
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
import com.pathly.ui.theme.TrackLineOrange
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

private val sheetPeekHeight = 200.dp
private val tuningSheetPeekHeight = 360.dp
private val rawTrackColor = Color(0x66424242)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
  track: GpsTrack,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  stops: List<Stop> = emptyList(),
  onEditPlaceName: (placeId: Long, name: String) -> Unit = { _, _ -> },
  onRetryNaming: () -> Unit = {},
) {
  val scaffoldState = rememberBottomSheetScaffoldState()
  var tuningMode by remember { mutableStateOf(false) }
  var tuningParams by remember { mutableStateOf(SmoothingParams()) }
  var editingStop by remember { mutableStateOf<Stop?>(null) }

  // 地図に描く点列。調整モードではスライダーの値で補正する。
  val displayPoints = remember(track, tuningMode, tuningParams) {
    if (tuningMode) TrackSmoother.smooth(track.points, tuningParams) else track.smoothedPoints
  }
  val peek = if (tuningMode) tuningSheetPeekHeight else sheetPeekHeight

  BottomSheetScaffold(
    modifier = modifier.fillMaxSize(),
    scaffoldState = scaffoldState,
    sheetPeekHeight = peek,
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
          onStopClick = { editingStop = it },
          onRetryNaming = onRetryNaming,
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      if (track.points.isNotEmpty()) {
        TrackMapView(
          track = track,
          displayPoints = displayPoints,
          stops = stops,
          showRawOverlay = tuningMode,
          contentPadding = PaddingValues(bottom = peek),
          modifier = Modifier.fillMaxSize(),
        )
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
  onStopClick: (Stop) -> Unit,
  onRetryNaming: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
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
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

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

    if (stops.isNotEmpty()) {
      Text(
        text = "立ち寄り ${stops.size}件",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
      )
      stops.forEach { stop ->
        StopRow(stop = stop, onClick = { onStopClick(stop) })
      }

      val unnamedCount = stops.count { it.place.name == null }
      if (unnamedCount > 0) {
        Surface(
          onClick = onRetryNaming,
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Text(
            text = "未命名 ${unnamedCount}件の名前を取得",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun StopRow(
  stop: Stop,
  onClick: () -> Unit,
) {
  val title = stop.place.name
    ?: "%.5f, %.5f".format(stop.place.latitude, stop.place.longitude)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
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
  contentPadding: PaddingValues = PaddingValues(0.dp),
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
