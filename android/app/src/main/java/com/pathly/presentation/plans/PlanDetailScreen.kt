package com.pathly.presentation.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanSlot
import com.pathly.presentation.common.DefaultMapLocation
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.MapBackTitle
import com.pathly.presentation.common.MapControls
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapOverflowMenuButton
import com.pathly.presentation.common.MapRoundButton
import com.pathly.presentation.common.MapTopBar
import com.pathly.presentation.common.MapTopBarInset
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.SheetRestoreButtonInset
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberLocationPermitted
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.requestCurrentLatLng
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlySpacing
import kotlinx.coroutines.launch

/** 計画の詳細（地図の型 → docs/specs/plans.md の「計画の詳細」）。計画が消えたら [onBack]。 */
@Composable
fun PlanDetailRoute(
  viewModel: PlanDetailViewModel,
  onBack: () -> Unit,
  onDeleted: (title: String) -> Unit,
  onOpenPlace: (placeId: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val places by viewModel.places.collectAsStateWithLifecycle()
  val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is PlanDetailEvent.Deleted -> onDeleted(event.title)

        is PlanDetailEvent.Failed -> snackbarHostState.showSnackbar(event.message)

        is PlanDetailEvent.ItemRemoved -> {
          val result = snackbarHostState.showSnackbar("「${event.label}」を外しました", actionLabel = "取り消す", withDismissAction = true)
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
        detail = s.detail,
        places = places,
        selectedDay = selectedDay,
        onSelectDay = viewModel::selectDay,
        onBack = onBack,
        onOpenPlace = onOpenPlace,
        viewModel = viewModel,
      )
      SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
  }
}

/** 開いているダイアログ。 */
private sealed interface PlanDialog {
  data object Title : PlanDialog
  data object Dates : PlanDialog
  data object Places : PlanDialog
  data object NewMemo : PlanDialog
  data class EditItem(val itemId: Long) : PlanDialog
  data class MoveDay(val itemId: Long) : PlanDialog
}

@Composable
private fun PlanDetailContent(
  detail: PlanDetail,
  places: List<PlaceListItem>,
  selectedDay: Int?,
  onSelectDay: (Int?) -> Unit,
  onBack: () -> Unit,
  onOpenPlace: (Long) -> Unit,
  viewModel: PlanDetailViewModel,
) {
  val plan = detail.plan
  val reorderFailures by viewModel.reorderFailures.collectAsStateWithLifecycle()
  var dialog by remember { mutableStateOf<PlanDialog?>(null) }
  val sheetState = rememberFloatingSheetState()
  val sheetListState = rememberLazyListState()
  val mapBottomPadding = sheetState.heightOf(sheetState.detent)
  val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(DefaultMapLocation, 11f) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val density = LocalDensity.current
  val hasLocationPermission = rememberLocationPermitted()
  val infoWindow = rememberMapInfoWindowState()
  var mapLoaded by remember { mutableStateOf(false) }
  // 日のチップの行の分だけ、地図の上の余白を足す（マーカーがチップに隠れないように）。
  val topInset = MapTopBarInset + if (plan.isDayTrip) 0.dp else DayChipRowHeight
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
  // 足した後は、足した日の見出しへ寄せる（足した行が見える所へ。日帰りは末尾へ）。行が増えてから動かす。
  var scrollToDay by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(detail.items.size) {
    val target = scrollToDay ?: return@LaunchedEffect
    scrollToDay = null
    val index = if (plan.isDayTrip) sheetListState.layoutInfo.totalItemsCount - 1 else headerIndexOf(detail, target)
    index?.takeIf { it >= 0 }?.let { sheetListState.animateScrollToItem(it) }
  }
  // 日のチップを押したら、シートもその日の見出しへ寄せる。
  LaunchedEffect(day) {
    if (day == null || plan.isDayTrip) return@LaunchedEffect
    headerIndexOf(detail, day)?.let { sheetListState.animateScrollToItem(it) }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(top = topInset, bottom = mapBottomPadding),
      properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
      uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
      onMapLoaded = { mapLoaded = true },
    ) {
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
        val color = planDayColor(item.dayIndex)
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
        ) {
          PlanNumberBadge(number = number, color = color)
        }
      }
    }

    MapTopBar(
      modifier = Modifier.align(Alignment.TopCenter),
      leading = {
        MapBackTitle(
          title = plan.title,
          onBack = onBack,
          onTitleClick = { dialog = PlanDialog.Title },
          titleClickLabel = "名前とメモを編集",
          modifier = Modifier.weight(1f),
        )
      },
      actions = {
        MapOverflowMenuButton { close ->
          DropdownMenuItem(text = { Text("名前とメモ") }, onClick = {
            close()
            dialog = PlanDialog.Title
          })
          DropdownMenuItem(text = { Text("日付と日数") }, onClick = {
            close()
            dialog = PlanDialog.Dates
          }, modifier = Modifier.testTag("planMenuDates"))
          HorizontalDivider()
          DropdownMenuItem(
            text = { Text("この計画を削除", color = MaterialTheme.colorScheme.error) },
            onClick = {
              close()
              viewModel.deletePlan(plan.title)
            },
            modifier = Modifier.testTag("planMenuDelete"),
          )
        }
      },
      status = {
        if (!plan.isDayTrip) DayChips(plan = detail, selectedDay = day, onSelect = onSelectDay)
      },
    )

    if (sheetState.detent != SheetDetent.FULL) {
      MapControls(
        bottomInset = if (sheetState.detent == SheetDetent.HIDDEN) SheetRestoreButtonInset else mapBottomPadding,
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

    FloatingSheet(
      state = sheetState,
      modifier = Modifier.align(Alignment.BottomCenter),
      restoreLabel = "▲ 計画に戻る",
    ) {
      PlanSheetContent(
        detail = detail,
        listState = sheetListState,
        reorderFailures = reorderFailures,
        actions = PlanSheetActions(
          onAddPlaces = { dialog = PlanDialog.Places },
          onAddMemo = { dialog = PlanDialog.NewMemo },
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
          onRemove = { viewModel.removeItem(it.id, it.label) },
          onReorder = viewModel::reorder,
        ),
      )
    }
  }

  // 足す日: 選んでいる日（「すべて」なら最後の日）。
  val addDay = day ?: plan.dayCount
  when (val d = dialog) {
    null -> Unit

    PlanDialog.Title -> PlanTitleDialog(plan = plan, onSave = viewModel::updateTitleAndNote, onDismiss = { dialog = null })

    PlanDialog.Dates -> PlanDatesDialog(
      plan = plan,
      slots = detail.items.map { PlanSlot(it.id, it.dayIndex, it.position) },
      onSave = viewModel::setDates,
      onDismiss = { dialog = null },
    )

    PlanDialog.Places -> PlanPlacePicker(
      places = places,
      destination = if (plan.isDayTrip) "この計画に足す" else "${dayLabel(plan, addDay)} に足す",
      onAdd = {
        scrollToDay = addDay
        viewModel.addPlaces(addDay, it)
      },
      onDismiss = { dialog = null },
    )

    PlanDialog.NewMemo -> PlanItemDialog(
      item = null,
      onSave = { title, time, _, note ->
        scrollToDay = addDay
        viewModel.addMemo(addDay, title.orEmpty(), time, note)
      },
      onDismiss = { dialog = null },
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
