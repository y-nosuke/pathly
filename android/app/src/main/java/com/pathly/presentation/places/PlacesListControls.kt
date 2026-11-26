package com.pathly.presentation.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.pathly.R
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.RegisteredPlace
import com.pathly.presentation.common.ActiveFilter
import com.pathly.presentation.common.ActiveFilterChips
import com.pathly.presentation.common.CategoryFilterSection
import com.pathly.presentation.common.ExpandableListStats
import com.pathly.presentation.common.FilterChoiceSection
import com.pathly.presentation.common.FilterPanelButton
import com.pathly.presentation.common.FilterPanelCloseButton
import com.pathly.presentation.common.ListDisplayMenuButton
import com.pathly.presentation.common.ListFilterPanel
import com.pathly.presentation.common.ListGroupHeader
import com.pathly.presentation.common.ListGroupIndent
import com.pathly.presentation.common.ListGroupRow
import com.pathly.presentation.common.ListHeader
import com.pathly.presentation.common.ListMapCamera
import com.pathly.presentation.common.ListMapLayout
import com.pathly.presentation.common.ListMapMode
import com.pathly.presentation.common.ListMapOverlay
import com.pathly.presentation.common.ListMapToggle
import com.pathly.presentation.common.ListMenuChoice
import com.pathly.presentation.common.ListMenuSectionTitle
import com.pathly.presentation.common.ListToolRow
import com.pathly.presentation.common.MapControls
import com.pathly.presentation.common.MapRoundButton
import com.pathly.presentation.common.MapRoundButtonSize
import com.pathly.presentation.common.RegionBoundaries
import com.pathly.presentation.common.RegionFilterSection
import com.pathly.presentation.common.RegionMarkerChip
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.presentation.common.ScrollToTopButton
import com.pathly.presentation.common.ScrollToTopButtonClearance
import com.pathly.presentation.common.WishlistFlagButton
import com.pathly.presentation.common.boundaryMapColorScheme
import com.pathly.presentation.common.boundaryMapOptions
import com.pathly.presentation.common.categoryChipLabel
import com.pathly.presentation.common.choiceLabel
import com.pathly.presentation.common.flippedChoice
import com.pathly.presentation.common.listMapSummary
import com.pathly.presentation.common.regionChipLabel
import com.pathly.presentation.common.rememberListMapCamera
import com.pathly.presentation.common.rememberLocationPermitted
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.requestCurrentLatLng
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme
import com.pathly.util.DateFormatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ツール行（左に絞り込みのボタン、右に [trailing]）と、効いている条件のチップ。一覧表示と地図表示で同じつなぎ方。
 *
 * @param inlinePanel 絞り込みの欄をここに出すとき（地図表示）の欄。一覧表示では null（欄は一覧の先頭に入れる）。
 */
@Composable
internal fun PlacesListControls(
  activeFilters: List<ActiveFilter>,
  panelOpen: Boolean,
  onPanelOpenChange: (Boolean) -> Unit,
  onReset: (() -> Unit)?,
  trailing: @Composable RowScope.() -> Unit,
  inlinePanel: (@Composable (Modifier) -> Unit)?,
) {
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
        onReset = onReset,
        modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge),
      )
    }
    if (inlinePanel != null && panelOpen) {
      Spacer(modifier = Modifier.height(PathlySpacing.sm))
      // 地図を隠しきらないよう高さを抑え、中でスクロールする。
      Box(
        modifier = Modifier
          .padding(horizontal = PathlySpacing.screenEdge)
          .heightIn(max = 360.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        inlinePanel(Modifier)
      }
    }
  }
}

/**
 * ツール行の右の表示のメニュー: 並べ替えの軸と向き・分け方。ボタンには今の見せ方（「登録順 ・ 新しい順」、
 * 分けているときは「… ・ 地域」）を出す。
 */
@Composable
internal fun PlacesDisplayMenu(
  state: PlacesState,
  onSortChange: (PlaceSort) -> Unit,
  onSortDescendingChange: (Boolean) -> Unit,
  onGroupingChange: (PlaceGrouping) -> Unit,
) {
  val label = buildString {
    append("${state.sort.label} ・ ${state.sort.directionLabel(state.sortDescending)}")
    state.grouping.shortLabel?.let { append(" ・ $it") }
  }
  ListDisplayMenuButton(label = label) { close ->
    ListMenuSectionTitle("並べ替え")
    PlaceSort.entries.forEach { sort ->
      ListMenuChoice(
        text = sort.label,
        selected = sort == state.sort,
        onClick = {
          close()
          onSortChange(sort)
        },
      )
    }
    ListMenuSectionTitle("向き", divider = true)
    // 向きは軸に合わせた文言で、既定の向き（登録順なら新しい順）を先に置く。
    listOf(state.sort.defaultDescending, !state.sort.defaultDescending).forEach { descending ->
      ListMenuChoice(
        text = state.sort.directionLabel(descending),
        selected = descending == state.sortDescending,
        onClick = {
          close()
          onSortDescendingChange(descending)
        },
      )
    }
    ListMenuSectionTitle("分け方", divider = true)
    PlaceGrouping.entries.forEach { grouping ->
      ListMenuChoice(
        text = grouping.label,
        selected = grouping == state.grouping,
        onClick = {
          close()
          onGroupingChange(grouping)
        },
      )
    }
  }
}

/** 表示のメニューのボタンに添える分け方の短い名前。分けていなければ null。 */
private val PlaceGrouping.shortLabel: String?
  get() = when (this) {
    PlaceGrouping.NONE -> null
    PlaceGrouping.REGION -> "地域"
    PlaceGrouping.CATEGORY -> "業種"
  }

/** 場所一覧の絞り込みの欄（行きたい・訪問・業種・地域）。 */
@Composable
internal fun PlacesFilterPanel(
  state: PlacesState,
  onClearFilters: () -> Unit,
  onResetListOptions: () -> Unit,
  onWishlistFilterChange: (WishlistFilter) -> Unit,
  onVisitedFilterChange: (VisitedFilter) -> Unit,
  onToggleCategoryFilter: (PlaceCategoryFacet) -> Unit,
  onToggleRegionFilter: (RegionKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  ListFilterPanel(
    resultCount = state.visibleItems.size,
    // 「すべて外す」は欄の軸だけを外す（名前の検索語は残す）ので、欄の軸が効いているときだけ押せる。
    canClear = state.wishlistFilter != WishlistFilter.ANY ||
      state.visitedFilter != VisitedFilter.ANY ||
      state.categoryFilter.isNotEmpty() ||
      state.regionFilter.isNotEmpty(),
    onClearAll = onClearFilters,
    canReset = !state.isInitialListOptions,
    onReset = onResetListOptions,
    modifier = modifier,
  ) {
    FilterChoiceSection(
      title = "行きたい",
      options = WishlistFilter.entries,
      selected = state.wishlistFilter,
      labelOf = { choiceLabel(it, WishlistFilter.ANY) { f -> f.chipLabel } },
      onSelect = onWishlistFilterChange,
    )
    FilterChoiceSection(
      title = "訪問",
      options = VisitedFilter.entries,
      selected = state.visitedFilter,
      labelOf = { choiceLabel(it, VisitedFilter.ANY) { f -> f.chipLabel } },
      onSelect = onVisitedFilterChange,
    )
    // 業種は複数選べる（選んだどれかに当たる場所を残す）。
    CategoryFilterSection(
      selected = state.categoryFilter,
      counts = state.categoryCounts,
      onToggle = onToggleCategoryFilter,
    )
    // 地域は都道府県（海外は国）→ 市区町村（都市）の 2 段。複数選べる。
    RegionFilterSection(
      selected = state.regionFilter,
      counts = state.regionCounts,
      onToggle = onToggleRegionFilter,
    )
  }
}

/** 場所一覧でいま効いている条件（絞り込みの欄の軸。名前の検索は検索の窓に出ているので含めない）。並びは欄と同じ。 */
internal fun placesActiveFilters(
  state: PlacesState,
  onWishlistFilterChange: (WishlistFilter) -> Unit,
  onVisitedFilterChange: (VisitedFilter) -> Unit,
  onClearCategoryFilter: () -> Unit,
  onClearRegionFilter: () -> Unit,
): List<ActiveFilter> = listOfNotNull(
  state.wishlistFilter.takeIf { it != WishlistFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { onWishlistFilterChange(WishlistFilter.ANY) },
      onToggle = { onWishlistFilterChange(flippedChoice(it, WishlistFilter.WISHLISTED, WishlistFilter.NOT_WISHLISTED)) },
    )
  },
  state.visitedFilter.takeIf { it != VisitedFilter.ANY }?.let {
    ActiveFilter(
      label = it.chipLabel,
      onRemove = { onVisitedFilterChange(VisitedFilter.ANY) },
      onToggle = { onVisitedFilterChange(flippedChoice(it, VisitedFilter.VISITED, VisitedFilter.UNVISITED)) },
    )
  },
  categoryChipLabel(state.categoryFilter)?.let { ActiveFilter(it, onClearCategoryFilter) },
  regionChipLabel(state.regionFilter)?.let { ActiveFilter(it, onClearRegionFilter) },
)
