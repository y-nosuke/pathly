package com.pathly.presentation.stops

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.presentation.common.MapSideActions
import com.pathly.ui.theme.PathlySpacing

/**
 * 立ち寄りを手で足す脇役の操作（記録画面の記録のシートと、経路詳細のシートで同じ名前・同じ並び・同じ形）。
 * 左に小さな見出し「立ち寄りを足す」を置き、アイコンの下に短い名前（1 行）を置いた小さなボタンを並べる。短い名前で
 * 何のボタンか分かるよう見出しで補い、長押しで元の名前（「今ここを立ち寄り」など）を吹き出しに出す。読み上げも元の名前。
 * 並んだボタンは同じ重さにし、どれも押された状態には見せない。
 *
 * - 「今ここ」（今ここを立ち寄り）: 記録中だけ（[onAddHere] が null なら出さない）。地点を指していないので、近くの施設候補から
 *   選ばせる。現在地がまだ取れていないあいだは [addHereEnabled] を false にして押せなくする。
 * - 「地図で指す」（地図で指して追加）: 地図で地点（施設・空き地点）を指して足す。[mapPickActive] の間だけ塗って「やめる」に
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
    Text(
      text = "立ち寄りを\n足す",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (onAddHere != null) {
      SheetToolButton(
        icon = R.drawable.ic_place,
        label = "今ここ",
        fullName = ADD_HERE_NAME,
        onClick = onAddHere,
        enabled = addHereEnabled,
      )
    }
    if (mapPickActive) {
      SheetToolButton(
        icon = R.drawable.ic_close,
        label = "やめる",
        fullName = MAP_PICK_STOP_NAME,
        onClick = onMapPick,
        active = true,
      )
    } else {
      SheetToolButton(
        icon = R.drawable.ic_add,
        label = "地図で指す",
        fullName = MAP_PICK_NAME,
        onClick = onMapPick,
        enabled = mapPickEnabled,
      )
    }
  }
}

/** 「今ここ」のボタンの元の名前（長押しの吹き出し・読み上げ・仕様書の呼び名）。 */
internal const val ADD_HERE_NAME = "今ここを立ち寄り"

/** 「地図で指す」のボタンの元の名前。 */
internal const val MAP_PICK_NAME = "地図で指して追加"

/** 地図で指すモードの間の「やめる」のボタンの元の名前。 */
internal const val MAP_PICK_STOP_NAME = "地図で指すのをやめる"

/**
 * シートの中の脇役の操作の小さなボタン（アイコンの下に短い名前、1 行）。ふだんは枠だけ、[active]（モードに入っている間）
 * だけ塗る。長押しで [fullName] を吹き出しに出し、読み上げも [fullName]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetToolButton(
  @DrawableRes icon: Int,
  label: String,
  fullName: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  active: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(fullName) } },
    state = rememberTooltipState(),
  ) {
    Surface(
      onClick = onClick,
      enabled = enabled,
      shape = MaterialTheme.shapes.medium,
      color = if (active) scheme.secondaryContainer else scheme.surface,
      contentColor = if (active) scheme.onSecondaryContainer else scheme.primary,
      border = if (active) null else BorderStroke(1.dp, scheme.outlineVariant),
      modifier = Modifier
        .width(SHEET_TOOL_BUTTON_WIDTH)
        .heightIn(min = SHEET_TOOL_BUTTON_MIN_HEIGHT)
        .semantics { contentDescription = fullName },
    ) {
      Column(
        modifier = Modifier
          .alpha(if (enabled) 1f else DISABLED_ALPHA)
          .padding(horizontal = PathlySpacing.xxs, vertical = PathlySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PathlySpacing.xxs, Alignment.CenterVertically),
      ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = if (active) scheme.onSecondaryContainer else scheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** ボタンの幅（短い名前「地図で指す」が 1 行に収まる幅）。 */
private val SHEET_TOOL_BUTTON_WIDTH = 72.dp

/** ボタンの高さの下限（押しやすい大きさ）。 */
private val SHEET_TOOL_BUTTON_MIN_HEIGHT = 56.dp

/** 押せないときの薄さ（Material の既定と同じ）。 */
private const val DISABLED_ALPHA = 0.38f
