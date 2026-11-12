package com.pathly.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.PlaceCategoryFacet

/**
 * 絞り込みの欄の「業種」（ふだんはたたみ、見出しに選んでいるものを出す）。件数つきのチップを並べ、複数選べる（選んだどれかに当たれば残す）。
 *
 * 選択肢は [counts] にある業種（全体に 1 件でもある業種。値は業種以外の絞り込みに合う件数で、0 のこともある。
 * → [com.pathly.domain.model.categoryFacetCounts]）。選択中の業種は、全体から消えても外せるように残す。
 * 選べる業種が 1 つも無ければ軸ごと出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFilterSection(
  selected: Set<PlaceCategoryFacet>,
  counts: Map<PlaceCategoryFacet, Int>,
  onToggle: (PlaceCategoryFacet) -> Unit,
) {
  val options = PlaceCategoryFacet.entries.filter { it in counts || it in selected }
  if (options.isEmpty()) return

  CollapsibleFilterSection("業種", summary = categoryChipLabel(selected)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      options.forEach { facet ->
        FilterChip(
          selected = facet in selected,
          onClick = { onToggle(facet) },
          label = { Text("${facet.label}（${counts[facet] ?: 0}）") },
        )
      }
    }
  }
}

/**
 * 地図のマーカーの絞り込みのパネルに置く「業種」のチップ（→ specs/map.md）。タップで業種の一覧を開き、複数選べる。
 * 一覧の絞り込みは絞り込みの欄（[CategoryFilterSection]）に移ったが、地図の上で軽く切り替える用にこちらは残す。
 * 選べる業種が 1 つも無ければチップごと出さない。
 */
@Composable
fun CategoryFilterChip(
  selected: Set<PlaceCategoryFacet>,
  counts: Map<PlaceCategoryFacet, Int>,
  onToggle: (PlaceCategoryFacet) -> Unit,
  onClear: () -> Unit,
) {
  val options = PlaceCategoryFacet.entries.filter { it in counts || it in selected }
  if (options.isEmpty()) return

  Box {
    var menuOpen by remember { mutableStateOf(false) }
    FilterChip(
      selected = selected.isNotEmpty(),
      onClick = { menuOpen = true },
      label = { Text(categoryChipLabel(selected) ?: "業種") },
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("すべて外す") },
        enabled = selected.isNotEmpty(),
        onClick = onClear,
      )
      HorizontalDivider()
      options.forEach { facet ->
        // 複数選ぶので、選んでもメニューは閉じない。
        DropdownMenuItem(
          text = { Text("${facet.label}（${counts[facet] ?: 0}）") },
          leadingIcon = { Checkbox(checked = facet in selected, onCheckedChange = null) },
          onClick = { onToggle(facet) },
        )
      }
    }
  }
}

/** 効いている条件のチップの文言。1 つならその業種、複数なら「先頭 他N」。未選択なら null。 */
internal fun categoryChipLabel(selected: Set<PlaceCategoryFacet>): String? {
  // 並びを選んだ順ではなく選択肢の順に揃え、同じ選択なら同じ文言にする。
  val ordered = PlaceCategoryFacet.entries.filter { it in selected }
  return when (ordered.size) {
    0 -> null
    1 -> ordered.single().label
    else -> "${ordered.first().label} 他${ordered.size - 1}"
  }
}

/** [item] が入っていれば外し、無ければ足した集合を返す（複数選べる絞り込みの切り替え）。 */
fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
