package com.pathly.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pathly.ui.theme.PathlySpacing

/** シートの本文の左右の余白。本文・見出しの行・下端の操作の行でそろえる。 */
val SheetContentHorizontalPadding = 20.dp

/**
 * シートの中身を「スクロールする本文（[content]）」と「下端に固定する操作の行（[actions]）」に分ける。
 * 保存・登録のような主役のボタンを、本文が長くてもスクロールせずに押せるようにする（→ docs/designs/ui.md）。
 *
 * [FloatingSheet] の content（つまみの行の下）に置く。本文は残りの高さを使い、操作の行は右寄せで並べる。
 */
@Composable
fun ColumnScope.SheetFormLayout(
  actions: @Composable FlowRowScope.() -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .weight(1f)
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SheetContentHorizontalPadding)
      .padding(bottom = PathlySpacing.lg),
    content = content,
  )
  HorizontalDivider()
  // 文字が大きい設定でボタンが横に収まらないときは、次の行へ折り返す（右寄せのまま）。
  FlowRow(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("sheetActions")
      .padding(horizontal = SheetContentHorizontalPadding, vertical = PathlySpacing.md),
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm, Alignment.End),
    verticalArrangement = Arrangement.spacedBy(PathlySpacing.xs),
    itemVerticalAlignment = Alignment.CenterVertically,
    content = actions,
  )
}
