package com.pathly.presentation.stops

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.presentation.common.MapSideActions
import com.pathly.ui.theme.PathlySpacing

/**
 * 立ち寄りを手で足す脇役の操作（記録画面の記録のシートと、経路詳細のシートで同じ名前・同じ並び）。
 *
 * - 「今ここを立ち寄り」: 記録中だけ（[onAddHere] が null なら出さない）。地点を指していないので、近くの施設候補から選ばせる。
 *   現在地がまだ取れていないあいだは [addHereEnabled] を false にして押せなくする。
 * - 「地図で指して追加」: 地図で地点（施設・空き地点）を指して足す。[mapPickActive] の間は「地図をタップ（やめる）」になり、
 *   押すとやめる（記録画面のように、モードの間もこのボタンが見えている画面で使う）。軌跡の点が足りず到着/出発を
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
      FilledTonalButton(onClick = onAddHere, enabled = addHereEnabled, modifier = Modifier.weight(1f)) {
        Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(PathlySpacing.xs))
        Text("今ここを立ち寄り", maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    // 「今ここ」が無い（記録が終わった経路）ときは幅いっぱいにせず、ほかの脇役の操作と同じ大きさにする。
    val pickModifier = if (onAddHere != null) Modifier.weight(1f) else Modifier
    if (mapPickActive) {
      FilledTonalButton(onClick = onMapPick, modifier = pickModifier) {
        Text("地図をタップ（やめる）", maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    } else {
      OutlinedButton(onClick = onMapPick, enabled = mapPickEnabled, modifier = pickModifier) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(PathlySpacing.xs))
        Text("地図で指して追加", maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
