package com.pathly.presentation.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.pathly.R
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlanTrackSource
import com.pathly.presentation.common.ListGroupHeader
import com.pathly.presentation.common.ListHeader
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.MapLayerDivider
import com.pathly.presentation.common.MapLayersButton
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.common.RegisteredPlacesLayerItems
import com.pathly.presentation.common.rememberMarkerIconCache
import com.pathly.presentation.common.rememberStopLabelsVisible
import com.pathly.presentation.common.stopLabelGlyph
import com.pathly.presentation.history.formatKm
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlySpacing
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// 計画と実績（→ docs/specs/plans.md の「計画と実績」）: レイヤーの「結び付いた経路」・地図に重ねる線・
// 「経路を結び付ける」の全画面・シートの「実際のお出掛け」の行。

/** 上のバーのレイヤー［≋］: 結び付いた経路（スイッチ）と登録済みの場所。 */
@Composable
internal fun PlanLayersButton(
  registeredPlaces: MapRegisteredPlaces,
  linkedTrackCount: Int,
  showTracks: Boolean,
  onToggleTracks: () -> Unit,
) {
  MapLayersButton(active = registeredPlaces.shown || (showTracks && linkedTrackCount > 0), filtered = registeredPlaces.filtered) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .toggleable(value = showTracks, enabled = linkedTrackCount > 0, role = Role.Switch, onValueChange = { onToggleTracks() })
        .heightIn(min = 48.dp)
        .padding(horizontal = PathlySpacing.lg)
        .testTag("planLayerTracks"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val color = if (linkedTrackCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
      Icon(painter = painterResource(R.drawable.ic_route), contentDescription = null, tint = color)
      Spacer(modifier = Modifier.width(PathlySpacing.md))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = "実際のお出掛け（${linkedTrackCount}本）", style = MaterialTheme.typography.bodyLarge, color = color)
        Text(
          text = if (linkedTrackCount == 0) "記録の開始か、⋮ で結び付けた経路" else "━ 実際の経路（実線）　┅ 計画（点線）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = showTracks && linkedTrackCount > 0, onCheckedChange = null, enabled = linkedTrackCount > 0)
    }
    MapLayerDivider()
    RegisteredPlacesLayerItems(
      show = registeredPlaces.shown,
      onToggle = registeredPlaces.onToggle,
      filter = registeredPlaces.filter,
      places = registeredPlaces.places,
      onFilterChange = registeredPlaces.onFilterChange,
    )
  }
}

/**
 * 地図に重ねる実際の経路（結び付いた過去の経路と、記録中の経路）。計画の日の色と混ざらないよう一色にし、
 * 計画の直線・マーカーより奥に描く。
 */
@Composable
internal fun PlanActualTracks(lines: List<Pair<Color, List<List<GpsPoint>>>>, liveTrack: Pair<Color, GpsTrack>?) {
  val density = LocalDensity.current
  val width = with(density) { 4.dp.toPx() }
  val casing = with(density) { 7.dp.toPx() }
  val segments = remember(lines) {
    lines.flatMap { (color, segs) -> segs.filter { it.size >= 2 }.map { seg -> color to seg.map { LatLng(it.latitude, it.longitude) } } }
  }
  segments.forEach { (color, points) ->
    Polyline(points = points, color = PathlyMapColors.OnMarker, width = casing, zIndex = ACTUAL_TRACK_Z_INDEX - 0.1f)
    Polyline(points = points, color = color, width = width, zIndex = ACTUAL_TRACK_Z_INDEX)
  }
  // 記録中の経路は少し太くする（いま歩いている所が分かるように）。現在地は地図の青い点。
  val live = remember(liveTrack) {
    liveTrack?.let { (color, track) -> track.smoothedSegments.filter { it.size >= 2 }.map { seg -> color to seg.map { LatLng(it.latitude, it.longitude) } } }.orEmpty()
  }
  live.forEach { (color, points) ->
    Polyline(points = points, color = PathlyMapColors.OnMarker, width = casing + 2f, zIndex = ACTUAL_TRACK_Z_INDEX - 0.1f)
    Polyline(points = points, color = color, width = width + 2f, zIndex = ACTUAL_TRACK_Z_INDEX)
  }
}

/**
 * 地図に出す実際の立ち寄り（白地の角丸に、日ごとに着いた順の番号）。縁と数字は計画の日の色（[colorOf]）、立ち寄り中は記録画面と同じティール。
 * 計画の番号のマーカーより奥・小さめにする。押すと場所の名前と時刻の吹き出し。
 * 下に記録画面・経路詳細と同じ名札（業種のアイコン＋名前）を添え、引いた地図では番号だけにする（→ specs/map.md）。
 */
@Composable
internal fun PlanActualStops(stops: List<ActualStop>, infoWindow: MapInfoWindowState, colorOf: (ActualStop) -> Color) {
  val zone = remember { ZoneId.systemDefault() }
  val icons = rememberMarkerIconCache()
  val showLabels = rememberStopLabelsVisible()
  stops.forEach { actual ->
    val stop = actual.stop
    key(stop.id, actual.live) {
      val state = remember(stop.place.latitude, stop.place.longitude) { MarkerState(LatLng(stop.place.latitude, stop.place.longitude)) }
      val color = if (actual.live) PathlyMapColors.CurrentStop else colorOf(actual)
      val arrival = CLOCK.format(stop.arrivalTime.toInstant().atZone(zone))
      // 番号の印の真ん中を場所に置く（計画の番号の丸と同じ）。名札を足しても指す点は変えない。
      val icon = icons.getLabeled(
        ActualStopIconKey(actual.number, color),
        stop.place.displayName.takeIf { showLabels },
        stopLabelGlyph(stop.place),
        anchorAtCenter = true,
      ) {
        ActualStopBadge(number = actual.number, color = color)
      }
      MapMarker(
        icon = icon.icon,
        infoWindow = infoWindow,
        state = state,
        title = stop.place.displayName,
        snippet = if (actual.live) "$arrival – 滞在中" else "$arrival–${CLOCK.format(stop.departureTime.toInstant().atZone(zone))}",
        anchor = icon.anchor,
        zIndex = ACTUAL_STOP_Z_INDEX,
        bubbleColor = color,
      )
    }
  }
}

// 実際の立ち寄りの印の画像のキー（[MarkerIconCache]）。見た目を決める値をすべて持つ。
private data class ActualStopIconKey(val number: Int, val color: Color)

/**
 * 実際の立ち寄りの番号の印。計画の番号（日の色で塗った丸に白い数字）と見分けるよう、白地の角丸に色の縁と数字にする。
 * 地図とシートで同じものを使う。
 */
@Composable
internal fun ActualStopBadge(number: Int, color: Color, modifier: Modifier = Modifier, size: Dp = ACTUAL_STOP_SIZE) {
  Box(
    modifier = modifier
      .size(size)
      .clip(ActualStopShape)
      .background(PathlyMapColors.OnMarker)
      .border(2.dp, color, ActualStopShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "$number",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = color,
      maxLines = 1,
    )
  }
}

/** 立ち寄りの時刻（「10:05–10:40」・立ち寄り中は「13:20– 立ち寄り中」）。[withDate] なら日付を前に付ける。 */
internal fun actualStopTime(actual: ActualStop, withDate: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
  val arrival = actual.stop.arrivalTime.toInstant().atZone(zone)
  val date = if (withDate) "${formatPlanDate(arrival.toLocalDate())} " else ""
  return if (actual.live) "$date${CLOCK.format(arrival)}– 立ち寄り中" else "$date${CLOCK.format(arrival)}–${CLOCK.format(actual.stop.departureTime.toInstant().atZone(zone))}"
}

/** 計画の行に添える、実際に立ち寄った時刻の 1 行（番号の印・時刻）。 */
@Composable
internal fun ActualVisitLine(actual: ActualStop, color: Color, showDate: Boolean) {
  Row(
    modifier = Modifier
      .padding(top = PathlySpacing.xxs)
      .testTag("planItemActual:${actual.stop.id}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ActualStopBadge(number = actual.number, color = color, size = 18.dp)
    Spacer(modifier = Modifier.width(PathlySpacing.xs))
    Text(
      text = actualStopTime(actual, withDate = showDate) + if (actual.live) "" else " に寄った",
      style = MaterialTheme.typography.bodySmall,
      color = color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** 実際の立ち寄りの印は、ふつうのマーカー（1）より手前、計画の番号のマーカー（5）より奥。 */
private const val ACTUAL_STOP_Z_INDEX = 2f
private val ACTUAL_STOP_SIZE = 22.dp
private val ActualStopShape = RoundedCornerShape(6.dp)

/** 実際の経路は計画の直線（zIndex 0）より奥。 */
private const val ACTUAL_TRACK_Z_INDEX = -1f

/**
 * 「経路を結び付ける」（計画の詳細の上に全画面で重ねる）。経路を新しい順に月ごとに並べ、チェックで結び付ける・外す
 * （押したらすぐ入る）。ほかの計画に結び付いている経路は、押すとそちらから付け替える。名前で探せる。
 */
@Composable
internal fun PlanTrackPicker(
  tracks: StateFlow<List<GpsTrack>>,
  linkedTrackIds: Set<Long>,
  /** 経路が結び付いている計画（経路の id → 計画の id と名前）。 */
  trackPlans: StateFlow<Map<Long, Pair<Long, String>>>,
  /** この計画の id。 */
  planId: Long,
  onToggle: (GpsTrack, Boolean) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val all by tracks.collectAsStateWithLifecycle()
  val plansOfTracks by trackPlans.collectAsStateWithLifecycle()
  // ほかの計画に結び付いている経路を押したときの確認（付け替える経路と、今結び付いている計画の名前）。
  var reassign by remember { mutableStateOf<Pair<GpsTrack, String>?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  // 名前の検索を開いている間の戻るは、検索を閉じる（語も消す）。
  BackHandler {
    if (searchOpen || query.isNotEmpty()) {
      searchOpen = false
      query = ""
    } else {
      onClose()
    }
  }
  val zone = remember { ZoneId.systemDefault() }
  val today = remember { LocalDate.now() }
  val matched = remember(all, query) {
    val q = query.trim()
    all.filter { q.isEmpty() || it.name?.contains(q, ignoreCase = true) == true }.sortedByDescending { it.startTime }
  }
  val months = remember(matched) { matched.groupBy { it.startTime.toLocalDate(zone).withDayOfMonth(1) } }

  Surface(modifier = modifier.fillMaxSize().testTag("planTrackPicker"), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!searchOpen && query.isEmpty()) {
          IconButton(onClick = onClose) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
          }
        }
        ListHeader(
          title = "経路を結び付ける",
          query = query,
          onQueryChange = { query = it },
          searchPlaceholder = "経路の名前で探す",
          searchOpen = searchOpen,
          onSearchOpenChange = { searchOpen = it },
          modifier = Modifier.weight(1f),
        )
      }
      Text(
        text = "チェックした経路を計画に結び付けます。1 つの経路が結び付く計画は 1 つだけです。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.xs),
      )
      LazyColumn(modifier = Modifier.weight(1f).testTag("planTrackPickerList")) {
        if (matched.isEmpty()) {
          item(key = "empty") {
            Text(
              text = if (all.isEmpty()) "経路はまだありません" else "名前に合う経路がありません",
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(PathlySpacing.xxl),
            )
          }
        }
        months.forEach { (month, inMonth) ->
          item(key = "month:$month") {
            ListGroupHeader(
              label = if (month.year == today.year) "${month.monthValue}月" else "${month.year}年${month.monthValue}月",
              trailing = "${inMonth.size}",
              depth = 1,
              expanded = true,
              indent = 0.dp,
              testTag = "planTrackMonth:$month",
              onClick = null,
            )
          }
          items(inMonth, key = { it.id }) { track ->
            val checked = track.id in linkedTrackIds
            // ほかの計画に結び付いていれば、押すとそちらから付け替える。
            val other = plansOfTracks[track.id]?.takeIf { it.first != planId }?.second
            TrackPickRow(
              track = track,
              checked = checked,
              enabled = true,
              supporting = other?.let { "「$it」から付け替えます" },
              onToggle = {
                if (!checked && other != null) reassign = track to other else onToggle(track, !checked)
              },
              zone = zone,
              today = today,
            )
          }
        }
      }
    }
  }
  reassign?.let { (track, otherTitle) ->
    AlertDialog(
      onDismissRequest = { reassign = null },
      modifier = Modifier.testTag("planReassignConfirm"),
      title = { Text("ほかの計画に結び付いています") },
      text = { Text("この経路は「$otherTitle」に結び付いています。この計画に付け替えますか？（1 つの経路が結び付く計画は 1 つだけです）") },
      confirmButton = {
        TextButton(onClick = {
          reassign = null
          onToggle(track, true)
        }, modifier = Modifier.testTag("planReassignOk")) { Text("付け替える") }
      },
      dismissButton = { TextButton(onClick = { reassign = null }) { Text("やめる") } },
    )
  }
}

@Composable
private fun TrackPickRow(
  track: GpsTrack,
  checked: Boolean,
  enabled: Boolean,
  supporting: String?,
  onToggle: () -> Unit,
  zone: ZoneId,
  today: LocalDate,
) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
        .heightIn(min = 56.dp)
        .padding(horizontal = PathlySpacing.screenEdge - PathlySpacing.md)
        .testTag("planTrackPick:${track.id}"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(PathlySpacing.md))
      Column(modifier = Modifier.weight(1f).padding(vertical = PathlySpacing.sm)) {
        Text(
          text = trackTitle(track, zone, today),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        listOfNotNull(trackSubtitle(track, zone), supporting).joinToString("・").takeIf { it.isNotEmpty() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/** シートの「実際のお出掛け」の 1 行。押すと経路詳細、手で結び付けたものは ⋮ で外せる。 */
@Composable
internal fun LinkedTrackRow(
  linked: LinkedTrack,
  recording: Boolean,
  /** 経路の印の色（計画の日の色。日付の外・日付なしは灰青）。 */
  color: Color,
  liveDistanceMeters: Double?,
  /** 行を押した（地図でこの経路に寄せる）。 */
  onOpen: () -> Unit,
  /** ⋮「経路の詳細を開く」。 */
  onOpenDetail: () -> Unit,
  onUnlink: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val zone = remember { ZoneId.systemDefault() }
  val today = remember { LocalDate.now() }
  val track = linked.track
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "地図でこの経路に寄せる", onClick = onOpen)
      .heightIn(min = 48.dp)
      .padding(start = PathlySpacing.screenEdge, end = PathlySpacing.xs)
      .testTag("planLinkedTrack:${track.id}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_route),
      contentDescription = null,
      tint = color,
    )
    Spacer(modifier = Modifier.width(PathlySpacing.md))
    Column(modifier = Modifier.weight(1f).padding(vertical = PathlySpacing.sm)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (recording) RecordingMark(modifier = Modifier.padding(end = PathlySpacing.sm))
        Text(
          text = trackTitle(track, zone, today),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
      }
      val distance = if (recording) liveDistanceMeters else track.storedDistanceMeters
      Text(
        text = listOfNotNull(
          trackSubtitle(track, zone, distance),
          when (linked.source) {
            PlanTrackSource.START -> "自動で結び付け"
            PlanTrackSource.MANUAL -> "手動で結び付け"
          },
        ).joinToString("・"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    run {
      var open by remember { mutableStateOf(false) }
      Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("planLinkedTrackMenu:${track.id}")) {
          Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "経路の操作")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
          DropdownMenuItem(text = { Text("経路の詳細を開く") }, onClick = {
            open = false
            onOpenDetail()
          }, modifier = Modifier.testTag("planOpenTrackDetail"))
          DropdownMenuItem(text = { Text("結び付きを外す") }, onClick = {
            open = false
            onUnlink()
          }, modifier = Modifier.testTag("planUnlinkTrack"))
        }
      }
    }
  }
}

/** 「● 記録中」の印（計画の一覧の行・シートの行）。 */
@Composable
internal fun RecordingMark(modifier: Modifier = Modifier) {
  Row(modifier = modifier.testTag("planRecordingMark"), verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .padding(end = PathlySpacing.xxs)
        .size(8.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.error),
    )
    Text(text = "記録中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 1)
  }
}

private fun Date.toLocalDate(zone: ZoneId): LocalDate = toInstant().atZone(zone).toLocalDate()

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN)

/** 経路の 1 行目: 名前（無ければ日付と開始の時刻）。 */
internal fun trackTitle(track: GpsTrack, zone: ZoneId, today: LocalDate): String {
  val date = track.startTime.toLocalDate(zone)
  val name = track.name?.trim()?.takeIf { it.isNotEmpty() }
  return if (name != null) "${formatPlanDate(date, today)} $name" else "${formatPlanDate(date, today)} ${CLOCK.format(track.startTime.toInstant().atZone(zone))}〜"
}

/** 経路の 2 行目: 開始の時刻（名前の無い経路は 1 行目に出しているので省く）と距離。 */
internal fun trackSubtitle(track: GpsTrack, zone: ZoneId, distanceMeters: Double? = track.storedDistanceMeters): String? = listOfNotNull(
  (CLOCK.format(track.startTime.toInstant().atZone(zone)) + "〜").takeIf { track.hasName },
  distanceMeters?.let(::formatKm),
).joinToString("・").ifEmpty { null }
