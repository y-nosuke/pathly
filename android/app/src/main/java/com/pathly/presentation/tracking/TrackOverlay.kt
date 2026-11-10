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
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Polyline
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.history.listTitleOf
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

/**
 * 地図に重ねる過去の経路の色（[TRACK_OVERLAY_MAX] 色）。記録中の経路（オレンジ）・立ち寄りの帯（濃い青・ティール）と
 * 見分けやすく、地図の地の色に埋もれないものを選んである。
 */
internal val TrackOverlayColors = listOf(
  Color(0xFF1E88E5), // 青
  Color(0xFF43A047), // 緑
  Color(0xFF8E24AA), // 紫
  Color(0xFFD81B60), // 赤紫
  Color(0xFF6D4C41), // 茶
  Color(0xFF00897B), // 青緑
  Color(0xFF9E9D24), // 黄緑
  Color(0xFF37474F), // 墨
)

/** 過去の経路の線。記録中の経路より細く・半透明にして、記録中の線が上に見えるようにする。 */
@Composable
@GoogleMapComposable
internal fun TrackOverlayLines(tracks: List<OverlayTrack>) {
  tracks.forEach { overlay ->
    key(overlay.track.id) {
      val color = TrackOverlayColors[overlay.colorIndex].copy(alpha = 0.8f)
      // 記録中は現在地の更新のたびに地図が描き直されるので、点の変換は線が変わったときだけにする。
      val lines = remember(overlay.segments) {
        overlay.segments.map { segment -> segment.map { LatLng(it.latitude, it.longitude) } }
      }
      lines.forEach { points ->
        Polyline(points = points, color = color, width = 5f, zIndex = -1f)
      }
    }
  }
}

/** 記録画面の右上の「この範囲の経路」ボタン。重ねている経路があれば塗りつぶす。 */
@Composable
internal fun TrackOverlayButton(
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    shadowElevation = 4.dp,
    modifier = modifier,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_route),
      contentDescription = "この範囲の経路",
      tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(8.dp),
    )
  }
}

/**
 * 地図に映っていた範囲の経路の一覧。チェックで複数選んで地図に重ねる（同時に [TRACK_OVERLAY_MAX] 本まで）。
 * 選んでいるが範囲の外にある経路は、外せるように下にまとめて出す。
 */
@Composable
internal fun TrackOverlaySheet(
  state: TrackOverlayState,
  sheetState: FloatingSheetState,
  onToggle: (GpsTrack) -> Unit,
  onClearAll: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FloatingSheet(state = sheetState, modifier = modifier.testTag("trackOverlaySheet"), restoreLabel = "この範囲の経路") {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (state.isSearching) "この範囲の経路" else "この範囲の経路 ${state.inRange.size} 件",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "地図に重ねる経路を選ぶ（${state.selected.size} / $TRACK_OVERLAY_MAX 本）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (state.selected.isNotEmpty()) {
        TextButton(onClick = onClearAll) { Text("すべて外す") }
      }
      IconButton(onClick = onClose) {
        Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "閉じる")
      }
    }

    when {
      state.isSearching -> Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
      }

      state.errorMessage != null -> Text(
        text = state.errorMessage,
        modifier = Modifier.padding(20.dp),
        color = MaterialTheme.colorScheme.error,
      )

      else -> {
        val outOfRange = state.selectedOutOfRange
        LazyColumn(
          modifier = Modifier
            .weight(1f, fill = false)
            .testTag("trackOverlayList"),
        ) {
          if (state.inRange.isEmpty()) {
            item {
              Text(
                text = "この範囲を通った経路はありません",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          items(state.inRange, key = { it.id }) { track ->
            TrackOverlayRow(track, state, onToggle)
          }
          if (outOfRange.isNotEmpty()) {
            item {
              HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
              Text(
                text = "範囲の外（重ねている経路）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
              )
            }
            items(outOfRange, key = { "out-${it.id}" }) { track ->
              TrackOverlayRow(track, state, onToggle)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TrackOverlayRow(track: GpsTrack, state: TrackOverlayState, onToggle: (GpsTrack) -> Unit) {
  val colorIndex = state.colorIndexOf(track.id)
  val checked = colorIndex != null
  // 上限まで選んでいるときは、まだ選んでいない経路を選べなくする（外すのはいつでもできる）。
  val enabled = checked || state.canSelectMore
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled) { onToggle(track) }
      .padding(start = 8.dp, end = 20.dp, top = 2.dp, bottom = 2.dp)
      .testTag("trackOverlayRow-${track.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Checkbox(checked = checked, onCheckedChange = { onToggle(track) }, enabled = enabled)
    // 地図の線と対応が分かるよう、選んだ経路には線と同じ色の印を出す。
    Box(
      modifier = Modifier
        .size(12.dp)
        .background(
          color = colorIndex?.let { TrackOverlayColors[it] } ?: Color.Transparent,
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
  }
}
