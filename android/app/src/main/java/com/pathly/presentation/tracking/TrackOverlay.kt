package com.pathly.presentation.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.pathly.R
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.RouteBadgeMarker
import com.pathly.presentation.common.rememberMarkerIconCache
import com.pathly.presentation.history.listTitleOf
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

/**
 * 一覧の「この経路の全体を見る」（[focusRequest]）を受けて地図を寄せる（記録画面の経路・履歴の地図表示で共通）。
 * 線を読み終えてから寄せる（まだ重ねていない経路は、重ねて読み終えるまで待つ）。重ねていない経路なら寄せずに消す。
 *
 * @param ready 地図の大きさが決まって範囲で寄せられるか（読み込みを待つ地図向け）。false の間は待つ。
 * @param onFit 寄せる範囲。寄せたあと [onConsume] で合図を消す。
 */
@Composable
internal fun TrackFocusEffect(
  focusRequest: TrackFocusRequest?,
  selected: List<OverlayTrack>,
  onFit: (GeoBounds) -> Unit,
  onConsume: () -> Unit,
  ready: Boolean = true,
) {
  val target = focusRequest?.let { req -> selected.firstOrNull { it.track.id == req.trackId } }
  LaunchedEffect(focusRequest, target?.isLoaded, ready) {
    if (focusRequest == null || !ready) return@LaunchedEffect
    if (target == null) {
      onConsume()
      return@LaunchedEffect
    }
    if (!target.isLoaded) return@LaunchedEffect
    target.bounds?.let(onFit)
    onConsume()
  }
}

/**
 * 過去の経路の線と立ち寄り。線は地図の上で見失わない太さにし、白い縁取りで地図の道路と分ける。
 * 記録中の経路より下に描く（記録中の線が上に見える）。立ち寄りは線と同じ色の番号つきバッジで出す。
 */
@Composable
@GoogleMapComposable
internal fun TrackOverlayContent(tracks: List<OverlayTrack>, infoWindow: MapInfoWindowState) {
  val icons = rememberMarkerIconCache()
  tracks.forEach { overlay ->
    key(overlay.track.id) {
      val color = PathlyMapColors.Overlay[overlay.colorIndex]
      // 記録中は現在地の更新のたびに地図が描き直されるので、点の変換は線が変わったときだけにする。
      val lines = remember(overlay.segments) {
        overlay.segments.map { segment -> segment.map { LatLng(it.latitude, it.longitude) } }
      }
      lines.forEach { points ->
        Polyline(points = points, color = PathlyMapColors.OnMarker, width = 20f, zIndex = -2f)
        Polyline(points = points, color = color, width = 12f, zIndex = -1f)
      }
      overlay.stops.forEachIndexed { index, stop ->
        key(stop.id) {
          val state = remember(stop.place.latitude, stop.place.longitude) {
            MarkerState(position = LatLng(stop.place.latitude, stop.place.longitude))
          }
          val number = index + 1
          val icon = icons.getLabeled(OverlayStopIconKey(overlay.colorIndex, number), stop.place.displayName) {
            RouteBadgeMarker(bg = color, size = 28.dp) {
              Text(text = "$number", color = PathlyMapColors.OnMarker, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }
          MapMarker(
            icon = icon.icon,
            infoWindow = infoWindow,
            state = state,
            title = stop.place.displayName,
            snippet = "${listTitleOf(overlay.track)} ・ ${DateFormatters.shortTime(stop.arrivalTime)} ・ 滞在${stop.durationMinutes}分",
            anchor = icon.anchor,
          )
        }
      }
    }
  }
}

/** 重ねた経路の立ち寄りのバッジ（色と番号ごとに 1 回だけ描く）。 */
private data class OverlayStopIconKey(val colorIndex: Int, val number: Int)

/**
 * 地図に映っていた範囲の経路の一覧。チェックで複数選んで地図に重ねる（同時に [TRACK_OVERLAY_MAX] 本まで）。
 * 選んでいるが範囲の外にある経路は、外せるように下にまとめて出す。
 */
@Composable
internal fun TrackOverlaySheet(
  state: TrackOverlayState,
  sheetState: FloatingSheetState,
  onToggle: (GpsTrack) -> Unit,
  onFocus: (GpsTrack) -> Unit,
  onClearAll: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // 畳みきれないシートにしてある（呼び出し側）。開け閉めは右上の経路のボタンと × で行う。
  TrackPickerSheet(
    title = if (state.isSearching) "この範囲の経路" else "この範囲の経路 ${state.inRange.size} 件",
    listed = state.inRange,
    selected = state.selected,
    sheetState = sheetState,
    emptyText = "この範囲を通った経路はありません",
    outsideLabel = "範囲の外（重ねている経路）",
    onToggle = onToggle,
    onFocus = onFocus,
    onClearAll = onClearAll,
    modifier = modifier,
    isLoading = state.isSearching,
    errorMessage = state.errorMessage,
    onClose = onClose,
  )
}

/**
 * 経路を選んで地図に重ねるシート（記録画面の「この範囲の経路」と履歴の地図表示で共有）。[listed] の経路をチェックで
 * 複数選ぶ（同時に [TRACK_OVERLAY_MAX] 本まで）。選んでいるが [listed] に無い経路は、外せるように下にまとめて出す
 * （見出しは [outsideLabel]）。
 *
 * @param onClose あれば右上に × を出す（閉じられるシートのとき）。
 * @param onFitAll あれば、選んだ経路をすべて入るまで地図を寄せるボタンを出す。
 * @param onOpen あれば、行のタップでその経路を開く（選ぶのはチェックで）。無ければ行のタップで選ぶ。
 */
@Composable
internal fun TrackPickerSheet(
  title: String,
  listed: List<GpsTrack>,
  selected: List<OverlayTrack>,
  sheetState: FloatingSheetState,
  emptyText: String,
  outsideLabel: String,
  onToggle: (GpsTrack) -> Unit,
  onFocus: (GpsTrack) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
  isLoading: Boolean = false,
  errorMessage: String? = null,
  onClose: (() -> Unit)? = null,
  onFitAll: (() -> Unit)? = null,
  onOpen: ((GpsTrack) -> Unit)? = null,
  testTagPrefix: String = "trackOverlay",
) {
  FloatingSheet(state = sheetState, modifier = modifier.testTag("${testTagPrefix}Sheet")) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "地図に重ねる経路を選ぶ（${selected.size} / $TRACK_OVERLAY_MAX 本）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (selected.isNotEmpty()) {
        TextButton(onClick = onClearAll) { Text("すべて外す") }
        onFitAll?.let {
          IconButton(onClick = it) {
            Icon(painter = painterResource(R.drawable.ic_fit), contentDescription = "選んだ経路をすべて見る")
          }
        }
      }
      onClose?.let {
        IconButton(onClick = it) {
          Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "閉じる")
        }
      }
    }

    when {
      isLoading -> Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
      }

      errorMessage != null -> Text(
        text = errorMessage,
        modifier = Modifier.padding(20.dp),
        color = MaterialTheme.colorScheme.error,
      )

      else -> {
        val outside = selected.outsideOf(listed)
        val canSelectMore = selected.canSelectMore()
        LazyColumn(
          modifier = Modifier
            .weight(1f, fill = false)
            .testTag("${testTagPrefix}List"),
        ) {
          if (listed.isEmpty()) {
            item {
              Text(
                text = emptyText,
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          items(listed, key = { it.id }) { track ->
            TrackPickerRow(track, selected.colorIndexOf(track.id), canSelectMore, onToggle, onFocus, onOpen, testTagPrefix)
          }
          if (outside.isNotEmpty()) {
            item {
              HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
              Text(
                text = outsideLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
              )
            }
            items(outside, key = { "out-${it.id}" }) { track ->
              TrackPickerRow(track, selected.colorIndexOf(track.id), canSelectMore, onToggle, onFocus, onOpen, testTagPrefix)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TrackPickerRow(
  track: GpsTrack,
  colorIndex: Int?,
  canSelectMore: Boolean,
  onToggle: (GpsTrack) -> Unit,
  onFocus: (GpsTrack) -> Unit,
  onOpen: ((GpsTrack) -> Unit)?,
  testTagPrefix: String,
) {
  val checked = colorIndex != null
  // 上限まで選んでいるときは、まだ選んでいない経路を選べなくする（外すのはいつでもできる）。
  val enabled = checked || canSelectMore
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(
        if (onOpen != null) {
          Modifier.clickable { onOpen(track) }
        } else {
          Modifier.clickable(enabled = enabled) { onToggle(track) }
        },
      )
      .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
      .testTag("${testTagPrefix}Row-${track.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = { onToggle(track) },
      enabled = enabled,
      modifier = Modifier.testTag("${testTagPrefix}Check-${track.id}"),
    )
    // 地図の線と対応が分かるよう、選んだ経路には線と同じ色の印を出す。
    Box(
      modifier = Modifier
        .size(12.dp)
        .background(
          color = colorIndex?.let { PathlyMapColors.Overlay[it] } ?: Color.Transparent,
          shape = CircleShape,
        ),
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 8.dp),
    ) {
      Text(
        text = listTitleOf(track),
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val distanceKm = (track.totalDistanceMeters / 100).roundToInt() / 10.0
      // 未命名なら見出しが日付なので、2 行目は時刻だけにする（日付を二度出さない）。
      val start = if (track.hasName) {
        "${DateFormatters.shortDate(track.startTime)} ${DateFormatters.shortTime(track.startTime)}"
      } else {
        DateFormatters.shortTime(track.startTime)
      }
      Text(
        text = "$start ・ ${distanceKm}km",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (track.isFavorite) {
      Icon(
        painter = painterResource(R.drawable.ic_favorite_filled),
        contentDescription = "お気に入り",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
    }
    // その経路の全体が見えるまで地図を寄せる（まだ重ねていなければ重ねてから）。
    IconButton(
      onClick = { onFocus(track) },
      enabled = enabled,
      modifier = Modifier.testTag("${testTagPrefix}Focus-${track.id}"),
    ) {
      Icon(painter = painterResource(R.drawable.ic_fit), contentDescription = "この経路の全体を見る")
    }
  }
}
