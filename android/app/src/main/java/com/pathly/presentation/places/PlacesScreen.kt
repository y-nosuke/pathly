package com.pathly.presentation.places

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
import androidx.compose.material3.FilledTonalIconButton
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
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.Priority

/** 「場所」タブ内の表示モード（一覧／追加／詳細）。MainActivity には触れず内部で切り替える。 */
private sealed interface PlacesMode {
  data object List : PlacesMode
  data object Add : PlacesMode
  data class Detail(val placeId: Long) : PlacesMode
}

@Composable
fun PlacesScreen(
  modifier: Modifier = Modifier,
  viewModel: PlacesViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var mode by remember { mutableStateOf<PlacesMode>(PlacesMode.List) }
  var deleteTarget by remember { mutableStateOf<PlaceListItem?>(null) }

  BackHandler(enabled = mode != PlacesMode.List) { mode = PlacesMode.List }

  uiState.errorMessage?.let { message ->
    LaunchedEffect(message) { viewModel.clearError() }
  }

  when (val current = mode) {
    PlacesMode.List -> PlacesListContent(
      modifier = modifier,
      state = uiState,
      onAddClick = { mode = PlacesMode.Add },
      onFilterChange = viewModel::setFilter,
      onItemClick = { mode = PlacesMode.Detail(it.place.id) },
      onToggleWishlist = viewModel::toggleWishlist,
      onDeleteRequest = { deleteTarget = it },
    )

    PlacesMode.Add -> AddPlaceContent(
      modifier = modifier,
      onCancel = { mode = PlacesMode.List },
      onSave = { lat, lng, name, wishlist, priority, memo ->
        viewModel.registerPlace(lat, lng, name, wishlist, priority, memo)
        mode = PlacesMode.List
      },
    )

    is PlacesMode.Detail -> {
      val item = uiState.items.firstOrNull { it.place.id == current.placeId }
      if (item == null) {
        LaunchedEffect(current.placeId) { mode = PlacesMode.List }
      } else {
        PlaceDetailContent(
          modifier = modifier,
          item = item,
          onBack = { mode = PlacesMode.List },
          onToggleWishlist = { viewModel.toggleWishlist(item) },
          onSaveWishlist = { priority, memo ->
            item.wishlistId?.let { viewModel.updateWishlist(it, priority, memo) }
          },
          onToggleVisited = { visited ->
            item.wishlistId?.let { viewModel.setVisited(it, visited) }
          },
          onDeleteRequest = { deleteTarget = item },
        )
      }
    }
  }

  deleteTarget?.let { target ->
    DeletePlaceDialog(
      item = target,
      onConfirm = {
        viewModel.deletePlace(target.place.id)
        deleteTarget = null
        // 詳細を開いていた場合は、削除で items から消えて自動的に一覧へ戻る。
      },
      onDismiss = { deleteTarget = null },
    )
  }
}

@Composable
private fun DeletePlaceDialog(
  item: PlaceListItem,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("削除しますか？") },
    text = { Text("「${item.displayName}」を削除します。") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("削除", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

// ---------------------------------------------------------------------------
// 一覧
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesListContent(
  state: PlacesState,
  onAddClick: () -> Unit,
  onFilterChange: (PlacesFilter) -> Unit,
  onItemClick: (PlaceListItem) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
  onDeleteRequest: (PlaceListItem) -> Unit,
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
        text = "場所",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
      Button(onClick = onAddClick) {
        Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
        Text(text = " 追加")
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PlacesFilter.entries.forEach { filter ->
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
            text = "場所がありません\n「追加」から登録できます",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      else -> {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(state.filteredItems, key = { it.place.id }) { item ->
            PlaceItemRow(
              item = item,
              onClick = { onItemClick(item) },
              onToggleWishlist = { onToggleWishlist(item) },
              onDelete = { onDeleteRequest(item) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceItemRow(
  item: PlaceListItem,
  onClick: () -> Unit,
  onToggleWishlist: () -> Unit,
  onDelete: () -> Unit,
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
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.displayName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
        val subtitle = buildList {
          if (item.isWishlisted) add("行きたい ${priorityStars(item.priority ?: Priority.MEDIUM)}")
          if (item.isVisited) {
            add(if (item.visitCount > 0) "✓訪問済み ${item.visitCount}回" else "✓訪問済み")
          }
          item.memo?.takeIf { it.isNotBlank() }?.let { add(it) }
          item.place.address?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" / ")
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

      WishlistFlagButton(active = item.isWishlisted, onClick = onToggleWishlist)
      // 立ち寄り記録がある場所は削除不可（記録を残すため）。
      val canDelete = item.visitCount == 0
      IconButton(onClick = onDelete, enabled = canDelete) {
        Icon(
          painter = painterResource(R.drawable.ic_delete),
          contentDescription = if (canDelete) "削除" else "立ち寄り記録があるため削除できません",
          tint = if (canDelete) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          },
        )
      }
    }
  }
}

@Composable
private fun WishlistFlagButton(
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
      painter = painterResource(R.drawable.ic_flag),
      contentDescription = if (active) "行きたいから外す" else "行きたいに追加",
      tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ---------------------------------------------------------------------------
// 追加（地図タップ → タップ後にフォーム表示）
// ---------------------------------------------------------------------------

@Composable
private fun AddPlaceContent(
  onCancel: () -> Unit,
  onSave: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var picked by remember { mutableStateOf<LatLng?>(null) }
  var name by remember { mutableStateOf("") }
  var memo by remember { mutableStateOf("") }
  var priority by remember { mutableStateOf(Priority.MEDIUM) }
  var wishlist by remember { mutableStateOf(false) }

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
      // アイコン（POI）をタップしたら名前を自動で入れる。
      onPOIClick = { poi ->
        picked = poi.latLng
        name = poi.name.orEmpty()
      },
      // 何もない場所をタップしたら空欄で。
      onMapClick = { latLng ->
        picked = latLng
        name = ""
      },
    ) {
      picked?.let { p ->
        val markerState = remember(p) { MarkerState(position = p) }
        Marker(state = markerState)
      }
    }

    // 未タップのときは大きなヒントだけ。フォームはタップ後に出す（地図を広く使う）。
    if (picked == null) {
      Card(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(12.dp),
      ) {
        Text(
          text = "地図をタップして場所を選ぶ",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(12.dp),
      ) {
        Text("キャンセル")
      }
    } else {
      Card(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "この場所を登録",
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

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("行きたいに登録")
            Switch(checked = wishlist, onCheckedChange = { wishlist = it })
          }

          if (wishlist) {
            Spacer(modifier = Modifier.height(8.dp))
            PrioritySelector(selected = priority, onSelect = { priority = it })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
              value = memo,
              onValueChange = { memo = it },
              label = { Text("メモ（任意）") },
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picked = null }, modifier = Modifier.weight(1f)) {
              Text("選び直す")
            }
            Button(
              onClick = {
                picked?.let { p ->
                  onSave(
                    p.latitude,
                    p.longitude,
                    name.ifBlank { null },
                    wishlist,
                    priority,
                    memo.ifBlank { null },
                  )
                }
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("保存")
            }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// 詳細
// ---------------------------------------------------------------------------

@Composable
private fun PlaceDetailContent(
  item: PlaceListItem,
  onBack: () -> Unit,
  onToggleWishlist: () -> Unit,
  onSaveWishlist: (priority: Priority, memo: String?) -> Unit,
  onToggleVisited: (Boolean) -> Unit,
  onDeleteRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var priority by remember(item.wishlistId) { mutableStateOf(item.priority ?: Priority.MEDIUM) }
  var memo by remember(item.wishlistId) { mutableStateOf(item.memo ?: "") }

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

    FilledTonalIconButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(12.dp),
    ) {
      Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "一覧に戻る")
    }

    Card(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(12.dp),
      shape = RoundedCornerShape(12.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = item.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
          )
          WishlistFlagButton(active = item.isWishlisted, onClick = onToggleWishlist)
        }
        item.place.address?.let { address ->
          Text(
            text = address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (item.isWishlisted) {
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
          if (item.visitCount > 0) {
            Text(
              text = "訪問済み（立ち寄り記録 ${item.visitCount} 件）",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.secondary,
            )
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(text = if (item.isManuallyVisited) "訪問済み" else "未訪問")
              Switch(checked = item.isManuallyVisited, onCheckedChange = onToggleVisited)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { onSaveWishlist(priority, memo.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("保存")
          }
        } else {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "「行きたい」に登録すると、優先度やメモを付けられます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
        val canDelete = item.visitCount == 0
        val deleteColor = if (canDelete) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        OutlinedButton(
          onClick = onDeleteRequest,
          enabled = canDelete,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = deleteColor,
          )
          Text(text = " この場所を削除", color = deleteColor)
        }
        if (!canDelete) {
          Text(
            text = "立ち寄り記録があるため削除できません（記録を残します）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
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
