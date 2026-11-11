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
 * 一覧の絞り込みバーに置く「地域」のチップ（→ adr/0029）。タップで 1 段目（日本の都道府県、続けて海外の国。件数つき）の
 * 一覧を開き、横の▼でその中の市区町村・都市（件数つき）を開ける。どの段も複数選べる（選んだどれかに当たれば残す）。
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
  val listed = counts.flatMap { c -> listOf(c.key) + c.cities.map { (city, _) -> c.cityKey(city) } }.toSet()
  val orphans = (selected - listed).sortedForDisplay()

  Box {
    var menuOpen by remember { mutableStateOf(false) }
    // 開いている 1 段目（▼で市区町村・都市を出す）。
    var expanded by remember { mutableStateOf(setOf<RegionKey>()) }
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
      counts.forEach { top ->
        val key = top.key
        val open = key in expanded
        // 複数選ぶので、選んでもメニューは閉じない。
        DropdownMenuItem(
          text = { Text("${key.label}（${top.count}）") },
          leadingIcon = { Checkbox(checked = key in selected, onCheckedChange = null) },
          trailingIcon = if (top.cities.isNotEmpty()) {
            {
              IconButton(onClick = { expanded = if (open) expanded - key else expanded + key }) {
                Icon(
                  painter = painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                  contentDescription = if (open) "${key.label}の中を閉じる" else "${key.label}の中を開く",
                )
              }
            }
          } else {
            null
          },
          onClick = { onToggle(key) },
        )
        if (open) {
          top.cities.forEach { (city, count) ->
            val cityKey = top.cityKey(city)
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
