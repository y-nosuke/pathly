package com.pathly.presentation.plans

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.MapSurface
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanSlot
import com.pathly.presentation.common.DefaultMapLocation
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.MapBackTitle
import com.pathly.presentation.common.MapControls
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapNoticeBanner
import com.pathly.presentation.common.MapOverflowMenuButton
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.common.MapRoundButton
import com.pathly.presentation.common.MapRoundButtonSize
import com.pathly.presentation.common.MapTopBar
import com.pathly.presentation.common.MapTopBarInset
import com.pathly.presentation.common.RegionBoundaries
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.presentation.common.RegisteredPlacesLayerButton
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.SheetRestoreButtonInset
import com.pathly.presentation.common.SwipeDismissSnackbarHost
import com.pathly.presentation.common.boundaryMapColorScheme
import com.pathly.presentation.common.boundaryMapOptions
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberLocationPermitted
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.rememberMarkerIconCache
import com.pathly.presentation.common.requestCurrentLatLng
import com.pathly.presentation.common.toGeoBounds
import com.pathly.presentation.common.toLatLngBounds
import com.pathly.presentation.places.PLACE_SHEET_PEEK_FRACTION
import com.pathly.presentation.places.PlaceEditHost
import com.pathly.presentation.places.PlacePickerRoute
import com.pathly.presentation.places.PlaceRegisterOptions
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.places.PlacesViewModel
import com.pathly.presentation.places.toRegisteredPlace
import com.pathly.presentation.tracking.RegionMarker
import com.pathly.presentation.tracking.RegionMarkerBadge
import com.pathly.presentation.tracking.RegionMarkerChips
import com.pathly.presentation.tracking.TrackingMapSearch
import com.pathly.presentation.tracking.VISIBLE_REGION_CHIPS
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlySpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 計画の詳細（地図の型 → docs/specs/plans.md の「計画の詳細」）。計画が消えたら [onBack]。 */
@Composable
fun PlanDetailRoute(
  viewModel: PlanDetailViewModel,
  onBack: () -> Unit,
  onDeleted: (title: String) -> Unit,
  onOpenPlace: (placeId: Long) -> Unit,
  onOpenTrack: (trackId: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      // スナックバーは出している間待つので、別に出す（待っている間に「画面を離れる」を取りこぼさないように）。
      when (event) {
        is PlanDetailEvent.Deleted -> onDeleted(event.title)

        PlanDetailEvent.Leave -> onBack()

        is PlanDetailEvent.Failed -> scope.launch { snackbarHostState.showSnackbar(event.message) }

        is PlanDetailEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.message) }

        // 中身の側で、足した日へシートを寄せる。
        is PlanDetailEvent.PlaceAdded -> Unit

        is PlanDetailEvent.ItemRemoved -> scope.launch {
          val result = snackbarHostState.showSnackbar("「${event.label}」を外しました", actionLabel = "取り消す")
          if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoveItem()
        }
      }
    }
  }
  when (val s = state) {
    PlanDetailState.Loading -> Box(modifier = modifier.fillMaxSize())

    PlanDetailState.Missing -> LaunchedEffect(Unit) { onBack() }

    is PlanDetailState.Loaded -> Box(modifier = modifier.fillMaxSize()) {
      PlanDetailContent(
        edit = s.edit,
        selectedDay = selectedDay,
        onSelectDay = viewModel::selectDay,
        onBack = onBack,
        onOpenPlace = onOpenPlace,
        onOpenTrack = onOpenTrack,
        viewModel = viewModel,
        snackbarHost = { SwipeDismissSnackbarHost(snackbarHostState, modifier = it) },
        onDismissSnackbar = { snackbarHostState.currentSnackbarData?.dismiss() },
      )
    }
  }
}

/** 開いているダイアログ。 */
private sealed interface PlanDialog {
  data object Title : PlanDialog
  data object Dates : PlanDialog
  data object Places : PlanDialog
  data object Tracks : PlanDialog
  data object NewMemo : PlanDialog
  data object Leave : PlanDialog
  data object DiscardDraft : PlanDialog
  data class EditItem(val itemId: Long) : PlanDialog
  data class MoveDay(val itemId: Long) : PlanDialog
  data class AddFromMap(val placeId: Long, val name: String) : PlanDialog
}

@Composable
private fun PlanDetailContent(
  edit: PlanEditState,
  selectedDay: Int?,
  onSelectDay: (Int?) -> Unit,
  onBack: () -> Unit,
  onOpenPlace: (Long) -> Unit,
  onOpenTrack: (Long) -> Unit,
  viewModel: PlanDetailViewModel,
  snackbarHost: @Composable (Modifier) -> Unit,
  onDismissSnackbar: () -> Unit,
) {
  val detail = edit.detail
  val plan = detail.plan
  // 登録済みの場所（地図のレイヤー［≋］と「場所を足す」）。並べ替え・絞り込みは場所タブと共有する。
  val placesViewModel = hiltViewModel<PlacesViewModel>(key = "planPlaces")
  val placesState by placesViewModel.uiState.collectAsStateWithLifecycle()
  val showRegisteredPlaces by placesViewModel.showRegisteredPlaces(MapSurface.PLAN_DETAIL).collectAsStateWithLifecycle()
  val mapPlaceFilter by placesViewModel.mapPlaceFilter(MapSurface.PLAN_DETAIL).collectAsStateWithLifecycle()
  val inPlan = remember(detail) { detail.items.mapNotNull { it.place?.placeId }.toSet() }
  val registeredPlaces = MapRegisteredPlaces(
    // 計画に入っている場所は番号のマーカーで出ているので、重ねない。
    places = placesState.items.filter { it.place.id !in inPlan }.map { it.toRegisteredPlace() },
    shown = showRegisteredPlaces,
    onToggle = { placesViewModel.toggleShowRegisteredPlaces(MapSurface.PLAN_DETAIL) },
    filter = mapPlaceFilter,
    onFilterChange = { placesViewModel.setMapPlaceFilter(MapSurface.PLAN_DETAIL, it) },
  )

  var dialog by remember { mutableStateOf<PlanDialog?>(null) }
  // 保存していない変更があれば、戻る前に保存・下書き・破棄を聞く。
  val requestBack = { if (edit.hasUnsavedChanges) dialog = PlanDialog.Leave else onBack() }
  BackHandler(enabled = edit.hasUnsavedChanges && dialog != PlanDialog.Places && dialog != PlanDialog.Tracks) { dialog = PlanDialog.Leave }

  // 計画と実績（結び付いた経路・行った・記録中の経路 → docs/specs/plans.md の「計画と実績」）。
  val actuals by viewModel.actuals.state.collectAsStateWithLifecycle()
  val liveTrack by viewModel.actuals.liveTrack.collectAsStateWithLifecycle()
  val showActualTracks by viewModel.actuals.showTracks.collectAsStateWithLifecycle()
  val actualLines by viewModel.actuals.lines.collectAsStateWithLifecycle()

  // 新しく登録する場所（地図のタップ・POI・検索で選んだ施設）。出している間は計画のシートを下げて登録のシートを出す。
  var placeSheetTarget by remember { mutableStateOf<PlaceSheetTarget?>(null) }
  // 地図の上の検索窓（記録画面と同じ。施設の登録と地域への移動）を開いているか・開いたときに映っていた範囲。
  var mapSearchOpen by remember { mutableStateOf(false) }
  var mapSearchBias by remember { mutableStateOf<GeoBounds?>(null) }
  // 「地図で選ぶ」の案内の帯を出しているか（地図をタップするか、計画のシートを戻せば消える）。
  var pickOnMap by remember { mutableStateOf(false) }
  val regionMarkers by viewModel.regionMarkers.state.collectAsStateWithLifecycle()
  val mapSearchMode by viewModel.mapSearchMode.collectAsStateWithLifecycle()

  val sheetState = rememberFloatingSheetState()
  val placeSheetState = rememberFloatingSheetState(peekFraction = PLACE_SHEET_PEEK_FRACTION)
  val sheetListState = rememberLazyListState()
  // 地図の下の余白は、いま出ているシート（登録のシートか計画のシート）に合わせる。
  val activeSheet = if (placeSheetTarget != null) placeSheetState else sheetState
  val mapBottomPadding = activeSheet.heightOf(activeSheet.detent)
  val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(DefaultMapLocation, 11f) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val density = LocalDensity.current
  val hasLocationPermission = rememberLocationPermitted()
  val infoWindow = rememberMapInfoWindowState()
  var mapLoaded by remember { mutableStateOf(false) }
  val draftUpdatedAt = edit.draftUpdatedAt
  // 日のチップの行・下書きの帯の分だけ、地図の上の余白を足す（マーカーが隠れないように）。
  val showPickBanner = pickOnMap && placeSheetTarget == null
  val topInset = MapTopBarInset + (if (plan.isDayTrip) 0.dp else DayChipRowHeight) +
    (if (draftUpdatedAt != null) DraftBannerHeight else 0.dp) + (if (showPickBanner) DraftBannerHeight else 0.dp)
  val day = selectedDay?.takeIf { it in 1..plan.dayCount }

  // 地図に出す場所の行（選んだ日だけ、または全部）。
  val shownItems = detail.items.filter { it.isPlace && (day == null || it.dayIndex == day) }
  val numbers = detail.placeNumbers

  // 出ているマーカーがすべて入るように寄せる（開いたとき・日を選び直したとき・行が増減したとき）。
  val fitKey = "$day:" + shownItems.joinToString(",") { "${it.place!!.placeId}" }
  var fittedFor by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(mapLoaded, fitKey) {
    if (!mapLoaded || fittedFor == fitKey) return@LaunchedEffect
    fittedFor = fitKey
    val points = shownItems.map { LatLng(it.place!!.latitude, it.place.longitude) }
    when {
      points.isEmpty() -> Unit

      points.distinct().size == 1 -> cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(points.first(), PLACE_ZOOM))

      else -> {
        val bounds = points.fold(LatLngBounds.builder()) { b, p -> b.include(p) }.build()
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, with(density) { FitPadding.roundToPx() }))
      }
    }
  }
  // 場所の行が無い計画で記録中の経路が結び付いていれば、記録中の経路へ寄せる（開いたとき 1 回。重ねた線が見えるように）。
  var fittedLive by rememberSaveable { mutableStateOf(false) }
  val livePoints = liveTrack?.smoothedPoints.orEmpty()
  LaunchedEffect(mapLoaded, livePoints.isNotEmpty(), shownItems.isEmpty()) {
    if (!mapLoaded || fittedLive || livePoints.isEmpty() || shownItems.isNotEmpty()) return@LaunchedEffect
    val bounds = livePoints.fold(LatLngBounds.builder()) { b, p -> b.include(LatLng(p.latitude, p.longitude)) }.build()
    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, with(density) { FitPadding.roundToPx() }))
    fittedLive = true
  }
  // 足した後は、足した日の見出しへ寄せる（足した行が見える所へ。日帰りは末尾へ）。行が増えてから動かす。
  var scrollToDay by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(detail.items.size) {
    val target = scrollToDay ?: return@LaunchedEffect
    scrollToDay = null
    val index = if (plan.isDayTrip) sheetListState.layoutInfo.totalItemsCount - 1 else headerIndexOf(detail, target)
    index?.takeIf { it >= 0 }?.let { sheetListState.animateScrollToItem(it) }
  }
  // 日のチップ・日の見出しを押したら、シートもその日の見出しへ寄せる。
  LaunchedEffect(day) {
    if (day == null || plan.isDayTrip) return@LaunchedEffect
    headerIndexOf(detail, day)?.let { sheetListState.animateScrollToItem(it) }
  }
  // 足す日: 選んでいる日（「すべて」なら最後の日）。
  val addDay = day ?: plan.dayCount
  val destination = if (plan.isDayTrip) "この計画に足す" else "${dayLabel(plan, addDay)} に足す"

  // 新しく登録した場所を足したら、その日へシートを寄せる（足すのは登録が済んでから ViewModel が行う）。
  LaunchedEffect(viewModel) {
    viewModel.events.collect {
      if (it is PlanDetailEvent.PlaceAdded) {
        scrollToDay = it.day
        // 「地図で選ぶ」で畳んでいたら、足した行が見えるようにハーフへ戻す。
        if (sheetState.detent == SheetDetent.HIDDEN) sheetState.settleTo(SheetDetent.PEEK)
      }
    }
  }
  // 地図に見えている登録済みの場所（番号のマーカー・レイヤーで出した場所）は、近接確認で確かめない。
  val visibleRegistered = registeredPlaces.visible
  LaunchedEffect(inPlan, visibleRegistered) {
    viewModel.setPlacesShownOnMap(inPlan + visibleRegistered.map { it.placeId })
  }
  // 「地図で選ぶ」の帯は、シートが畳み切ってから戻されたら消す（押した直後はまだ畳み切っていない）。
  LaunchedEffect(pickOnMap) {
    if (!pickOnMap) return@LaunchedEffect
    snapshotFlow { sheetState.detent }.first { it == SheetDetent.HIDDEN }
    snapshotFlow { sheetState.detent }.first { it != SheetDetent.HIDDEN }
    pickOnMap = false
  }
  // 「地図で選ぶ」の間に戻るを押したら、選ぶのをやめて計画のシートを戻す（計画の一覧へは戻らない）。
  // 保存していない変更のときの戻る（上の BackHandler）より後に置き、こちらを先に効かせる。
  BackHandler(enabled = pickOnMap && placeSheetTarget == null) {
    pickOnMap = false
    sheetState.settleTo(SheetDetent.PEEK)
  }
  // 登録のシートを開いた場所へ地図を寄せる（シートの裏に隠れないように）。
  LaunchedEffect(placeSheetTarget) {
    placeSheetTarget?.registerLatLng()?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLng(it)) }
  }
  // 地域の目印からその地域の範囲へ寄せ直す（選んだときと同じ合わせ方）。
  fun refitRegion(region: RegionMarker) {
    viewModel.regionMarkers.focus(region.placeId)
    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(region.bounds.toLatLngBounds(), with(density) { FitPadding.roundToPx() })) }
  }
  // この地図で境界を描けるか。描ける間だけ、目印があるときに地図を明るくする（境界は明るい地図にしか描けない。記録画面と同じ）。
  var boundaryAvailable by remember { mutableStateOf(false) }
  val regionPlaceIds = remember(regionMarkers.markers) { regionMarkers.markers.map { it.placeId }.toSet() }
  // 地域の地名のチップ。地名でその地域へ寄せ直し、× でその目印を外す（記録画面と同じ）。
  val regionChips: @Composable () -> Unit = {
    RegionMarkerChips(
      markers = regionMarkers.markers,
      focusedPlaceId = regionMarkers.focusedPlaceId,
      onRefit = ::refitRegion,
      onClear = { viewModel.regionMarkers.remove(it.placeId) },
      onClearAll = viewModel.regionMarkers::clear,
      onMove = { region, to -> viewModel.regionMarkers.move(region.placeId, to) },
      onFold = { viewModel.regionMarkers.fold(it.placeId) },
      onBringUp = { viewModel.regionMarkers.bringUp(it.placeId, VISIBLE_REGION_CHIPS - 1) },
    )
  }
  fun openRegister(target: PlaceSheetTarget) {
    pickOnMap = false
    // 出ているスナックバー（取り消しは押すまで残る）が登録のボタンに重ならないように閉じる。
    onDismissSnackbar()
    infoWindow.close()
    placeSheetTarget = target
  }
  // 登録のシートで足す日（初期は今見ている日。「すべて」なら最後の日）。
  var registerDay by remember(placeSheetTarget) { mutableIntStateOf(addDay) }
  val registerOptions = PlaceRegisterOptions(
    initialWishlist = true,
    registerLabel = if (plan.isDayTrip) "登録して足す" else "登録して ${registerDay}日目に足す",
    belowHeading = if (plan.isDayTrip) {
      null
    } else {
      { RegisterDayChips(dayCount = plan.dayCount, selected = registerDay, onSelect = { registerDay = it }) }
    },
    belowHeadingHeight = if (plan.isDayTrip) 0.dp else RegisterDayRowHeight,
    onRegister = { viewModel.prepareRegister(if (plan.isDayTrip) 1 else registerDay) },
  )

  Box(modifier = Modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(top = topInset, bottom = mapBottomPadding),
      // 地域の境界を描くので、マップ ID を付ける（→ ADR 0046）。境界を出している間はダークモードでも明るくする。
      googleMapOptionsFactory = ::boundaryMapOptions,
      mapColorScheme = boundaryMapColorScheme(boundaryAvailable && regionPlaceIds.isNotEmpty()),
      properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
      uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
      onMapLoaded = { mapLoaded = true },
      // 地図・施設のタップは、いつでも登録のシートを出す（記録画面・経路詳細と同じ）。
      onMapClick = { openRegister(PlaceSheetTarget.NewPoint(it)) },
      onPOIClick = { openRegister(PlaceSheetTarget.NewPoi(it)) },
    ) {
      // 登録済みの場所（レイヤーで出したとき）。押すと、この計画に足すかを聞く。
      RegisteredPlaceMarkers(registeredPlaces.visible, infoWindow) { dialog = PlanDialog.AddFromMap(it.placeId, it.name ?: "この場所") }
      // 結び付いた経路（レイヤーで重ねたとき）と、記録中の経路（結び付いていればいつも）。計画の線・マーカーより奥に一色で描く。
      PlanActualTracks(
        lines = if (showActualTracks) actuals.linked.mapNotNull { actualLines[it.track.id] } else emptyList(),
        liveTrack = liveTrack,
      )
      // 同じ日の中を順に直線で結ぶ（「すべて」のときは日ごとに色を変える）。
      shownItems.groupBy { it.dayIndex }.forEach { (d, dayItems) ->
        if (dayItems.size >= 2) {
          Polyline(
            points = dayItems.map { LatLng(it.place!!.latitude, it.place.longitude) },
            color = planDayColor(d),
            width = with(density) { RouteWidth.toPx() },
          )
        }
      }
      shownItems.forEach { item ->
        val place = item.place!!
        val number = numbers[item.id] ?: return@forEach
        val markerState = remember(place.latitude, place.longitude) { MarkerState(LatLng(place.latitude, place.longitude)) }
        // 行った場所は緑（番号はそのまま）。
        val color = if (place.placeId in actuals.visitedPlaceIds) PathlyMapColors.PlaceVisited else planDayColor(item.dayIndex)
        MapMarker(
          "plan-item",
          item.id,
          number,
          color,
          infoWindow = infoWindow,
          state = markerState,
          title = place.name,
          snippet = listOfNotNull(dayLabel(plan, item.dayIndex).takeIf { !plan.isDayTrip }, itemTimeLine(item)).joinToString("・").ifEmpty { null },
          bubbleColor = color,
          // 番号の丸の真ん中を場所に置く（線が丸の中心を通るように）。
          anchor = Offset(0.5f, 0.5f),
          zIndex = PLAN_MARKER_Z_INDEX,
        ) {
          PlanNumberBadge(number = number, color = color)
        }
      }
      // 検索で選んだ施設の青いピン（地図に施設のアイコンが無いこともあるので示す。記録画面と同じ）。
      (placeSheetTarget as? PlaceSheetTarget.NewSearchResult)?.result?.let { result ->
        val pick = LatLng(result.latitude, result.longitude)
        val pickState = remember(pick) { MarkerState(position = pick) }
        MapMarker(
          "plan-pick",
          pick.latitude,
          pick.longitude,
          infoWindow = infoWindow,
          state = pickState,
          title = result.name,
          bubbleColor = PathlyMapColors.Picked,
        ) {
          MapPinMarker(bg = PathlyMapColors.Picked, glyph = R.drawable.ic_add)
        }
      }
      // 検索の「地域」で出した地域の境界（目印と一緒に消える）。
      RegionBoundaries(regionPlaceIds, onAvailableChange = { boundaryAvailable = it })
      // 検索の「地域」で出した目印。押すとその地域の範囲へ寄せ直す（保存しない）。
      val regionIcons = rememberMarkerIconCache()
      regionMarkers.markers.forEach { region ->
        key(region.placeId) {
          val icon = remember(regionIcons, region.name) { regionIcons.createLabeled(null) { RegionMarkerBadge(region.name) } }
          val state = remember(region) { MarkerState(position = LatLng(region.latitude, region.longitude)) }
          MapMarker(
            icon = icon.icon,
            infoWindow = infoWindow,
            state = state,
            anchor = icon.anchor,
            onClick = { refitRegion(region) },
          )
        }
      }
    }

    // 検索窓を開いている間は、上のバー（日のチップ・帯）を出さない（検索窓の「場所｜地域」と重なるため）。
    if (!mapSearchOpen) {
      MapTopBar(
        modifier = Modifier.align(Alignment.TopCenter),
        leading = {
          MapBackTitle(
            title = plan.title,
            onBack = requestBack,
            onTitleClick = { dialog = PlanDialog.Title },
            titleClickLabel = "名前とメモを編集",
            modifier = Modifier.weight(1f),
          )
        },
        actions = {
          // 一度も保存していない計画だけ、画面を閉じずに下書きに残せる（保存済みの計画は「保存」で足りる）。
          if (edit.isNew) DraftSaveButton(enabled = edit.hasUnsavedChanges, onClick = { viewModel.saveDraft() })
          SaveButton(enabled = edit.canSave, onClick = { viewModel.save() })
          PlanLayersButton(
            registeredPlaces = registeredPlaces,
            linkedTrackCount = actuals.linked.size,
            showTracks = showActualTracks,
            onToggleTracks = viewModel.actuals::toggleShowTracks,
          )
          MapOverflowMenuButton { close ->
            DropdownMenuItem(text = { Text("名前とメモ") }, onClick = {
              close()
              dialog = PlanDialog.Title
            })
            DropdownMenuItem(text = { Text("日付と日数") }, onClick = {
              close()
              dialog = PlanDialog.Dates
            }, modifier = Modifier.testTag("planMenuDates"))
            // 結び付きは保存を待たずに入るので、まだ作られていない（一度も保存していない）計画には出さない。
            if (!edit.isNew) {
              DropdownMenuItem(text = { Text("経路を結び付ける") }, onClick = {
                close()
                dialog = PlanDialog.Tracks
              }, modifier = Modifier.testTag("planMenuLinkTracks"))
            }
            if (edit.draftId != null) {
              DropdownMenuItem(text = { Text("下書きを捨てる") }, onClick = {
                close()
                dialog = PlanDialog.DiscardDraft
              })
            }
            if (!edit.isNew) {
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text("この計画を削除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                  close()
                  viewModel.deletePlan()
                },
                modifier = Modifier.testTag("planMenuDelete"),
              )
            }
          }
        },
        status = {
          if (draftUpdatedAt != null) {
            MapNoticeBanner(
              text = "下書きを編集中（${draftStamp(draftUpdatedAt)} に残した）",
              icon = R.drawable.ic_edit,
              onClick = { dialog = PlanDialog.DiscardDraft },
            )
          }
          if (showPickBanner) {
            MapNoticeBanner(text = "地図をタップして足す場所を選ぶ", icon = R.drawable.ic_add, onClick = null)
          }
          if (!plan.isDayTrip) DayChips(plan = detail, selectedDay = day, onSelect = onSelectDay)
          regionChips()
        },
      )
    }

    if (activeSheet.detent != SheetDetent.FULL) {
      MapControls(
        bottomInset = if (activeSheet.detent == SheetDetent.HIDDEN) SheetRestoreButtonInset else mapBottomPadding,
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {
        if (shownItems.isNotEmpty()) {
          MapRoundButton(
            icon = R.drawable.ic_fit,
            contentDescription = "計画の全体を見る",
            onClick = { fittedFor = null },
          )
        }
        if (hasLocationPermission) {
          MapRoundButton(
            icon = R.drawable.ic_my_location,
            contentDescription = "現在地へ移動",
            onClick = {
              requestCurrentLatLng(context) { latLng ->
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, PLACE_ZOOM)) }
              }
            },
          )
        }
      }
    }

    // 登録のシートを出している間は、計画のシートを下げる（閉じれば戻る）。
    if (placeSheetTarget == null) {
      FloatingSheet(
        state = sheetState,
        modifier = Modifier.align(Alignment.BottomCenter),
        restoreLabel = "▲ 計画に戻る",
      ) {
        PlanSheetContent(
          detail = detail,
          listState = sheetListState,
          selectedDay = day,
          actions = PlanSheetActions(
            onPickPlaces = { dialog = PlanDialog.Places },
            onSearchPlace = {
              mapSearchBias = cameraPositionState.projection?.visibleRegion?.latLngBounds?.toGeoBounds()
              viewModel.startPlaceSearch()
              mapSearchOpen = true
            },
            onPickOnMap = {
              pickOnMap = true
              sheetState.settleTo(SheetDetent.HIDDEN)
            },
            onAddMemo = { dialog = PlanDialog.NewMemo },
            onSummaryClick = { dialog = PlanDialog.Dates },
            onDayClick = { onSelectDay(if (day == it) null else it) },
            onRemoveDay = viewModel::removeDay,
            onItemClick = { item ->
              val place = item.place
              if (place == null) {
                dialog = PlanDialog.EditItem(item.id)
              } else {
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), PLACE_ZOOM)) }
              }
            },
            onEdit = { dialog = PlanDialog.EditItem(it.id) },
            onMoveToDay = { dialog = PlanDialog.MoveDay(it.id) },
            onOpenPlace = { item -> item.place?.let { onOpenPlace(it.placeId) } },
            onRemove = { viewModel.removeItem(it.id) },
            onReorder = viewModel::reorder,
            onOpenTrack = { onOpenTrack(it.id) },
            onUnlinkTrack = { viewModel.setTrackLinked(it.id, linked = false) },
            onLinkTracks = { dialog = PlanDialog.Tracks }.takeIf { !edit.isNew },
          ),
          actuals = actuals,
          liveDistanceMeters = liveTrack?.totalDistanceMeters,
        )
      }
    }

    // 新しく登録する場所のシート（場所タブ・記録画面と同じ登録のシートに、足す日と「登録して N日目に足す」を足す）。
    // 近くに登録済みの場所があれば、その場所を使うか新しく作るかを聞く（ADR-0009）。
    PlaceEditHost(
      placeEdit = viewModel.placeEdit,
      target = placeSheetTarget,
      onDismiss = { placeSheetTarget = null },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      sheetState = placeSheetState,
      registerOptions = registerOptions,
    )

    snackbarHost(Modifier.align(Alignment.BottomCenter))

    // 地図はそのままに上端へ検索窓を重ねる（記録画面と同じ窓）。
    if (mapSearchOpen) {
      TrackingMapSearch(
        registerEnabled = true,
        selectedMode = mapSearchMode,
        onSelectMode = viewModel::setMapSearchMode,
        bias = mapSearchBias,
        onPredictPlaces = viewModel::predictPlacesNear,
        onFetchPlace = viewModel.placeEdit::fetchPlaceResult,
        onPredictRegions = viewModel::predictRegions,
        onFetchRegion = viewModel::fetchRegion,
        regionMarkers = regionMarkers,
        // 場所: 閉じて、その場所へ地図を寄せ、登録のシートを出す。
        onPlaceSelected = { result ->
          mapSearchOpen = false
          openRegister(PlaceSheetTarget.NewSearchResult(result))
        },
        // 地域: 閉じて、目印を出し、その地域の範囲へ地図を移す。
        onRegionSelected = { region ->
          mapSearchOpen = false
          viewModel.regionMarkers.show(region)
          scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(region.bounds.toLatLngBounds(), with(density) { FitPadding.roundToPx() })) }
        },
        onClose = { mapSearchOpen = false },
        modifier = Modifier.align(Alignment.TopCenter),
        belowModes = regionChips,
      )
    }

    // 場所を選ぶ（場所タブの一覧・地図を選ぶモードで、詳細の上に全画面で重ねる）。
    // 経路を結び付ける（全画面。押したらすぐ結び付く・外れる）。
    if (dialog == PlanDialog.Tracks) {
      PlanTrackPicker(
        tracks = viewModel.actuals.allTracks,
        linkedTrackIds = actuals.linkedTrackIds,
        trackPlans = viewModel.actuals.trackPlans,
        planId = edit.saved?.plan?.id ?: 0L,
        onToggle = { track, linked -> viewModel.setTrackLinked(track.id, linked) },
        onClose = { dialog = null },
      )
    }

    if (dialog == PlanDialog.Places) {
      PlacePickerRoute(
        viewModel = placesViewModel,
        destination = destination,
        onAdd = {
          scrollToDay = addDay
          viewModel.addPlaces(addDay, it)
        },
        onClose = { dialog = null },
      )
    }
  }

  when (val d = dialog) {
    null, PlanDialog.Places, PlanDialog.Tracks -> Unit

    PlanDialog.Title -> PlanTitleDialog(plan = plan, onSave = viewModel::updateTitleAndNote, onDismiss = { dialog = null })

    PlanDialog.Dates -> PlanDatesDialog(
      plan = plan,
      slots = detail.items.map { PlanSlot(it.id, it.dayIndex, it.position) },
      onSave = viewModel::setDates,
      onDismiss = { dialog = null },
      overlapOf = viewModel::overlappingPlan,
      outsideTracksOf = { start, days -> viewModel.tracksOutside(start, days).map { trackTitle(it, ZoneId.systemDefault(), LocalDate.now()) } },
    )

    PlanDialog.NewMemo -> PlanItemDialog(
      item = null,
      onSave = { title, time, _, note ->
        scrollToDay = addDay
        viewModel.addMemo(addDay, title.orEmpty(), time, note)
      },
      onDismiss = { dialog = null },
    )

    PlanDialog.Leave -> LeaveDialog(
      isNew = edit.isNew,
      onSave = {
        dialog = null
        viewModel.save(leave = true)
      },
      onSaveDraft = {
        dialog = null
        viewModel.saveDraft(leave = true)
      },
      onDiscard = {
        dialog = null
        onBack()
      },
      onDismiss = { dialog = null },
    )

    PlanDialog.DiscardDraft -> AlertDialog(
      onDismissRequest = { dialog = null },
      title = { Text("下書きを捨てますか？") },
      text = { Text(if (edit.isNew) "この計画はまだ保存していないので、計画ごと無くなります。" else "保存してある計画の中身に戻ります。") },
      confirmButton = {
        TextButton(onClick = {
          dialog = null
          viewModel.discardDraft()
        }) { Text("捨てる", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { dialog = null }) { Text("やめる") } },
    )

    is PlanDialog.AddFromMap -> AlertDialog(
      onDismissRequest = { dialog = null },
      title = { Text(d.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
      text = { Text("この場所を$destination。") },
      confirmButton = {
        TextButton(onClick = {
          dialog = null
          scrollToDay = addDay
          viewModel.addPlaces(addDay, listOf(d.placeId))
        }, modifier = Modifier.testTag("planAddFromMap")) { Text("足す") }
      },
      dismissButton = { TextButton(onClick = { dialog = null }) { Text("やめる") } },
    )

    is PlanDialog.EditItem -> ItemOrClose(detail, d.itemId, onMissing = { dialog = null }) { item ->
      PlanItemDialog(
        item = item,
        onSave = { title, time, stay, note -> viewModel.updateItem(item.id, title, time, stay, note) },
        onDismiss = { dialog = null },
      )
    }

    is PlanDialog.MoveDay -> ItemOrClose(detail, d.itemId, onMissing = { dialog = null }) { item ->
      PlanMoveDayDialog(
        plan = plan,
        current = item.dayIndex,
        onPick = { viewModel.moveItemToDay(item.id, it) },
        onDismiss = { dialog = null },
      )
    }
  }
}

/** 上のバーの「保存」。保存できる変更があるときだけ押せる。 */
@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    elevation = ButtonDefaults.buttonElevation(defaultElevation = PathlyElevation.floating, disabledElevation = PathlyElevation.floating),
    // 押せないときも地図の上で見えるよう、地は白くする。
    colors = ButtonDefaults.buttonColors(
      disabledContainerColor = MaterialTheme.colorScheme.surface,
      disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    ),
    contentPadding = PaddingValues(horizontal = PathlySpacing.md),
    modifier = Modifier
      .height(MapRoundButtonSize)
      .testTag("planSave"),
  ) {
    Text("保存")
  }
}

/** 上のバーの「下書き」（下書きに残す。一度も保存していない計画だけ）。画面は閉じない。 */
@Composable
private fun DraftSaveButton(enabled: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    elevation = ButtonDefaults.buttonElevation(defaultElevation = PathlyElevation.floating, disabledElevation = PathlyElevation.floating),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.primary,
      disabledContainerColor = MaterialTheme.colorScheme.surface,
      disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    ),
    contentPadding = PaddingValues(horizontal = PathlySpacing.md),
    modifier = Modifier
      .height(MapRoundButtonSize)
      .testTag("planSaveDraft"),
  ) {
    Text("下書き", maxLines = 1)
  }
}

/** 保存していない変更があるときの戻る（保存・下書きに残す・破棄。外を押せば編集を続ける）。 */
@Composable
private fun LeaveDialog(isNew: Boolean, onSave: () -> Unit, onSaveDraft: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("保存していない変更があります") },
    text = { Text(if (isNew) "保存しないと、この計画は作られません。" else "保存しないと、今の変更は計画に入りません。") },
    confirmButton = {
      Column(horizontalAlignment = Alignment.End) {
        TextButton(onClick = onSave, modifier = Modifier.testTag("planLeaveSave")) { Text("保存") }
        TextButton(onClick = onSaveDraft, modifier = Modifier.testTag("planLeaveDraft")) { Text("下書きに残す") }
        TextButton(onClick = onDiscard, modifier = Modifier.testTag("planLeaveDiscard")) {
          Text("破棄", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDismiss) { Text("編集を続ける") }
      }
    },
  )
}

/** [itemId] の行があれば [content] を出し、無ければ（外した・消えた）ダイアログを閉じる。 */
@Composable
private fun ItemOrClose(detail: PlanDetail, itemId: Long, onMissing: () -> Unit, content: @Composable (PlanItem) -> Unit) {
  val item = detail.items.firstOrNull { it.id == itemId }
  if (item == null) LaunchedEffect(itemId) { onMissing() } else content(item)
}

/** シートの一覧の中の、[day] 日目の見出しの位置（先頭の要約の次から数える）。 */
private fun headerIndexOf(detail: PlanDetail, day: Int): Int? {
  if (day !in 1..detail.plan.dayCount) return null
  // 要約（1）＋ それより前の日ごとに、見出し（1）＋ 行の数（行が無ければ空の案内 1）。
  var index = 1
  for (d in 1 until day) index += 1 + detail.itemsOn(d).size.coerceAtLeast(1)
  return index
}

/** 日のチップ（すべて／N日目）。横にスクロールし、選んだチップが見える所まで寄せる。点はその日の色。 */
@Composable
private fun DayChips(plan: PlanDetail, selectedDay: Int?, onSelect: (Int?) -> Unit) {
  val listState = rememberLazyListState()
  LaunchedEffect(selectedDay) {
    listState.animateScrollToItem(((selectedDay ?: 0) - 1).coerceAtLeast(0))
  }
  val chips = listOf<Int?>(null) + (1..plan.plan.dayCount).toList()
  LazyRow(
    state = listState,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("planDayChips"),
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.xs),
  ) {
    itemsIndexed(chips) { _, chipDay ->
      val selected = chipDay == selectedDay
      FilterChip(
        selected = selected,
        onClick = { onSelect(chipDay) },
        label = {
          Text(
            text = chipDay?.let { dayLabel(plan.plan, it) } ?: "すべて",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        leadingIcon = chipDay?.let { d -> { DayDot(planDayColor(d)) } },
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = FilterChipDefaults.filterChipElevation(elevation = PathlyElevation.floating),
        modifier = Modifier.testTag("planDayChip:${chipDay ?: 0}"),
      )
    }
  }
}

@Composable
private fun DayDot(color: Color) {
  Box(
    modifier = Modifier
      .size(10.dp)
      .clip(CircleShape)
      .background(color),
  )
}

/** 日のチップの行が上の区画で占める高さ（チップと間）。 */
private val DayChipRowHeight = 40.dp
private val FitPadding = 48.dp
private val RouteWidth = 4.dp
private const val PLACE_ZOOM = 16f

/** 下書きの帯が上の区画で占める高さ。 */
private val DraftBannerHeight = 48.dp

// 計画の番号のマーカーは、登録済みの場所のマーカーより上に描く。
private const val PLAN_MARKER_Z_INDEX = 5f

/** 下書きを残した日時（「11/27 0:23」）。 */
private fun draftStamp(date: java.util.Date): String = java.time.format.DateTimeFormatter.ofPattern("M/d H:mm")
  .format(date.toInstant().atZone(java.time.ZoneId.systemDefault()))

/** 登録のシートの対象の地点（地図をそこへ寄せる）。 */
private fun PlaceSheetTarget.registerLatLng(): LatLng? = when (this) {
  is PlaceSheetTarget.NewPoint -> latLng
  is PlaceSheetTarget.NewPoi -> poi.latLng
  is PlaceSheetTarget.NewSearchResult -> LatLng(result.latitude, result.longitude)
  is PlaceSheetTarget.Existing -> null
}

/** 登録のシートの「足す日」（1日目・2日目…）。横にスクロールする。点はその日の色。 */
@Composable
private fun RegisterDayChips(dayCount: Int, selected: Int, onSelect: (Int) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(RegisterDayRowHeight)
      .testTag("planRegisterDays"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("足す日", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.width(PathlySpacing.sm))
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(PathlySpacing.xs),
    ) {
      (1..dayCount).forEach { d ->
        FilterChip(
          selected = d == selected,
          onClick = { onSelect(d) },
          label = { Text("${d}日目", maxLines = 1) },
          leadingIcon = { DayDot(planDayColor(d)) },
          modifier = Modifier.testTag("planRegisterDay:$d"),
        )
      }
    }
  }
}

/** 登録のシートの「足す日」の行の高さ。 */
private val RegisterDayRowHeight = 48.dp
