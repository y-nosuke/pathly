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
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
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
  // まとまりの見出しを押したとき（[ListGroup.key]）。開く／閉じる。
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
  // 地域の見出しから地図で見るときに、その地域（範囲と境界の Place ID）を引く（Places。2 つ目はその地域の場所が入る範囲）。
  // 引けなければ null。
  onFetchRegion: suspend (RegionKey, GeoBounds?) -> MapRegion?,
  modifier: Modifier = Modifier,
  // 地図表示の地図スロット。null（既定）は実マップ（GoogleMap）を描画する。
  // テストは空スロット（{}）を渡し、GMS 依存の地図描画を避けて切り替え・文言だけを検証する。
  mapContent: (@Composable () -> Unit)? = null,
  // 地図で境界を描けるか（マップ ID・端末の対応）の初めの値。実マップが分かり次第置き換える。テストは地図を描かないので true を渡す。
  boundaryAvailableInitially: Boolean = false,
  // 選ぶモード（計画の「場所を足す」）。null なら場所タブのふつうの一覧。
  selection: PlaceSelection? = null,
) {
  val isMap = state.viewMode == ListMapMode.MAP
  // 地図表示はタブの中の一段奥の見方として扱い、システムバックで一覧表示へ戻す（タブは離れない）。
  // 吹き出しが出ているときは、後から登録される地図側の処理が先に吹き出しを閉じる。
  BackHandler(enabled = isMap) { onViewModeChange(ListMapMode.LIST) }

  // 地図表示の範囲は、一覧表示に切り替えても捨てない。地図に戻ったら前に見ていた範囲のまま出す
  // （その間に絞り込みの結果が変わっていれば、戻ったときに合わせ直す）。
  val mapCamera = rememberListMapCamera()
  // 一覧のスクロール位置と、最後に先頭へ戻したときの条件。地図表示の間は一覧を描かないので、ここで持って
  // 一覧へ戻ったときに同じ位置から出す（地図表示の間に絞り込みを変えていれば、戻ったときに先頭へ戻す）。
  val listState = rememberLazyListState()
  val listScrolledFor = rememberSaveable { mutableStateOf<String?>(null) }

  // 地域の見出しから地図で見る: 地図表示に切り替え、その地域の範囲（引けなければその地域の場所が全部入る範囲）へ寄せる。
  // 絞り込みは変えない。範囲を引く間に別の見出しを押したら、後に押したほうだけを寄せる。
  val scope = rememberCoroutineScope()
  var regionFocus by remember { mutableStateOf<PlacesMapFocus?>(null) }
  val regionFocusJob = remember { mutableStateOf<Job?>(null) }
  // 境界を描いている地域（1 つだけ。別の見出しから来ると置き換わり、一覧表示に戻ると消える → ADR 0046）。
  var boundary by remember { mutableStateOf<PlacesBoundary?>(null) }
  var boundaryAvailable by remember { mutableStateOf(boundaryAvailableInitially) }
  val showGroupOnMap: (PlaceGroup, RegionKey) -> Unit = { group, region ->
    val places = group.allItems().map { it.toRegisteredPlace() }
    // 地図を開いたとき・絞り込みの結果が変わったときの「全部入る範囲」への寄せは、この地域へ寄せるので飛ばす。
    mapCamera.fittedFor = placesFitKey(state.visibleItems)
    onViewModeChange(ListMapMode.MAP)
    // 前の地域の境界は、引いている間も残さない（引けなかったときに別の地域の境界が残らないように）。
    boundary = null
    regionFocusJob.value?.cancel()
    regionFocusJob.value = scope.launch {
      val fetched = onFetchRegion(region, placesBounds(places))
      val focus = PlacesMapFocus(places, fetched?.bounds)
      regionFocus = focus
      boundary = fetched?.let { PlacesBoundary(placeId = it.placeId, name = group.label, focus = focus) }
    }
  }
  // 範囲を引いている間に一覧表示へ戻ったら、寄せるのをやめる（戻った後に絞り込みを変えると、その結果に合わせ直したいので）。
  // 境界も一覧表示に戻ったら消す。
  LaunchedEffect(isMap) {
    if (!isMap) {
      regionFocusJob.value?.cancel()
      regionFocus = null
      boundary = null
    }
  }

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
      title = if (selection != null) "場所を選ぶ" else "場所",
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
          onPlaceClick = { id -> if (selection != null) selection.onToggle(id) else onMapPlaceClick(id) },
          selectedPlaceIds = selection?.selectedIds.orEmpty(),
          focus = regionFocus,
          onFocusConsumed = { regionFocus = null },
          boundary = boundary?.takeIf { boundaryAvailable },
          onBoundaryAvailableChange = { boundaryAvailable = it },
          // 地名を押すと、その地域の範囲へ寄せ直す（見出しから来たときと同じ寄せ方）。
          onBoundaryRefit = { boundary?.let { regionFocus = it.focus } },
          onBoundaryClear = { boundary = null },
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
        onShowGroupOnMap = showGroupOnMap,
        listState = listState,
        scrolledForState = listScrolledFor,
        onItemClick = { item -> if (selection != null) selection.onToggle(item.place.id) else onItemClick(item) },
        onToggleWishlist = onToggleWishlist,
        selectedIds = selection?.selectedIds,
      )
    }

    if (selection == null) {
      // 右下: 追加（一覧の主役）。一覧表示・地図表示で同じ位置に置き、切り替えても動かないようにする。
      PlaceAddButton(
        onAddBySearch = onAddBySearch,
        onAddByMap = onAddByMap,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = PathlySpacing.screenEdge, bottom = PathlySpacing.screenEdge),
      )
    } else {
      // 選ぶモードの主役: 選んだ場所を足す（右下の追加の代わり）。
      PlaceSelectionBar(selection, modifier = Modifier.align(Alignment.BottomCenter))
    }
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
  onShowGroupOnMap: (PlaceGroup, RegionKey) -> Unit,
  listState: LazyListState,
  scrolledForState: MutableState<String?>,
  onItemClick: (PlaceListItem) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
  selectedIds: Set<Long>?,
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
        onShowGroupOnMap = onShowGroupOnMap,
        listState = listState,
        scrolledForState = scrolledForState,
        onItemClick = onItemClick,
        onToggleWishlist = onToggleWishlist,
        selectedIds = selectedIds,
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
  onShowGroupOnMap: (PlaceGroup, RegionKey) -> Unit,
  listState: LazyListState,
  scrolledForState: MutableState<String?>,
  onItemClick: (PlaceListItem) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
  selectedIds: Set<Long>?,
) {
  // 絞り込み・並べ替え・まとめを状態が変わったときだけ計算し直す（描き直しのたびにやらない）。
  val rows = remember(state) { state.listRows }
  // 絞り込み・並べ替えを変えたとき、または先頭が変わったとき（＝追加時）に先頭へ戻す。
  // 詳細から戻って一覧を出し直したときは戻さない（前に見ていた位置のまま）。そのため、最後に
  // 先頭へ戻したときの条件を画面の状態として取っておき（[scrolledForState]）、条件が変わったときだけ動かす。
  val scrollKey = state.scrollResetKey
  var scrolledFor by scrolledForState
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
        val indent = ListGroupIndent * row.depth
        when (row) {
          is ListGroupRow.Header -> ListGroupHeader(
            label = row.group.label,
            trailing = "${row.group.count}",
            depth = row.depth,
            expanded = row.expanded,
            indent = indent,
            testTag = "placeGroup:${row.group.key}",
            onClick = groupToggle?.let { toggle -> { toggle(row.group.key) } },
            // 地域のまとまり（地域不明を除く）は、右端の地図のボタンでその地域を地図で見られる（開閉とは別）。
            action = placeGroupRegion(row.group)?.let { region ->
              {
                IconButton(
                  onClick = { onShowGroupOnMap(row.group, region) },
                  modifier = Modifier.testTag("placeGroupMap:${row.group.key}"),
                ) {
                  Icon(
                    painter = painterResource(R.drawable.ic_map),
                    contentDescription = "${row.group.label}を地図で見る",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            },
          )

          is ListGroupRow.Item -> PlaceItemRow(
            item = row.item,
            sort = state.sort,
            indent = indent,
            onClick = { onItemClick(row.item) },
            onToggleWishlist = { onToggleWishlist(row.item) },
            selected = selectedIds?.let { row.item.place.id in it },
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

/** 右下の追加の丸の大きさ。 */
internal val PlaceAddButtonSize = 56.dp

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

// 一覧・地図で 0 件のときの文言。場所はあるのに絞り込みで消えたときは、追加を促さず条件のせいだと分かる文言にする。
internal fun PlacesState.emptyMessage(): String = if (items.isEmpty()) "場所がありません\n右下の ＋ から登録できます" else "条件に合う場所がありません"

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

/**
 * 選ぶモード（計画の「場所を足す」→ docs/specs/plans.md）。場所タブの一覧・地図をそのまま使い、行やマーカーを押すと
 * 選ぶ／外すが切り替わる。右下の追加の代わりに、下に「N 件を足す」を出す。
 */
class PlaceSelection(
  val selectedIds: Set<Long>,
  val onToggle: (Long) -> Unit,
  /** 足す先の文言（「2日目 11/30(日) に足す」）。 */
  val destination: String,
  val onConfirm: () -> Unit,
  val onCancel: () -> Unit,
  /** 選んだものを全部外す。 */
  val onClearAll: () -> Unit,
)

/** 選ぶモードの下の帯: 足す先、全解除・やめる・「N 件を足す」。 */
@Composable
private fun PlaceSelectionBar(selection: PlaceSelection, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = PathlyElevation.sheet,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.sm),
    ) {
      Text(
        text = selection.destination,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = selection.onClearAll,
          enabled = selection.selectedIds.isNotEmpty(),
          modifier = Modifier.testTag("placeSelectionClearAll"),
        ) { Text("全解除") }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = selection.onCancel) { Text("やめる") }
        Button(
          onClick = selection.onConfirm,
          enabled = selection.selectedIds.isNotEmpty(),
          modifier = Modifier.testTag("placeSelectionConfirm"),
        ) {
          Text(if (selection.selectedIds.isEmpty()) "選んでください" else "${selection.selectedIds.size} 件を足す")
        }
      }
    }
  }
}
