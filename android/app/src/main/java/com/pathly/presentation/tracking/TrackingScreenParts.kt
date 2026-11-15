package com.pathly.presentation.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.RegisteredPlace
import com.pathly.presentation.common.MapPlaceFilterButton
import kotlinx.coroutines.delay

// 記録画面（[TrackingScreen]）のまとまりごとの部品（ダイアログ・上部のボタン列・左上の操作・下部のコントロール）。

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
            modifier = Modifier.padding(top = 12.dp),
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
 * 上部右のボタン列: 登録済みの場所のトグル（ON のときは下に絞り込み）・この範囲の経路・検索して登録・地域へ移動。
 *
 * @param actionsEnabled 立ち寄りの追加中は、そちらのシートが優先なので経路・検索して登録を押せなくする。
 */
@Composable
internal fun TrackingMapButtons(
  showRegisteredPlaces: Boolean,
  registeredPlaces: List<RegisteredPlace>,
  mapPlaceFilter: MapPlaceFilter,
  onToggleRegisteredPlaces: () -> Unit,
  onMapPlaceFilterChange: (MapPlaceFilter) -> Unit,
  overlayActive: Boolean,
  actionsEnabled: Boolean,
  onToggleOverlay: () -> Unit,
  onSearchPlace: () -> Unit,
  onSearchRegion: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .statusBarsPadding()
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // 登録済みの場所を地図に出すトグル（記録画面・画面別）。
    Surface(
      onClick = onToggleRegisteredPlaces,
      shape = CircleShape,
      color = if (showRegisteredPlaces) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_place),
        contentDescription = if (showRegisteredPlaces) "登録済みの場所を隠す" else "登録済みの場所を表示",
        tint = if (showRegisteredPlaces) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
      )
    }
    if (showRegisteredPlaces) {
      MapPlaceFilterButton(
        filter = mapPlaceFilter,
        places = registeredPlaces,
        onFilterChange = onMapPlaceFilterChange,
      )
    }
    // この範囲の経路: 押した時点で地図に映っている範囲で探す（地図を動かすたびには探さない）。
    TrackOverlayButton(
      // 押した時点で塗りつぶす（経路を選ぶ前から、一覧を出していることが分かるように）。
      active = overlayActive,
      enabled = actionsEnabled,
      onClick = onToggleOverlay,
    )
    // 検索して登録: 場所タブへ移らずに、キーワードで探した場所をこの地図の上で登録する。
    Surface(
      onClick = onSearchPlace,
      enabled = actionsEnabled,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_search),
        contentDescription = "検索して場所を登録",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
      )
    }
    // 地域へ移動: 地名を探して、その地域が収まるように地図を移す（登録はしない）。
    Surface(
      onClick = onSearchRegion,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_public),
        contentDescription = "地域へ移動",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
      )
    }
  }
}

/**
 * 左上の操作（縦積み）: 記録中は「立ち寄りを追加」モード切替 と 「今ここ」ショートカット、その下に地域の目印のチップ。
 * 右端は右上のボタン列（幅 64dp）にかからないよう空ける。
 */
@Composable
internal fun TrackingTopStartControls(
  isTracking: Boolean,
  manualMode: Boolean,
  onToggleManualMode: () -> Unit,
  hasLocationPermission: Boolean,
  currentLocation: LocationInfo?,
  onAddStopHere: (LocationInfo) -> Unit,
  regionMarker: RegionMarker?,
  onRefitRegion: (RegionMarker) -> Unit,
  onClearRegion: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .statusBarsPadding()
      .padding(start = 12.dp, top = 12.dp, end = 76.dp, bottom = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (isTracking) {
      // 「立ち寄りを追加」モード（ON=地図タップが立ち寄り追加／OFF=場所登録）。
      Surface(
        onClick = onToggleManualMode,
        shape = RoundedCornerShape(20.dp),
        color = if (manualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_place),
            contentDescription = null,
            tint = if (manualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (manualMode) "立ち寄り追加中（地図をタップ）" else "立ち寄りを追加",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (manualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      // 「今ここ」を立ち寄りに追加（現在地あり）。
      if (hasLocationPermission) {
        Surface(
          // 地点を指していないので、近くの施設候補から選ばせる。
          onClick = { currentLocation?.let(onAddStopHere) },
          enabled = currentLocation != null,
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 4.dp,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_place),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "今ここを立ち寄り",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }

    // 「地域へ移動」で出した目印の地名のチップ。× で目印ごと消し（地図は動かさない）、地名で地域の範囲へ寄せ直す。
    regionMarker?.let { region ->
      RegionMarkerChip(
        name = region.name,
        onRefit = { onRefitRegion(region) },
        onClear = onClearRegion,
        // 停止中は上部中央のピル（登録の確認・現在地の取得中）の段を避けて、その下に置く。
        modifier = if (isTracking) Modifier else Modifier.padding(top = 44.dp),
      )
    }
  }
}

/** 場所を登録した直後の一時メッセージ（上部中央）。2 秒で [onShown] を呼んで消す。 */
@Composable
internal fun PlaceRegisteredToast(
  message: String,
  isTracking: Boolean,
  onShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(message) {
    delay(2000)
    onShown()
  }
  Surface(
    modifier = modifier.padding(top = if (isTracking) 56.dp else 12.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    shadowElevation = 4.dp,
  ) {
    Text(
      text = message,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

/** 下部コントロール：電池最適化の案内＋統計カード（記録中）＋エラー＋位置情報 OFF の案内＋記録ボタン。 */
@Composable
internal fun TrackingBottomControls(
  uiState: TrackingState,
  onDisableBatteryOptimization: () -> Unit,
  onOpenLocationSettings: () -> Unit,
  onStartTracking: () -> Unit,
  onStopTracking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .padding(bottom = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 電池の最適化が有効なままだとバックグラウンド記録が止まりやすいので案内する
    if (uiState.hasLocationPermission && !uiState.isIgnoringBatteryOptimizations) {
      BatteryOptimizationCard(
        onDisable = onDisableBatteryOptimization,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (uiState.isTracking) {
      // 「立ち寄り中」は地図上のマーカー（滞在時間ラベル付き）で示すので、下部カードは出さない。
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

    if (uiState.isLocationDisabled && uiState.interruptedTrack == null) {
      LocationDisabledCard(onOpenSettings = onOpenLocationSettings)
    }

    if (uiState.hasLocationPermission) {
      RecordFab(
        isTracking = uiState.isTracking,
        onStartTracking = onStartTracking,
        onStopTracking = onStopTracking,
      )
    }
  }
}
