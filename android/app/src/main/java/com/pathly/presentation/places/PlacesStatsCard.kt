package com.pathly.presentation.places

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pathly.R
import com.pathly.presentation.common.StatsRing
import com.pathly.presentation.common.StatsSectionTitle
import com.pathly.presentation.common.StatsShare
import com.pathly.presentation.common.StatsShareBar
import com.pathly.presentation.common.StatsTile
import com.pathly.presentation.common.StatsTileRow
import com.pathly.presentation.common.StatsTileTone
import com.pathly.ui.theme.PathlySpacing

/**
 * 場所の統計のカードの中身（→ docs/specs/screens.md の「統計のカード」）。数字のタイル（場所の数・訪問済み・いま行きたい）・
 * 行きたいで行けたの輪・業種の内訳。場所が 0 件（絞り込みで残らない）ならタイルだけ。
 */
@Composable
internal fun PlacesStatsCardContent(
  statistics: PlacesStatistics,
  details: PlacesStatsDetails,
  totalCount: Int,
  isFiltered: Boolean,
) {
  StatsTileRow {
    StatsTile(
      icon = R.drawable.ic_place,
      value = if (isFiltered) "${statistics.placeCount} / $totalCount" else "${statistics.placeCount}",
      label = "場所",
      tone = StatsTileTone.PRIMARY,
    )
    StatsTile(icon = R.drawable.ic_check_circle, value = "${statistics.visitedCount}", label = "訪問済み", tone = StatsTileTone.GREEN)
    StatsTile(icon = R.drawable.ic_flag, value = "${details.wishlistedCount}", label = "いま行きたい", tone = StatsTileTone.WISHLIST)
  }
  if (statistics.placeCount == 0) return
  WishFulfilledRow(statistics)
  Column {
    StatsSectionTitle(title = "業種の内訳")
    StatsShareBar(
      shares = details.categories.map { StatsShare(it.label, it.count) },
      lastIsRest = details.categories.lastOrNull()?.facet == null,
      modifier = Modifier.padding(top = PathlySpacing.xs),
    )
  }
}

/** 行きたいで行けたの輪と数。行きたいにした場所が無ければ、輪の代わりに文言だけ。 */
@Composable
private fun WishFulfilledRow(statistics: PlacesStatistics) {
  val percent = statistics.fulfilledPercent
  if (percent == null) {
    Text(text = "行きたいにした場所はまだありません", style = MaterialTheme.typography.bodySmall)
    return
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    StatsRing(percent = percent)
    Column(modifier = Modifier.padding(start = PathlySpacing.md)) {
      Text(text = "行きたいで行けた", style = MaterialTheme.typography.labelMedium)
      Text(
        text = "${statistics.fulfilledCount} / ${statistics.wishedCount} か所",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
