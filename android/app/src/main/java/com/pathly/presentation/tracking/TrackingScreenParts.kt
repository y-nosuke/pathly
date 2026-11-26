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

// 記録画面（[TrackingScreen]）のまとまりごとの部品（ダイアログ・上のバー・下の記録のシート）。
// 置き方は地図の画面の型（docs/designs/ui-direction.md）。

/** 中断された記録の再開/完了を確認するダイアログ。 */
@Composable
internal fun InterruptedTrackDialog(
  isLocationDisabled: Boolean,
  onResume: () -> Unit,
  onFinish: () -> Unit,
  onOpenLocationSettings: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onFinish,
    title = { Text("前回の記録が中断されています") },
    text = {
      Column {
        Text("アプリの更新などで記録が中断されました。続けて記録を再開しますか？")
        // 再開が位置情報 OFF で断られたとき。画面側の案内はダイアログの裏に隠れて押せず、
        // 外側のタップは「完了にする」になってしまうので、ダイアログの中に導線を出す。
        if (isLocationDisabled) {
          Text(
            text = "端末の位置情報がオフのため再開できません。",
            modifier = Modifier.padding(top = PathlySpacing.md),
            color = MaterialTheme.colorScheme.error,
          )
          TextButton(onClick = onOpenLocationSettings) {
            Text("位置情報の設定を開く")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onResume) {
        Text("再開する")
      }
    },
    dismissButton = {
      TextButton(onClick = onFinish) {
        Text("完了にする")
      }
    },
  )
}

/** 停止の確認ダイアログ（記録中の停止の誤爆防止）。 */
@Composable
internal fun StopTrackingConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("記録を停止しますか？") },
    text = { Text("記録を停止して履歴に保存します。") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("停止する")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("続ける") }
    },
  )
}

/**
 * 記録画面の上の区画。左は検索の入口、右端はレイヤー［≋］（登録済みの場所の表示・絞り込み、この範囲の経路）。
 * その下（上の下）に、今の状態（[status]）を積む。
 *
 * @param overlayEnabled 立ち寄りの追加中は、そちらのシートが優先なので「この範囲の経路」を押せなくする。
 * @param registerEnabled 立ち寄りの追加中は登録できないので、検索の入口を「地域を検索」にする（窓も地域だけを出す）。
 */
@Composable
internal fun TrackingTopBar(
  showRegisteredPlaces: Boolean,
  registeredPlaces: List<RegisteredPlace>,
  mapPlaceFilter: MapPlaceFilter,
  onToggleRegisteredPlaces: () -> Unit,
  onMapPlaceFilterChange: (MapPlaceFilter) -> Unit,
  overlayActive: Boolean,
  overlayEnabled: Boolean,
  registerEnabled: Boolean,
  onToggleOverlay: () -> Unit,
  onSearch: () -> Unit,
  modifier: Modifier = Modifier,
  status: @Composable () -> Unit = {},
) {
  MapTopBar(
    modifier = modifier,
    leading = {
      TrackingSearchEntry(label = mapSearchLabel(registerEnabled), onSearch = onSearch, modifier = Modifier.weight(1f))
    },
    actions = {
      MapLayersButton(
        active = showRegisteredPlaces || overlayActive,
        filtered = showRegisteredPlaces && !mapPlaceFilter.isEmpty,
      ) { close ->
        RegisteredPlacesLayerItems(
          show = showRegisteredPlaces,
          onToggle = onToggleRegisteredPlaces,
          filter = mapPlaceFilter,
          places = registeredPlaces,
          onFilterChange = onMapPlaceFilterChange,
        )
        MapLayerDivider()
        // この範囲の経路: 押した時点で地図に映っている範囲で探す（地図を動かすたびには探さない）。もう一度押すと閉じる。
        MapLayerActionItem(
          icon = R.drawable.ic_route,
          title = "この範囲の経路",
          supporting = if (overlayActive) "表示中（押すと閉じる）" else "いま見えている範囲で前に通った道",
          selected = overlayActive,
          enabled = overlayEnabled,
          onClick = {
            close()
            onToggleOverlay()
          },
        )
      }
    },
    status = { status() },
  )
}

/** 上のバーの検索の入口（施設の登録と地域への移動を 1 つの窓で → ADR 0039）。押すと地図の上端に検索窓を重ねる。 */
@Composable
private fun TrackingSearchEntry(
  label: String,
  onSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.height(48.dp),
    shape = PathlyShapes.pill,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = PathlyElevation.floating,
  ) {
    Row(
      modifier = Modifier
        .fillMaxHeight()
        .clickable(role = Role.Button, onClick = onSearch)
        .semantics(mergeDescendants = true) { contentDescription = label }
        .padding(horizontal = PathlySpacing.lg),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_search),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** 記録画面の検索の入口と窓の文言。立ち寄りの追加中（[registerEnabled] が false）は地域だけを探すので「地域を検索」。 */
internal fun mapSearchLabel(registerEnabled: Boolean): String = if (registerEnabled) "場所・地域を検索" else "地域を検索"

/** 場所を登録した直後の一時メッセージ（上の下）。2 秒で [onShown] を呼んで消す。 */
@Composable
internal fun PlaceRegisteredToast(
  message: String,
  onShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(message) {
    delay(2000)
    onShown()
  }
  Surface(
    modifier = modifier,
    // 文が折り返して複数行になり得るので丸（pill）にはしない（端の文字が欠ける）。
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.secondaryContainer,
    shadowElevation = PathlyElevation.floating,
  ) {
    Text(
      text = message,
      modifier = Modifier.padding(horizontal = PathlySpacing.lg, vertical = PathlySpacing.sm),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

/** 現在地を取得中の表示（上の下）。 */
@Composable
internal fun LocatingPill(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = PathlyShapes.pill,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = PathlyElevation.floating,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = PathlySpacing.lg, vertical = PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(text = "現在地を取得中…", style = MaterialTheme.typography.labelMedium)
    }
  }
}

/**
 * 上の下の 1 行の帯: 電池の最適化（押すと設定へ）・位置情報 OFF（押すと端末の設定へ）・記録のエラー。
 */
@Composable
internal fun TrackingNotices(
  uiState: TrackingState,
  onOpenBatterySettings: () -> Unit,
  onOpenLocationSettings: () -> Unit,
) {
  // 電池の最適化が有効なままだとバックグラウンド記録が止まりやすい。詳しい説明と切り替えは設定にある。
  if (uiState.hasLocationPermission && !uiState.isIgnoringBatteryOptimizations) {
    MapNoticeBanner(
      text = "電池の最適化で記録が止まることがあります",
      icon = R.drawable.ic_battery_alert,
      onClick = onOpenBatterySettings,
    )
  }
  if (uiState.isLocationDisabled && uiState.interruptedTrack == null) {
    MapNoticeBanner(
      text = "端末の位置情報がオフです",
      icon = R.drawable.ic_my_location,
      onClick = onOpenLocationSettings,
      isError = true,
    )
  }
  uiState.errorMessage?.let { error ->
    // 押せない知らせなので、全文が読めるよう行を増やす。
    MapNoticeBanner(text = error, icon = R.drawable.ic_help, onClick = null, isError = true, maxLines = 4)
  }
}

/** 記録のシートの立ち寄りの行・経路の名前から開くダイアログ（経路詳細と同じもの）。 */
@Composable
internal fun TrackingStopDialogs(
  track: GpsTrack?,
  stops: List<Stop>,
  editingStop: Stop?,
  editingNoteStop: Stop?,
  renamingTrack: Boolean,
  stopEdit: StopEditActions,
  sameFacility: SameFacilityReview,
  placeEdit: PlaceEditUi,
  onRenameTrack: (String) -> Unit,
  onPickAnotherFacility: (placeId: Long) -> Unit,
  onDismissEditingStop: () -> Unit,
  onDismissEditingNote: () -> Unit,
  onDismissRename: () -> Unit,
) {
  SameFacilityReviewDialog(sameFacility = sameFacility, stops = stops, placeEdit = placeEdit, onPickAnother = onPickAnotherFacility)
  if (renamingTrack && track != null) {
    RenameTrackDialog(
      initialName = track.name.orEmpty(),
      defaultName = DateFormatters.date(track.startTime),
      onDismiss = onDismissRename,
      onConfirm = { name ->
        onRenameTrack(name)
        onDismissRename()
      },
    )
  }
  editingStop?.let { stop ->
    PlaceNameDialog(
      stop = stop,
      onDismiss = onDismissEditingStop,
      onConfirm = { name ->
        stopEdit.updatePlaceName(stop.place.id, name)
        onDismissEditingStop()
      },
    )
  }
  editingNoteStop?.let { stop ->
    StopNoteDialog(
      stop = stop,
      onDismiss = onDismissEditingNote,
      onConfirm = { note ->
        stopEdit.updateNote(stop, note)
        onDismissEditingNote()
      },
    )
  }
}
