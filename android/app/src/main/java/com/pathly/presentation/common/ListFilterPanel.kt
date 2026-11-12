package com.pathly.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.pathly.R

/**
 * 一覧の絞り込みで、いま効いている条件 1 つ。一覧の上にチップで並べ、× で外す（→ specs/screens.md）。
 *
 * @param label チップの文言（「行きたい」「東京都 他1」など）。
 * @param onRemove その軸の絞り込みを外す。
 * @param onToggle チップを押したときに切り替える先（3 状態の軸の「〇〇 ⇄ 〇〇以外」）。null なら押すと絞り込みの欄を開く
 *   （業種・地域のように、切り替える先が 1 つに決まらない軸）。
 */
data class ActiveFilter(val label: String, val onRemove: () -> Unit, val onToggle: (() -> Unit)? = null)

/** 3 状態の軸のチップを押したときの切り替え先。効いている 2 つ（〇〇 と 〇〇以外）の間で入れ替える（外すのは ×）。 */
fun <T> flippedChoice(current: T, first: T, second: T): T = if (current == first) second else first

/**
 * 「絞り込み」ボタン。押すたびに一覧の先頭の絞り込みの欄を開く／閉じる。効いている条件があれば塗りつぶし、数を添える。
 */
@Composable
fun FilterPanelButton(activeCount: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  FilterChip(
    selected = activeCount > 0,
    onClick = onClick,
    label = { Text(if (activeCount > 0) "絞り込み $activeCount" else "絞り込み") },
    leadingIcon = {
      Icon(
        painter = painterResource(R.drawable.ic_filter),
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize),
      )
    },
    trailingIcon = {
      Icon(
        painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
        contentDescription = if (expanded) "絞り込みを閉じる" else "絞り込みを開く",
        modifier = Modifier.size(FilterChipDefaults.IconSize),
      )
    },
    modifier = modifier.testTag("filterPanelButton"),
  )
}

/**
 * いま効いている条件のチップの行。× で個別に外す。チップ自体を押すと、3 状態の軸は「〇〇 ⇄ 〇〇以外」を切り替え
 * （[ActiveFilter.onToggle]）、それ以外は絞り込みの欄を開く（[onChipClick]）。うっかり押して条件が消えないように、
 * 外すのは × だけ。何も効いていなければ何も出さない。
 * 横に収まらなければ横にスクロールする。絞り込みの欄を開いている間は出さない（呼ぶ側で。欄で選ぶたびに
 * 行が出たり消えたりして、欄がずれないように → adr/0031）。
 *
 * [onReset] があれば、行の先頭に「初期状態に戻す」を出す（絞り込み・検索・並べ替えをまとめて戻す。
 * 初期状態のときは null を渡して出さない）。
 */
@Composable
fun ActiveFilterChips(
  filters: List<ActiveFilter>,
  onChipClick: () -> Unit,
  onReset: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  if (filters.isEmpty() && onReset == null) return
  Row(
    modifier = modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onReset != null) {
      AssistChip(onClick = onReset, label = { Text("初期状態に戻す") })
    }
    filters.forEach { filter ->
      InputChip(
        selected = true,
        onClick = filter.onToggle ?: onChipClick,
        // テストから押せるように（一覧の行にも同じ文言が出るため）。
        modifier = Modifier.testTag("activeFilter=${filter.label}"),
        label = { Text(filter.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
          Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "${filter.label}の絞り込みを外す",
            modifier = Modifier
              .size(FilterChipDefaults.IconSize)
              .clickable(role = Role.Button, onClick = filter.onRemove),
          )
        },
      )
    }
  }
}

/**
 * 絞り込みの欄。一覧の先頭（一覧と同じスクロールの中）に差し込み、選びながら下へスクロールして結果を見られるようにする
 * （→ adr/0031）。各軸を縦に並べ、選んだ時点で一覧に効く。開く／閉じるは上の「絞り込み」ボタン。
 *
 * 見出しには残る件数と「初期状態に戻す」「すべて外す」を置く。どちらも押せないときは消さずに押せなくするだけにする
 * （出たり消えたりして、隣のボタンの位置が変わらないように）。
 *
 * @param resultCount いまの条件で残る件数（見出しに出す）。
 * @param canClear 外せる条件があるか（無ければ「すべて外す」を押せなくする）。
 * @param canReset 絞り込み・検索・並べ替えのどれかが初期状態と違うか（無ければ「初期状態に戻す」を押せなくする）。
 * @param onReset 絞り込み・検索・並べ替えをまとめて初期状態に戻す。
 */
@Composable
fun ListFilterPanel(
  resultCount: Int,
  canClear: Boolean,
  onClearAll: () -> Unit,
  canReset: Boolean,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("listFilterPanel"),
  ) {
    Column(
      modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "$resultCount 件",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset, enabled = canReset) { Text("初期状態に戻す") }
        TextButton(onClick = onClearAll, enabled = canClear) { Text("すべて外す") }
      }
      Column(modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        content()
      }
    }
  }
}

/**
 * 絞り込みの欄を開いている間だけ、画面の下に浮かべる「絞り込みを閉じる」ボタン。上の「絞り込み」ボタンは片手だと
 * 親指が届きにくいので、下でも閉じられるようにする（戻る操作でも閉じる → specs/screens.md）。
 * 高さは「先頭へ戻る」ボタンと同じ 40dp にし、一覧の下の余白（[ScrollToTopButtonClearance]）に収める。
 */
@Composable
fun FilterPanelCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shadowElevation = 3.dp,
    modifier = modifier
      .height(40.dp)
      .testTag("filterPanelCloseButton"),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, end = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(painter = painterResource(R.drawable.ic_expand_less), contentDescription = null, modifier = Modifier.size(18.dp))
      Text("絞り込みを閉じる", style = MaterialTheme.typography.labelLarge)
    }
  }
}

/** 絞り込みの欄の中の 1 つの軸（見出し＋中身）。 */
@Composable
fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    content()
  }
}

/**
 * たためる軸（業種・地域など、選択肢が多い軸）。ふだんはたたみ、見出しに選んでいるもの（[summary]）を出す。
 * 見出しを押すと開く／閉じる。欄が長くなりすぎて結果が遠くならないようにするため。
 */
@Composable
fun CollapsibleFilterSection(title: String, summary: String?, content: @Composable ColumnScope.() -> Unit) {
  var expanded by rememberSaveable(title) { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = { expanded = !expanded })
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        summary ?: "指定なし",
        style = MaterialTheme.typography.bodyMedium,
        color = if (summary != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 12.dp),
      )
      Icon(
        painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
        contentDescription = if (expanded) "${title}をたたむ" else "${title}を開く",
      )
    }
    if (expanded) content()
  }
}

/**
 * 3 状態の軸（指定なし／〇〇／〇〇以外 など）を、並べたボタンから 1 つ選ぶ。
 * 押すたびに状態が巡る形にしない（押す前に何が起きるか分かるように）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FilterChoiceSection(
  title: String,
  options: List<T>,
  selected: T,
  labelOf: (T) -> String,
  onSelect: (T) -> Unit,
) {
  FilterSection(title) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, option ->
        SegmentedButton(
          selected = option == selected,
          onClick = { onSelect(option) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
          // テストから「軸=選択肢」で押せるように（一覧の行にも同じ文言が出るため）。
          modifier = Modifier.testTag("$title=${labelOf(option)}"),
          icon = {},
          // 3 つ並ぶと幅が狭いので、少し小さい文字にする（「お気に入り以外」が切れないように）。
          label = { Text(labelOf(option), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
      }
    }
  }
}

/** 3 状態の軸の選択肢の文言。指定なしは「指定なし」、ほかはその状態の名前。 */
fun <T : Enum<T>> choiceLabel(option: T, any: T, label: (T) -> String): String = if (option == any) "指定なし" else label(option)
