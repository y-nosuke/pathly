package com.pathly.presentation.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.pathly.R

/**
 * 「行きたい」の旗。タップで付け外しする（ON＝塗り・OFF＝輪郭）。
 * 場所タブの一覧の行と、経路詳細の立ち寄りの行で同じ見た目にする。
 */
@Composable
internal fun WishlistFlagButton(
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
      painter = painterResource(
        if (active) R.drawable.ic_flag_filled else R.drawable.ic_flag,
      ),
      contentDescription = if (active) "行きたいから外す" else "行きたいに追加",
      tint = if (active) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
  }
}
