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
 * 見出しの下のツール行（左に絞り込みのボタン、右に [trailing]）と、効いている条件のチップ。一覧表示と地図表示で同じつなぎ方。
 *
 * @param inlinePanel 絞り込みの欄をここに出すか（地図表示。一覧表示では欄を一覧の先頭に入れる）。
 */
@Composable
internal fun HistoryListControls(
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

/**
 * ツール行の右の表示のメニュー: 並べ替えの軸と向き・分け方。ボタンには今の見せ方（「日付 ・ 新しい順」、
 * 分けているときは「… ・ 年月」）を出す。
 */
@Composable
internal fun HistoryDisplayMenu(state: HistoryState, viewModel: HistoryViewModel) {
  val label = buildString {
    append("${state.sort.label} ・ ${state.sort.directionLabel(state.sortDescending)}")
    if (state.grouping == TrackGrouping.MONTH) append(" ・ 年月")
  }
  ListDisplayMenuButton(label = label) { close ->
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
    ListMenuSectionTitle("分け方", divider = true)
    TrackGrouping.entries.forEach { grouping ->
      ListMenuChoice(
        text = grouping.label,
        selected = grouping == state.grouping,
        onClick = {
          close()
          viewModel.setGrouping(grouping)
        },
      )
    }
  }
}

/** 経路一覧の絞り込みの欄（お気に入り・名前・立ち寄り・業種・地域）。 */
@Composable
internal fun HistoryFilterPanel(state: HistoryState, viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
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
