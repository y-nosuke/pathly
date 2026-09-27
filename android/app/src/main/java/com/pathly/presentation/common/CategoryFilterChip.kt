package com.pathly.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pathly.domain.model.PlaceCategoryFacet

/**
 * 一覧の絞り込みバーに置く「業種」のチップ。タップで業種の一覧を開き、複数選べる（選んだどれかに当たれば残す）。
 *
 * 選択肢は件数のある業種だけ（[counts] に無い業種は出さない）。ただし選択中の業種は、件数が 0 になっても
 * 外せるように残す。選べる業種が 1 つも無ければチップごと出さない。
 */
@Composable
fun CategoryFilterChip(
  selected: Set<PlaceCategoryFacet>,
  counts: Map<PlaceCategoryFacet, Int>,
  onToggle: (PlaceCategoryFacet) -> Unit,
) {
  val options = PlaceCategoryFacet.entries.filter { it in counts || it in selected }
  if (options.isEmpty()) return

  Box {
    var menuOpen by remember { mutableStateOf(false) }
    FilterChip(
      selected = selected.isNotEmpty(),
      onClick = { menuOpen = true },
      label = { Text(categoryChipLabel(selected)) },
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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

/** チップの文言。未選択なら軸の名前、1 つならその業種、複数なら「先頭 他N」。 */
internal fun categoryChipLabel(selected: Set<PlaceCategoryFacet>): String {
  // 並びを選んだ順ではなく選択肢の順に揃え、同じ選択なら同じ文言にする。
  val ordered = PlaceCategoryFacet.entries.filter { it in selected }
  return when (ordered.size) {
    0 -> "業種"
    1 -> ordered.single().label
    else -> "${ordered.first().label} 他${ordered.size - 1}"
  }
}

/** [item] が入っていれば外し、無ければ足した集合を返す（複数選べる絞り込みの切り替え）。 */
fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
