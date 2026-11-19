package com.pathly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing

/**
 * 一覧のタブ（場所・履歴）の、一覧表示と地図表示の切り替えの器。
 *
 * 地図は一度開いたら、一覧表示に切り替えても捨てずに一覧の裏に残す（作り直すと、地図の絵が読み込まれるまでの一瞬、
 * 何も描かれていない面が見えてちらつくため）。一覧は裏の地図を隠し、触っても地図に届かないようにする。
 *
 * 地図表示のときのシステムバック（一覧へ戻す）は呼ぶ側で持つ。戻るの優先は登録の順で決まり、絞り込みの欄を閉じる戻る
 * をそれより後、地図の吹き出しを閉じる戻るをさらに後に登録したいため。
 *
 * @param map 地図表示。引数の visible が false のときは一覧の裏に残しているだけ（地図に重ねるものは出さない）。
 * @param list 一覧表示。背景は器が塗る。
 */
@Composable
internal fun ListMapLayout(
  mode: ListMapMode,
  map: @Composable (visible: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  list: @Composable () -> Unit,
) {
  val isMap = mode == ListMapMode.MAP
  var keepMap by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(isMap) { if (isMap) keepMap = true }

  Box(modifier = modifier.fillMaxSize()) {
    if (isMap || keepMap) map(isMap)
    if (!isMap) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .pointerInput(Unit) {},
      ) {
        list()
      }
    }
  }
}

/**
 * 一覧のタブの地図表示で、地図の上に重ねる見出し・検索欄・絞り込み。たためて、たたむと一覧への切り替えと
 * 絞り込みの様子（[collapsedSummary]）だけの小さな帯になる。
 *
 * @param onHeightChange 重ねたものの高さ。地図の上の余白にして、線・マーカー・寄せた範囲が隠れないようにする。
 * @param header 見出しの行。右端にたたむボタンを足して出す（押すとキーボードも閉じる）。
 * @param controls 見出しの下に出す検索欄・絞り込み。
 * @param padded 見出しと controls の左右に余白を付けるか。一覧の型の部品（[ListHeader]・[ListToolRow]）は自分で
 *   余白を持つので false にする（場所タブも型に移したら（#197）この引数ごと無くす）。
 * @param collapsedActions たたんだ帯の右端（ひろげるの手前）に残すもの（地図表示のレイヤー［≋］など）。
 */
@Composable
internal fun ListMapOverlay(
  mode: ListMapMode,
  onModeChange: (ListMapMode) -> Unit,
  collapsed: Boolean,
  onCollapsedChange: (Boolean) -> Unit,
  collapsedSummary: String,
  onHeightChange: (Dp) -> Unit,
  header: @Composable () -> Unit,
  controls: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  padded: Boolean = true,
  collapsedActions: @Composable RowScope.() -> Unit = {},
) {
  val density = LocalDensity.current
  val focusManager = LocalFocusManager.current
  Box(
    modifier = modifier
      .fillMaxWidth()
      .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) },
  ) {
    if (collapsed) {
      ListMapCollapsedBar(
        mode = mode,
        onModeChange = onModeChange,
        summary = collapsedSummary,
        onExpand = { onCollapsedChange(false) },
        actions = collapsedActions,
        modifier = Modifier.padding(12.dp),
      )
    } else {
      Surface(
        // 地図の文字が透けると読みにくいので、重ねたものは不透明にする。
        color = MaterialTheme.colorScheme.surface,
        shape = PathlyShapes.sheetBottom,
        shadowElevation = PathlyElevation.low,
      ) {
        // 余白は一覧表示と揃え、切り替えても見出しの位置が動かないようにする。
        val padding = if (padded) {
          PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
        } else {
          PaddingValues(bottom = PathlySpacing.md, end = PathlySpacing.xs)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { header() }
            IconButton(
              onClick = {
                // 検索欄に入力中なら、キーボードも閉じる。
                focusManager.clearFocus()
                onCollapsedChange(true)
              },
            ) {
              Icon(painter = painterResource(R.drawable.ic_expand_less), contentDescription = "たたむ")
            }
          }
          Spacer(modifier = Modifier.height(if (padded) 12.dp else PathlySpacing.xs))
          controls()
        }
      }
    }
  }
}

/**
 * 地図表示で見出し・検索欄・絞り込みをたたんだときの帯。一覧へ戻る切り替えと、絞り込んでいることが分かる件数を残す
 * （絞り込みを忘れて「足りない」と思わないように）。件数の部分を押してもひろがる。
 */
@Composable
private fun ListMapCollapsedBar(
  mode: ListMapMode,
  onModeChange: (ListMapMode) -> Unit,
  summary: String,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = PathlyShapes.pill,
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = PathlyElevation.low,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ListMapToggle(mode = mode, onChange = onModeChange)
      Text(
        text = summary,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .weight(1f)
          .clickable(onClick = onExpand)
          .padding(horizontal = 4.dp, vertical = 12.dp),
      )
      actions()
      IconButton(onClick = onExpand) {
        Icon(painter = painterResource(R.drawable.ic_expand_more), contentDescription = "ひろげる")
      }
    }
  }
}

/**
 * たたんだ帯の文言。絞り込んでいなければ全件の数（「場所 12 件」）、絞り込んでいれば条件の数と「残った数 / 全件」
 * （「絞り込み 2・5 / 12 件」）。
 */
internal fun listMapSummary(noun: String, activeFilterCount: Int, visibleCount: Int, totalCount: Int): String = if (activeFilterCount == 0) {
  "$noun $totalCount 件"
} else {
  "絞り込み $activeFilterCount・$visibleCount / $totalCount 件"
}
