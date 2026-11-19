package com.pathly.presentation.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing

// 地図の画面の型（docs/designs/ui-direction.md）の「上」と「上の下」の区画。
// 記録・経路詳細・場所詳細・履歴と場所の地図で同じ並びにする。

/**
 * 上の区画の 1 行目（戻る＋見出し・レイヤー・⋮）が地図の上を占める高さ（上下の余白込み）。地図の `contentPadding` の
 * top に渡すと、カメラを寄せた対象やその吹き出しが上のバーの下に隠れない。上の下（帯・ピル）が出ている間の分は含まない。
 */
val MapTopBarInset: Dp = 76.dp

/**
 * 地図の画面の上の区画。1 行目は [leading]（戻る＋見出し、またはタブの画面の検索の入口。残りの幅を使う）と
 * 右端の [actions]（レイヤー［≋］・⋮）。その下に [status]（今の状態のピル・チップと、1 行の案内の帯）を縦に積む。
 *
 * 呼び出し側の Box で `.align(Alignment.TopCenter)` して使う。地図のタップを妨げないよう、中身の無いところは
 * 透明のまま（背景を持たない）。
 */
@Composable
fun MapTopBar(
  modifier: Modifier = Modifier,
  leading: @Composable RowScope.() -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  status: @Composable ColumnScope.() -> Unit = {},
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(PathlySpacing.mapEdge),
    verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        content = leading,
      )
      actions()
    }
    status()
  }
}

/**
 * 地図に浮かぶ丸いボタン（上のバーのレイヤー・右下の地図の操作）。48dp。
 * [active] のときは塗りつぶして、いま効いている（表示中・追従中・開いている）ことを示す。
 */
@Composable
fun MapRoundButton(
  @DrawableRes icon: Int,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  active: Boolean = false,
  enabled: Boolean = true,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.size(48.dp),
    shape = PathlyShapes.pill,
    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    shadowElevation = PathlyElevation.floating,
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = contentDescription,
      modifier = Modifier.padding(PathlySpacing.md),
    )
  }
}

/**
 * 地図の上に出す帯。ずっと出しておく案内（電池の最適化など）は押すと詳しい画面へ移る（[onClick]。右端に ›）。
 * 地図の上を大きく占めないよう、文は [maxLines]（既定 1 行）で省く。押せない知らせ（エラー）は全文が読めるよう
 * 行を増やして渡す。[isError] なら、うまくいかなかったことの知らせの色にする。
 */
@Composable
fun MapNoticeBanner(
  text: String,
  @DrawableRes icon: Int,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  isError: Boolean = false,
  maxLines: Int = 1,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 44.dp)
      .testTag("mapNoticeBanner"),
    shape = MaterialTheme.shapes.small,
    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    shadowElevation = PathlyElevation.floating,
  ) {
    Row(
      modifier = Modifier
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = PathlySpacing.md, vertical = PathlySpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (onClick != null) {
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
      }
    }
  }
}

/**
 * 詳細の画面の上の左: 戻る＋見出し（[title]。その下に小さく [subtitle]）を 1 つの浮かぶ窓にまとめる。
 * [MapTopBar] の leading に `Modifier.weight(1f)` を付けて置く。[onTitleClick] を渡すと見出しを押せる
 * （経路詳細の名前の編集など。押せることは [titleClickLabel] で読み上げる）。
 */
@Composable
fun MapBackTitle(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onTitleClick: (() -> Unit)? = null,
  titleClickLabel: String? = null,
) {
  Surface(
    modifier = modifier.heightIn(min = 48.dp),
    shape = PathlyShapes.pill,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = PathlyElevation.floating,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .then(
            if (onTitleClick != null) {
              Modifier.clickable(onClickLabel = titleClickLabel, role = Role.Button, onClick = onTitleClick)
            } else {
              Modifier
            },
          )
          .padding(end = PathlySpacing.lg, top = PathlySpacing.xs, bottom = PathlySpacing.xs),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * 上のバーの右端の ⋮。その画面の対象への操作（名前を編集・お気に入り・再解析、開発版の補正の調整など）を
 * この中にしまう（→ docs/designs/ui-direction.md）。中身は画面ごとに [content] で渡す（`close` でメニューを閉じる）。
 */
@Composable
fun MapOverflowMenuButton(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier = modifier) {
    MapRoundButton(
      icon = R.drawable.ic_more_vert,
      contentDescription = "その他の操作",
      onClick = { open = !open },
      active = open,
    )
    DropdownMenu(
      expanded = open,
      onDismissRequest = { open = false },
      modifier = Modifier
        .widthIn(min = 220.dp)
        .testTag("mapOverflowMenu"),
    ) {
      content { open = false }
    }
  }
}
