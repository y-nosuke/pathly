package com.pathly.presentation.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.ListMapCamera
import com.pathly.presentation.common.ListMapMode
import com.pathly.presentation.common.ListMapOverlay
import com.pathly.presentation.common.ListMapToggle
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.listMapSummary
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.requestCurrentLatLng
import com.pathly.presentation.common.toLatLngBounds
import com.pathly.presentation.tracking.TrackFocusEffect
import com.pathly.presentation.tracking.TrackOverlayContent
import com.pathly.presentation.tracking.TrackPickerSheet
import kotlinx.coroutines.launch

/**
 * 履歴タブの地図表示。地図をタブいっぱいに広げ、見出し・検索欄・絞り込みを上に重ねる（たためる。場所タブの地図表示と同じ）。
 * 下のシートに絞り込んだ経路の一覧を出し、チェックで選んだ経路だけを色分けで地図に重ねる（記録画面の「この範囲の経路」と同じ）。
 * 行のタップは経路詳細へ。
 *
 * @param visible false のときは一覧表示の裏に地図だけを残している（重ねるもの・シートは出さない）。
 * @param camera 地図の範囲と、初めて開いたとき（現在地へ寄せる）を一度だけにするための印。画面の状態として持つ。
 * @param filterControls 重ねたものの中に出す、名前の検索・絞り込みのボタンと欄。
 */
@Composable
internal fun HistoryMapLayout(
  state: HistoryState,
  visible: Boolean,
  camera: ListMapCamera,
  onViewModeChange: (ListMapMode) -> Unit,
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
    if (!mapLoaded) return
    val latLngBounds = bounds.toLatLngBounds() ?: return
    scope.launch { camera.position.animate(CameraUpdateFactory.newLatLngBounds(latLngBounds, fitPaddingPx)) }
  }

  // 初めて地図を開いたときは、現在地あたりを見せる（経路を選ぶまで何も描かないので、見慣れた範囲から始める）。
  LaunchedEffect(mapLoaded) {
    if (!mapLoaded || camera.isInitialized) return@LaunchedEffect
    camera.isInitialized = true
    if (state.selected.isNotEmpty()) return@LaunchedEffect
    requestCurrentLatLng(context) { latLng ->
      scope.launch { camera.position.animate(CameraUpdateFactory.newLatLngZoom(latLng, 13f)) }
    }
  }

  // 一覧の「この経路の全体を見る」。
  TrackFocusEffect(
    focusRequest = state.focusRequest,
    selected = state.selected,
    onFit = { fit(listOf(it)) },
    onConsume = onConsumeFocus,
    ready = mapLoaded,
  )

  Box(modifier = modifier.fillMaxSize()) {
    if (mapContent != null) {
      mapContent()
    } else {
      GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera.position,
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
      ListMapOverlay(
        mode = state.viewMode,
        onModeChange = onViewModeChange,
        collapsed = state.controlsCollapsed,
        onCollapsedChange = onControlsCollapsedChange,
        collapsedSummary = listMapSummary("経路", state.activeFilterCount, state.visibleTracks.size, state.tracks.size),
        onHeightChange = { overlayHeight = it },
        header = { HistoryHeader(viewMode = state.viewMode, onViewModeChange = onViewModeChange) },
        controls = filterControls,
        modifier = Modifier.align(Alignment.TopCenter),
      )

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

/** 履歴タブの見出しの行（「外出履歴」・一覧／地図の切り替え）。地図表示では右端にたたむボタンが足される。 */
@Composable
internal fun HistoryHeader(
  viewMode: ListMapMode,
  onViewModeChange: (ListMapMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "外出履歴",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
    )
    ListMapToggle(mode = viewMode, onChange = onViewModeChange)
  }
}

// 選んだ経路が入るまで寄せるときの、端の余白（線と立ち寄りのバッジが切れないように）。
private val HistoryMapFitPadding = 56.dp
