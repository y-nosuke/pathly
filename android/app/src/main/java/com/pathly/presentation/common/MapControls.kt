package com.pathly.presentation.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pathly.ui.theme.PathlySpacing

// 地図の画面の型（docs/designs/ui-direction.md）の「右下」と、シートの中の「脇役の操作」。

/**
 * 右下の地図の操作（現在地・全体を見る）を縦に積む。シートの上端に沿わせるため、[bottomInset] に
 * いま出ているシートの高さ（段の確定値。畳んだシートは戻すボタンの分）を渡す。段が変わると動いて追いつく。
 *
 * 呼び出し側の Box で `.align(Alignment.BottomEnd)` して使う。
 */
@Composable
fun MapControls(
  bottomInset: Dp,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val inset by animateDpAsState(targetValue = bottomInset, label = "mapControlsInset")
  Column(
    modifier = modifier.padding(end = PathlySpacing.mapEdge, bottom = inset + PathlySpacing.mapEdge),
    verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
    content = content,
  )
}

/**
 * シートの中の脇役の操作を横に並べる器（記録中の「今ここを立ち寄り」「地図で指して追加」など）。
 * Material 3 Expressive を入れるとき（#201）、中身を浮かぶツールバーに差し替える場所（→ ADR 0041）。
 */
@Composable
fun MapSideActions(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = PathlySpacing.screenEdge),
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/** 畳んだシートの戻すボタン（[FloatingSheet] の restoreLabel）が占める高さ。右下の操作をその上に置くのに使う。 */
val SheetRestoreButtonInset: Dp = 64.dp
