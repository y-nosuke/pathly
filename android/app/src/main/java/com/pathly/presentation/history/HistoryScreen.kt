package com.pathly.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.domain.model.GpsTrack
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
  modifier: Modifier = Modifier,
  viewModel: HistoryViewModel = hiltViewModel(),
  onTrackClick: (GpsTrack) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    Text(
      text = "外出履歴",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    when {
      uiState.isLoading -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }

      uiState.tracks.isEmpty() -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "記録がありません",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      else -> {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item {
            StatisticsSummaryCard(tracks = uiState.tracks)
            Spacer(modifier = Modifier.height(16.dp))
          }

          items(uiState.tracks) { track ->
            TrackItem(
              track = track,
              onTrackClick = { onTrackClick(track) },
              onDeleteClick = { viewModel.deleteTrack(track) },
            )
          }
        }
      }
    }

    uiState.errorMessage?.let { errorMessage ->
      LaunchedEffect(errorMessage) {
        viewModel.clearError()
      }
    }
  }
}

@Composable
private fun StatisticsSummaryCard(
  tracks: List<GpsTrack>,
  modifier: Modifier = Modifier,
) {
  val totalTracks = tracks.size
  val totalDistance = tracks.sumOf { it.totalDistanceMeters } / 1000.0
  val totalDuration = tracks.mapNotNull { track ->
    track.endTime?.let { endTime ->
      (endTime.time - track.startTime.time) / (1000 * 60) // minutes
    }
  }.sum()

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
    ),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "📊 お出掛け統計",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        StatisticItem(
          icon = "🗓️",
          label = "記録数",
          value = "${totalTracks}回",
        )
        StatisticItem(
          icon = "📏",
          label = "総距離",
          value = "${String.format("%.1f", totalDistance)}km",
        )
        StatisticItem(
          icon = "⏱️",
          label = "総時間",
          value = "${totalDuration}分",
        )
      }
    }
  }
}

@Composable
private fun StatisticItem(
  icon: String,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = icon,
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.secondary,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackItem(
  track: GpsTrack,
  onTrackClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onTrackClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = DateFormatters.SHORT_DATE_FORMAT.format(track.startTime),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = "開始: ${DateFormatters.SHORT_TIME_FORMAT.format(track.startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          track.endTime?.let { endTime ->
            Text(
              text = "終了: ${DateFormatters.SHORT_TIME_FORMAT.format(endTime)}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // デバッグ用：距離と座標点数を表示
        val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
        Text(
          text = "移動距離: ${distanceKm}km (${track.points.size}点)",
          style = MaterialTheme.typography.bodyMedium,
          color = if (track.totalDistanceMeters > 0) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          fontWeight = FontWeight.Medium,
        )
      }

      IconButton(
        onClick = onDeleteClick,
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "削除",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
