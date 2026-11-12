package com.pathly.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.pathly.R
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.ListMapToggle
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.tracking.TrackOverlayContent
import com.pathly.presentation.tracking.TrackPickerSheet
import kotlinx.coroutines.launch

/**
 * 履歴タブの地図表示。地図をタブいっぱいに広げ、見出し・検索欄・絞り込みを上に重ねる（たためる。場所タブの地図表示と同じ）。
 * 下のシートに絞り込んだ経路の一覧を出し、チェックで選んだ経路だけを色分けで地図に重ねる（記録画面の「この範囲の経路」と同じ）。
 * 行のタップは経路詳細へ。
 *
 * @param visible false のときは一覧表示の裏に地図だけを残している（重ねるもの・シートは出さない）。
 * @param initialized 地図を初めて開いたとき（現在地へ寄せる）を一度だけにするための印。画面の状態として持つ。
 * @param filterControls 重ねたものの中に出す、名前の検索・絞り込みのボタンと欄。
 */
@Composable
internal fun HistoryMapLayout(
  state: HistoryState,
  visible: Boolean,
  camera: CameraPositionState,
  initialized: Boolean,
  onInitialized: () -> Unit,
  onViewModeChange: (HistoryViewMode) -> Unit,
  onControlsCollapsedChange: (Boolean) -> Unit,
  onToggle: (GpsTrack) -> Unit,
  onFocus: (GpsTrack) -> Unit,
  onClearAll: () -> Unit,
  onConsumeFocus: () -> Unit,
  onOpenTrack: (GpsTrack) -> Unit,
  filterControls: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  // 地図スロット。null（既定）は実マップ（GoogleMap）を描く。テストは空スロットを渡し、GMS に頼らずに確かめる。
  mapContent: (@Composable () -> Unit)? = null,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val scope = rememberCoroutineScope()
  // 重ねたもの（上）の高さ。地図の余白にして、線・寄せた範囲が隠れないようにする（下はシートの高さ）。
  var overlayHeight by remember { mutableStateOf(0.dp) }
  // 経路の一覧は畳みきれないようにする（地図表示の間はいつも出している）。地図を広く見られるよう低めにする。
  val sheetState = rememberFloatingSheetState(peekFraction = 0.35f, allowHidden = false)
  val sheetHeight = sheetState.heightOf(sheetState.detent)
  val infoWindow = rememberMapInfoWindowState()
  // 一覧表示の裏に回ったら吹き出しを閉じる。見えない吹き出しにバックを吸われないように。
  LaunchedEffect(visible) { if (!visible) infoWindow.close() }
  // 範囲（bounds）で寄せるには地図の大きさが決まっている必要があるので、読み込みを待つ。
  var mapLoaded by remember { mutableStateOf(false) }
  val fitPaddingPx = with(density) { HistoryMapFitPadding.roundToPx() }

  fun fit(bounds: List<GeoBounds>) {
    if (!mapLoaded || bounds.isEmpty()) return
    val latLngBounds = LatLngBounds.builder().apply {
      bounds.forEach {
        include(LatLng(it.south, it.west))
        include(LatLng(it.north, it.east))
      }
    }.build()
    scope.launch { camera.animate(CameraUpdateFactory.newLatLngBounds(latLngBounds, fitPaddingPx)) }
  }

  // 初めて地図を開いたときは、現在地あたりを見せる（経路を選ぶまで何も描かないので、見慣れた範囲から始める）。
  LaunchedEffect(mapLoaded) {
    if (!mapLoaded || initialized) return@LaunchedEffect
    onInitialized()
    if (state.selected.isNotEmpty()) return@LaunchedEffect
    try {
      LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
          location?.let {
            scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 13f)) }
          }
        }
    } catch (e: SecurityException) {
      // 権限が無ければ既定の位置のまま。
    }
  }

  // 一覧の「この経路の全体を見る」: 線を読み終えてから寄せる（まだ重ねていない経路は、重ねて読み終えるまで待つ）。
  val focusTarget = state.focusRequest?.let { req -> state.selected.firstOrNull { it.track.id == req.trackId } }
  LaunchedEffect(state.focusRequest, focusTarget?.isLoaded, mapLoaded) {
    if (state.focusRequest == null || !mapLoaded) return@LaunchedEffect
    if (focusTarget == null) {
      onConsumeFocus()
      return@LaunchedEffect
    }
    if (!focusTarget.isLoaded) return@LaunchedEffect
    fit(listOfNotNull(focusTarget.bounds))
    onConsumeFocus()
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (mapContent != null) {
      mapContent()
    } else {
      GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(
          zoomControlsEnabled = false,
          mapToolbarEnabled = false,
          myLocationButtonEnabled = false,
        ),
        contentPadding = PaddingValues(top = overlayHeight, bottom = if (visible) sheetHeight else 0.dp),
        onMapLoaded = { mapLoaded = true },
      ) {
        TrackOverlayContent(state.selected, infoWindow)
      }
    }

    // 一覧表示の裏にいる間は重ねない（重ねたもの・シート）。
    if (visible) {
      Box(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } },
      ) {
        if (state.controlsCollapsed) {
          HistoryCollapsedBar(
            state = state,
            onViewModeChange = onViewModeChange,
            onExpand = { onControlsCollapsedChange(false) },
            modifier = Modifier.padding(12.dp),
          )
        } else {
          Surface(
            // 地図の文字が透けると読みにくいので、重ねたものは不透明にする。
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            shadowElevation = 2.dp,
          ) {
            // 余白は一覧表示と揃え、切り替えても見出しの位置が動かないようにする。
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
              HistoryHeader(
                viewMode = state.viewMode,
                onViewModeChange = onViewModeChange,
                onCollapse = {
                  // 検索欄に入力中なら、キーボードも閉じる。
                  focusManager.clearFocus()
                  onControlsCollapsedChange(true)
                },
              )
              Spacer(modifier = Modifier.height(12.dp))
              filterControls()
            }
          }
        }
      }

      val visibleTracks = state.visibleTracks
      TrackPickerSheet(
        title = if (state.noFilter) "経路 ${visibleTracks.size} 件" else "絞り込んだ経路 ${visibleTracks.size} 件",
        listed = visibleTracks,
        selected = state.selected,
        sheetState = sheetState,
        emptyText = if (state.tracks.isEmpty()) "記録がありません" else "条件に合う記録がありません",
        outsideLabel = "絞り込みの外（重ねている経路）",
        onToggle = onToggle,
        onFocus = onFocus,
        onClearAll = onClearAll,
        isLoading = state.isLoading,
        onFitAll = { fit(state.selected.mapNotNull { it.bounds }) },
        onOpen = onOpenTrack,
        testTagPrefix = "historyMap",
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

/** 履歴タブの見出しの行（「外出履歴」・一覧／地図の切り替え）。[onCollapse] があれば右端にたたむボタンを出す。 */
@Composable
internal fun HistoryHeader(
  viewMode: HistoryViewMode,
  onViewModeChange: (HistoryViewMode) -> Unit,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "外出履歴",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
    )
    HistoryViewModeToggle(viewMode = viewMode, onChange = onViewModeChange)
    onCollapse?.let {
      IconButton(onClick = it) {
        Icon(painter = painterResource(R.drawable.ic_expand_less), contentDescription = "たたむ")
      }
    }
  }
}

@Composable
private fun HistoryViewModeToggle(viewMode: HistoryViewMode, onChange: (HistoryViewMode) -> Unit) {
  ListMapToggle(isMap = viewMode == HistoryViewMode.MAP) { toMap -> onChange(if (toMap) HistoryViewMode.MAP else HistoryViewMode.LIST) }
}

/**
 * 地図表示で見出し・検索欄・絞り込みをたたんだときの帯。一覧へ戻る切り替えと、絞り込んでいることが分かる件数を残す
 * （場所タブと同じ）。件数の部分を押してもひろがる。
 */
@Composable
private fun HistoryCollapsedBar(
  state: HistoryState,
  onViewModeChange: (HistoryViewMode) -> Unit,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      HistoryViewModeToggle(viewMode = state.viewMode, onChange = onViewModeChange)
      Text(
        text = if (state.noFilter) {
          "経路 ${state.tracks.size} 件"
        } else {
          "絞り込み ${state.activeFilterCount}・${state.visibleTracks.size} / ${state.tracks.size} 件"
        },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .weight(1f)
          .clickable(onClick = onExpand)
          .padding(horizontal = 4.dp, vertical = 12.dp),
      )
      IconButton(onClick = onExpand) {
        Icon(painter = painterResource(R.drawable.ic_expand_more), contentDescription = "ひろげる")
      }
    }
  }
}

// 選んだ経路が入るまで寄せるときの、端の余白（線と立ち寄りのバッジが切れないように）。
private val HistoryMapFitPadding = 56.dp
