package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.ActiveFilter
import com.pathly.presentation.common.ActiveFilterChips
import com.pathly.presentation.common.CategoryFilterSection
import com.pathly.presentation.common.ExpandableListStats
import com.pathly.presentation.common.FilterChoiceSection
import com.pathly.presentation.common.FilterPanelButton
import com.pathly.presentation.common.FilterPanelCloseButton
import com.pathly.presentation.common.ListDisplayMenuButton
import com.pathly.presentation.common.ListFilterPanel
import com.pathly.presentation.common.ListHeader
import com.pathly.presentation.common.ListMapLayout
import com.pathly.presentation.common.ListMapMode
import com.pathly.presentation.common.ListMapToggle
import com.pathly.presentation.common.ListMenuChoice
import com.pathly.presentation.common.ListMenuSectionTitle
import com.pathly.presentation.common.ListToolRow
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.common.RegionFilterSection
import com.pathly.presentation.common.ScrollToTopButton
import com.pathly.presentation.common.ScrollToTopButtonClearance
import com.pathly.presentation.common.categoryChipLabel
import com.pathly.presentation.common.choiceLabel
import com.pathly.presentation.common.flippedChoice
import com.pathly.presentation.common.regionChipLabel
import com.pathly.presentation.common.rememberListMapCamera
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme
import com.pathly.util.DateFormatters
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 履歴タブ。一覧の画面の型（見出し＋ツール行 → docs/designs/ui-direction.md）に載せ、地図表示ではその見出しとツール行を
 * 地図に重ねる。
 */
@Composable
fun HistoryScreen(
  modifier: Modifier = Modifier,
  viewModel: HistoryViewModel = hiltViewModel(),
  onTrackClick: (GpsTrack) -> Unit = {},
  // 地図表示の地図スロット。null（既定）は実マップ（GoogleMap）を描く。テストは空スロット（{}）を渡す。
  mapContent: (@Composable () -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val registeredPlaces by viewModel.registeredPlaces.collectAsStateWithLifecycle()
  val showRegisteredPlaces by viewModel.showRegisteredPlaces.collectAsStateWithLifecycle()
  val mapPlaceFilter by viewModel.mapPlaceFilter.collectAsStateWithLifecycle()
  val isMap = uiState.viewMode == ListMapMode.MAP
  // 地図表示はタブの中の一段奥の見方として扱い、システムバックで一覧表示へ戻す（タブは離れない。場所タブと同じ）。
  // 吹き出しが出ているときは、後から登録される地図側の処理が先に吹き出しを閉じる。
  BackHandler(enabled = isMap) { viewModel.setViewMode(ListMapMode.LIST) }
  // 地図表示の範囲は、一覧表示に切り替えても・経路詳細へ行って戻っても捨てない。
  val mapCamera = rememberListMapCamera()

  // 名前を編集中の経路（null=ダイアログ非表示）。
  var renameTarget by remember { mutableStateOf<GpsTrack?>(null) }
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

  // 見出しの検索の窓を開いているか。一覧表示と地図表示で同じ見出しを出すので、ここで持って揃える。
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  val header: @Composable () -> Unit = {
    ListHeader(
      title = "外出履歴",
      query = uiState.nameQuery,
      onQueryChange = viewModel::setNameQuery,
      searchPlaceholder = "名前・日付で検索",
      searchOpen = searchOpen,
      onSearchOpenChange = { searchOpen = it },
    ) {
      ListMapToggle(mode = uiState.viewMode, onChange = viewModel::setViewMode)
    }
  }

  ListMapLayout(
    mode = uiState.viewMode,
    modifier = modifier,
    map = { visible ->
      HistoryMapLayout(
        state = uiState,
        visible = visible,
        camera = mapCamera,
        mapPlaces = MapRegisteredPlaces(
          places = registeredPlaces,
          shown = showRegisteredPlaces,
          onToggle = viewModel::toggleShowRegisteredPlaces,
          filter = mapPlaceFilter,
          onFilterChange = viewModel::setMapPlaceFilter,
        ),
        onViewModeChange = viewModel::setViewMode,
        onControlsCollapsedChange = viewModel::setControlsCollapsed,
        onToggle = viewModel::toggleOverlay,
        onFocus = viewModel::focusOverlay,
        onClearAll = viewModel::clearOverlays,
        onConsumeFocus = viewModel::consumeFocus,
        onOpenTrack = onTrackClick,
        header = header,
        listControls = { trailing ->
          // 地図表示では並びに意味が無いので、ツール行の右は表示のメニューの代わりにレイヤー［≋］。
          // 欄は重ねたものの中に出す（一覧が無いので）。
          HistoryListControls(
            state = uiState,
            viewModel = viewModel,
            panelOpen = panelVisible,
            onPanelOpenChange = { filterPanelOpen = it },
            inlinePanel = true,
            trailing = trailing,
          )
        },
        mapContent = mapContent,
      )
    },
    list = {
      HistoryListContent(
        uiState = uiState,
        viewModel = viewModel,
        panelVisible = panelVisible,
        onPanelOpenChange = { filterPanelOpen = it },
        onTrackClick = onTrackClick,
        onRenameClick = { renameTarget = it },
        header = header,
      )
    },
  )

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
}

/** 履歴の一覧表示（見出し・ツール行・統計の帯・経路の一覧）。 */
@Composable
private fun HistoryListContent(
  uiState: HistoryState,
  viewModel: HistoryViewModel,
  panelVisible: Boolean,
  onPanelOpenChange: (Boolean) -> Unit,
  onTrackClick: (GpsTrack) -> Unit,
  onRenameClick: (GpsTrack) -> Unit,
  header: @Composable () -> Unit,
) {
  val statsExpanded by viewModel.statsExpanded.collectAsStateWithLifecycle()
  Column(modifier = Modifier.fillMaxSize()) {
    header()

    // 記録が1件でもあればツール行を出す（空・記録中のみのときは邪魔なので隠す）。
    if (uiState.tracks.isNotEmpty()) {
      HistoryListControls(
        state = uiState,
        viewModel = viewModel,
        panelOpen = panelVisible,
        onPanelOpenChange = onPanelOpenChange,
        trailing = { HistoryDisplayMenu(state = uiState, viewModel = viewModel) },
      )
    }
    Spacer(modifier = Modifier.height(PathlySpacing.sm))

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
          ) {
            // 絞り込みの欄は一覧の先頭に入れ、一覧と一緒にスクロールする（選びながら下の結果を見られる）。
            if (panelVisible) {
              item(key = "filterPanel") {
                HistoryFilterPanel(
                  state = uiState,
                  viewModel = viewModel,
                  modifier = Modifier.padding(start = PathlySpacing.screenEdge, end = PathlySpacing.screenEdge, bottom = PathlySpacing.sm),
                )
              }
            }
            item(key = "stats") {
              // 統計は一覧と同じく絞り込み後の経路で数える。絞っているときは全体の件数も添える。
              // 帯を押すと、その場で統計のカードに広がる。
              val isFiltered = !uiState.noFilter
              ExpandableListStats(
                barText = historyStatsText(tracks = visibleTracks, totalCount = uiState.tracks.size, isFiltered = isFiltered),
                expanded = statsExpanded,
                onExpandedChange = viewModel::setStatsExpanded,
                isFiltered = isFiltered,
                modifier = Modifier.padding(bottom = PathlySpacing.sm),
              ) {
                // 経路・絞り込みが変わったときと、月が替わったとき（棒グラフの 12 か月がずれる）だけ数え直す。
                val month = YearMonth.now()
                val stats = remember(uiState, month) { HistoryStatistics.of(visibleTracks, now = month) }
                HistoryStatsCardContent(
                  stats = stats,
                  totalCount = uiState.tracks.size,
                  isFiltered = isFiltered,
                  onTrackClick = onTrackClick,
                )
              }
            }

            // 記録中のお出掛けは、一覧の先頭に別枠で出す。
            uiState.activeTrack?.let { activeTrack ->
              item(key = "active") {
                ActiveTrackBanner(
                  track = activeTrack,
                  onClick = { onTrackClick(activeTrack) },
                  modifier = Modifier.padding(start = PathlySpacing.screenEdge, end = PathlySpacing.screenEdge, bottom = PathlySpacing.sm),
                )
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
                    .padding(vertical = PathlySpacing.xl),
                  textAlign = TextAlign.Center,
                )
              }
            } else {
              item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            }

            items(visibleTracks, key = { it.id }) { track ->
              TrackRow(
                track = track,
                onClick = { onTrackClick(track) },
                onToggleFavorite = { viewModel.toggleFavorite(track) },
                onRenameClick = { onRenameClick(track) },
              )
            }
          }
          ScrollToTopButton(
            listState = listState,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(end = PathlySpacing.screenEdge, bottom = PathlySpacing.sm),
          )
          // 欄を開いている間は、親指の届く下にも閉じるボタンを浮かべる。
          if (panelVisible) {
            FilterPanelCloseButton(
              onClick = { onPanelOpenChange(false) },
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PathlySpacing.sm),
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

/**
 * 見出しの下のツール行（左に絞り込みのボタン、右に [trailing]）と、効いている条件のチップ。一覧表示と地図表示で同じつなぎ方。
 *
 * @param inlinePanel 絞り込みの欄をここに出すか（地図表示。一覧表示では欄を一覧の先頭に入れる）。
 */
@Composable
private fun HistoryListControls(
  state: HistoryState,
  viewModel: HistoryViewModel,
  panelOpen: Boolean,
  onPanelOpenChange: (Boolean) -> Unit,
  trailing: @Composable RowScope.() -> Unit,
  inlinePanel: Boolean = false,
) {
  // 絞り込みの各軸はボタンで開く欄にまとめ、効いている条件だけをチップで出す（→ specs/screens.md）。
  val activeFilters = historyActiveFilters(state = state, viewModel = viewModel)
  Column {
    ListToolRow(
      leading = {
        FilterPanelButton(activeCount = activeFilters.size, expanded = panelOpen, onClick = { onPanelOpenChange(!panelOpen) })
      },
      trailing = trailing,
    )
    // 欄を開いている間はチップの行を出さない（欄に選んでいるものが見えている。選ぶたびに行が出たり消えたりして
    // 欄がずれないように）。「初期状態に戻す」は欄の見出しにある。
    if (!panelOpen) {
      ActiveFilterChips(
        activeFilters,
        onChipClick = { onPanelOpenChange(true) },
        onReset = viewModel::resetListOptions.takeUnless { state.isInitialListOptions },
        modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge),
      )
    }
    if (inlinePanel && panelOpen) {
      Spacer(modifier = Modifier.height(PathlySpacing.sm))
      // 地図を隠しきらないよう高さを抑え、中でスクロールする。
      Box(
        modifier = Modifier
          .padding(horizontal = PathlySpacing.screenEdge)
          .heightIn(max = 360.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        HistoryFilterPanel(state = state, viewModel = viewModel)
      }
    }
  }
}

/** ツール行の右の表示のメニュー: 並べ替えの軸と向き。ボタンには今の見せ方（「日付 ・ 新しい順」）を出す。 */
@Composable
private fun HistoryDisplayMenu(state: HistoryState, viewModel: HistoryViewModel) {
  ListDisplayMenuButton(label = "${state.sort.label} ・ ${state.sort.directionLabel(state.sortDescending)}") { close ->
    ListMenuSectionTitle("並べ替え")
    TrackSort.entries.forEach { sort ->
      ListMenuChoice(
        text = sort.label,
        selected = sort == state.sort,
        onClick = {
          close()
          viewModel.setSort(sort)
        },
      )
    }
    ListMenuSectionTitle("向き", divider = true)
    // 向きは軸に合わせた文言で、既定の向き（日付なら新しい順）を先に置く。
    listOf(state.sort.defaultDescending, !state.sort.defaultDescending).forEach { descending ->
      ListMenuChoice(
        text = state.sort.directionLabel(descending),
        selected = descending == state.sortDescending,
        onClick = {
          close()
          viewModel.setSortDescending(descending)
        },
      )
    }
  }
}

/** 経路一覧の絞り込みの欄（お気に入り・名前・立ち寄り・業種・地域）。 */
@Composable
private fun HistoryFilterPanel(state: HistoryState, viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
  ListFilterPanel(
    resultCount = state.visibleTracks.size,
    // 「すべて外す」は欄の軸だけを外す（名前の検索語は残す）ので、欄の軸が効いているときだけ押せる。
    canClear = state.favoriteFilter != TrackFavoriteFilter.ANY ||
      state.namedFilter != TrackNamedFilter.ANY ||
      state.stopFilter != TrackStopFilter.ANY ||
      state.categoryFilter.isNotEmpty() ||
      state.regionFilter.isNotEmpty(),
    onClearAll = viewModel::clearFilters,
    canReset = !state.isInitialListOptions,
    onReset = viewModel::resetListOptions,
    modifier = modifier,
  ) {
    FilterChoiceSection(
      title = "お気に入り",
      options = TrackFavoriteFilter.entries,
      selected = state.favoriteFilter,
      labelOf = { choiceLabel(it, TrackFavoriteFilter.ANY) { f -> f.chipLabel } },
      onSelect = viewModel::setFavoriteFilter,
    )
    FilterChoiceSection(
      title = "名前",
      options = TrackNamedFilter.entries,
      selected = state.namedFilter,
      labelOf = { choiceLabel(it, TrackNamedFilter.ANY) { f -> f.chipLabel } },
      onSelect = viewModel::setNamedFilter,
    )
    FilterChoiceSection(
      title = "立ち寄り",
      options = TrackStopFilter.entries,
      selected = state.stopFilter,
      labelOf = { choiceLabel(it, TrackStopFilter.ANY) { f -> f.chipLabel } },
      onSelect = viewModel::setStopFilter,
    )
    // 業種は立ち寄った場所の業種。複数選べる（どれかに立ち寄った経路を残す）。
    CategoryFilterSection(
      selected = state.categoryFilter,
      counts = state.categoryCounts,
      onToggle = viewModel::toggleCategoryFilter,
    )
    // 地域は立ち寄った場所の地域。都道府県（海外は国）→ 市区町村（都市）の 2 段。複数選べる。
    RegionFilterSection(
      selected = state.regionFilter,
      counts = state.regionCounts,
      onToggle = viewModel::toggleRegionFilter,
    )
  }
}

/** 経路一覧でいま効いている条件（絞り込みの欄の軸。名前の検索は検索の窓に出ているので含めない）。並びは欄と同じ。 */
private fun historyActiveFilters(state: HistoryState, viewModel: HistoryViewModel): List<ActiveFilter> = listOfNotNull(
  state.favoriteFilter.takeIf { it != TrackFavoriteFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { viewModel.setFavoriteFilter(TrackFavoriteFilter.ANY) },
      onToggle = { viewModel.setFavoriteFilter(flippedChoice(it, TrackFavoriteFilter.FAVORITE, TrackFavoriteFilter.NOT_FAVORITE)) },
    )
  },
  state.namedFilter.takeIf { it != TrackNamedFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { viewModel.setNamedFilter(TrackNamedFilter.ANY) },
      onToggle = { viewModel.setNamedFilter(flippedChoice(it, TrackNamedFilter.NAMED, TrackNamedFilter.UNNAMED)) },
    )
  },
  state.stopFilter.takeIf { it != TrackStopFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { viewModel.setStopFilter(TrackStopFilter.ANY) },
      onToggle = { viewModel.setStopFilter(flippedChoice(it, TrackStopFilter.HAS_STOPS, TrackStopFilter.NO_STOPS)) },
    )
  },
  categoryChipLabel(state.categoryFilter)?.let { ActiveFilter(it, viewModel::clearCategoryFilter) },
  regionChipLabel(state.regionFilter)?.let { ActiveFilter(it, viewModel::clearRegionFilter) },
)

/** 記録中のお出掛け（一覧の先頭の 1 行の帯）。押すと経路詳細へ。 */
@Composable
private fun ActiveTrackBanner(track: GpsTrack, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = PathlySpacing.md, vertical = PathlySpacing.sm + PathlySpacing.xxs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .background(PathlyTheme.statusColors.visited, CircleShape),
      )
      Text(
        text = "記録中 ・ ${DateFormatters.shortTime(track.startTime)} から ・ ${formatKm(track.totalDistanceMeters)}",
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
    }
  }
}

/**
 * 経路の 1 行（影の無い平らな行。下に区切り線）。見出しは名前があれば名前（無ければ日付）、その下に日付・時刻・距離・立ち寄りの数。
 * 右はその場で切り替えるお気に入りと、⋮（名前を付ける）だけ。削除は経路詳細の ⋮ にある。
 */
@Composable
private fun TrackRow(
  track: GpsTrack,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRenameClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .padding(start = PathlySpacing.screenEdge, end = PathlySpacing.xs, top = PathlySpacing.sm, bottom = PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (track.hasName) track.name.orEmpty() else DateFormatters.shortDate(track.startTime),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        // 幅が足りないときは、まとまりごと次の行へ送る（日本語は仮名の間でも折り返すので、1 つの文にすると
        // 「立ち寄|り 3」のように切れる）。
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.xs)) {
          val parts = trackRowSummaryParts(track)
          parts.forEachIndexed { index, part ->
            Text(
              // 区切りは前のまとまりの後ろに付ける（折り返した行が「・」から始まらないように）。
              text = if (index == parts.lastIndex) part else "$part ・",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }

      // お気に入りはその場で切り替える。
      IconButton(onClick = onToggleFavorite) {
        Icon(
          painter = painterResource(if (track.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
          contentDescription = if (track.isFavorite) "お気に入り解除" else "お気に入り",
          tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

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
        }
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.testTag("trackRowDivider"))
  }
}

/** 行の 2 行目のまとまり。「2026/11/08(日) 10:00–15:20」「1.8 km」「立ち寄り 3」。日付が見出しのとき（名前なし）は日付を省く。 */
internal fun trackRowSummaryParts(track: GpsTrack): List<String> {
  val time = DateFormatters.shortTime(track.startTime) + (track.endTime?.let { "–" + DateFormatters.shortTime(it) } ?: "")
  val head = if (track.hasName) "${DateFormatters.shortDate(track.startTime)} $time" else time
  return listOf(head, formatKm(track.totalDistanceMeters), "立ち寄り ${track.stopCount}")
}

/**
 * 統計の帯の文。「12 回 ・ 148.3 km ・ 41 時間 20 分」。絞り込み中は、絞った数字を全体と取り違えないよう
 * 「12 / 38 回」と全体の数を添える（一致していても絞り込み中であることは出す）。
 */
internal fun historyStatsText(tracks: List<GpsTrack>, totalCount: Int, isFiltered: Boolean): String {
  val count = if (isFiltered) "${tracks.size} / $totalCount 回" else "${tracks.size} 回"
  val distanceMeters = tracks.sumOf { it.totalDistanceMeters }
  val minutes = tracks.sumOf { durationMinutesOf(it) }
  return "$count ・ ${formatTotalKm(distanceMeters)} ・ ${formatDuration(minutes)}"
}

/** 合計の距離。100 km 以上は小数を落として 3 桁区切り（「3,815 km」）、それより短ければ小数 1 桁（「4.3 km」）。 */
internal fun formatTotalKm(meters: Double): String {
  val km = meters / 1000.0
  return if (km >= 100) String.format(Locale.ROOT, "%,d km", km.roundToLong()) else String.format(Locale.ROOT, "%.1f km", km)
}

/** 距離を小数 1 桁の km で（「1.8 km」）。 */
internal fun formatKm(meters: Double): String = String.format(Locale.ROOT, "%.1f km", meters / 1000.0)

/** 時間を「41 時間 20 分」「45 分」「3 時間」で。 */
internal fun formatDuration(minutes: Long): String {
  val h = minutes / 60
  val m = minutes % 60
  return when {
    h == 0L -> "$m 分"
    m == 0L -> "$h 時間"
    else -> "$h 時間 $m 分"
  }
}
