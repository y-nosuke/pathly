package com.pathly.presentation.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathly.R
import com.pathly.ui.theme.PathlyMapColors

/** 立ち寄りの一覧の行の頭の丸。訪問順の番号で、地図の立ち寄りのピンの番号と対応する（経路詳細・記録画面で同じ見た目）。 */
@Composable
internal fun StopNumberBadge(number: Int, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(StopBadgeSize)
      .background(PathlyMapColors.Stop, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = "$number", color = PathlyMapColors.OnMarker, fontWeight = FontWeight.Bold, fontSize = 13.sp)
  }
}

/** 立ち寄り中（まだ番号の付いていない、滞在している最中の立ち寄り）の丸。地図の立ち寄り中のバッジと同じ色。 */
@Composable
internal fun CurrentStopListBadge(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(StopBadgeSize)
      .background(PathlyMapColors.CurrentStop, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_place),
      contentDescription = null,
      tint = PathlyMapColors.OnMarker,
      modifier = Modifier.size(16.dp),
    )
  }
}

private val StopBadgeSize = 26.dp
