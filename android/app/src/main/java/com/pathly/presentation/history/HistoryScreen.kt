package com.pathly.presentation.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
  modifier: Modifier = Modifier,
  viewModel: HistoryViewModel = hiltViewModel(),
  onTrackClick: (GpsTrack) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // 名前を編集中の経路（null=ダイアログ非表示）。
  var renameTarget by remember { mutableStateOf<GpsTrack?>(null) }
  // 削除確認中の経路（null=ダイアログ非表示）。削除は破壊的（点列・立ち寄りもCASCADEで消える）ため確認する。
  var deleteTarget by remember { mutableStateOf<GpsTrack?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    Text(
      text = "外出履歴",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 12.dp),
    )

    // 記録が1件でもあれば絞り込み・並べ替えバーを出す（空・記録中のみのときは邪魔なので隠す）。
    if (uiState.tracks.isNotEmpty()) {
      FilterSortBar(
        state = uiState,
        onClearFilters = viewModel::clearFilters,
        onFavoriteFilterChange = viewModel::setFavoriteFilter,
        onNamedFilterChange = viewModel::setNamedFilter,
        onStopFilterChange = viewModel::setStopFilter,
        onSortChange = viewModel::setSort,
        onToggleSortDirection = viewModel::toggleSortDirection,
      )
      Spacer(modifier = Modifier.height(8.dp))
    }

    when {
      uiState.isLoading -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }

      uiState.tracks.isEmpty() && uiState.activeTrack == null -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "記録がありません",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      else -> {
        val visibleTracks = uiState.visibleTracks
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item {
            StatisticsSummaryCard(tracks = uiState.tracks)
            Spacer(modifier = Modifier.height(16.dp))
          }

          // 記録中のアクティブトラックを表示
          uiState.activeTrack?.let { activeTrack ->
            item {
              ActiveTrackItem(
                track = activeTrack,
                onTrackClick = { onTrackClick(activeTrack) },
              )
              Spacer(modifier = Modifier.height(8.dp))
            }
          }

          if (visibleTracks.isEmpty()) {
            item {
              Text(
                text = "条件に合う記録がありません",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
              )
            }
          }

          items(visibleTracks) { track ->
            TrackItem(
              track = track,
              onTrackClick = { onTrackClick(track) },
              onToggleFavorite = { viewModel.toggleFavorite(track) },
              onRenameClick = { renameTarget = track },
              onDeleteClick = { deleteTarget = track },
            )
          }
        }
      }
    }

    uiState.errorMessage?.let { errorMessage ->
      LaunchedEffect(errorMessage) {
        viewModel.clearError()
      }
    }
  }

  renameTarget?.let { target ->
    RenameTrackDialog(
      initialName = target.name.orEmpty(),
      onDismiss = { renameTarget = null },
      onConfirm = { newName ->
        viewModel.renameTrack(target.id, newName)
        renameTarget = null
      },
    )
  }

  deleteTarget?.let { target ->
    val label = target.name?.takeIf { it.isNotBlank() }
      ?: DateFormatters.SHORT_DATE_FORMAT.format(target.startTime)
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("記録を削除しますか？") },
      text = { Text("「$label」の経路・立ち寄りをすべて削除します。この操作は元に戻せません。") },
      confirmButton = {
        TextButton(onClick = {
          viewModel.deleteTrack(target)
          deleteTarget = null
        }) {
          Text("削除する", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortBar(
  state: HistoryState,
  onClearFilters: () -> Unit,
  onFavoriteFilterChange: (TrackFavoriteFilter) -> Unit,
  onNamedFilterChange: (TrackNamedFilter) -> Unit,
  onStopFilterChange: (TrackStopFilter) -> Unit,
  onSortChange: (TrackSort) -> Unit,
  onToggleSortDirection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    // 絞り込みは3軸独立（お気に入り / 命名 / 立ち寄り）。横に収まらない端末向けに横スクロール可。
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // すべて: 絞り込みを全解除。何も絞っていないときは選択状態にして現在地を示す。
      FilterChip(
        selected = state.noFilter,
        onClick = onClearFilters,
        label = { Text("すべて") },
      )
      // 各軸は1つのトグル。タップで 指定なし→…→… と循環する。
      FilterChip(
        selected = state.favoriteFilter != TrackFavoriteFilter.ANY,
        onClick = {
          val entries = TrackFavoriteFilter.entries
          onFavoriteFilterChange(entries[(state.favoriteFilter.ordinal + 1) % entries.size])
        },
        label = { Text(state.favoriteFilter.chipLabel) },
      )
      FilterChip(
        selected = state.namedFilter != TrackNamedFilter.ANY,
        onClick = {
          val entries = TrackNamedFilter.entries
          onNamedFilterChange(entries[(state.namedFilter.ordinal + 1) % entries.size])
        },
        label = { Text(state.namedFilter.chipLabel) },
      )
      FilterChip(
        selected = state.stopFilter != TrackStopFilter.ANY,
        onClick = {
          val entries = TrackStopFilter.entries
          onStopFilterChange(entries[(state.stopFilter.ordinal + 1) % entries.size])
        },
        label = { Text(state.stopFilter.chipLabel) },
      )
    }

    // 並べ替え（軸メニュー ＋ 昇順/降順トグル）。
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box {
        var sortOpen by remember { mutableStateOf(false) }
        TextButton(onClick = { sortOpen = true }) {
          Text(text = "並べ替え: ${state.sort.label} ▾")
        }
        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
          TrackSort.entries.forEach { sort ->
            DropdownMenuItem(
              text = { Text(sort.label) },
              onClick = {
                sortOpen = false
                onSortChange(sort)
              },
            )
          }
        }
      }
      TextButton(onClick = onToggleSortDirection) {
        Text(text = if (state.sortDescending) "降順 ↓" else "昇順 ↑")
      }
    }
  }
}

@Composable
private fun RenameTrackDialog(
  initialName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("経路の名前") },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("例: 鎌倉さんぽ") },
        supportingText = { Text("空にすると未命名に戻ります") },
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

@Composable
private fun StatisticsSummaryCard(
  tracks: List<GpsTrack>,
  modifier: Modifier = Modifier,
) {
  val totalTracks = tracks.size
  val totalDistance = tracks.sumOf { it.totalDistanceMeters } / 1000.0
  val totalDuration = tracks.mapNotNull { track ->
    track.endTime?.let { endTime ->
      (endTime.time - track.startTime.time) / (1000 * 60) // minutes
    }
  }.sum()

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
    ),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "📊 お出掛け統計",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        StatisticItem(
          icon = "🗓️",
          label = "記録数",
          value = "${totalTracks}回",
        )
        StatisticItem(
          icon = "📏",
          label = "総距離",
          value = "${String.format("%.1f", totalDistance)}km",
        )
        StatisticItem(
          icon = "⏱️",
          label = "総時間",
          value = "${totalDuration}分",
        )
      }
    }
  }
}

@Composable
private fun StatisticItem(
  icon: String,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = icon,
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.secondary,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveTrackItem(
  track: GpsTrack,
  onTrackClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onTrackClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_play_arrow),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = "🟢 記録中",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "開始: ${DateFormatters.SHORT_TIME_FORMAT.format(track.startTime)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Spacer(modifier = Modifier.height(4.dp))

        val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
        Text(
          text = "移動距離: ${distanceKm}km (${track.pointCount}点)",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
          fontWeight = FontWeight.Medium,
        )
      }

      Icon(
        painter = painterResource(R.drawable.ic_location_on),
        contentDescription = "地図で表示",
        tint = MaterialTheme.colorScheme.secondary,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackItem(
  track: GpsTrack,
  onTrackClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRenameClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onTrackClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
      ) {
        // 見出しは名前があれば名前、無ければ日付。名前ありのときは日付を副題に添える。
        if (track.hasName) {
          Text(
            text = track.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = DateFormatters.SHORT_DATE_FORMAT.format(track.startTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            text = DateFormatters.SHORT_DATE_FORMAT.format(track.startTime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = "開始: ${DateFormatters.SHORT_TIME_FORMAT.format(track.startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          track.endTime?.let { endTime ->
            Text(
              text = "終了: ${DateFormatters.SHORT_TIME_FORMAT.format(endTime)}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(
            text = "移動距離: ${distanceKm}km",
            style = MaterialTheme.typography.bodyMedium,
            color = if (track.totalDistanceMeters > 0) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium,
          )
          Text(
            text = "立ち寄り: ${track.stopCount}件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      // お気に入りトグル。
      IconButton(onClick = onToggleFavorite) {
        Icon(
          painter = painterResource(
            if (track.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
          ),
          contentDescription = if (track.isFavorite) "お気に入り解除" else "お気に入り",
          tint = if (track.isFavorite) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }

      // 名前編集・削除はまとめてメニューに（カードの横幅を圧迫しないため）。
      Box {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
          Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = "その他",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(if (track.hasName) "名前を編集" else "名前を付ける") },
            onClick = {
              menuOpen = false
              onRenameClick()
            },
          )
          DropdownMenuItem(
            text = { Text("削除") },
            onClick = {
              menuOpen = false
              onDeleteClick()
            },
          )
        }
      }
    }
  }
}
