package com.pathly.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.TrackSmoother
import kotlin.math.roundToInt

// 補正パラメータの調整パネル（デバッグビルドのみ）。生データと補正後を数値で見比べる。

@Composable
internal fun TuningSheet(
  track: GpsTrack,
  params: SmoothingParams,
  onParamsChange: (SmoothingParams) -> Unit,
  modifier: Modifier = Modifier,
) {
  val smoothed = remember(track, params) { TrackSmoother.smooth(track.points, params) }
  val rawKm = (TrackSmoother.totalDistanceMeters(track.points) / 1000.0 * 100).roundToInt() / 100.0
  val smKm = (TrackSmoother.totalDistanceMeters(smoothed) / 1000.0 * 100).roundToInt() / 100.0
  val rawTurn = TrackSmoother.totalTurningDegrees(track.points).roundToInt()
  val smTurn = TrackSmoother.totalTurningDegrees(smoothed).roundToInt()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "補正の調整（デバッグ）",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "距離: 生 ${rawKm}km → 補正 ${smKm}km",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "曲がり角合計: 生 $rawTurn° → 補正 $smTurn°（小さいほど滑らか）",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ParamSlider(
      label = "速度上限（ジャンプ除外）: ${params.maxSpeedMps.roundToInt()} m/s",
      value = params.maxSpeedMps.toFloat(),
      valueRange = 10f..100f,
      onChange = { onParamsChange(params.copy(maxSpeedMps = it.toDouble())) },
    )
    ParamSlider(
      label = "平滑窓: ${params.window}",
      value = params.window.toFloat(),
      valueRange = 1f..15f,
      onChange = {
        val window = it.roundToInt().let { v -> if (v % 2 == 0) v + 1 else v }.coerceIn(1, 15)
        onParamsChange(params.copy(window = window))
      },
    )

    Text(
      text = "MAX_SPEED=${params.maxSpeedMps.roundToInt()}, WINDOW=${params.window}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

@Composable
internal fun ParamSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
    )
    Slider(
      value = value,
      onValueChange = onChange,
      valueRange = valueRange,
    )
  }
}
