package com.pathly.presentation.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 登録済みの場所を選ぶ（計画の「場所を足す」→ docs/specs/plans.md）。場所タブの一覧・地図を選ぶモードで出す。
 * 並べ替え・向き・分け方・絞り込みは場所タブと共有する（[PlaceListOptionsHub]）。選んだ順に [onAdd] へ渡す。
 */
@Composable
fun PlacePickerRoute(
  viewModel: PlacesViewModel,
  destination: String,
  onAdd: (placeIds: List<Long>) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val statsExpanded by viewModel.statsExpanded.collectAsStateWithLifecycle()
  // 選んだ順を保つ（足す順になる）。
  var selected by remember { mutableStateOf(listOf<Long>()) }
  BackHandler(onBack = onClose)

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    PlacesListContent(
      state = uiState,
      onAddByMap = {},
      onAddBySearch = {},
      onClearFilters = viewModel::clearFilters,
      onResetListOptions = viewModel::resetListOptions,
      onNameQueryChange = viewModel::setNameQuery,
      onWishlistFilterChange = viewModel::setWishlistFilter,
      onVisitedFilterChange = viewModel::setVisitedFilter,
      onToggleCategoryFilter = viewModel::toggleCategoryFilter,
      onClearCategoryFilter = viewModel::clearCategoryFilter,
      onToggleRegionFilter = viewModel::toggleRegionFilter,
      onClearRegionFilter = viewModel::clearRegionFilter,
      onSortChange = viewModel::setSort,
      onSortDescendingChange = viewModel::setSortDescending,
      onGroupingChange = viewModel::setGrouping,
      onToggleGroup = viewModel::toggleGroup,
      onViewModeChange = viewModel::setViewMode,
      onControlsCollapsedChange = viewModel::setControlsCollapsed,
      onItemClick = {},
      onMapPlaceClick = {},
      onToggleWishlist = viewModel::toggleWishlist,
      statsExpanded = statsExpanded,
      onStatsExpandedChange = viewModel::setStatsExpanded,
      onFetchRegion = viewModel::findRegion,
      selection = PlaceSelection(
        selectedIds = selected.toSet(),
        onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
        destination = destination,
        onConfirm = {
          onAdd(selected)
          onClose()
        },
        onCancel = onClose,
      ),
    )
  }
}
