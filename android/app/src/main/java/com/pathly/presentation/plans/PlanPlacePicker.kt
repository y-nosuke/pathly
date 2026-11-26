package com.pathly.presentation.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pathly.R
import com.pathly.domain.model.PlaceListItem
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme

/**
 * 登録済みの場所から選んで足す（全画面）。名前で探せ、行きたいの場所を上に、そのあとを名前の順に並べる。
 * 複数選べる。選んだ順に、足す先の日の末尾へ足す。
 */
@Composable
internal fun PlanPlacePicker(
  places: List<PlaceListItem>,
  /** 足す先の文言（「2日目 11/30(日) に足す」）。 */
  destination: String,
  onAdd: (placeIds: List<Long>) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  // 選んだ順を保つ（足す順になる）。
  var selected by remember { mutableStateOf(listOf<Long>()) }
  val sorted = remember(places) {
    places.sortedWith(compareByDescending<PlaceListItem> { it.isWishlisted }.thenBy { it.displayName })
  }
  val shown = remember(sorted, query) {
    val q = query.trim()
    if (q.isEmpty()) sorted else sorted.filter { it.displayName.contains(q, ignoreCase = true) }
  }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .safeDrawingPadding(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = PathlySpacing.screenEdge)) {
          IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "やめる")
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("登録済みの場所から選ぶ", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(destination, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
          }
        }
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("場所の名前で探す") },
          leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.sm)
            .testTag("planPlaceSearch"),
        )
        LazyColumn(modifier = Modifier.weight(1f).testTag("planPlaceList")) {
          if (shown.isEmpty()) {
            item {
              Text(
                text = if (places.isEmpty()) "登録済みの場所がありません" else "名前に合う場所がありません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(PathlySpacing.screenEdge),
              )
            }
          }
          items(shown, key = { it.place.id }) { item ->
            val checked = item.place.id in selected
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selected = if (checked) selected - item.place.id else selected + item.place.id }
                .padding(horizontal = PathlySpacing.sm)
                .testTag("planPlaceRow:${item.place.id}"),
            ) {
              Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(PathlySpacing.md))
              Column(modifier = Modifier.weight(1f).padding(vertical = PathlySpacing.sm)) {
                Text(item.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOfNotNull(item.region.prefecture, item.region.city).joinToString(" ").ifEmpty { null }?.let {
                  Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
              }
              if (item.isWishlisted) {
                Spacer(modifier = Modifier.width(PathlySpacing.xs))
                Icon(
                  painterResource(R.drawable.ic_flag_filled),
                  contentDescription = "行きたい",
                  tint = PathlyTheme.statusColors.wishlist,
                  modifier = Modifier.size(20.dp).padding(end = 0.dp),
                )
              }
            }
          }
        }
        HorizontalDivider()
        Button(
          onClick = {
            onAdd(selected)
            onDismiss()
          },
          enabled = selected.isNotEmpty(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(PathlySpacing.screenEdge)
            .testTag("planPlaceAdd"),
        ) {
          Text(if (selected.isEmpty()) "場所を選んでください" else "${selected.size} 件を足す")
        }
      }
    }
  }
}
