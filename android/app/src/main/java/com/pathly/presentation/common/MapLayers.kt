package com.pathly.presentation.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.RegisteredPlace
import com.pathly.ui.theme.PathlySpacing

// 地図の画面の上のバーの右端の「レイヤー［≋］」。地図に重ねるもの（登録済みの場所の表示・絞り込み、
// 記録画面のこの範囲の経路）をこの中にしまう（→ docs/designs/ui-direction.md）。

/**
 * レイヤーのボタンと、押すと開くパネル。パネルの中身は画面ごとに [content] で渡す（`close` でパネルを閉じる）。
 * [active] は、何かを重ねているとき（登録済みの場所を表示中など）に塗りつぶして示すため。
 */
@Composable
fun MapLayersButton(
  active: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier = modifier) {
    MapRoundButton(
      icon = R.drawable.ic_layers,
      contentDescription = "地図に重ねるもの",
      onClick = { open = !open },
      active = active || open,
    )
    DropdownMenu(
      expanded = open,
      onDismissRequest = { open = false },
      modifier = Modifier
        .widthIn(min = 280.dp)
        .testTag("mapLayersPanel"),
    ) {
      Text(
        text = "地図に重ねる",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PathlySpacing.lg, vertical = PathlySpacing.xs),
      )
      content { open = false }
    }
  }
}

/**
 * レイヤーの中の「登録済みの場所」: 表示のスイッチと、表示中だけ出す絞り込みのチップ（行きたい・訪問・業種）。
 * 記録・経路詳細・場所詳細・履歴の地図で同じものを使う。
 */
@Composable
fun RegisteredPlacesLayerItems(
  show: Boolean,
  onToggle: () -> Unit,
  filter: MapPlaceFilter,
  places: List<RegisteredPlace>,
  onFilterChange: (MapPlaceFilter) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .toggleable(value = show, role = Role.Switch, onValueChange = { onToggle() })
      .heightIn(min = 48.dp)
      .padding(horizontal = PathlySpacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(PathlySpacing.md))
    Text(text = "登録済みの場所", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    // 行全体で切り替える（スイッチ単体は押せる範囲が狭い）。
    Switch(checked = show, onCheckedChange = null)
  }
  if (show) {
    Column(modifier = Modifier.padding(start = PathlySpacing.xl)) {
      MapPlaceFilterPanel(filter = filter, places = places, onFilterChange = onFilterChange)
    }
  }
}

/** レイヤーの中の区切り線。 */
@Composable
fun MapLayerDivider() {
  HorizontalDivider(modifier = Modifier.padding(vertical = PathlySpacing.xs))
}

/**
 * レイヤーの中の、押すと何かを開く行（記録画面の「この範囲の経路」など）。[selected] のときは文字を主役の色にして、
 * いま開いていることを示す。
 */
@Composable
fun MapLayerActionItem(
  @DrawableRes icon: Int,
  title: String,
  supporting: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  enabled: Boolean = true,
) {
  val color = when {
    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    selected -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .heightIn(min = 48.dp)
      .padding(horizontal = PathlySpacing.lg, vertical = PathlySpacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(PathlySpacing.md))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge, color = color)
      supporting?.let {
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

/**
 * 地図に出す登録済みの場所（画面別のトグルと、この地図だけの絞り込み。場所一覧とも別）。
 * [places] は絞り込む前のもの（絞り込みのチップの件数に使う）。
 */
data class MapRegisteredPlaces(
  val places: List<RegisteredPlace> = emptyList(),
  val shown: Boolean = false,
  val onToggle: () -> Unit = {},
  val filter: MapPlaceFilter = MapPlaceFilter(),
  val onFilterChange: (MapPlaceFilter) -> Unit = {},
) {
  /** 地図に描く場所（表示 OFF なら無し。絞り込みに合うものだけ）。 */
  val visible: List<RegisteredPlace> by lazy { if (shown) places.filter(filter::matches) else emptyList() }
}

/** 上のバーのレイヤー［≋］のうち、登録済みの場所の表示・絞り込みだけを持つもの（経路詳細・場所詳細・地図で選ぶ）。 */
@Composable
fun RegisteredPlacesLayerButton(mapPlaces: MapRegisteredPlaces, modifier: Modifier = Modifier) {
  MapLayersButton(active = mapPlaces.shown, modifier = modifier) {
    RegisteredPlacesLayerItems(
      show = mapPlaces.shown,
      onToggle = mapPlaces.onToggle,
      filter = mapPlaces.filter,
      places = mapPlaces.places,
      onFilterChange = mapPlaces.onFilterChange,
    )
  }
}
