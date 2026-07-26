package com.pathly.presentation.wishlist

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.Priority
import com.pathly.domain.model.WishlistItem

/** 行きたいタブ内の表示モード（一覧／追加／詳細）。MainActivity には触れず内部で切り替える。 */
private sealed interface WishlistMode {
  data object List : WishlistMode
  data object Add : WishlistMode
  data class Detail(val id: Long) : WishlistMode
}

@Composable
fun WishlistScreen(
  modifier: Modifier = Modifier,
  viewModel: WishlistViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var mode by remember { mutableStateOf<WishlistMode>(WishlistMode.List) }

  // 追加・詳細表示中は戻るで一覧へ。
  BackHandler(enabled = mode != WishlistMode.List) { mode = WishlistMode.List }

  uiState.errorMessage?.let { message ->
    LaunchedEffect(message) { viewModel.clearError() }
  }

  when (val current = mode) {
    WishlistMode.List -> WishlistListContent(
      modifier = modifier,
      state = uiState,
      onAddClick = { mode = WishlistMode.Add },
      onFilterChange = viewModel::setFilter,
      onItemClick = { mode = WishlistMode.Detail(it.id) },
    )

    WishlistMode.Add -> AddWishlistContent(
      modifier = modifier,
      onCancel = { mode = WishlistMode.List },
      onSave = { lat, lng, name, priority, memo ->
        viewModel.addByCoordinate(lat, lng, name, priority, memo)
        mode = WishlistMode.List
      },
    )

    is WishlistMode.Detail -> {
      val item = uiState.items.firstOrNull { it.id == current.id }
      if (item == null) {
        // 削除された等で見つからなければ一覧へ戻す。
        LaunchedEffect(current.id) { mode = WishlistMode.List }
      } else {
        WishlistDetailContent(
          modifier = modifier,
          item = item,
          onBack = { mode = WishlistMode.List },
          onSave = { priority, memo -> viewModel.updateItem(item.id, priority, memo) },
          onToggleVisited = { visited -> viewModel.setVisited(item.id, visited) },
          onDelete = {
            viewModel.remove(item.id)
            mode = WishlistMode.List
          },
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------
// 一覧
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistListContent(
  state: WishlistState,
  onAddClick: () -> Unit,
  onFilterChange: (WishlistFilter) -> Unit,
  onItemClick: (WishlistItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "行きたい場所",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
      Button(onClick = onAddClick) {
        Icon(
          painter = painterResource(R.drawable.ic_flag),
          contentDescription = null,
        )
        Spacer(modifier = Modifier.height(0.dp))
        Text(text = " 追加")
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      WishlistFilter.entries.forEach { filter ->
        FilterChip(
          selected = state.filter == filter,
          onClick = { onFilterChange(filter) },
          label = { Text(filter.label) },
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    when {
      state.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }

      state.filteredItems.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "行きたい場所がありません\n「追加」から登録できます",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      else -> {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(state.filteredItems, key = { it.id }) { item ->
            WishlistItemRow(item = item, onClick = { onItemClick(item) })
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistItemRow(
  item: WishlistItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${priorityStars(item.priority)} ${item.displayName}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        if (item.isVisited) {
          Text(
            text = "✓訪問済み",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      val subtitle = listOfNotNull(item.place.address, item.memo?.takeIf { it.isNotBlank() })
        .joinToString(" / ")
      if (subtitle.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------
// 追加（地図タップ）
// ---------------------------------------------------------------------------

@Composable
private fun AddWishlistContent(
  onCancel: () -> Unit,
  onSave: (lat: Double, lng: Double, name: String?, priority: Priority, memo: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var picked by remember { mutableStateOf<LatLng?>(null) }
  var name by remember { mutableStateOf("") }
  var memo by remember { mutableStateOf("") }
  var priority by remember { mutableStateOf(Priority.MEDIUM) }

  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(DEFAULT_LOCATION, 12f)
  }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
      ),
      onMapClick = { latLng -> picked = latLng },
    ) {
      picked?.let { p ->
        val markerState = remember(p) { MarkerState(position = p) }
        Marker(state = markerState, title = "ここを登録")
      }
    }

    Card(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(12.dp),
      shape = RoundedCornerShape(12.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = if (picked == null) "地図をタップして場所を選ぶ" else "この場所を登録",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名前（任意・後で取得も可）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrioritySelector(selected = priority, onSelect = { priority = it })

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = memo,
          onValueChange = { memo = it },
          label = { Text("メモ（任意）") },
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("キャンセル")
          }
          Button(
            onClick = {
              picked?.let { p ->
                onSave(p.latitude, p.longitude, name.ifBlank { null }, priority, memo.ifBlank { null })
              }
            },
            enabled = picked != null,
            modifier = Modifier.weight(1f),
          ) {
            Text("保存")
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// 詳細（編集・訪問トグル・削除）
// ---------------------------------------------------------------------------

@Composable
private fun WishlistDetailContent(
  item: WishlistItem,
  onBack: () -> Unit,
  onSave: (priority: Priority, memo: String?) -> Unit,
  onToggleVisited: (Boolean) -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var priority by remember(item.id) { mutableStateOf(item.priority) }
  var memo by remember(item.id) { mutableStateOf(item.memo ?: "") }
  var showDeleteDialog by remember { mutableStateOf(false) }

  val position = LatLng(item.place.latitude, item.place.longitude)
  val cameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom(position, 16f)
  }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
      ),
    ) {
      val markerState = remember(position) { MarkerState(position = position) }
      Marker(state = markerState, title = item.displayName)
    }

    IconButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(8.dp),
    ) {
      Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
    }

    Card(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(12.dp),
      shape = RoundedCornerShape(12.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = item.displayName,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        item.place.address?.let { address ->
          Text(
            text = address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PrioritySelector(selected = priority, onSelect = { priority = it })

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = memo,
          onValueChange = { memo = it },
          label = { Text("メモ") },
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(text = if (item.isVisited) "訪問済み" else "未訪問")
          Switch(
            checked = item.isVisited,
            onCheckedChange = onToggleVisited,
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.weight(1f),
          ) {
            Text("削除", color = MaterialTheme.colorScheme.error)
          }
          Button(
            onClick = { onSave(priority, memo.ifBlank { null }) },
            modifier = Modifier.weight(1f),
          ) {
            Text("保存")
          }
        }
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("削除しますか？") },
      text = { Text("「${item.displayName}」を行きたいリストから削除します。場所そのものは残ります。") },
      confirmButton = {
        TextButton(onClick = {
          showDeleteDialog = false
          onDelete()
        }) {
          Text("削除", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
      },
    )
  }
}

// ---------------------------------------------------------------------------
// 共通
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrioritySelector(
  selected: Priority,
  onSelect: (Priority) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    priorityOptions.forEach { (priority, label) ->
      FilterChip(
        selected = selected == priority,
        onClick = { onSelect(priority) },
        label = { Text(label) },
      )
    }
  }
}

private val priorityOptions = listOf(
  Priority.HIGH to "高 ★★★",
  Priority.MEDIUM to "中 ★★☆",
  Priority.LOW to "低 ★☆☆",
)

private fun priorityStars(priority: Priority): String = when (priority) {
  Priority.HIGH -> "★★★"
  Priority.MEDIUM -> "★★☆"
  Priority.LOW -> "★☆☆"
}

private val DEFAULT_LOCATION = LatLng(35.6762, 139.6503) // 東京駅
