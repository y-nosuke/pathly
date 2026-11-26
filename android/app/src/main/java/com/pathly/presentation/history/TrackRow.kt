package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.TrackGrouping
import com.pathly.presentation.common.ActiveFilter
import com.pathly.presentation.common.ActiveFilterChips
import com.pathly.presentation.common.CategoryFilterSection
import com.pathly.presentation.common.ExpandableListStats
import com.pathly.presentation.common.FilterChoiceSection
import com.pathly.presentation.common.FilterPanelButton
import com.pathly.presentation.common.FilterPanelCloseButton
import com.pathly.presentation.common.ListDisplayMenuButton
import com.pathly.presentation.common.ListFilterPanel
import com.pathly.presentation.common.ListGroup
import com.pathly.presentation.common.ListGroupHeader
import com.pathly.presentation.common.ListGroupIndent
import com.pathly.presentation.common.ListGroupRow
import com.pathly.presentation.common.ListHeader
import com.pathly.presentation.common.ListMapLayout
import com.pathly.presentation.common.ListMapMode
import com.pathly.presentation.common.ListMapToggle
import com.pathly.presentation.common.ListMenuChoice
import com.pathly.presentation.common.ListMenuSectionTitle
import com.pathly.presentation.common.ListToolRow
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.common.RegionFilterSection
import com.pathly.presentation.common.ScrollToTopButton
import com.pathly.presentation.common.ScrollToTopButtonClearance
import com.pathly.presentation.common.StatsBarItem
import com.pathly.presentation.common.StatsTileTone
import com.pathly.presentation.common.categoryChipLabel
import com.pathly.presentation.common.choiceLabel
import com.pathly.presentation.common.flippedChoice
import com.pathly.presentation.common.regionChipLabel
import com.pathly.presentation.common.rememberListMapCamera
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme
import com.pathly.util.DateFormatters
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToLong

/** 記録中のお出掛け（一覧の先頭の 1 行の帯）。押すと経路詳細へ。 */
@Composable
internal fun ActiveTrackBanner(track: GpsTrack, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = PathlySpacing.md, vertical = PathlySpacing.sm + PathlySpacing.xxs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .background(PathlyTheme.statusColors.visited, CircleShape),
      )
      Text(
        text = "記録中 ・ ${DateFormatters.shortTime(track.startTime)} から ・ ${formatKm(track.totalDistanceMeters)}",
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
    }
  }
}

/**
 * 経路の 1 行（影の無い平らな行。下に区切り線）。見出しは名前があれば名前（無ければ日付）、その下に日付・時刻・距離・立ち寄りの数。
 * 右はその場で切り替えるお気に入りと、⋮（名前を付ける）だけ。削除は経路詳細の ⋮ にある。
 */
@Composable
internal fun TrackRow(
  track: GpsTrack,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRenameClick: () -> Unit,
  modifier: Modifier = Modifier,
  indent: Dp = 0.dp,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .padding(start = PathlySpacing.screenEdge + indent, end = PathlySpacing.xs, top = PathlySpacing.sm, bottom = PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (track.hasName) track.name.orEmpty() else DateFormatters.shortDate(track.startTime),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        // 幅が足りないときは、まとまりごと次の行へ送る（日本語は仮名の間でも折り返すので、1 つの文にすると
        // 「立ち寄|り 3」のように切れる）。
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.xs)) {
          val parts = trackRowSummaryParts(track)
          parts.forEachIndexed { index, part ->
            Text(
              // 区切りは前のまとまりの後ろに付ける（折り返した行が「・」から始まらないように）。
              text = if (index == parts.lastIndex) part else "$part ・",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }

      // お気に入りはその場で切り替える。
      IconButton(onClick = onToggleFavorite) {
        Icon(
          painter = painterResource(if (track.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
          contentDescription = if (track.isFavorite) "お気に入り解除" else "お気に入り",
          tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Box {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
          Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = "その他",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(if (track.hasName) "名前を編集" else "名前を付ける") },
            onClick = {
              menuOpen = false
              onRenameClick()
            },
          )
        }
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.testTag("trackRowDivider"))
  }
}

/** 行の 2 行目のまとまり。「2026/11/08(日) 10:00–15:20」「1.8 km」「立ち寄り 3」。日付が見出しのとき（名前なし）は日付を省く。 */
internal fun trackRowSummaryParts(track: GpsTrack): List<String> {
  val time = DateFormatters.shortTime(track.startTime) + (track.endTime?.let { "–" + DateFormatters.shortTime(it) } ?: "")
  val head = if (track.hasName) "${DateFormatters.shortDate(track.startTime)} $time" else time
  return listOf(head, formatKm(track.totalDistanceMeters), "立ち寄り ${track.stopCount}")
}

/**
 * 統計の帯のタイル。回数「12 回」・距離「148.3 km」・時間「41 時間 20 分」（カードのタイルと同じ色）。絞り込み中は、
 * 絞った数字を全体と取り違えないよう「12 / 38 回」と全体の数を添える（一致していても絞り込み中であることは出す）。
 */
internal fun historyStatsBarItems(tracks: List<GpsTrack>, totalCount: Int, isFiltered: Boolean): List<StatsBarItem> {
  val count = if (isFiltered) "${tracks.size} / $totalCount 回" else "${tracks.size} 回"
  val distanceMeters = tracks.sumOf { it.totalDistanceMeters }
  val minutes = tracks.sumOf { durationMinutesOf(it) }
  return listOf(
    StatsBarItem(icon = R.drawable.ic_route, value = count, label = "回数", tone = StatsTileTone.PRIMARY),
    StatsBarItem(icon = R.drawable.ic_straighten, value = formatTotalKm(distanceMeters), label = "距離", tone = StatsTileTone.GREEN),
    StatsBarItem(icon = R.drawable.ic_schedule, value = formatDuration(minutes), label = "時間", tone = StatsTileTone.TERTIARY),
  )
}

/** 年月のまとまりの見出しの右に出す件数と距離（「5 回 ・ 32.1 km」）。距離の書き方は統計の総距離と同じ。 */
internal fun trackGroupSummary(group: ListGroup<GpsTrack>): String = "${group.count} 回 ・ ${formatTotalKm(group.allItems().sumOf { it.totalDistanceMeters })}"

/** 合計の距離。100 km 以上は小数を落として 3 桁区切り（「3,815 km」）、それより短ければ小数 1 桁（「4.3 km」）。 */
internal fun formatTotalKm(meters: Double): String {
  val km = meters / 1000.0
  return if (km >= 100) String.format(Locale.ROOT, "%,d km", km.roundToLong()) else String.format(Locale.ROOT, "%.1f km", km)
}

/** 距離を小数 1 桁の km で（「1.8 km」）。 */
internal fun formatKm(meters: Double): String = String.format(Locale.ROOT, "%.1f km", meters / 1000.0)

/** 時間を「41 時間 20 分」「45 分」「3 時間」で。 */
internal fun formatDuration(minutes: Long): String {
  val h = minutes / 60
  val m = minutes % 60
  return when {
    h == 0L -> "$m 分"
    m == 0L -> "$h 時間"
    else -> "$h 時間 $m 分"
  }
}
