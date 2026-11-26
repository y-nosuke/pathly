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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.TrackGrouping
import com.pathly.presentation.common.ActiveFilter
import com.pathly.presentation.common.ActiveFilterChips
import com.pathly.presentation.common.CategoryFilterSection
import com.pathly.presentation.common.ExpandableListStats
import com.pathly.presentation.common.FilterChoiceSection
import com.pathly.presentation.common.FilterPanelButton
import com.pathly.presentation.common.FilterPanelCloseButton
import com.pathly.presentation.common.ListDisplayMenuButton
import com.pathly.presentation.common.ListFilterPanel
import com.pathly.presentation.common.ListGroup
import com.pathly.presentation.common.ListGroupHeader
import com.pathly.presentation.common.ListGroupIndent
import com.pathly.presentation.common.ListGroupRow
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
import com.pathly.presentation.common.StatsBarItem
import com.pathly.presentation.common.StatsTileTone
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
      defaultName = DateFormatters.date(target.startTime),
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
        // 絞り込み・並べ替え・まとめを状態が変わったときだけ計算し直す（描き直しのたびにやらない）。
        val visibleTracks = remember(uiState) { uiState.visibleTracks }
        val rows = remember(visibleTracks, uiState) { uiState.listRowsOf(visibleTracks) }
        val listState = rememberLazyListState()
        // 分け方を変えたら先頭へ戻す（見出しの並びに変わるので、見ていた位置に意味が無くなる）。開閉では動かさない。
        // 詳細から戻って一覧を出し直したときは動かさないよう、最後に見た分け方を画面の状態として取っておく。
        var scrolledForGrouping by rememberSaveable { mutableStateOf(uiState.grouping) }
        LaunchedEffect(uiState.grouping) {
          if (scrolledForGrouping != uiState.grouping) listState.scrollToItem(0)
          scrolledForGrouping = uiState.grouping
        }
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
                barItems = historyStatsBarItems(tracks = visibleTracks, totalCount = uiState.tracks.size, isFiltered = isFiltered),
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

            // 分けているときは、年月の見出しと開いたまとまりの中の経路（段ごとに字下げ）。
            // 名前の検索中はまとまりをすべて開いて見せるので、見出しを押しても開閉しない。
            val searching = uiState.nameQuery.isNotBlank()
            items(rows, key = { it.listKey }, contentType = { it::class }) { row ->
              val indent = ListGroupIndent * row.depth
              when (row) {
                is ListGroupRow.Header -> {
                  val summary = remember(row.group) { trackGroupSummary(row.group) }
                  ListGroupHeader(
                    label = row.group.label,
                    trailing = summary,
                    depth = row.depth,
                    expanded = row.expanded,
                    indent = indent,
                    testTag = "trackGroup:${row.group.key}",
                    onClick = if (searching) null else ({ viewModel.toggleGroup(row.group.key) }),
                  )
                }

                is ListGroupRow.Item -> {
                  val track = row.item
                  TrackRow(
                    track = track,
                    indent = indent,
                    onClick = { onTrackClick(track) },
                    onToggleFavorite = { viewModel.toggleFavorite(track) },
                    onRenameClick = { onRenameClick(track) },
                  )
                }
              }
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
