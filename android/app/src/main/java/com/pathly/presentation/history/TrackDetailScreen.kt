package com.pathly.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
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
import com.pathly.domain.model.GpsTrack
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    track: GpsTrack,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("外出記録詳細") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "基本情報",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    DetailRow(
                        label = "日付",
                        value = dateFormat.format(track.startTime)
                    )

                    DetailRow(
                        label = "開始時刻",
                        value = timeFormat.format(track.startTime)
                    )

                    track.endTime?.let { endTime ->
                        DetailRow(
                            label = "終了時刻",
                            value = timeFormat.format(endTime)
                        )

                        val durationMs = endTime.time - track.startTime.time
                        val durationMinutes = (durationMs / 1000 / 60).toInt()
                        val hours = durationMinutes / 60
                        val minutes = durationMinutes % 60

                        DetailRow(
                            label = "所要時間",
                            value = if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
                        )
                    }

                    // 総移動距離を常に表示（デバッグ用に座標点数も表示）
                    val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
                    DetailRow(
                        label = "総移動距離",
                        value = "${distanceKm}km (${track.points.size}点)"
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "記録状態",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    DetailRow(
                        label = "状態",
                        value = if (track.isActive) "記録中" else "完了"
                    )

                    DetailRow(
                        label = "トラックID",
                        value = track.id.toString()
                    )
                }
            }

            // Map display for tracks with GPS points
            if (track.points.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "軌跡地図",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        TrackMapView(
                            track = track,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }

            if (track.endTime == null && track.isActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "この記録は現在進行中です",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TrackMapView(
    track: GpsTrack,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState()
    val defaultPosition = LatLng(35.6762, 139.6503) // Tokyo Station as default

    LaunchedEffect(track) {
        if (track.points.isNotEmpty()) {
            // Create bounds that include all GPS points in this track
            val boundsBuilder = LatLngBounds.Builder()
            track.points.forEach { point ->
                boundsBuilder.include(LatLng(point.latitude, point.longitude))
            }
            val bounds = boundsBuilder.build()

            // Animate camera to show the track with padding
            val padding = 50 // padding in pixels
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, padding)
            )
        } else {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(defaultPosition, 12f)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            zoomGesturesEnabled = true,
            scrollGesturesEnabled = true
        )
    ) {
        if (track.points.size >= 2) {
            // Convert GPS points to LatLng
            val polylinePoints = track.points.map {
                LatLng(it.latitude, it.longitude)
            }

            // Draw polyline for the track
            Polyline(
                points = polylinePoints,
                color = Color.Blue,
                width = 4f
            )

            // Add start marker (green)
            val startPoint = track.points.first()
            Marker(
                state = MarkerState(position = LatLng(startPoint.latitude, startPoint.longitude)),
                title = "開始",
                snippet = "記録開始地点"
            )

            // Add end marker (red) 
            val endPoint = track.points.last()
            Marker(
                state = MarkerState(position = LatLng(endPoint.latitude, endPoint.longitude)),
                title = "終了",
                snippet = "記録終了地点"
            )
        }
    }
}