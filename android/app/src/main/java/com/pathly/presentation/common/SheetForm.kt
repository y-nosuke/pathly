package com.pathly.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
 * [showActions] が false の間は操作の行をしまい、その分を本文に回す（押せるものが無いうちに場所を取らないように。
 * 場所詳細の「保存」だけの行は、変更があるときだけ下から出す）。
 */
@Composable
fun ColumnScope.SheetFormLayout(
  actions: @Composable FlowRowScope.() -> Unit,
  modifier: Modifier = Modifier,
  showActions: Boolean = true,
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
  SheetActionsBar(visible = showActions, actions = actions)
}

/**
 * シートの下端に固定する操作の行（区切りの線つき）。[actions] は右寄せ、[leading] は左端に置く。
 * [visible] が false の間はしまい、その分を上の本文に回す（出し入れは下から伸び縮みする）。
 *
 * [SheetFormLayout] のほか、一覧（LazyColumn）を本文にするシート（計画の詳細）でも、一覧の下にそのまま置いて使う。
 */
@Composable
fun ColumnScope.SheetActionsBar(
  visible: Boolean,
  modifier: Modifier = Modifier,
  leading: @Composable RowScope.() -> Unit = {},
  actions: @Composable FlowRowScope.() -> Unit,
) {
  AnimatedVisibility(visible = visible, modifier = modifier, enter = expandVertically(), exit = shrinkVertically()) {
    Column {
      HorizontalDivider()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sheetActions")
          .padding(horizontal = SheetContentHorizontalPadding, vertical = PathlySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        leading()
        // 文字が大きい設定でボタンが横に収まらないときは、次の行へ折り返す（右寄せのまま）。
        FlowRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm, Alignment.End),
          verticalArrangement = Arrangement.spacedBy(PathlySpacing.xs),
          itemVerticalAlignment = Alignment.CenterVertically,
          content = actions,
        )
      }
    }
  }
}
