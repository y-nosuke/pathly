package com.pathly.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.nextTriState

/**
 * 地図の「登録済みの場所」の絞り込みボタン。「登録済みの場所を表示」のトグルの隣に、表示 ON のときだけ置く。
 *
 * 押すと小さなパネルが開き、場所一覧と同じ 3 つのチップ（行きたい・訪問・業種）と「すべて外す」を並べる。
 * 絞り込み中はボタンを塗りつぶして示す（条件はアプリを閉じても残るので、気づけるように）。
 *
 * @param places 業種の選択肢の件数を数えるための、絞り込む前の登録済みの場所。
 */
@Composable
fun MapPlaceFilterButton(
  filter: MapPlaceFilter,
  places: List<RegisteredPlace>,
  onFilterChange: (MapPlaceFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  val active = !filter.isEmpty
  Box(modifier = modifier) {
    Surface(
      onClick = { open = true },
      shape = CircleShape,
      color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_filter),
        contentDescription = if (active) "登録済みの場所を絞り込み中" else "登録済みの場所を絞り込む",
        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
      )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      MapPlaceFilterPanel(filter = filter, places = places, onFilterChange = onFilterChange)
    }
  }
}

/** 絞り込みのパネルの中身。チップを押しても閉じない（続けて条件を変えられるように）。 */
@Composable
private fun MapPlaceFilterPanel(
  filter: MapPlaceFilter,
  places: List<RegisteredPlace>,
  onFilterChange: (MapPlaceFilter) -> Unit,
) {
  // 業種の件数はほかの絞り込みに関係なく全体で数える（場所一覧と同じ）。
  val counts: Map<PlaceCategoryFacet, Int> = remember(places) { places.groupingBy { it.categoryFacet }.eachCount() }
  Column(modifier = Modifier.padding(horizontal = 12.dp)) {
    // 行きたい・訪問は 1 つのチップで 指定なし→あり→なし→… と切り替える（場所一覧と同じ）。
    FilterChip(
      selected = filter.wishlisted != null,
      onClick = { onFilterChange(filter.copy(wishlisted = filter.wishlisted.nextTriState())) },
      label = { Text(wishlistChipLabel(filter.wishlisted)) },
    )
    FilterChip(
      selected = filter.visited != null,
      onClick = { onFilterChange(filter.copy(visited = filter.visited.nextTriState())) },
      label = { Text(visitedChipLabel(filter.visited)) },
    )
    CategoryFilterChip(
      selected = filter.categories,
      counts = counts,
      onToggle = { facet -> onFilterChange(filter.copy(categories = filter.categories.toggled(facet))) },
      onClear = { onFilterChange(filter.copy(categories = emptySet())) },
    )
    TextButton(onClick = { onFilterChange(MapPlaceFilter()) }, enabled = !filter.isEmpty) {
      Text("すべて外す")
    }
  }
}

/** 行きたいのチップの文言（場所一覧の WishlistFilter と同じ）。 */
internal fun wishlistChipLabel(wishlisted: Boolean?): String = when (wishlisted) {
  null -> "行きたい"
  true -> "行きたい"
  false -> "行きたい以外"
}

/** 訪問のチップの文言（場所一覧の VisitedFilter と同じ）。 */
internal fun visitedChipLabel(visited: Boolean?): String = when (visited) {
  null -> "訪問"
  true -> "訪問済み"
  false -> "未訪問"
}
