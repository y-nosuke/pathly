package com.pathly.presentation.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pathly.R
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.WishlistFlag
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.MapLayerActionItem
import com.pathly.presentation.common.MapLayerDivider
import com.pathly.presentation.common.MapLayersButton
import com.pathly.presentation.common.MapNoticeBanner
import com.pathly.presentation.common.MapSideActions
import com.pathly.presentation.common.MapTopBar
import com.pathly.presentation.common.RegisteredPlacesLayerItems
import com.pathly.presentation.common.SheetContentHorizontalPadding
import com.pathly.presentation.history.SheetTodoBanner
import com.pathly.presentation.stops.AddStopActions
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme
import kotlinx.coroutines.delay
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
