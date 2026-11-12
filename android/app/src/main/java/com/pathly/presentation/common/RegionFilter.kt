package com.pathly.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.RegionCount
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.sortedForDisplay

/**
 * 絞り込みの欄の「地域」（→ adr/0029・adr/0030）。1 段目（日本の都道府県、続けて海外の国。件数つき）を並べ、
 * 横の▼でその中の市区町村・都市（件数つき）を開ける。どの段も複数選べる（選んだどれかに当たれば残す）。
 *
 * 選択肢・件数の考え方は業種（[CategoryFilterSection]）と同じ。選択中のものは、全体から消えても外せるように残す。
 * 選べる地域が 1 つも無ければ軸ごと出さない。
 */
@Composable
fun RegionFilterSection(
  selected: Set<RegionKey>,
  counts: List<RegionCount>,
  onToggle: (RegionKey) -> Unit,
) {
  if (counts.isEmpty() && selected.isEmpty()) return
  // 全体から消えた選択中の地域（場所を消したなど）も、外せるように並べる。
  val listed = counts.flatMap { c -> listOf(c.key) + c.cities.map { (city, _) -> c.cityKey(city) } }.toSet()
  val orphans = (selected - listed).sortedForDisplay()
  // 開いている 1 段目（▼で市区町村・都市を出す）。
  var expanded by remember { mutableStateOf(setOf<RegionKey>()) }

  CollapsibleFilterSection("地域", summary = regionChipLabel(selected)) {
    Column {
      counts.forEach { top ->
        val key = top.key
        val open = key in expanded
        RegionRow(
          label = "${key.label}（${top.count}）",
          checked = key in selected,
          onToggle = { onToggle(key) },
          trailing = if (top.cities.isNotEmpty()) {
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
        )
        if (open) {
          top.cities.forEach { (city, count) ->
            val cityKey = top.cityKey(city)
            RegionRow(
              label = "$city（$count）",
              checked = cityKey in selected,
              onToggle = { onToggle(cityKey) },
              modifier = Modifier.padding(start = 32.dp),
            )
          }
        }
      }
      orphans.forEach { key ->
        RegionRow(label = "${key.fullLabel}（0）", checked = true, onToggle = { onToggle(key) })
      }
    }
  }
}

/** 地域の 1 行（チェック＋名前。行のどこを押しても選ぶ／外す）。 */
@Composable
private fun RegionRow(
  label: String,
  checked: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(role = Role.Checkbox, onClick = onToggle),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    Text(label, modifier = Modifier.weight(1f))
    trailing?.invoke()
  }
}

/** 効いている条件のチップの文言。1 つならその地域、複数なら「先頭 他N」（並びは選択肢の順）。未選択なら null。 */
internal fun regionChipLabel(selected: Set<RegionKey>): String? {
  val ordered = selected.sortedForDisplay()
  return when (ordered.size) {
    0 -> null
    1 -> ordered.single().fullLabel
    else -> "${ordered.first().fullLabel} 他${ordered.size - 1}"
  }
}
