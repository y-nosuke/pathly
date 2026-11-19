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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.MapPlaceFilterButton
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
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .background(PathlyTheme.statusColors.visited, CircleShape),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "記録中",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.width(8.dp))
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
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
internal fun RecordFab(
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

/**
 * 地域の目印のチップ（左上に縦に並べる）。上から「すべて外す」（2 つ以上のとき）・選んだ順の地名のチップ
 * [VISIBLE_REGION_CHIPS] 個まで・6 つ目からをまとめた「他 N 件」。地図の上でスクロールさせると地図のドラッグと紛れるので、
 * 縦に出す数を決めて残りはまとめのメニューから選ぶ。最後に地図を移した地域（[focusedPlaceId]）のチップは塗りつぶし、
 * それがまとめの中なら、まとめのチップを塗りつぶす。
 */
@Composable
internal fun RegionMarkerChips(
  markers: List<RegionMarker>,
  focusedPlaceId: String?,
  onRefit: (RegionMarker) -> Unit,
  onClear: (RegionMarker) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (markers.isEmpty()) return
  val shown = markers.take(VISIBLE_REGION_CHIPS)
  val folded = markers.drop(VISIBLE_REGION_CHIPS)
  Column(
    modifier = modifier.testTag("regionMarkerChips"),
    verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
  ) {
    if (markers.size >= 2) {
      RegionChipSurface(focused = false, onClick = onClearAll) {
        Text(
          text = "すべて外す",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
      }
    }
    shown.forEach { marker ->
      key(marker.placeId) {
        RegionMarkerChip(
          name = marker.name,
          focused = marker.placeId == focusedPlaceId,
          onRefit = { onRefit(marker) },
          onClear = { onClear(marker) },
        )
      }
    }
    if (folded.isNotEmpty()) {
      FoldedRegionChip(
        markers = folded,
        focusedPlaceId = focusedPlaceId,
        onRefit = onRefit,
        onClear = onClear,
      )
    }
  }
}

/** 縦に出す地域のチップの数。これより後に選んだ地域は「他 N 件」にまとめる。 */
internal const val VISIBLE_REGION_CHIPS = 5

/** 6 つ目からの地域をまとめたチップ。押すとメニューで地名を出し、行でその地域へ寄せ直し、× で外す。 */
@Composable
private fun FoldedRegionChip(
  markers: List<RegionMarker>,
  focusedPlaceId: String?,
  onRefit: (RegionMarker) -> Unit,
  onClear: (RegionMarker) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Box {
    RegionChipSurface(
      focused = markers.any { it.placeId == focusedPlaceId },
      onClick = { menuOpen = true },
      modifier = Modifier.testTag("regionMarkerFoldedChip"),
    ) {
      Row(
        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "他 ${markers.size} 件",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
        )
        Icon(
          painter = painterResource(R.drawable.ic_expand_more),
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      markers.forEach { marker ->
        key(marker.placeId) {
          DropdownMenuItem(
            text = {
              Text(
                text = marker.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (marker.placeId == focusedPlaceId) FontWeight.Bold else null,
              )
            },
            leadingIcon = {
              Icon(
                painter = painterResource(R.drawable.ic_public),
                contentDescription = null,
                tint = PathlyMapColors.Region,
                modifier = Modifier.size(18.dp),
              )
            },
            trailingIcon = {
              // 外してもメニューは開いたまま（続けて外せるように）。最後の 1 つを外すと、まとめごと消える。
              IconButton(onClick = { onClear(marker) }) {
                Icon(
                  painter = painterResource(R.drawable.ic_close),
                  contentDescription = "${marker.name}の目印を外す",
                  modifier = Modifier.size(18.dp),
                )
              }
            },
            onClick = {
              menuOpen = false
              onRefit(marker)
            },
          )
        }
      }
    }
  }
}

/** 地域のチップの外枠（地図に浮かぶピル）。[focused] なら塗りつぶす。 */
@Composable
private fun RegionChipSurface(
  focused: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.semantics { selected = focused },
    shape = PathlyShapes.pill,
    color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    shadowElevation = PathlyElevation.floating,
    content = content,
  )
}

/** 地域の目印の地名のチップ。地名の部分で [onRefit]（範囲へ寄せ直す）、× で [onClear]（その目印を外す）。 */
@Composable
internal fun RegionMarkerChip(
  name: String,
  focused: Boolean,
  onRefit: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .testTag("regionMarkerChip")
      .semantics { selected = focused },
    shape = PathlyShapes.pill,
    color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    shadowElevation = PathlyElevation.floating,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier = Modifier
          .clickable(onClickLabel = "この地域へ地図を寄せる", onClick = onRefit)
          .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_public),
          contentDescription = null,
          tint = PathlyMapColors.Region,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        // 横にスクロールする行の中なので、長い地名は幅を決めて省略する。
        Text(
          text = name,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 160.dp),
        )
      }
      // × は小さいので、押せる範囲は 48dp 四方を確保する（隣の地名＝寄せ直しを誤って押さないように）。
      Box(
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clickable(onClick = onClear),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_close),
          contentDescription = "${name}の目印を外す",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
