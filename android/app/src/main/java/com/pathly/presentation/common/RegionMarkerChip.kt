package com.pathly.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing

/**
 * 地域の地名のチップ（記録画面の地域の目印・場所一覧の地図の境界）。地名の部分で [onRefit]（範囲へ寄せ直す）、
 * × で [onClear]（その地域を外す）。[clearDescription] は × の読み上げ。[lifted] は長押しで掴んでいる間（影を濃くして浮かせる）。
 * [customActions] は地名の部分（読み上げで止まるところ）に付ける操作（並べ替えなど）。
 */
@Composable
internal fun RegionMarkerChip(
  name: String,
  focused: Boolean,
  onRefit: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
  clearDescription: String = "${name}の目印を外す",
  lifted: Boolean = false,
  customActions: List<CustomAccessibilityAction> = emptyList(),
) {
  Surface(
    modifier = modifier
      .testTag("regionMarkerChip")
      .semantics { selected = focused },
    shape = PathlyShapes.pill,
    color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    shadowElevation = if (lifted) PathlyElevation.sheet else PathlyElevation.floating,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier = Modifier
          .clickable(onClickLabel = "この地域へ地図を寄せる", onClick = onRefit)
          .semantics { if (customActions.isNotEmpty()) this.customActions = customActions }
          .padding(start = PathlySpacing.md, top = PathlySpacing.sm, bottom = PathlySpacing.sm, end = PathlySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_public),
          contentDescription = null,
          tint = PathlyMapColors.Region,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(PathlySpacing.sm))
        // 長い地名は幅を決めて省略する。
        Text(
          text = name,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 160.dp),
        )
      }
      // × は小さいので、押せる範囲は 48dp 四方を確保する（隣の地名＝寄せ直しを誤って押さないように）。
      Box(
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clickable(onClick = onClear),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_close),
          contentDescription = clearDescription,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
