package com.pathly.presentation.common

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing

// 一覧の画面の型（docs/designs/ui-direction.md）の「見出し」「ツール行」（統計の帯は ListStatsCard.kt）。履歴・場所の一覧で同じものを使う。

/**
 * 一覧の見出し。左に画面の名前、右端に検索のアイコンと [actions]（一覧／地図の切り替えなど）。
 * 検索のアイコンを押すと、見出しの位置が名前の検索の窓に変わる。検索語が入っている間は窓のまま
 * （語が効いていることが見えるように）。窓の ← で閉じると語も消える。
 *
 * 窓を開いているか（[searchOpen]）は呼ぶ側で持つ（一覧表示と地図表示で同じ見出しを出すので、切り替えても揃うように）。
 */
@Composable
fun ListHeader(
  title: String,
  query: String,
  onQueryChange: (String) -> Unit,
  searchPlaceholder: String,
  searchOpen: Boolean,
  onSearchOpenChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val showSearch = searchOpen || query.isNotEmpty()
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = ListHeaderHeight)
      .padding(start = if (showSearch) PathlySpacing.xs else PathlySpacing.screenEdge, end = PathlySpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showSearch) {
      ListSearchBox(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = searchPlaceholder,
        // 開いたときだけ入力へ移る（画面の回転・詳細から戻ったときに、キーボードが勝手に出ないように）。
        requestFocus = searchOpen,
        onClose = {
          onSearchOpenChange(false)
          onQueryChange("")
        },
        modifier = Modifier.weight(1f),
      )
    } else {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { onSearchOpenChange(true) }) {
        Icon(painter = painterResource(R.drawable.ic_search), contentDescription = "検索")
      }
    }
    actions()
  }
}

/** 見出しの位置に開く検索の窓。左の ← で閉じ、右の × で語だけ消す。 */
@Composable
private fun ListSearchBox(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  requestFocus: Boolean,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  var focusedOnce by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(requestFocus) {
    if (requestFocus && !focusedOnce) {
      focusRequester.requestFocus()
      focusedOnce = true
    }
  }
  TextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier
      .focusRequester(focusRequester)
      .testTag("listSearchBox"),
    singleLine = true,
    shape = PathlyShapes.pill,
    placeholder = { Text(placeholder) },
    leadingIcon = {
      IconButton(onClick = {
        focusManager.clearFocus()
        onClose()
      }) {
        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "検索を閉じる")
      }
    },
    trailingIcon = if (query.isNotEmpty()) {
      {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "検索語を消す")
        }
      }
    } else {
      null
    },
    colors = TextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
    ),
    // 入力するたびに絞るので、確定キーはキーボードを閉じるだけ。
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
  )
}

/**
 * 一覧のツール行。左に絞り込みのボタン（[leading]）、右に表示のメニュー（[trailing]。地図表示ではレイヤー）。
 * Material 3 Expressive を入れるとき（#201）、中身をボタンのグループに差し替える場所（→ ADR 0041）。
 */
@Composable
fun ListToolRow(
  modifier: Modifier = Modifier,
  leading: @Composable RowScope.() -> Unit = {},
  trailing: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = PathlySpacing.screenEdge),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leading()
    Spacer(modifier = Modifier.weight(1f))
    trailing()
  }
}

/**
 * ツール行の右の「表示」のメニュー。ボタンには今の見せ方（[label]。「日付 ・ 新しい順」など）を出し、押すと
 * 並べ替えの軸・向き（場所では分け方も）を選ぶメニューが開く。中身は [content] で渡す（`close` で閉じる）。
 */
@Composable
fun ListDisplayMenuButton(
  label: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier = modifier) {
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("listDisplayMenuButton")) {
      Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Icon(
        painter = painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
        contentDescription = "表示のしかた",
        modifier = Modifier.size(18.dp),
      )
    }
    DropdownMenu(
      expanded = open,
      onDismissRequest = { open = false },
      modifier = Modifier
        .widthIn(min = 220.dp)
        .testTag("listDisplayMenu"),
    ) {
      content { open = false }
    }
  }
}

/** 表示のメニューの中の、まとまりの見出し（「並べ替え」「向き」など）。2 つ目からは上に区切り線を引く。 */
@Composable
fun ListMenuSectionTitle(text: String, divider: Boolean = false) {
  if (divider) HorizontalDivider(modifier = Modifier.padding(vertical = PathlySpacing.xs))
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = PathlySpacing.lg, vertical = PathlySpacing.xs),
  )
}

/** 表示のメニューの中の、1 つだけ選ぶ項目（左に丸）。 */
@Composable
fun ListMenuChoice(text: String, selected: Boolean, onClick: () -> Unit) {
  DropdownMenuItem(
    text = { Text(text) },
    leadingIcon = { RadioButton(selected = selected, onClick = null) },
    onClick = onClick,
  )
}

/**
 * 分けた一覧のまとまりの見出し（開閉の印・名前・[trailing]）。押すと開閉する。[onClick] が null なら（名前の検索中）
 * 押しても開閉しない。いちばん外の段（[depth] が 0）は字を大きくする。下に区切り線を引く。
 *
 * @param trailing 右端に出す件数など（場所なら「12」、履歴なら「5 回 ・ 32.1 km」）。
 * @param action [trailing] のさらに右に置く、開閉とは別の操作のボタン（場所一覧の地域の「地図で見る」）。
 *   押せる広さ（48dp）を見出しの上下の余白に重ねるよう、置くときは上下の余白を詰める（行は 48dp。
 *   字の大きい 1 段目は置かないときと同じ高さ、2 段目は少し高くなる）。
 */
@Composable
fun ListGroupHeader(
  label: String,
  trailing: String,
  depth: Int,
  expanded: Boolean,
  indent: Dp,
  testTag: String,
  onClick: (() -> Unit)?,
  action: (@Composable () -> Unit)? = null,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .testTag(testTag)
        .then(
          if (onClick != null) {
            Modifier.clickable(onClickLabel = if (expanded) "閉じる" else "開く", onClick = onClick)
          } else {
            Modifier
          },
        )
        .semantics { stateDescription = if (expanded) "開いている" else "閉じている" }
        .padding(
          start = PathlySpacing.screenEdge + indent,
          // ボタンの絵の右端を画面の端の余白に揃える（ボタンは絵の周りに 12dp の押せる余白を持つ）。
          end = if (action != null) PathlySpacing.screenEdge - ListGroupActionInset else PathlySpacing.screenEdge,
          top = if (action != null) 0.dp else PathlySpacing.md,
          bottom = if (action != null) 0.dp else PathlySpacing.md,
        ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // 開いていれば ⌄、閉じていれば ›（同じ絵を回す）。
      Icon(
        painter = painterResource(R.drawable.ic_expand_more),
        contentDescription = null,
        modifier = Modifier.rotate(if (expanded) 0f else -90f),
      )
      Spacer(modifier = Modifier.width(PathlySpacing.sm))
      Text(
        text = label,
        style = if (depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = trailing,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      if (action != null) {
        Spacer(modifier = Modifier.width(PathlySpacing.xs))
        action()
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

// 見出しの右端のボタン（48dp）の、絵（24dp）の周りの押せる余白。
private val ListGroupActionInset = 12.dp

/** 分けた一覧の、まとまりの段ごとの字下げ。 */
val ListGroupIndent = PathlySpacing.lg

/** [text] の中の数字（「3,815」「4.3」など）だけに [numberStyle] をかける。 */
internal fun emphasizeNumbers(text: String, numberStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
  append(text)
  NumberPattern.findAll(text).forEach { addStyle(numberStyle, it.range.first, it.range.last + 1) }
}

private val NumberPattern = Regex("""\d[\d,.]*""")

/** 見出しの行の高さ（タイトルと 48dp のアイコンが収まる高さ）。 */
private val ListHeaderHeight = 56.dp
