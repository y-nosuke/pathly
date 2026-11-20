package com.pathly.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.StatsBarChart
import com.pathly.presentation.common.StatsSectionTitle
import com.pathly.presentation.common.StatsTile
import com.pathly.presentation.common.StatsTileRow
import com.pathly.presentation.common.StatsTileTone
import com.pathly.ui.theme.PathlySpacing
import com.pathly.util.DateFormatters

/**
 * 履歴の統計のカードの中身（→ docs/specs/screens.md の「統計のカード」）。数字のタイル（回数・総距離・総時間）・
 * 月ごとの距離・いちばん長いお出掛け（押すと経路詳細へ）。経路が 0 件ならタイルだけ。
 */
@Composable
internal fun HistoryStatsCardContent(
  stats: HistoryStatistics,
  totalCount: Int,
  isFiltered: Boolean,
  onTrackClick: (GpsTrack) -> Unit,
) {
  StatsTileRow {
    StatsTile(
      icon = R.drawable.ic_route,
      value = if (isFiltered) "${stats.count} / $totalCount 回" else "${stats.count} 回",
      label = "回数",
      tone = StatsTileTone.PRIMARY,
    )
    StatsTile(icon = R.drawable.ic_straighten, value = formatTotalKm(stats.distanceMeters), label = "距離", tone = StatsTileTone.GREEN)
    StatsTile(icon = R.drawable.ic_schedule, value = formatDuration(stats.minutes), label = "時間", tone = StatsTileTone.TERTIARY)
  }
  val longest = stats.longest ?: return
  // 12 か月の外の経路だけなら、空の棒グラフは出さない。
  if (stats.maxMonthMeters > 0.0) {
    Column {
      StatsSectionTitle(title = "月ごとの距離", trailing = "今月 ${formatTotalKm(stats.thisMonthMeters)}")
      StatsBarChart(
        values = stats.monthly.map { it.meters },
        labels = stats.monthly.map { "${it.month.monthValue}" },
        modifier = Modifier.padding(top = PathlySpacing.xs),
      )
    }
  }
  Column {
    StatsSectionTitle(title = "いちばん長いお出掛け")
    LongestTrackRow(track = longest, onClick = { onTrackClick(longest) })
  }
}

/** いちばん長いお出掛けの行（名前・日付・距離・›）。押すと経路詳細へ。 */
@Composable
private fun LongestTrackRow(track: GpsTrack, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = PathlySpacing.xs)
      .testTag("statsLongestTrack"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = listTitleOf(track),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      // 名前が無ければ見出しが日付なので、日付は重ねて出さない。
      if (track.hasName) {
        Text(text = DateFormatters.shortDate(track.startTime), style = MaterialTheme.typography.bodySmall)
      }
    }
    Text(
      text = formatTotalKm(track.totalDistanceMeters),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = PathlySpacing.sm),
    )
    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
  }
}
