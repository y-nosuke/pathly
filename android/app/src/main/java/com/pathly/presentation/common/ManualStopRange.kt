package com.pathly.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.GpsPoint
import com.pathly.util.DateFormatters
import java.util.Date
import kotlin.math.cos
import kotlin.math.roundToInt

/** 手動追加の既定の滞在時間（この幅ぶん出発を後ろに置く）。 */
const val DEFAULT_MANUAL_STAY_MILLIS = 3 * 60 * 1000L

/** 指定座標に最も近い軌跡点のインデックス（経度は緯度で補正した簡易距離）。 */
fun nearestPointIndex(points: List<GpsPoint>, lat: Double, lng: Double): Int {
  if (points.isEmpty()) return 0
  var best = 0
  var bestD = Double.MAX_VALUE
  points.forEachIndexed { i, p ->
    val dLat = p.latitude - lat
    val dLng = (p.longitude - lng) * cos(Math.toRadians(lat))
    val d = dLat * dLat + dLng * dLng
    if (d < bestD) {
      bestD = d
      best = i
    }
  }
  return best
}

/** 到着インデックスから、既定の滞在時間ぶん後ろの出発インデックスを求める。 */
fun defaultDepartureIndex(points: List<GpsPoint>, arrivalIdx: Int): Int {
  if (points.isEmpty()) return arrivalIdx
  val arrivalMillis = points[arrivalIdx].timestamp.time
  var idx = arrivalIdx
  while (idx + 1 < points.size && points[idx + 1].timestamp.time - arrivalMillis < DEFAULT_MANUAL_STAY_MILLIS) {
    idx++
  }
  return idx
}

/**
 * 手動追加の滞在区間（到着〜出発）を軌跡点のインデックスで調整するUI。スライダーで大まかに、
 * ＋/− で1点ずつ微調整する。記録画面・履歴詳細の手動追加で共用する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopRangeEditor(
  arrivalTime: Date,
  departureTime: Date,
  arrivalIdx: Int,
  departureIdx: Int,
  lastIdx: Int,
  onRangeChange: (start: Int, end: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val durationMinutes = ((departureTime.time - arrivalTime.time) / 1000 / 60).toInt()
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "滞在${durationMinutes}分（青いハイライトが滞在区間）",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "スライダーで大まかに、＋/− で1点ずつ微調整できます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RangeSlider(
      value = arrivalIdx.toFloat()..departureIdx.toFloat(),
      onValueChange = { range ->
        val start = range.start.roundToInt().coerceIn(0, lastIdx)
        val end = range.endInclusive.roundToInt().coerceIn(start, lastIdx)
        onRangeChange(start, end)
      },
      valueRange = 0f..lastIdx.toFloat(),
    )
    // 微調整（1点ずつ）。到着は出発を超えられず、出発は到着を下回れない。
    StepperRow(
      label = "到着",
      time = arrivalTime,
      minusEnabled = arrivalIdx > 0,
      plusEnabled = arrivalIdx < departureIdx,
      onMinus = { onRangeChange((arrivalIdx - 1).coerceAtLeast(0), departureIdx) },
      onPlus = { onRangeChange((arrivalIdx + 1).coerceAtMost(departureIdx), departureIdx) },
    )
    StepperRow(
      label = "出発",
      time = departureTime,
      minusEnabled = departureIdx > arrivalIdx,
      plusEnabled = departureIdx < lastIdx,
      onMinus = { onRangeChange(arrivalIdx, (departureIdx - 1).coerceAtLeast(arrivalIdx)) },
      onPlus = { onRangeChange(arrivalIdx, (departureIdx + 1).coerceAtMost(lastIdx)) },
    )
  }
}

/** 到着／出発を1点ずつ微調整する行（時刻表示＋ −/＋）。 */
@Composable
private fun StepperRow(
  label: String,
  time: Date,
  minusEnabled: Boolean,
  plusEnabled: Boolean,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = DateFormatters.TIME_FORMAT.format(time),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(start = 12.dp),
    )
    Spacer(modifier = Modifier.weight(1f))
    OutlinedButton(
      onClick = onMinus,
      enabled = minusEnabled,
      shape = CircleShape,
      contentPadding = PaddingValues(0.dp),
      modifier = Modifier.size(40.dp),
    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
    Spacer(modifier = Modifier.width(8.dp))
    OutlinedButton(
      onClick = onPlus,
      enabled = plusEnabled,
      shape = CircleShape,
      contentPadding = PaddingValues(0.dp),
      modifier = Modifier.size(40.dp),
    ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
  }
}
