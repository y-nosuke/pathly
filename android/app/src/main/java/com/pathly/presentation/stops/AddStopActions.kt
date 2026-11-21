package com.pathly.presentation.stops

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.presentation.common.MapSideActions
import com.pathly.ui.theme.PathlySpacing

/**
 * 立ち寄りを手で足す脇役の操作（記録画面の記録のシートと、経路詳細のシートで同じ名前・同じ並び）。
 * アイコンの下に名前を置いたボタンを左に寄せて並べる。並んだボタンは同じ重さにし、どれも押された状態には見せない。
 *
 * - 「今ここを立ち寄り」: 記録中だけ（[onAddHere] が null なら出さない）。地点を指していないので、近くの施設候補から選ばせる。
 *   現在地がまだ取れていないあいだは [addHereEnabled] を false にして押せなくする。
 * - 「地図で指して追加」: 地図で地点（施設・空き地点）を指して足す。[mapPickActive] の間だけ塗って「地図をタップ（やめる）」に
 *   なり、押すとやめる（記録画面のように、モードの間もこのボタンが見えている画面で使う）。軌跡の点が足りず到着/出発を
 *   決められない経路では [mapPickEnabled] を false にして押せなくする。
 */
@Composable
internal fun AddStopActions(
  onAddHere: (() -> Unit)?,
  addHereEnabled: Boolean,
  onMapPick: () -> Unit,
  modifier: Modifier = Modifier,
  mapPickActive: Boolean = false,
  mapPickEnabled: Boolean = true,
) {
  MapSideActions(modifier = modifier) {
    if (onAddHere != null) {
      SheetToolButton(icon = R.drawable.ic_place, label = "今ここを\n立ち寄り", onClick = onAddHere, enabled = addHereEnabled)
    }
    if (mapPickActive) {
      SheetToolButton(icon = R.drawable.ic_close, label = "地図をタップ\n（やめる）", onClick = onMapPick, active = true)
    } else {
      SheetToolButton(icon = R.drawable.ic_add, label = "地図で指して\n追加", onClick = onMapPick, enabled = mapPickEnabled)
    }
  }
}

/**
 * シートの中の脇役の操作のボタン（アイコンの下に名前、2 行まで。自動の折り返しは日本語の語の途中で切れるので、
 * 名前には改行を入れておく）。ふだんは枠だけ、[active]（モードに入っている間）だけ塗る。
 */
@Composable
private fun SheetToolButton(
  @DrawableRes icon: Int,
  label: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  active: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.medium,
    color = if (active) scheme.secondaryContainer else scheme.surface,
    contentColor = if (active) scheme.onSecondaryContainer else scheme.primary,
    border = if (active) null else BorderStroke(1.dp, scheme.outlineVariant),
    modifier = Modifier
      .width(SheetToolButtonWidth)
      .heightIn(min = SheetToolButtonMinHeight),
  ) {
    Column(
      modifier = Modifier
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
        .padding(horizontal = PathlySpacing.xs, vertical = PathlySpacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PathlySpacing.xxs, Alignment.CenterVertically),
    ) {
      Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) LocalContentColor.current else scheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** ボタンの幅（名前は日本語の区切りで自分で 2 行に分け、1 行 6 字ほどが収まる幅）。 */
private val SheetToolButtonWidth = 96.dp

/** ボタンの高さの下限（押しやすい大きさ）。 */
private val SheetToolButtonMinHeight = 72.dp

/** 押せないときの薄さ（Material の既定と同じ）。 */
private const val DISABLED_ALPHA = 0.38f
