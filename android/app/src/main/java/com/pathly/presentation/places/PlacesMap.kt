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
 * 場所タブの地図表示。地図をタブいっぱいに広げ、一覧と同じ見出しとツール行を上に重ねる（たためる）。
 * 右下は地図の操作（絞り込んだ場所をすべて見る・現在地）で、その下の追加の丸（[PlacesListContent] が置く）の上に積む。
 *
 * @param visible false のときは一覧表示の裏に地図だけを残している（重ねるもの・右下の操作は出さない）。
 */
@Composable
internal fun PlacesMapLayout(
  state: PlacesState,
  visible: Boolean,
  camera: ListMapCamera,
  header: @Composable () -> Unit,
  controls: @Composable () -> Unit,
  onViewModeChange: (ListMapMode) -> Unit,
  onControlsCollapsedChange: (Boolean) -> Unit,
  onPlaceClick: (Long) -> Unit,
  focus: PlacesMapFocus?,
  onFocusConsumed: () -> Unit,
  // 境界を描いている地域（描けないときは null）。地図に境界を描き、重ねたものの下に地名のチップを出す。
  boundary: PlacesBoundary?,
  onBoundaryAvailableChange: (Boolean) -> Unit,
  onBoundaryRefit: () -> Unit,
  onBoundaryClear: () -> Unit,
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
        focus = focus,
        onFocusConsumed = onFocusConsumed,
        boundaryPlaceId = boundary?.placeId,
        onBoundaryAvailableChange = onBoundaryAvailableChange,
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

      // 境界を描いている地域のチップ。重ねたもの（たたんだときは上端の帯）のすぐ下の左に出す。
      boundary?.let { b ->
        RegionMarkerChip(
          name = b.name,
          focused = false,
          onRefit = onBoundaryRefit,
          onClear = onBoundaryClear,
          clearDescription = "${b.name}の境界を外す",
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = overlayHeight + PathlySpacing.sm, start = PathlySpacing.screenEdge),
        )
      }

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
  // 地域の見出しから地図で見るときの寄せ先。地図の読み込みを待って寄せ、寄せたら [onFocusConsumed]。
  focus: PlacesMapFocus?,
  onFocusConsumed: () -> Unit,
  // 境界を描く地域の Place ID（無ければ null）と、この地図で境界を描けるかの知らせ。
  boundaryPlaceId: String?,
  onBoundaryAvailableChange: (Boolean) -> Unit,
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
  val fitKey = remember(items) { placesFitKey(items) }
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
  LaunchedEffect(focus, mapLoaded) {
    if (focus == null || !mapLoaded) return@LaunchedEffect
    // 寄せた後に絞り込みの結果が変わるまでは、全部入る範囲へ合わせ直さない。
    fittedFor = fitKey
    // 寄せている途中で地図を動かされて止まっても片付ける（残ると、同じ地域をもう一度押しても寄らない）。
    try {
      regionFocusUpdate(focus, boundsPaddingPx)?.let { cameraPositionState.animate(it) }
    } finally {
      onFocusConsumed()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (mapContent != null) {
      mapContent()
    } else {
      GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        // マップ ID は境界を描くこの地図にだけ付ける。境界は明るい地図にしか描けないので、描いている間は明るくする（→ ADR 0046）。
        googleMapOptionsFactory = ::boundaryMapOptions,
        mapColorScheme = boundaryMapColorScheme(boundaryPlaceId != null),
        properties = MapProperties(isMyLocationEnabled = showMyLocation),
        uiSettings = MapUiSettings(
          zoomControlsEnabled = false,
          mapToolbarEnabled = false,
          myLocationButtonEnabled = false,
        ),
        contentPadding = contentPadding,
        onMapLoaded = { mapLoaded = true },
      ) {
        RegionBoundaries(
          placeIds = remember(boundaryPlaceId) { setOfNotNull(boundaryPlaceId) },
          onAvailableChange = onBoundaryAvailableChange,
        )
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

/** 地図表示の範囲を最後に合わせたときの印（絞り込んだ場所の id を並べたもの）。 */
internal fun placesFitKey(items: List<PlaceListItem>): String = items.map { it.place.id }.sorted().joinToString(",")

/** 地域の見出しから地図で見るときの寄せ先。[places] はその地域の場所、[region] は Places で引いた地域の範囲（引けなければ null）。 */
internal data class PlacesMapFocus(val places: List<RegisteredPlace>, val region: GeoBounds?)

/** 地図に境界を描いている地域。[name] は見出しの地名、[focus] はチップの地名から寄せ直すときの寄せ先。 */
internal data class PlacesBoundary(val placeId: String, val name: String, val focus: PlacesMapFocus)

/** [places] が全部入る範囲（地域の候補を寄せる先）。0 件なら null。 */
internal fun placesBounds(places: List<RegisteredPlace>): GeoBounds? = if (places.isEmpty()) {
  null
} else {
  GeoBounds(
    south = places.minOf { it.latitude },
    west = places.minOf { it.longitude },
    north = places.maxOf { it.latitude },
    east = places.maxOf { it.longitude },
  )
}

/**
 * 地域の見出しから地図で見るときのカメラの動き。地域の範囲がまるごと入るように寄せ、はみ出す場所があれば広げて入れる。
 * 範囲が引けなかったとき・どの場所も範囲に入らない（別の同じ名前の地域を引いた）ときは、その地域の場所が全部入る範囲。
 */
private fun regionFocusUpdate(focus: PlacesMapFocus, boundsPaddingPx: Int): CameraUpdate? {
  val region = focus.region?.let { LatLngBounds(LatLng(it.south, it.west), LatLng(it.north, it.east)) }
  val points = focus.places.map { LatLng(it.latitude, it.longitude) }
  if (region == null || points.none(region::contains)) return placesFitUpdate(focus.places, boundsPaddingPx)
  return CameraUpdateFactory.newLatLngBounds(points.fold(region) { acc, p -> acc.including(p) }, boundsPaddingPx)
}

// 地図表示で絞り込んだ場所が全部入るよう範囲を合わせるときの、端の余白（マーカーと名前が切れないように）。
private val PlacesMapBoundsPadding = 56.dp

// 現在地へ寄せるときの、いちばん引いた縮尺（これより引いていれば寄せる）。
private const val MY_LOCATION_ZOOM = 15f
