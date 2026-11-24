package com.pathly.presentation.stops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.SheetContentHorizontalPadding
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.ui.theme.PathlySpacing

/**
 * 保存済みの立ち寄りの滞在期間（到着〜出発）を手で直すシート。
 *
 * 区切り方は位置と時間だけで決めているので、実態とずれることがある（信号待ちで切れる・
 * 屋内で座標が固まる）。ここで直せるのは **stops の到着・出発だけ**で、GPS 点・補正後の点は
 * 触らない（観測した事実は変えない → adr/0012・adr/0024）。
 *
 * 手動追加と同じ [StopRangeEditor] を使い、地図を見ながら調整できるよう非モーダルの
 * [FloatingSheet] で出す。他の立ち寄りと期間が重なっても（入れ子でも）止めない。
 */
@Composable
fun StopDurationSheet(
  stop: Stop,
  points: List<GpsPoint>,
  onConfirm: (arrivalIdx: Int, departureIdx: Int) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  // 滞在区間の変更を地図のハイライトへ伝える（到着〜出発のインデックス）。
  onRangeChange: (start: Int, end: Int) -> Unit = { _, _ -> },
  // 地図の下パディングを合わせたい画面は、自分で作った state を渡す。
  sheetState: FloatingSheetState = rememberFloatingSheetState(peekFraction = 0.5f),
  // 立ち寄り中（まだ保存していない）の到着だけを直す。出発は末尾（いま）に固定する。
  arrivalOnly: Boolean = false,
) {
  val lastIdx = (points.size - 1).coerceAtLeast(0)
  // 保存済みの時刻を、いちばん近い軌跡点に対応づけて始める。
  var arrivalIdx by remember(stop.id, points) {
    mutableIntStateOf(nearestIndexByTime(points, stop.arrivalTime))
  }
  var departureIdx by remember(stop.id, points) {
    mutableIntStateOf(if (arrivalOnly) lastIdx else nearestIndexByTime(points, stop.departureTime).coerceAtLeast(arrivalIdx))
  }
  val arrival = points[arrivalIdx].timestamp
  val departure = points[departureIdx].timestamp

  LaunchedEffect(arrivalIdx, departureIdx) { onRangeChange(arrivalIdx, departureIdx) }

  FloatingSheet(state = sheetState, modifier = modifier) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = SheetContentHorizontalPadding),
      verticalArrangement = Arrangement.spacedBy(PathlySpacing.md),
    ) {
      Text(
        text = stop.place.displayName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = if (arrivalOnly) "立ち寄り中の到着時刻を直します（GPSの記録そのものは変わりません）。" else "滞在期間だけを直します（GPSの記録そのものは変わりません）。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      StopRangeEditor(
        arrivalTime = arrival,
        departureTime = departure,
        arrivalIdx = arrivalIdx,
        departureIdx = departureIdx,
        lastIdx = lastIdx,
        onRangeChange = { s, e ->
          arrivalIdx = s
          departureIdx = e
        },
        arrivalOnly = arrivalOnly,
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = SheetContentHorizontalPadding)
        .padding(top = PathlySpacing.sm, bottom = PathlySpacing.lg),
      horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
    ) {
      TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
      Button(
        onClick = { onConfirm(arrivalIdx, departureIdx) },
        // 到着＝出発（滞在0分）は押し間違いでしか作れないので保存させない。
        enabled = departureIdx > arrivalIdx,
        modifier = Modifier.weight(1f),
      ) { Text("保存") }
    }
  }
}
