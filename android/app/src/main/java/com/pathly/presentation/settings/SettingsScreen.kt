package com.pathly.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val interval by viewModel.gpsIntervalSeconds.collectAsStateWithLifecycle()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "設定",
      style = MaterialTheme.typography.headlineMedium,
    )

    Text(
      text = "GPS記録間隔",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 8.dp),
    )
    Text(
      text = "短いほど軌跡が滑らかですが電池を多く使います。長いほど省電力です。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    viewModel.gpsIntervalOptions.forEach { seconds ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .selectable(
            selected = interval == seconds,
            onClick = { viewModel.setGpsIntervalSeconds(seconds) },
          )
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = interval == seconds,
          onClick = { viewModel.setGpsIntervalSeconds(seconds) },
        )
        Text(
          text = gpsIntervalLabel(seconds),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }

    Text(
      text = "※ 変更は次に記録を開始したときから反映されます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

private fun gpsIntervalLabel(seconds: Int): String = when (seconds) {
  5 -> "5秒（高精度・電池大）"
  10 -> "10秒（標準）"
  30 -> "30秒（省電力）"
  60 -> "60秒（最省電力）"
  else -> "${seconds}秒"
}
