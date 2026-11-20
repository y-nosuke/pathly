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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
import com.pathly.presentation.common.RegionFilterSection
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.presentation.common.ScrollToTopButton
import com.pathly.presentation.common.ScrollToTopButtonClearance
import com.pathly.presentation.common.WishlistFlagButton
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
import kotlinx.coroutines.launch

// 「場所」タブの一覧表示と地図表示。一覧の画面の型（見出し＋ツール行 → docs/designs/ui-direction.md）に載せ、
// 地図表示ではその見出しとツール行を地図に重ねる（履歴タブと同じ）。追加は右下の丸（両方の表示で同じ位置）。

@Composable
internal fun PlacesListContent(
  state: PlacesState,
  onAddByMap: () -> Unit,
  onAddBySearch: () -> Unit,
  onClearFilters: () -> Unit,
  onResetListOptions: () -> Unit,
  onNameQueryChange: (String) -> Unit,
  onWishlistFilterChange: (WishlistFilter) -> Unit,
  onVisitedFilterChange: (VisitedFilter) -> Unit,
  onToggleCategoryFilter: (PlaceCategoryFacet) -> Unit,
  onClearCategoryFilter: () -> Unit,
  onToggleRegionFilter: (RegionKey) -> Unit,
  onClearRegionFilter: () -> Unit,
  onSortChange: (PlaceSort) -> Unit,
  onSortDescendingChange: (Boolean) -> Unit,
  onGroupingChange: (PlaceGrouping) -> Unit,
  // まとまりの見出しを押したとき（[PlaceGroup.key]）。開く／閉じる。
  onToggleGroup: (String) -> Unit,
  onViewModeChange: (ListMapMode) -> Unit,
  // 地図表示で、地図に重ねた見出し・ツール行をたたむ（true）／ひろげる（false）。
  onControlsCollapsedChange: (Boolean) -> Unit,
  onItemClick: (PlaceListItem) -> Unit,
  // 地図表示のマーカーのタップ（場所の id）。一覧の行と同じく場所詳細へ進む。
  onMapPlaceClick: (Long) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
  // 統計の帯を統計のカードに広げているか・広げる／畳む。
  statsExpanded: Boolean,
  onStatsExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  // 地図表示の地図スロット。null（既定）は実マップ（GoogleMap）を描画する。
  // テストは空スロット（{}）を渡し、GMS 依存の地図描画を避けて切り替え・文言だけを検証する。
  mapContent: (@Composable () -> Unit)? = null,
) {
  val isMap = state.viewMode == ListMapMode.MAP
  // 地図表示はタブの中の一段奥の見方として扱い、システムバックで一覧表示へ戻す（タブは離れない）。
  // 吹き出しが出ているときは、後から登録される地図側の処理が先に吹き出しを閉じる。
  BackHandler(enabled = isMap) { onViewModeChange(ListMapMode.LIST) }

  // 地図表示の範囲は、一覧表示に切り替えても捨てない。地図に戻ったら前に見ていた範囲のまま出す
  // （その間に絞り込みの結果が変わっていれば、戻ったときに合わせ直す）。
  val mapCamera = rememberListMapCamera()

  // 絞り込みの欄を開いているか。一覧表示と地図表示で共有する（切り替えても開いたまま）。
  var filterPanelOpen by rememberSaveable { mutableStateOf(false) }
  // 欄を開いているときの戻るは、欄を閉じる（地図表示から一覧へ戻すより先。吹き出しを閉じるのはさらに先）。
  // 一覧表示の読み込み中・場所が 1 件も無いとき（ツール行を出さない）や、地図表示で重ねたものをたたんでいるときは
  // 欄が見えないので、開いたままでも出ていない扱いにする（戻る・閉じるボタン・ボタンの ▴ を欄の見え方に揃える）。
  val panelVisible = filterPanelOpen &&
    state.items.isNotEmpty() &&
    if (isMap) !state.controlsCollapsed else !state.isLoading
  BackHandler(enabled = panelVisible) { filterPanelOpen = false }

  // 見出しの検索の窓を開いているか。一覧表示と地図表示で同じ見出しを出すので、ここで持って揃える。
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  val header: @Composable () -> Unit = {
    ListHeader(
      title = "場所",
      query = state.nameQuery,
      onQueryChange = onNameQueryChange,
      searchPlaceholder = "名前で検索",
      searchOpen = searchOpen,
      onSearchOpenChange = { searchOpen = it },
    ) {
      ListMapToggle(mode = state.viewMode, onChange = onViewModeChange)
    }
  }

  val activeFilters = placesActiveFilters(
    state = state,
    onWishlistFilterChange = onWishlistFilterChange,
    onVisitedFilterChange = onVisitedFilterChange,
    onClearCategoryFilter = onClearCategoryFilter,
    onClearRegionFilter = onClearRegionFilter,
  )
  val filterPanel: @Composable (Modifier) -> Unit = { panelModifier ->
    PlacesFilterPanel(
      state = state,
      onClearFilters = onClearFilters,
      onResetListOptions = onResetListOptions,
      onWishlistFilterChange = onWishlistFilterChange,
      onVisitedFilterChange = onVisitedFilterChange,
      onToggleCategoryFilter = onToggleCategoryFilter,
      onToggleRegionFilter = onToggleRegionFilter,
      modifier = panelModifier,
    )
  }
  // ツール行と効いている条件のチップ。[inlinePanel] は地図表示（欄をここに出す。一覧表示では一覧の先頭に入れる）。
  val listControls: @Composable (inlinePanel: Boolean, trailing: @Composable RowScope.() -> Unit) -> Unit = { inlinePanel, trailing ->
    PlacesListControls(
      activeFilters = activeFilters,
      panelOpen = panelVisible,
      onPanelOpenChange = { filterPanelOpen = it },
      onReset = onResetListOptions.takeUnless { state.isInitialListOptions },
      trailing = trailing,
      inlinePanel = if (inlinePanel) filterPanel else null,
    )
  }

  Box(modifier = modifier.fillMaxSize()) {
    ListMapLayout(
      mode = state.viewMode,
      map = { visible ->
        PlacesMapLayout(
          state = state,
          visible = visible,
          camera = mapCamera,
          header = header,
          // 地図表示では並びに意味が無いので、ツール行の右（表示のメニュー）は出さない。マーカーが場所そのものなので、
          // 履歴の地図のようなレイヤー［≋］（登録済みの場所の表示）も置かない。
          controls = { if (state.items.isNotEmpty()) listControls(true) {} },
          onViewModeChange = onViewModeChange,
          onControlsCollapsedChange = onControlsCollapsedChange,
          onPlaceClick = onMapPlaceClick,
          mapContent = mapContent,
        )
      },
    ) {
      PlacesListBody(
        state = state,
        panelVisible = panelVisible,
        header = header,
        controls = {
          listControls(false) {
            PlacesDisplayMenu(
              state = state,
              onSortChange = onSortChange,
              onSortDescendingChange = onSortDescendingChange,
              onGroupingChange = onGroupingChange,
            )
          }
        },
        filterPanel = filterPanel,
        stats = { statsModifier ->
          val isFiltered = !state.noFilter
          ExpandableListStats(
            barItems = placesStatsBarItems(state.statistics, totalCount = state.items.size, isFiltered = isFiltered),
            expanded = statsExpanded,
            onExpandedChange = onStatsExpandedChange,
            isFiltered = isFiltered,
            modifier = statsModifier,
          ) {
            // 絞り込みを数え直すので、状態が変わったときだけ計算する。
            val statistics = remember(state) { state.statistics }
            val details = remember(state) { state.statsDetails }
            PlacesStatsCardContent(
              statistics = statistics,
              details = details,
              totalCount = state.items.size,
              isFiltered = isFiltered,
            )
          }
        },
        onToggleGroup = onToggleGroup,
        onItemClick = onItemClick,
        onToggleWishlist = onToggleWishlist,
      )
    }

    // 右下: 追加（一覧の主役）。一覧表示・地図表示で同じ位置に置き、切り替えても動かないようにする。
    PlaceAddButton(
      onAddBySearch = onAddBySearch,
      onAddByMap = onAddByMap,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = PathlySpacing.screenEdge, bottom = PathlySpacing.screenEdge),
    )
    // 欄を開いている間は、親指の届く下にも閉じるボタンを浮かべる（一覧表示・地図表示とも）。
    if (panelVisible) {
      FilterPanelCloseButton(
        onClick = { filterPanelOpen = false },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = PathlySpacing.sm),
      )
    }
  }
}

/** 一覧表示（見出し・ツール行・統計の帯・場所の一覧）。 */
@Composable
private fun PlacesListBody(
  state: PlacesState,
  panelVisible: Boolean,
  header: @Composable () -> Unit,
  controls: @Composable () -> Unit,
  filterPanel: @Composable (Modifier) -> Unit,
  stats: @Composable (Modifier) -> Unit,
  onToggleGroup: (String) -> Unit,
  onItemClick: (PlaceListItem) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    header()
    // 場所が 1 件でもあればツール行を出す（何も無いときは絞り込む・並べるものが無い）。
    if (state.items.isNotEmpty()) controls()
    Spacer(modifier = Modifier.height(PathlySpacing.sm))

    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      PlacesLazyList(
        state = state,
        panelVisible = panelVisible,
        filterPanel = filterPanel,
        stats = stats,
        onToggleGroup = onToggleGroup,
        onItemClick = onItemClick,
        onToggleWishlist = onToggleWishlist,
      )
    }
  }
}

/** 場所の一覧（先頭に絞り込みの欄か統計の帯、続いてまとまりの見出しと行）。右下に先頭へ戻るボタンを重ねる。 */
@Composable
private fun PlacesLazyList(
  state: PlacesState,
  panelVisible: Boolean,
  filterPanel: @Composable (Modifier) -> Unit,
  stats: @Composable (Modifier) -> Unit,
  onToggleGroup: (String) -> Unit,
  onItemClick: (PlaceListItem) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
) {
  val listState = rememberLazyListState()
  // 絞り込み・並べ替え・まとめを状態が変わったときだけ計算し直す（描き直しのたびにやらない）。
  val rows = remember(state) { state.listRows }
  // 絞り込み・並べ替えを変えたとき、または先頭が変わったとき（＝追加時）に先頭へ戻す。
  // 詳細から戻って一覧を出し直したときは戻さない（前に見ていた位置のまま）。そのため、最後に
  // 先頭へ戻したときの条件を画面の状態として取っておき、条件が変わったときだけ動かす。
  val scrollKey = state.scrollResetKey
  var scrolledFor by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(scrollKey) {
    if (scrolledFor != null && scrolledFor != scrollKey) listState.scrollToItem(0)
    scrolledFor = scrollKey
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
      // 最後の行が右下の追加と先頭へ戻るボタンの上まで上がるよう、下に余白を足す（行の旗が隠れないように）。
      contentPadding = PaddingValues(bottom = PlaceAddButtonClearance + ScrollToTopButtonClearance),
    ) {
      // 絞り込みの欄は一覧の先頭に入れ、一覧と一緒にスクロールする（選びながら下の結果を見られる）。
      if (panelVisible) {
        item(key = "filterPanel") {
          filterPanel(
            Modifier.padding(start = PathlySpacing.screenEdge, end = PathlySpacing.screenEdge, bottom = PathlySpacing.sm),
          )
        }
      } else if (state.items.isNotEmpty()) {
        // 統計は一覧と同じく絞り込み後の場所で数える。絞り込みの欄を開いている間は、欄と結果の間に挟まって
        // 邪魔になるので出さない。場所が 1 件も無いときは案内の文言だけにする。
        // 帯を押すと、その場で統計のカードに広がる（履歴と同じ）。
        item(key = "statistics") {
          stats(Modifier.padding(bottom = PathlySpacing.sm))
        }
      }
      if (state.visibleItems.isEmpty()) {
        item(key = "empty") {
          Text(
            text = state.emptyMessage(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.xxl),
          )
        }
      } else {
        item(key = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
      }
      // 分けているときは、まとまりの見出しと開いたまとまりの中の場所（段ごとに字下げ）。
      // 名前の検索中はまとまりをすべて開いて見せるので、見出しを押しても開閉しない。
      val groupToggle = onToggleGroup.takeIf { state.nameQuery.isBlank() }
      items(
        rows,
        key = { it.listKey },
        contentType = { it::class },
      ) { row ->
        val indent = GroupIndent * row.depth
        when (row) {
          is PlacesListRow.Header -> PlaceGroupHeader(
            header = row,
            indent = indent,
            onClick = groupToggle?.let { toggle -> { toggle(row.group.key) } },
          )

          is PlacesListRow.Item -> PlaceItemRow(
            item = row.item,
            sort = state.sort,
            indent = indent,
            onClick = { onItemClick(row.item) },
            onToggleWishlist = { onToggleWishlist(row.item) },
          )
        }
      }
    }
    // 先頭へ戻るは右下の追加の上に置く。
    ScrollToTopButton(
      listState = listState,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = PathlySpacing.screenEdge + PathlySpacing.sm, bottom = PlaceAddButtonClearance),
    )
  }
}

/** まとまりの段ごとの字下げ。 */
private val GroupIndent = PathlySpacing.lg

/** 右下の追加の丸の大きさ。 */
private val PlaceAddButtonSize = 56.dp

/** 右下の追加の丸が下端から占める高さ（丸と下の余白）に、上に置くものとの間を足したもの。 */
internal val PlaceAddButtonClearance = PlaceAddButtonSize + PathlySpacing.screenEdge + PathlySpacing.sm

/**
 * 右下の追加の丸。押すと「検索して追加」「地図で選ぶ」のメニューが開く。
 * Material 3 Expressive を入れるとき（#201）、FAB のメニューに差し替える場所（→ ADR 0041）。
 */
@Composable
private fun PlaceAddButton(onAddBySearch: () -> Unit, onAddByMap: () -> Unit, modifier: Modifier = Modifier) {
  Box(modifier = modifier) {
    var open by remember { mutableStateOf(false) }
    FloatingActionButton(
      onClick = { open = true },
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = PathlyElevation.floating),
      modifier = Modifier.testTag("placeAddButton"),
    ) {
      Icon(painter = painterResource(R.drawable.ic_add), contentDescription = "場所を追加")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text("検索して追加") },
        onClick = {
          open = false
          onAddBySearch()
        },
      )
      DropdownMenuItem(
        text = { Text("地図で選ぶ") },
        onClick = {
          open = false
          onAddByMap()
        },
      )
    }
  }
}

/**
 * ツール行（左に絞り込みのボタン、右に [trailing]）と、効いている条件のチップ。一覧表示と地図表示で同じつなぎ方。
 *
 * @param inlinePanel 絞り込みの欄をここに出すとき（地図表示）の欄。一覧表示では null（欄は一覧の先頭に入れる）。
 */
@Composable
private fun PlacesListControls(
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
private fun PlacesDisplayMenu(
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

/** まとまりの見出し（開閉の印・名前・件数）。[onClick] が null なら（名前の検索中）押しても開閉しない。 */
@Composable
private fun PlaceGroupHeader(header: PlacesListRow.Header, indent: Dp, onClick: (() -> Unit)?) {
  val group = header.group
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("placeGroup:${group.key}")
        .then(
          if (onClick != null) {
            Modifier.clickable(onClickLabel = if (header.expanded) "閉じる" else "開く", onClick = onClick)
          } else {
            Modifier
          },
        )
        .semantics { stateDescription = if (header.expanded) "開いている" else "閉じている" }
        .padding(start = PathlySpacing.screenEdge + indent, end = PathlySpacing.screenEdge, top = PathlySpacing.md, bottom = PathlySpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // 開いていれば ⌄、閉じていれば ›（同じ絵を回す）。
      Icon(
        painter = painterResource(R.drawable.ic_expand_more),
        contentDescription = null,
        modifier = Modifier.rotate(if (header.expanded) 0f else -90f),
      )
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = group.label,
        style = if (header.depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "${group.count}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/**
 * 場所の 1 行（影の無い平らな行。下に区切り線。履歴の行と同じ形）。名前 → 業種 → バッジ → 住所 → 並びの日付。
 * 右はその場で切り替える行きたいの旗だけ。削除は場所詳細の ⋮ にある。
 */
@Composable
private fun PlaceItemRow(
  item: PlaceListItem,
  sort: PlaceSort,
  indent: Dp,
  onClick: () -> Unit,
  onToggleWishlist: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .padding(start = PathlySpacing.screenEdge + indent, end = PathlySpacing.xs, top = PathlySpacing.sm, bottom = PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.displayName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        // カテゴリ（業種）。あれば名前の下に小さく出す（「どんな場所か」の手がかり）。
        // 業種・住所・日付は脇役なので、太さの付かない bodySmall の灰色にそろえる（名前と状態の色だけが目立つように）。
        item.place.category?.let { category ->
          Text(
            text = category.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // バッジ行（行きたい／訪問済み／行きたいで行った回数）。無いときは出さない。
        if (item.isWishlisted || item.isVisited || item.fulfilledWishCount > 0) {
          Row(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.md)) {
            if (item.isWishlisted) {
              Text(
                text = "行きたい ${priorityStars(item.priority ?: Priority.MEDIUM)}",
                style = MaterialTheme.typography.labelMedium,
                color = PathlyTheme.statusColors.wishlist,
              )
            }
            if (item.isVisited) {
              Text(
                text = if (item.visitCount > 0) "✓ 訪問${item.visitCount}回" else "✓ 訪問済み",
                style = MaterialTheme.typography.labelMedium,
                color = PathlyTheme.statusColors.visited,
              )
            }
            if (item.fulfilledWishCount > 0) {
              Text(
                text = "行きたいで行った ${item.fulfilledWishCount}回",
                style = MaterialTheme.typography.labelMedium,
                color = PathlyTheme.statusColors.visited,
              )
            }
          }
        }

        // 住所（1行）。メモは詳細で見る。
        item.place.googleAddress?.takeIf { it.isNotBlank() }?.let { address ->
          Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // 現在の並び順に対応する日付だけを小さく出す（何で並んでいるか分かる・常時全部は出さない）。
        val sortDate: String? = when (sort) {
          PlaceSort.REGISTERED -> "登録 " + DateFormatters.shortDate(item.place.createdAt)

          PlaceSort.UPDATED -> "更新 " + DateFormatters.shortDate(item.place.updatedAt)

          PlaceSort.VISITED -> item.visitRecencyAt?.let { "訪問 " + DateFormatters.shortDate(it) }

          // 回数はバッジ（「✓ 訪問N回」「行きたいで行った N回」）で分かるので追加表示しない。
          PlaceSort.VISIT_COUNT, PlaceSort.FULFILLED_WISH_COUNT, PlaceSort.PRIORITY, PlaceSort.NAME -> null
        }
        sortDate?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      WishlistFlagButton(flag = item.wishlistFlag, onClick = onToggleWishlist)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/** 場所一覧の絞り込みの欄（行きたい・訪問・業種・地域）。 */
@Composable
private fun PlacesFilterPanel(
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
private fun placesActiveFilters(
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

// 一覧・地図で 0 件のときの文言。場所はあるのに絞り込みで消えたときは、追加を促さず条件のせいだと分かる文言にする。
private fun PlacesState.emptyMessage(): String = if (items.isEmpty()) "場所がありません\n右下の ＋ から登録できます" else "条件に合う場所がありません"

/** 地図に「登録済みの場所」として出すための形。 */
internal fun PlaceListItem.toRegisteredPlace(): RegisteredPlace = RegisteredPlace(
  placeId = place.id,
  name = displayName,
  latitude = place.latitude,
  longitude = place.longitude,
  isWishlisted = isWishlisted,
  isVisited = isVisited,
  fulfilledWishCount = fulfilledWishCount,
  // 業種のグリフと、地図の絞り込みの業種（未分類／その他の分け方）に使う。
  categoryCode = place.category?.code,
  hasGoogleInfo = place.googlePlaceId != null,
)

private fun priorityStars(priority: Priority): String = when (priority) {
  Priority.HIGH -> "★★★"
  Priority.MEDIUM -> "★★☆"
  Priority.LOW -> "★☆☆"
}

/**
 * 場所タブの地図表示。地図をタブいっぱいに広げ、一覧と同じ見出しとツール行を上に重ねる（たためる）。
 * 右下は地図の操作（絞り込んだ場所をすべて見る・現在地）で、その下の追加の丸（[PlacesListContent] が置く）の上に積む。
 *
 * @param visible false のときは一覧表示の裏に地図だけを残している（重ねるもの・右下の操作は出さない）。
 */
@Composable
private fun PlacesMapLayout(
  state: PlacesState,
  visible: Boolean,
  camera: ListMapCamera,
  header: @Composable () -> Unit,
  controls: @Composable () -> Unit,
  onViewModeChange: (ListMapMode) -> Unit,
  onControlsCollapsedChange: (Boolean) -> Unit,
  onPlaceClick: (Long) -> Unit,
  modifier: Modifier = Modifier,
  mapContent: (@Composable () -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // 重ねたものの高さ。地図の上の余白にして、マーカー・範囲合わせ・0 件の文言が隠れないようにする。
  var overlayHeight by remember { mutableStateOf(0.dp) }
  // 位置情報の許可があるときだけ、地図に現在地を出し、右下に現在地のボタンを置く（この画面からは許可を求めない）。
  val hasLocationPermission = rememberLocationPermitted()
  val boundsPaddingPx = with(LocalDensity.current) { PlacesMapBoundsPadding.roundToPx() }

  Box(modifier = modifier.fillMaxSize()) {
    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      // 0 件でも地図を出し、文言を上に重ねる。
      PlacesMapView(
        items = state.visibleItems,
        emptyMessage = if (visible && state.visibleItems.isEmpty()) state.emptyMessage() else null,
        onPlaceClick = onPlaceClick,
        visible = visible,
        camera = camera,
        showMyLocation = hasLocationPermission,
        // 下は右下の追加・現在地の丸の分。範囲を合わせたマーカーが丸の下に隠れないように。
        contentPadding = PaddingValues(top = overlayHeight, bottom = PlaceAddButtonClearance),
        mapContent = mapContent,
      )
    }

    // 一覧表示の裏にいる間は重ねない。高さは前に測った値のままにし、戻ったときに地図の余白が変わって
    // 範囲がずれないようにする。
    if (visible) {
      ListMapOverlay(
        mode = state.viewMode,
        onModeChange = onViewModeChange,
        collapsed = state.controlsCollapsed,
        onCollapsedChange = onControlsCollapsedChange,
        collapsedSummary = listMapSummary("場所", state.activeFilterCount, state.visibleItems.size, state.items.size),
        onHeightChange = { overlayHeight = it },
        header = header,
        controls = controls,
        modifier = Modifier.align(Alignment.TopCenter),
      )

      // 右下: 地図の操作（絞り込んだ場所をすべて見る・現在地）。下の追加の丸の上に積む。
      MapControls(
        bottomInset = PlaceAddButtonClearance - PathlySpacing.mapEdge,
        // 下の追加の丸（56dp・右の余白 16dp）と縦の中心を揃える（丸ボタンは 48dp・右の余白は mapEdge）。
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = PathlySpacing.screenEdge + (PlaceAddButtonSize - MapRoundButtonSize) / 2 - PathlySpacing.mapEdge),
      ) {
        // 地図を動かした後に、絞り込んだ場所が全部入る範囲へ戻す（開いたとき・絞り込みを変えたときと同じ寄せ方）。
        if (state.visibleItems.isNotEmpty()) {
          MapRoundButton(
            icon = R.drawable.ic_fit,
            contentDescription = "すべての場所を見る",
            onClick = {
              val update = placesFitUpdate(state.visibleItems.map { it.toRegisteredPlace() }, boundsPaddingPx) ?: return@MapRoundButton
              scope.launch { camera.position.animate(update) }
            },
          )
        }
        if (hasLocationPermission) {
          MapRoundButton(
            icon = R.drawable.ic_my_location,
            contentDescription = "現在地へ移動",
            onClick = {
              requestCurrentLatLng(context) { latLng ->
                // 見ている縮尺は変えず、引いているときだけ寄せる。
                val zoom = maxOf(camera.position.position.zoom, MY_LOCATION_ZOOM)
                scope.launch { camera.position.animate(CameraUpdateFactory.newLatLngZoom(latLng, zoom)) }
              }
            },
          )
        }
      }
    }
  }
}

/**
 * 場所タブの地図表示の地図。一覧と同じ絞り込みの結果（[items]）だけをマーカーで出す。見た目はほかの地図の
 * 登録済みの場所と同じ（まとまり・名前のラベルも）。マーカーのタップは一覧の行と同じく場所詳細へ（[onPlaceClick]）。
 */
@Composable
private fun PlacesMapView(
  items: List<PlaceListItem>,
  emptyMessage: String?,
  onPlaceClick: (Long) -> Unit,
  visible: Boolean,
  camera: ListMapCamera,
  showMyLocation: Boolean,
  modifier: Modifier = Modifier,
  // 地図に重ねたもの（見出し・ツール行）の分の余白。範囲合わせ・Google のロゴ・0 件の文言がその下に収まる。
  contentPadding: PaddingValues = PaddingValues(),
  mapContent: (@Composable () -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val places = remember(items) { items.map { it.toRegisteredPlace() } }
  val cameraPositionState = camera.position
  // マーカーの吹き出しをシステムバックで閉じられるようにする。
  val infoWindow = rememberMapInfoWindowState()
  // 一覧表示の裏に回ったら吹き出しを閉じる。見えない吹き出しにバックを吸われないように。
  LaunchedEffect(visible) { if (!visible) infoWindow.close() }

  // 範囲を合わせるのは、地図を初めて開いたときと絞り込みの結果が変わったときだけ。詳細から戻ったときや、
  // 一覧表示を挟んで地図に戻ったときは前に見ていた範囲のまま（[camera] は地図表示の外で持っている）。
  val fitKey = remember(places) { places.map { it.placeId }.sorted().joinToString(",") }
  var fittedFor by camera::fittedFor
  // 範囲（bounds）で合わせるには地図の大きさが決まっている必要があるので、読み込みを待つ。
  var mapLoaded by remember { mutableStateOf(false) }
  val boundsPaddingPx = with(LocalDensity.current) { PlacesMapBoundsPadding.roundToPx() }
  LaunchedEffect(fitKey, mapLoaded) {
    if (!mapLoaded || fitKey == fittedFor) return@LaunchedEffect
    val firstFit = fittedFor == null
    fittedFor = fitKey
    when (places.size) {
      // 0 件: 最初に開いたときだけ現在地あたりへ寄る。絞り込みで 0 件になったときは今の範囲のまま。
      0 -> if (firstFit) {
        requestCurrentLatLng(context) { latLng ->
          // 現在地が返るまでに場所が出てきて範囲を合わせ直していたら、そちらを優先する。
          if (fittedFor != fitKey) return@requestCurrentLatLng
          scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 14f)) }
        }
      }

      else -> cameraPositionState.animate(placesFitUpdate(places, boundsPaddingPx) ?: return@LaunchedEffect)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (mapContent != null) {
      mapContent()
    } else {
      GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = showMyLocation),
        uiSettings = MapUiSettings(
          zoomControlsEnabled = false,
          mapToolbarEnabled = false,
          myLocationButtonEnabled = false,
        ),
        contentPadding = contentPadding,
        onMapLoaded = { mapLoaded = true },
      ) {
        RegisteredPlaceMarkers(places, infoWindow) { onPlaceClick(it.placeId) }
      }
    }
    emptyMessage?.let { message ->
      Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
          modifier = Modifier.padding(PathlySpacing.lg),
        ) {
          Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(PathlySpacing.lg),
          )
        }
      }
    }
  }
}

/**
 * [places] が全部入るように寄せるカメラの動き。0 件なら null。1 件は範囲では寄りすぎるので、その場所を中心に
 * 街区が見える程度に寄る。範囲（bounds）で寄せるので、地図の大きさが決まってから（読み込み後に）使う。
 */
private fun placesFitUpdate(places: List<RegisteredPlace>, boundsPaddingPx: Int): CameraUpdate? = when (places.size) {
  0 -> null

  1 -> places.first().let { CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f) }

  else -> {
    val bounds = LatLngBounds.builder().apply {
      places.forEach { include(LatLng(it.latitude, it.longitude)) }
    }.build()
    CameraUpdateFactory.newLatLngBounds(bounds, boundsPaddingPx)
  }
}

// 地図表示で絞り込んだ場所が全部入るよう範囲を合わせるときの、端の余白（マーカーと名前が切れないように）。
private val PlacesMapBoundsPadding = 56.dp

// 現在地へ寄せるときの、いちばん引いた縮尺（これより引いていれば寄せる）。
private const val MY_LOCATION_ZOOM = 15f
