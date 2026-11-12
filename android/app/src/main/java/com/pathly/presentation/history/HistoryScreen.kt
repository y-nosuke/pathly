package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.RegionKey
import com.pathly.presentation.common.ActiveFilter
import com.pathly.presentation.common.ActiveFilterChips
import com.pathly.presentation.common.CategoryFilterSection
import com.pathly.presentation.common.FilterChoiceSection
import com.pathly.presentation.common.FilterPanelButton
import com.pathly.presentation.common.FilterPanelCloseButton
import com.pathly.presentation.common.ListFilterPanel
import com.pathly.presentation.common.ListNameSearchField
import com.pathly.presentation.common.RegionFilterSection
import com.pathly.presentation.common.ScrollToTopButton
import com.pathly.presentation.common.ScrollToTopButtonClearance
import com.pathly.presentation.common.categoryChipLabel
import com.pathly.presentation.common.choiceLabel
import com.pathly.presentation.common.flippedChoice
import com.pathly.presentation.common.regionChipLabel
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
  modifier: Modifier = Modifier,
  viewModel: HistoryViewModel = hiltViewModel(),
  onTrackClick: (GpsTrack) -> Unit = {},
  // 地図表示の地図スロット。null（既定）は実マップ（GoogleMap）を描く。テストは空スロット（{}）を渡す。
  mapContent: (@Composable () -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val isMap = uiState.viewMode == HistoryViewMode.MAP
  // 地図表示はタブの中の一段奥の見方として扱い、システムバックで一覧表示へ戻す（タブは離れない。場所タブと同じ）。
  // 吹き出しが出ているときは、後から登録される地図側の処理が先に吹き出しを閉じる。
  BackHandler(enabled = isMap) { viewModel.setViewMode(HistoryViewMode.LIST) }
  // 地図表示の範囲は、一覧表示に切り替えても・経路詳細へ行って戻っても捨てない。
  val mapCamera = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(LatLng(35.6762, 139.6503), 12f)
  }
  var mapInitialized by rememberSaveable { mutableStateOf(false) }
  // 地図は一度開いたら、一覧表示に切り替えても捨てずに一覧の裏に残す（作り直すと地図の絵が出るまでちらつくため）。
  var keepMap by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(isMap) { if (isMap) keepMap = true }

  // 名前を編集中の経路（null=ダイアログ非表示）。
  var renameTarget by remember { mutableStateOf<GpsTrack?>(null) }
  // 削除確認中の経路（null=ダイアログ非表示）。削除は破壊的（点列・立ち寄りもCASCADEで消える）ため確認する。
  var deleteTarget by remember { mutableStateOf<GpsTrack?>(null) }
  // 絞り込みの欄を開いているか（欄は一覧の先頭に入れる）。
  var filterPanelOpen by rememberSaveable { mutableStateOf(false) }
  // 欄が実際に出ているか。読み込み中や、完了した経路が無い（記録中だけの）ときは一覧に欄を入れないので、
  // 開いたままでも出ていない扱いにする（閉じるボタン・戻る・ボタンの ▴ を欄の見え方に揃える）。
  // 地図表示では、重ねたものをたたんでいる間は欄が見えない。
  val panelVisible = filterPanelOpen &&
    uiState.tracks.isNotEmpty() &&
    if (isMap) !uiState.controlsCollapsed else !uiState.isLoading
  // 欄が出ているときの戻るは、欄を閉じる（地図表示から一覧へ戻すより先）。
  BackHandler(enabled = panelVisible) { filterPanelOpen = false }

  Box(modifier = modifier.fillMaxSize()) {
    if (isMap || keepMap) {
      HistoryMapLayout(
        state = uiState,
        visible = isMap,
        camera = mapCamera,
        initialized = mapInitialized,
        onInitialized = { mapInitialized = true },
        onViewModeChange = viewModel::setViewMode,
        onControlsCollapsedChange = viewModel::setControlsCollapsed,
        onToggle = viewModel::toggleOverlay,
        onFocus = viewModel::focusOverlay,
        onClearAll = viewModel::clearOverlays,
        onConsumeFocus = viewModel::consumeFocus,
        onOpenTrack = onTrackClick,
        filterControls = {
          if (uiState.tracks.isNotEmpty()) {
            FilterSortBar(
              state = uiState,
              onClearFilters = viewModel::clearFilters,
              onResetListOptions = viewModel::resetListOptions,
              onNameQueryChange = viewModel::setNameQuery,
              onFavoriteFilterChange = viewModel::setFavoriteFilter,
              onNamedFilterChange = viewModel::setNamedFilter,
              onStopFilterChange = viewModel::setStopFilter,
              onToggleCategoryFilter = viewModel::toggleCategoryFilter,
              onClearCategoryFilter = viewModel::clearCategoryFilter,
              onToggleRegionFilter = viewModel::toggleRegionFilter,
              onClearRegionFilter = viewModel::clearRegionFilter,
              onSortChange = viewModel::setSort,
              onToggleSortDirection = viewModel::toggleSortDirection,
              panelOpen = panelVisible,
              onPanelOpenChange = { filterPanelOpen = it },
              // 地図表示では並びに意味が無いので並べ替えは出さない。欄は重ねたものの中に出す（一覧が無いので）。
              showSort = false,
              inlinePanel = true,
            )
          }
        },
        mapContent = mapContent,
      )
    }
    if (!isMap) {
      HistoryListContent(
        uiState = uiState,
        viewModel = viewModel,
        panelVisible = panelVisible,
        onPanelOpenChange = { filterPanelOpen = it },
        onTrackClick = onTrackClick,
        onRenameClick = { renameTarget = it },
        onDeleteClick = { deleteTarget = it },
      )
    }
  }

  renameTarget?.let { target ->
    RenameTrackDialog(
      initialName = target.name.orEmpty(),
      onDismiss = { renameTarget = null },
      onConfirm = { newName ->
        viewModel.renameTrack(target.id, newName)
        renameTarget = null
      },
    )
  }

  deleteTarget?.let { target ->
    val label = target.name?.takeIf { it.isNotBlank() }
      ?: DateFormatters.shortDate(target.startTime)
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("記録を削除しますか？") },
      text = { Text("「$label」の経路・立ち寄りをすべて削除します。この操作は元に戻せません。") },
      confirmButton = {
        TextButton(onClick = {
          viewModel.deleteTrack(target)
          deleteTarget = null
        }) {
          Text("削除する", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
      },
    )
  }
}

/** 履歴の一覧表示（見出し・絞り込み・統計・経路の一覧）。裏に残した地図を隠し、触っても地図に届かないようにする。 */
@Composable
private fun HistoryListContent(
  uiState: HistoryState,
  viewModel: HistoryViewModel,
  panelVisible: Boolean,
  onPanelOpenChange: (Boolean) -> Unit,
  onTrackClick: (GpsTrack) -> Unit,
  onRenameClick: (GpsTrack) -> Unit,
  onDeleteClick: (GpsTrack) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .pointerInput(Unit) {}
      .padding(16.dp),
  ) {
    HistoryHeader(
      viewMode = uiState.viewMode,
      onViewModeChange = viewModel::setViewMode,
      modifier = Modifier.padding(bottom = 12.dp),
    )

    // 記録が1件でもあれば絞り込み・並べ替えバーを出す（空・記録中のみのときは邪魔なので隠す）。
    if (uiState.tracks.isNotEmpty()) {
      FilterSortBar(
        state = uiState,
        onClearFilters = viewModel::clearFilters,
        onResetListOptions = viewModel::resetListOptions,
        onNameQueryChange = viewModel::setNameQuery,
        onFavoriteFilterChange = viewModel::setFavoriteFilter,
        onNamedFilterChange = viewModel::setNamedFilter,
        onStopFilterChange = viewModel::setStopFilter,
        onToggleCategoryFilter = viewModel::toggleCategoryFilter,
        onClearCategoryFilter = viewModel::clearCategoryFilter,
        onToggleRegionFilter = viewModel::toggleRegionFilter,
        onClearRegionFilter = viewModel::clearRegionFilter,
        onSortChange = viewModel::setSort,
        onToggleSortDirection = viewModel::toggleSortDirection,
        panelOpen = panelVisible,
        onPanelOpenChange = onPanelOpenChange,
      )
      Spacer(modifier = Modifier.height(8.dp))
    }

    when {
      uiState.isLoading -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }

      uiState.tracks.isEmpty() && uiState.activeTrack == null -> {
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
        val visibleTracks = uiState.visibleTracks
        val listState = rememberLazyListState()
        // 絞り込みの欄を開いたら、欄が見えるよう先頭へ寄せる。開いたまま組み直したとき（画面の回転・詳細から戻る）は
        // 動かさない（見ていた位置のまま）。
        var scrolledForOpen by remember { mutableStateOf(panelVisible) }
        LaunchedEffect(panelVisible) {
          if (panelVisible && !scrolledForOpen) listState.animateScrollToItem(0)
          scrolledForOpen = panelVisible
        }
        Box(modifier = Modifier.fillMaxSize()) {
          LazyColumn(
            state = listState,
            // 最後の行が先頭へ戻るボタンの上まで上がるよう、下に余白を足す（右端の操作が隠れないように）。
            contentPadding = PaddingValues(bottom = ScrollToTopButtonClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // 絞り込みの欄は一覧の先頭に入れ、一覧と一緒にスクロールする（選びながら下の結果を見られる）。
            if (panelVisible) {
              item(key = "filterPanel") {
                HistoryFilterPanel(
                  state = uiState,
                  onClearFilters = viewModel::clearFilters,
                  onResetListOptions = viewModel::resetListOptions,
                  onFavoriteFilterChange = viewModel::setFavoriteFilter,
                  onNamedFilterChange = viewModel::setNamedFilter,
                  onStopFilterChange = viewModel::setStopFilter,
                  onToggleCategoryFilter = viewModel::toggleCategoryFilter,
                  onToggleRegionFilter = viewModel::toggleRegionFilter,
                )
              }
            }
            item {
              // 統計は一覧と同じく絞り込み後の経路で数える。絞っているときは全体の件数も添える。
              StatisticsSummaryCard(
                tracks = visibleTracks,
                totalCount = uiState.tracks.size,
                isFiltered = !uiState.noFilter,
              )
              Spacer(modifier = Modifier.height(16.dp))
            }

            // 記録中のアクティブトラックを表示
            uiState.activeTrack?.let { activeTrack ->
              item {
                ActiveTrackItem(
                  track = activeTrack,
                  onTrackClick = { onTrackClick(activeTrack) },
                )
                Spacer(modifier = Modifier.height(8.dp))
              }
            }

            if (visibleTracks.isEmpty()) {
              item {
                Text(
                  text = "条件に合う記録がありません",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                  textAlign = TextAlign.Center,
                )
              }
            }

            items(visibleTracks) { track ->
              TrackItem(
                track = track,
                onTrackClick = { onTrackClick(track) },
                onToggleFavorite = { viewModel.toggleFavorite(track) },
                onRenameClick = { onRenameClick(track) },
                onDeleteClick = { onDeleteClick(track) },
              )
            }
          }
          ScrollToTopButton(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
          )
          // 欄を開いている間は、親指の届く下にも閉じるボタンを浮かべる。
          if (panelVisible) {
            FilterPanelCloseButton(
              onClick = { onPanelOpenChange(false) },
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSortBar(
  state: HistoryState,
  onClearFilters: () -> Unit,
  onResetListOptions: () -> Unit,
  onNameQueryChange: (String) -> Unit,
  onFavoriteFilterChange: (TrackFavoriteFilter) -> Unit,
  onNamedFilterChange: (TrackNamedFilter) -> Unit,
  onStopFilterChange: (TrackStopFilter) -> Unit,
  onToggleCategoryFilter: (PlaceCategoryFacet) -> Unit,
  onClearCategoryFilter: () -> Unit,
  onToggleRegionFilter: (RegionKey) -> Unit,
  onClearRegionFilter: () -> Unit,
  onSortChange: (TrackSort) -> Unit,
  onToggleSortDirection: () -> Unit,
  panelOpen: Boolean,
  onPanelOpenChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  // 並べ替えを出すか（地図表示では並びに意味が無いので出さない）。
  showSort: Boolean = true,
  // 絞り込みの欄をここに出すか（地図表示。一覧表示では欄を一覧の先頭に入れる）。
  inlinePanel: Boolean = false,
) {
  Column(modifier = modifier) {
    // 名前の検索（名前、未命名なら見出しの日付に含むものだけ残す）。ほかの絞り込みとは AND。消すのは欄の ×。
    ListNameSearchField(
      query = state.nameQuery,
      onQueryChange = onNameQueryChange,
      placeholder = "名前・日付で検索",
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 絞り込みの各軸はボタンで開く欄（一覧の先頭）にまとめ、効いている条件だけをチップで出す（→ specs/screens.md）。
    val activeFilters = historyActiveFilters(
      state = state,
      onFavoriteFilterChange = onFavoriteFilterChange,
      onNamedFilterChange = onNamedFilterChange,
      onStopFilterChange = onStopFilterChange,
      onClearCategoryFilter = onClearCategoryFilter,
      onClearRegionFilter = onClearRegionFilter,
    )
    // 絞り込みのボタンと、並べ替え（軸メニュー ＋ 昇順/降順トグル）。
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      FilterPanelButton(activeCount = activeFilters.size, expanded = panelOpen, onClick = { onPanelOpenChange(!panelOpen) })
      Spacer(modifier = Modifier.weight(1f))
      if (showSort) {
        SortControls(state = state, onSortChange = onSortChange, onToggleSortDirection = onToggleSortDirection)
      }
    }
    // 欄を開いている間はチップの行を出さない（欄に選んでいるものが見えている。選ぶたびに行が出たり消えたりして
    // 欄がずれないように）。「初期状態に戻す」は欄の見出しにある。
    if (!panelOpen) {
      ActiveFilterChips(
        activeFilters,
        onChipClick = { onPanelOpenChange(true) },
        onReset = onResetListOptions.takeUnless { state.isInitialListOptions },
      )
    }
    if (inlinePanel && panelOpen) {
      Spacer(modifier = Modifier.height(8.dp))
      // 地図を隠しきらないよう高さを抑え、中でスクロールする。
      Box(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        HistoryFilterPanel(
          state = state,
          onClearFilters = onClearFilters,
          onResetListOptions = onResetListOptions,
          onFavoriteFilterChange = onFavoriteFilterChange,
          onNamedFilterChange = onNamedFilterChange,
          onStopFilterChange = onStopFilterChange,
          onToggleCategoryFilter = onToggleCategoryFilter,
          onToggleRegionFilter = onToggleRegionFilter,
        )
      }
    }
  }
}

/** 並べ替え（軸メニュー ＋ 昇順/降順トグル）。 */
@Composable
private fun SortControls(
  state: HistoryState,
  onSortChange: (TrackSort) -> Unit,
  onToggleSortDirection: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box {
      var sortOpen by remember { mutableStateOf(false) }
      TextButton(onClick = { sortOpen = true }) {
        Text(text = "並べ替え: ${state.sort.label} ▾")
      }
      DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
        TrackSort.entries.forEach { sort ->
          DropdownMenuItem(
            text = { Text(sort.label) },
            onClick = {
              sortOpen = false
              onSortChange(sort)
            },
          )
        }
      }
    }
    TextButton(onClick = onToggleSortDirection) {
      Text(text = if (state.sortDescending) "降順 ↓" else "昇順 ↑")
    }
  }
}

/** 経路一覧の絞り込みの欄（お気に入り・名前・立ち寄り・業種・地域）。 */
@Composable
private fun HistoryFilterPanel(
  state: HistoryState,
  onClearFilters: () -> Unit,
  onResetListOptions: () -> Unit,
  onFavoriteFilterChange: (TrackFavoriteFilter) -> Unit,
  onNamedFilterChange: (TrackNamedFilter) -> Unit,
  onStopFilterChange: (TrackStopFilter) -> Unit,
  onToggleCategoryFilter: (PlaceCategoryFacet) -> Unit,
  onToggleRegionFilter: (RegionKey) -> Unit,
) {
  ListFilterPanel(
    resultCount = state.visibleTracks.size,
    // 「すべて外す」は欄の軸だけを外す（名前の検索語は残す）ので、欄の軸が効いているときだけ押せる。
    canClear = state.favoriteFilter != TrackFavoriteFilter.ANY ||
      state.namedFilter != TrackNamedFilter.ANY ||
      state.stopFilter != TrackStopFilter.ANY ||
      state.categoryFilter.isNotEmpty() ||
      state.regionFilter.isNotEmpty(),
    onClearAll = onClearFilters,
    canReset = !state.isInitialListOptions,
    onReset = onResetListOptions,
  ) {
    FilterChoiceSection(
      title = "お気に入り",
      options = TrackFavoriteFilter.entries,
      selected = state.favoriteFilter,
      labelOf = { choiceLabel(it, TrackFavoriteFilter.ANY) { f -> f.chipLabel } },
      onSelect = onFavoriteFilterChange,
    )
    FilterChoiceSection(
      title = "名前",
      options = TrackNamedFilter.entries,
      selected = state.namedFilter,
      labelOf = { choiceLabel(it, TrackNamedFilter.ANY) { f -> f.chipLabel } },
      onSelect = onNamedFilterChange,
    )
    FilterChoiceSection(
      title = "立ち寄り",
      options = TrackStopFilter.entries,
      selected = state.stopFilter,
      labelOf = { choiceLabel(it, TrackStopFilter.ANY) { f -> f.chipLabel } },
      onSelect = onStopFilterChange,
    )
    // 業種は立ち寄った場所の業種。複数選べる（どれかに立ち寄った経路を残す）。
    CategoryFilterSection(
      selected = state.categoryFilter,
      counts = state.categoryCounts,
      onToggle = onToggleCategoryFilter,
    )
    // 地域は立ち寄った場所の地域。都道府県（海外は国）→ 市区町村（都市）の 2 段。複数選べる。
    RegionFilterSection(
      selected = state.regionFilter,
      counts = state.regionCounts,
      onToggle = onToggleRegionFilter,
    )
  }
}

/** 経路一覧でいま効いている条件（絞り込みの欄の軸。名前の検索は検索欄に出ているので含めない）。並びは欄と同じ。 */
private fun historyActiveFilters(
  state: HistoryState,
  onFavoriteFilterChange: (TrackFavoriteFilter) -> Unit,
  onNamedFilterChange: (TrackNamedFilter) -> Unit,
  onStopFilterChange: (TrackStopFilter) -> Unit,
  onClearCategoryFilter: () -> Unit,
  onClearRegionFilter: () -> Unit,
): List<ActiveFilter> = listOfNotNull(
  state.favoriteFilter.takeIf { it != TrackFavoriteFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { onFavoriteFilterChange(TrackFavoriteFilter.ANY) },
      onToggle = { onFavoriteFilterChange(flippedChoice(it, TrackFavoriteFilter.FAVORITE, TrackFavoriteFilter.NOT_FAVORITE)) },
    )
  },
  state.namedFilter.takeIf { it != TrackNamedFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { onNamedFilterChange(TrackNamedFilter.ANY) },
      onToggle = { onNamedFilterChange(flippedChoice(it, TrackNamedFilter.NAMED, TrackNamedFilter.UNNAMED)) },
    )
  },
  state.stopFilter.takeIf { it != TrackStopFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { onStopFilterChange(TrackStopFilter.ANY) },
      onToggle = { onStopFilterChange(flippedChoice(it, TrackStopFilter.HAS_STOPS, TrackStopFilter.NO_STOPS)) },
    )
  },
  categoryChipLabel(state.categoryFilter)?.let { ActiveFilter(it, onClearCategoryFilter) },
  regionChipLabel(state.regionFilter)?.let { ActiveFilter(it, onClearRegionFilter) },
)

@Composable
private fun StatisticsSummaryCard(
  tracks: List<GpsTrack>,
  totalCount: Int,
  isFiltered: Boolean,
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
        text = statisticsTitle(shownCount = tracks.size, totalCount = totalCount, isFiltered = isFiltered),
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
          // Composable 内で Locale を直接読むと再コンポーズに追随しないため、
          // 一覧の各行と同じ「丸めて文字列化」に揃える。
          value = "${(totalDistance * 10).roundToInt() / 10.0}km",
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
private fun ActiveTrackItem(
  track: GpsTrack,
  onTrackClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onTrackClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_play_arrow),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = "🟢 記録中",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "開始: ${DateFormatters.shortTime(track.startTime)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Spacer(modifier = Modifier.height(4.dp))

        val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
        Text(
          text = "移動距離: ${distanceKm}km (${track.pointCount}点)",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
          fontWeight = FontWeight.Medium,
        )
      }

      Icon(
        painter = painterResource(R.drawable.ic_location_on),
        contentDescription = "地図で表示",
        tint = MaterialTheme.colorScheme.secondary,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackItem(
  track: GpsTrack,
  onTrackClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRenameClick: () -> Unit,
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
        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
      ) {
        // 見出しは名前があれば名前、無ければ日付。名前ありのときは日付を副題に添える。
        if (track.hasName) {
          Text(
            text = track.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = DateFormatters.shortDate(track.startTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            text = DateFormatters.shortDate(track.startTime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = "開始: ${DateFormatters.shortTime(track.startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          track.endTime?.let { endTime ->
            Text(
              text = "終了: ${DateFormatters.shortTime(endTime)}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(
            text = "移動距離: ${distanceKm}km",
            style = MaterialTheme.typography.bodyMedium,
            color = if (track.totalDistanceMeters > 0) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium,
          )
          Text(
            text = "立ち寄り: ${track.stopCount}件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      // お気に入りトグル。
      IconButton(onClick = onToggleFavorite) {
        Icon(
          painter = painterResource(
            if (track.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
          ),
          contentDescription = if (track.isFavorite) "お気に入り解除" else "お気に入り",
          tint = if (track.isFavorite) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }

      // 名前編集・削除はまとめてメニューに（カードの横幅を圧迫しないため）。
      Box {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
          Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = "その他",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(if (track.hasName) "名前を編集" else "名前を付ける") },
            onClick = {
              menuOpen = false
              onRenameClick()
            },
          )
          DropdownMenuItem(
            text = { Text("削除") },
            onClick = {
              menuOpen = false
              onDeleteClick()
            },
          )
        }
      }
    }
  }
}

/** 統計カードの見出し。絞り込み中は、絞った数字を全体と取り違えないよう「n / 全体 回」を添える。 */
internal fun statisticsTitle(shownCount: Int, totalCount: Int, isFiltered: Boolean): String = if (isFiltered) {
  "📊 お出掛け統計（絞り込み中 $shownCount / $totalCount 回）"
} else {
  "📊 お出掛け統計"
}
