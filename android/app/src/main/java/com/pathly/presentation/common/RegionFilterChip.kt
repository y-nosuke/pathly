package com.pathly.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.RegionCount
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.sortedForDisplay

/**
 * 一覧の絞り込みバーに置く「地域」のチップ（→ adr/0029）。タップで都道府県の一覧（件数つき）を開き、
 * 都道府県の横の▸でその中の市区町村（件数つき）を開ける。都道府県も市区町村も複数選べる（選んだどれかに当たれば残す）。
 *
 * 選択肢・件数の考え方は業種のチップ（[CategoryFilterChip]）と同じ。選択中のものは、全体から消えても外せるように残す。
 * 選べる地域が 1 つも無ければチップごと出さない。
 */
@Composable
fun RegionFilterChip(
  selected: Set<RegionKey>,
  counts: List<RegionCount>,
  onToggle: (RegionKey) -> Unit,
  onClear: () -> Unit,
) {
  if (counts.isEmpty() && selected.isEmpty()) return
  // 全体から消えた選択中の地域（場所を消したなど）も、外せるように並べる。
  val listed = counts.flatMap { c -> listOf(c.key) + c.cities.map { (city, _) -> RegionKey(c.prefecture, city) } }.toSet()
  val orphans = (selected - listed).sortedForDisplay()

  Box {
    var menuOpen by remember { mutableStateOf(false) }
    // 開いている都道府県（▸で市区町村を出す）。
    var expanded by remember { mutableStateOf(setOf<String>()) }
    FilterChip(
      selected = selected.isNotEmpty(),
      onClick = { menuOpen = true },
      label = { Text(regionChipLabel(selected)) },
      modifier = Modifier.testTag("regionFilterChip"),
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("すべて外す") },
        enabled = selected.isNotEmpty(),
        onClick = onClear,
      )
      HorizontalDivider()
      counts.forEach { pref ->
        val prefKey = pref.key
        val open = pref.prefecture != null && pref.prefecture in expanded
        // 複数選ぶので、選んでもメニューは閉じない。
        DropdownMenuItem(
          text = { Text("${prefKey.label}（${pref.count}）") },
          leadingIcon = { Checkbox(checked = prefKey in selected, onCheckedChange = null) },
          trailingIcon = if (pref.prefecture != null && pref.cities.isNotEmpty()) {
            {
              IconButton(onClick = { expanded = if (open) expanded - pref.prefecture else expanded + pref.prefecture }) {
                Icon(
                  painter = painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                  contentDescription = if (open) "${pref.prefecture}の市区町村を閉じる" else "${pref.prefecture}の市区町村を開く",
                )
              }
            }
          } else {
            null
          },
          onClick = { onToggle(prefKey) },
        )
        if (open) {
          pref.cities.forEach { (city, count) ->
            val cityKey = RegionKey(pref.prefecture, city)
            DropdownMenuItem(
              text = { Text("$city（$count）") },
              leadingIcon = { Checkbox(checked = cityKey in selected, onCheckedChange = null) },
              onClick = { onToggle(cityKey) },
              modifier = Modifier.padding(start = 24.dp),
            )
          }
        }
      }
      orphans.forEach { key ->
        DropdownMenuItem(
          text = { Text("${key.fullLabel}（0）") },
          leadingIcon = { Checkbox(checked = true, onCheckedChange = null) },
          onClick = { onToggle(key) },
        )
      }
    }
  }
}

/** チップの文言。未選択なら軸の名前、1 つならその地域、複数なら「先頭 他N」（並びは選択肢の順）。 */
internal fun regionChipLabel(selected: Set<RegionKey>): String {
  val ordered = selected.sortedForDisplay()
  return when (ordered.size) {
    0 -> "地域"
    1 -> ordered.single().fullLabel
    else -> "${ordered.first().fullLabel} 他${ordered.size - 1}"
  }
}
