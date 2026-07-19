package com.pathly.presentation.tracking

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.domain.model.GpsTrack

@Composable
fun TrackingScreen(
  modifier: Modifier = Modifier,
  onRequestPermission: () -> Unit,
  onNavigateToMap: (GpsTrack) -> Unit = {},
  viewModel: TrackingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.checkLocationPermission()
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = "Pathly - GPS記録",
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
    )

    when {
      !uiState.hasLocationPermission -> {
        LocationPermissionContent(
          onRequestPermission = onRequestPermission,
        )
      }

      uiState.isTracking -> {
        TrackingActiveContent(
          onStopTracking = viewModel::stopTracking,
          onNavigateToMap = { uiState.currentTrack?.let(onNavigateToMap) },
          currentLocation = uiState.currentLocation,
          locationCount = uiState.locationCount,
          showMapButton = uiState.currentTrack?.let { it.points.isNotEmpty() } ?: false,
        )
      }

      else -> {
        TrackingInactiveContent(
          onStartTracking = viewModel::startTracking,
        )
      }
    }

    uiState.errorMessage?.let { error ->
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
      ) {
        Text(
          text = error,
          modifier = Modifier.padding(16.dp),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}

@Composable
private fun LocationPermissionContent(
  onRequestPermission: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = "位置情報の権限が必要です",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )

    Text(
      text = "GPS記録機能を使用するため、位置情報へのアクセスを許可してください。",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )

    Button(
      onClick = {
        Log.d("TrackingScreen", "Permission button clicked")
        onRequestPermission()
      },
    ) {
      Text("位置情報を許可")
    }
  }
}

@Composable
private fun TrackingActiveContent(
  onStopTracking: () -> Unit,
  onNavigateToMap: () -> Unit = {},
  currentLocation: LocationInfo? = null,
  locationCount: Int = 0,
  showMapButton: Boolean = false,
) {
  // アニメーション状態
  val buttonScale by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(300),
    label = "buttonScale",
  )

  val cardBackgroundColor by animateColorAsState(
    targetValue = MaterialTheme.colorScheme.primaryContainer,
    animationSpec = tween(500),
    label = "cardBackgroundColor",
  )
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Card(
      colors = CardDefaults.cardColors(
        containerColor = cardBackgroundColor,
      ),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "記録中",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Text(
          text = "GPS位置を記録しています",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "📊 記録回数: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = "${locationCount}回",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
          )
        }
      }
    }

    // 現在の位置情報を表示
    currentLocation?.let { location ->
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
        ) {
          Text(
            text = "最新の位置情報",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "緯度: ${String.format("%.6f", location.latitude)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "経度: ${String.format("%.6f", location.longitude)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "精度: ${String.format("%.1f", location.accuracy)}m",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "時刻: ${location.timestamp}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    // 地図ボタン（記録中の経路がある場合のみ表示）
    if (showMapButton) {
      Button(
        onClick = onNavigateToMap,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.tertiary,
        ),
        modifier = Modifier
          .size(width = 200.dp, height = 50.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "経路を地図で確認",
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }

    Button(
      onClick = onStopTracking,
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
      ),
      modifier = Modifier
        .size(width = 200.dp, height = 60.dp)
        .scale(buttonScale),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(
          text = "⏸",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "記録停止",
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}

@Composable
private fun TrackingInactiveContent(
  onStartTracking: () -> Unit,
) {
  // アニメーション状態
  val buttonScale by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(300),
    label = "buttonScale",
  )
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
      text = "お出掛けの記録を開始しましょう",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )

    Button(
      onClick = onStartTracking,
      modifier = Modifier
        .size(width = 200.dp, height = 60.dp)
        .scale(buttonScale),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "記録開始",
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }

    Text(
      text = "ボタンを押すと、30秒間隔でGPS位置を記録します",
      style = MaterialTheme.typography.bodySmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
