package com.pathly.presentation.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.pathly.R
import com.pathly.domain.model.WishlistFlag

/**
 * 「行きたい」の旗。タップで付け外しする。
 * 見た目は 3 通り（いま行きたい＝塗り・行きたいで行ったことがある＝緑の輪郭の旗に丸いチェック・それ以外＝輪郭）。
 * 行きたいは行けば終わるので、終わった後も「かつて行きたいだった」ことが分かるようにする（→ adr/0036）。
 * 場所タブの一覧の行と、経路詳細の立ち寄りの行で同じ見た目にする。
 */
@Composable
internal fun WishlistFlagButton(
  flag: WishlistFlag,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
      painter = painterResource(
        when (flag) {
          WishlistFlag.ACTIVE -> R.drawable.ic_flag_filled
          WishlistFlag.FULFILLED -> R.drawable.ic_flag_check
          WishlistFlag.NONE -> R.drawable.ic_flag
        },
      ),
      contentDescription = when (flag) {
        WishlistFlag.ACTIVE -> "行きたいから外す"
        WishlistFlag.FULFILLED -> "行きたいにまた入れる"
        WishlistFlag.NONE -> "行きたいに追加"
      },
      // 行きたいで行った旗は、形（丸いチェック）だけだと輪郭の旗と見分けにくいので、「行った」の緑で塗る
      // （地図の訪問済みの丸と同じ色）。
      tint = when (flag) {
        WishlistFlag.ACTIVE -> MaterialTheme.colorScheme.primary
        WishlistFlag.FULFILLED -> MarkerVisitedGreen
        WishlistFlag.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
  }
}
