package com.pathly.presentation.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.WishlistFlag
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.DefaultMapLocation
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.MapLayerActionItem
import com.pathly.presentation.common.MapLayerDivider
import com.pathly.presentation.common.MapLayersButton
import com.pathly.presentation.common.MapNoticeBanner
import com.pathly.presentation.common.MapSideActions
import com.pathly.presentation.common.MapTopBar
import com.pathly.presentation.common.MapTopBarInset
import com.pathly.presentation.common.RegisteredPlacesLayerItems
import com.pathly.presentation.common.SheetContentHorizontalPadding
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.SheetRestoreButtonInset
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFixedFloatingSheetState
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.toGeoBounds
import com.pathly.presentation.common.toLatLngBounds
import com.pathly.presentation.history.PlaceNameDialog
import com.pathly.presentation.history.RenameTrackDialog
import com.pathly.presentation.history.SameFacilityReview
import com.pathly.presentation.history.SameFacilityReviewDialog
import com.pathly.presentation.history.SheetTodoBanner
import com.pathly.presentation.history.StopNoteDialog
import com.pathly.presentation.places.PLACE_SHEET_PEEK_FRACTION
import com.pathly.presentation.places.PlaceEditHost
import com.pathly.presentation.places.PlaceEditUi
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.AddStopActions
import com.pathly.presentation.stops.LiveStopActions
import com.pathly.presentation.stops.ManualStopHost
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.StopDurationSheet
import com.pathly.presentation.stops.StopEditActions
import com.pathly.presentation.stops.StopReassignHost
import com.pathly.presentation.stops.isLive
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme
import com.pathly.util.DateFormatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 下の記録のシート。記録中は、経路の名前（押すと名前の編集）・距離・地点数・立ち寄り中と停止、出ているときだけの「やること」の帯
 * （場所を取得・同じ施設の確認待ち）、脇役の操作（今ここを立ち寄り・地図で指して追加）、このお出掛けの立ち寄りの一覧
 * （新しい順。行は経路詳細と同じ操作 → [RecordingStopList]）。ハーフでは新しい立ち寄りが 2 件ほど見え、上へ引くと広がって
 * 全部見える。停止中は「記録していません」と右端の開始の丸だけの小さなシート。主役の開始・停止はシートの中に置き、畳むと戻すボタン
 * （[restoreLabel]）だけが残る（→ adr/0044）。
 */
@Composable
internal fun TrackingRecordSheet(
  uiState: TrackingState,
  sheetState: FloatingSheetState,
  manualMode: Boolean,
  onToggleManualMode: () -> Unit,
  onAddStopHere: (LocationInfo) -> Unit,
  onStartTracking: () -> Unit,
  onStopTracking: () -> Unit,
  modifier: Modifier = Modifier,
  todos: RecordSheetTodos = RecordSheetTodos(),
  onRenameTrack: () -> Unit = {},
  rowActions: RecordingStopRowActions = RecordingStopRowActions(),
  wishlistFlags: Map<Long, WishlistFlag> = emptyMap(),
) {
  FloatingSheet(
    state = sheetState,
    modifier = modifier.testTag("trackingRecordSheet"),
    restoreLabel = if (uiState.isTracking) "▲ 記録中・${formatDistanceKm(uiState.currentTrack?.totalDistanceMeters)}" else "▲ 記録していません",
  ) {
    if (uiState.isTracking) {
      RecordTrackName(
        name = uiState.currentTrack?.name,
        onClick = onRenameTrack,
        modifier = Modifier.padding(horizontal = SheetContentHorizontalPadding),
      )
      TrackingRecordInfo(
        distanceMeters = uiState.currentTrack?.totalDistanceMeters,
        pointCount = uiState.currentTrack?.points?.size ?: uiState.locationCount,
        currentStopName = uiState.currentStop?.place?.displayName,
        onStopTracking = onStopTracking,
        modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge),
      )
      // 出ているときだけの「やること」（経路詳細のシートと同じ帯）。
      if (todos.unresolvedCount > 0) {
        Spacer(modifier = Modifier.height(PathlySpacing.sm))
        SheetTodoBanner(text = "場所を取得（未取得 ${todos.unresolvedCount}件）", onClick = todos.onResolveNames)
      }
      if (todos.sameFacilityPendingCount > 0) {
        Spacer(modifier = Modifier.height(PathlySpacing.sm))
        SheetTodoBanner(text = "同じ施設の確認待ち ${todos.sameFacilityPendingCount}件", onClick = todos.onOpenSameFacility)
      }
      Spacer(modifier = Modifier.height(PathlySpacing.md))
      // 「今ここ」は近くの施設候補から選ばせる。「地図で指して追加」はモード（ON=地図タップが立ち寄り追加／OFF=場所登録）。
      AddStopActions(
        onAddHere = { uiState.currentLocation?.let(onAddStopHere) },
        // 立ち寄り中は押せない（今いる立ち寄りを変えるのは、立ち寄り中の行の ⋮ から）。
        addHereEnabled = uiState.hasLocationPermission && uiState.currentLocation != null && uiState.currentStop == null,
        onMapPick = onToggleManualMode,
        mapPickActive = manualMode,
      )
      Spacer(modifier = Modifier.height(PathlySpacing.md))
      RecordingStopList(
        stops = uiState.stops,
        currentStop = uiState.currentStop,
        rowActions = rowActions,
        wishlistFlags = wishlistFlags,
        modifier = Modifier.weight(1f),
      )
    } else {
      // 記録中の停止と同じ位置（右端）に開始の丸を置く。
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = PathlySpacing.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "記録していません",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        RecordStartButton(onClick = onStartTracking)
      }
    }
  }
}

/** 記録のシートの「やること」の帯（出ているときだけ。経路詳細のシートと同じ）。 */
internal data class RecordSheetTodos(
  /** 未取得（Google の施設が付いていない）場所の数。0 なら帯を出さない。 */
  val unresolvedCount: Int = 0,
  val onResolveNames: () -> Unit = {},
  /** 同じ施設の確認待ちの数（→ adr/0037）。0 なら帯を出さない。 */
  val sameFacilityPendingCount: Int = 0,
  val onOpenSameFacility: () -> Unit = {},
)

/** 記録中の経路の名前（未命名なら「名前を付ける」）。押すと経路詳細と同じ名前のダイアログ。 */
@Composable
private fun RecordTrackName(
  name: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .clip(MaterialTheme.shapes.small)
      .clickable(onClickLabel = "経路の名前を編集", onClick = onClick)
      .padding(horizontal = PathlySpacing.xs, vertical = PathlySpacing.xxs),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.xs),
  ) {
    Text(
      text = name ?: "名前を付ける",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = if (name != null) FontWeight.Bold else null,
      color = if (name != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Icon(
      painter = painterResource(R.drawable.ic_edit),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
  }
}

/**
 * 記録中のシートの情報の段: 移動距離・地点数・立ち寄り中（いるときだけ）と、右端の停止。数字は太さで目立たせる。
 * 立ち寄り中の名前が長くても省いて縮め、停止を押し出さない。
 */
@Composable
private fun TrackingRecordInfo(
  distanceMeters: Double?,
  pointCount: Int,
  currentStopName: String?,
  onStopTracking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RecordInfoColumn(value = formatDistanceKm(distanceMeters), label = "移動距離")
    RecordInfoColumn(value = "$pointCount", label = "地点数")
    if (currentStopName != null) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "● 立ち寄り中",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = PathlyTheme.statusColors.visited,
        )
        Text(
          text = currentStopName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }
    RecordStopButton(onClick = onStopTracking)
  }
}

@Composable
private fun RecordInfoColumn(
  value: String,
  label: String,
) {
  Column {
    Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** 距離を「1.82km」の形にする（小数 2 桁）。 */
internal fun formatDistanceKm(meters: Double?): String = String.format(Locale.ROOT, "%.2fkm", (meters ?: 0.0) / 1000.0)

/** 記録中でまだ立ち寄りが無いときの記録のシートの高さ（つまみ・経路の名前・情報の段と停止・脇役の操作の段・「まだ立ち寄りはありません」）。 */
internal val RECORDING_SHEET_HEIGHT = 280.dp

/** 記録中で立ち寄りがあるときの、記録のシートのハーフの高さ（上の段に加えて、新しい立ち寄りが 2 件ほど見える）。 */
internal val RECORDING_SHEET_WITH_STOPS_HEIGHT = 376.dp

/** 記録中で立ち寄りがあるときの、記録のシートのフルの高さ（画面に対する割合）。上の区画（検索・ピル・案内の帯）は隠さない高さにとどめる。 */
internal const val RECORDING_SHEET_FULL_FRACTION = 0.6f

/** 記録のシートの「やること」の帯 1 本ぶんの高さ（帯と上の余白）。出ている数だけハーフを高くする。 */
internal val RECORD_SHEET_TODO_HEIGHT = 52.dp

/** 停止中の記録のシートの高さ（つまみと「記録していません」・開始の丸）。 */
internal val STOPPED_SHEET_HEIGHT = 128.dp
