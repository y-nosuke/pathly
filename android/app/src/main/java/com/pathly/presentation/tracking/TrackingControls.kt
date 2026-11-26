package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.MapPlaceFilterButton
import com.pathly.presentation.common.NearbyConfirmPurpose
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.RegionMarkerChip
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
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

// 記録画面の地図の上に重ねる操作・表示（記録中のピル・記録ボタン・権限の案内・保存中の覆い・地域の目印のチップ）。

@Composable
internal fun RecordingStatusPill(
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

  // 記録中は主役の淡い地に、「動いている」の緑の点（→ Design System）。
  Surface(
    modifier = modifier,
    shape = PathlyShapes.pill,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shadowElevation = PathlyElevation.floating,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = PathlySpacing.md, vertical = PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .background(PathlyTheme.statusColors.visited, CircleShape),
      )
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = "記録中",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = elapsedText,
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

/**
 * 保存（確定）中の覆い。タップを吸って操作させず、まだ終わっていないことを見せる。
 *
 * 経路が長いほど確定（全点の再平滑化・立ち寄り検出）に時間がかかる。ここで待たせずに
 * 画面を閉じられると、保存が終わっていないのに終わったように見える（→ adr/0021）。
 */
@Composable
internal fun FinalizingOverlay() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
      // タップを全部ここで吸う（下の地図やボタンに通さない）。押した見た目は出さない。
      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    contentAlignment = Alignment.Center,
  ) {
    Card {
      Column(
        modifier = Modifier.padding(horizontal = PathlySpacing.xl, vertical = PathlySpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PathlySpacing.md),
      ) {
        CircularProgressIndicator()
        Text(text = "記録を保存しています", style = MaterialTheme.typography.titleSmall)
        Text(
          text = "経路が長いと少し時間がかかります",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * 記録のシートの開始・停止の丸（右端の同じ位置・同じ大きさ → adr/0044）。開始は青い塗りの丸 ▶「開始」で目立たせ、
 * 停止は誤って押しにくいよう塗らずに赤い枠の丸 ■「停止」にする（色と塗りで対にする）。停止は押すと確認ダイアログ（画面側）。
 * 読み上げは「記録開始」「記録停止」。
 */
@Composable
internal fun RecordStartButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primary = MaterialTheme.colorScheme.primary
  RecordCircleButton(
    label = "開始",
    description = "記録開始",
    labelColor = primary,
    onClick = onClick,
    modifier = modifier,
    circle = Modifier.background(primary, CircleShape),
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_play_arrow),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.size(28.dp),
    )
  }
}

/** 停止（[RecordStartButton] と対）。 */
@Composable
internal fun RecordStopButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val error = MaterialTheme.colorScheme.error
  RecordCircleButton(
    label = "停止",
    description = "記録停止",
    labelColor = error,
    onClick = onClick,
    modifier = modifier,
    circle = Modifier.border(2.dp, error, CircleShape),
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_stop),
      contentDescription = null,
      tint = error,
      modifier = Modifier.size(24.dp),
    )
  }
}

/** 丸と、その下の短い名前（開始・停止で共通の形）。 */
@Composable
private fun RecordCircleButton(
  label: String,
  description: String,
  labelColor: Color,
  onClick: () -> Unit,
  circle: Modifier,
  modifier: Modifier = Modifier,
  icon: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .clip(MaterialTheme.shapes.medium)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics(mergeDescendants = true) { contentDescription = description }
      .padding(horizontal = PathlySpacing.xs, vertical = PathlySpacing.xxs),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(RECORD_STOP_BUTTON_SIZE)
        .then(circle),
      contentAlignment = Alignment.Center,
    ) { icon() }
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = labelColor)
  }
}

/** 開始・停止の丸の大きさ。 */
private val RECORD_STOP_BUTTON_SIZE = 48.dp

@Composable
internal fun LocationPermissionOverlay(
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
      modifier = Modifier.padding(PathlySpacing.xl),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PathlySpacing.md),
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
