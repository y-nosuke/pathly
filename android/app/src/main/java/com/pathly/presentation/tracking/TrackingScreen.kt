package com.pathly.presentation.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.ui.theme.TrackLineOrange
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun TrackingScreen(
  modifier: Modifier = Modifier,
  onRequestPermission: () -> Unit,
  viewModel: TrackingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.checkLocationPermission()
  }

  Box(modifier = modifier.fillMaxSize()) {
    TrackingMapView(
      hasPermission = uiState.hasLocationPermission,
      track = uiState.currentTrack,
      currentLocation = uiState.currentLocation,
      modifier = Modifier.fillMaxSize(),
    )

    // 記録中の状態ピル（上部中央）
    if (uiState.isTracking) {
      RecordingStatusPill(
        startTime = uiState.currentTrack?.startTime,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 12.dp),
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

    // 下部コントロール：統計カード（記録中）＋エラー＋記録ボタン
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (uiState.isTracking) {
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
          onStopTracking = viewModel::stopTracking,
        )
      }
    }
  }
}

@Composable
private fun TrackingMapView(
  hasPermission: Boolean,
  track: GpsTrack?,
  currentLocation: LocationInfo?,
  modifier: Modifier = Modifier,
) {
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(LatLng(35.6762, 139.6503), 15f)
  }

  // 現在地に追従
  LaunchedEffect(currentLocation) {
    currentLocation?.let { loc ->
      cameraPositionState.animate(
        CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)),
      )
    }
  }

  GoogleMap(
    modifier = modifier,
    cameraPositionState = cameraPositionState,
    properties = MapProperties(
      mapType = MapType.NORMAL,
      isMyLocationEnabled = hasPermission,
    ),
    uiSettings = MapUiSettings(
      zoomControlsEnabled = false,
      myLocationButtonEnabled = false,
      mapToolbarEnabled = false,
      compassEnabled = false,
    ),
  ) {
    val points = track?.points.orEmpty()
    if (points.size >= 2) {
      Polyline(
        points = points.map { LatLng(it.latitude, it.longitude) },
        color = TrackLineOrange,
        width = 8f,
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
