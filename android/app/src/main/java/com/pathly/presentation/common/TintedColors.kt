package com.pathly.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 色 [color]（状態の色・数字の種類の色）を [base] の上に薄く敷いた地と、その上に載せる字の色。
 * 統計のタイル・場所の状態のボタンなど、「色付きの薄い地に同じ色の字」を描くところで共通に使う。
 *
 * 明るい表示では、薄い地の上だと元の色のままでは字が 4.5:1 に届かない（緑・紅で 3.7 ほど）ので、字を一段濃くする。
 * 暗い表示の状態の色は字用の淡い色なので、そのままで 5:1 を超える。
 */
@Composable
internal fun tintedColors(color: Color, base: Color = MaterialTheme.colorScheme.surfaceContainer): Pair<Color, Color> {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val container = color.copy(alpha = TINT_ALPHA).compositeOver(base)
  val content = if (dark) color else lerp(color, Color.Black, LIGHT_TEXT_DARKEN)
  return container to content
}

/** 地に敷く濃さ。 */
private const val TINT_ALPHA = 0.16f

/** 明るい表示で字を黒に寄せる割合（緑・紅・橙・藍の字が薄い地の上で 5:1 を超える）。 */
private const val LIGHT_TEXT_DARKEN = 0.2f
