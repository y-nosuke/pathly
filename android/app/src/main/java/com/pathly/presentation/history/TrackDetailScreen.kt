package com.pathly.presentation.history

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
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.ui.theme.TrackLineOrange
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

private val sheetPeekHeight = 200.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
  track: GpsTrack,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scaffoldState = rememberBottomSheetScaffoldState()

  BottomSheetScaffold(
    modifier = modifier.fillMaxSize(),
    scaffoldState = scaffoldState,
    sheetPeekHeight = sheetPeekHeight,
    sheetContent = {
      TrackDetailSheet(track = track)
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
          contentPadding = PaddingValues(bottom = sheetPeekHeight),
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

      Surface(
        onClick = onBackClick,
        modifier = Modifier.padding(12.dp),
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
    }
  }
}

@Composable
private fun TrackDetailSheet(
  track: GpsTrack,
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
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  val cameraPositionState = rememberCameraPositionState()
  val defaultPosition = LatLng(35.6762, 139.6503) // Tokyo Station as default

  LaunchedEffect(track) {
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
    val points = track.smoothedPoints
    if (points.size >= 2) {
      val polylinePoints = points.map {
        LatLng(it.latitude, it.longitude)
      }
      Polyline(
        points = polylinePoints,
        color = TrackLineOrange,
        width = 6f,
      )

      val startPoint = points.first()
      val startMarkerState = remember(startPoint) {
        MarkerState(position = LatLng(startPoint.latitude, startPoint.longitude))
      }
      Marker(
        state = startMarkerState,
        title = "開始",
        snippet = "記録開始地点 - ${DateFormatters.TIME_FORMAT.format(track.startTime)}",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
      )

      val endPoint = points.last()
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
  }
}
