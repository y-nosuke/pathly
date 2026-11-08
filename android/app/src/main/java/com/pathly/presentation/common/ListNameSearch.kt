package com.pathly.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import com.pathly.R
import java.text.Normalizer
import java.util.Locale

// 一覧（場所・経路）を名前で絞り込むための照合と入力欄。端末内のデータだけで絞る（Places API は使わない）。

/**
 * [text] が検索語 [query] を含むか。検索語が空（空白だけ）なら絞らない（true）。
 *
 * 全角・半角（英数字・記号・カタカナ）、英字の大文字・小文字、ひらがな・カタカナの違いは無視する
 * （NFKC で揃えて小文字にし、カタカナをひらがなに置き換えてから比べる）。漢字の読みでは探さない。
 */
fun matchesNameQuery(text: String, query: String): Boolean {
  val q = normalizeForSearch(query.trim())
  if (q.isEmpty()) return true
  return normalizeForSearch(text).contains(q)
}

private fun normalizeForSearch(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT).katakanaToHiragana()

// カタカナ（ァ〜ヶ）を、同じ並びにあるひらがな（ぁ〜ゖ）に置き換える。「ヴ」は「ゔ」になる。
// 対応するひらがなが無い文字（ヷ〜ヺ など）と、ひらがな・カタカナで共通の「ー」はそのまま。
private const val KATAKANA_FIRST = 'ァ'
private const val KATAKANA_LAST = 'ヶ'
private const val KATAKANA_TO_HIRAGANA = 0x60

private fun String.katakanaToHiragana(): String = map { c -> if (c in KATAKANA_FIRST..KATAKANA_LAST) c - KATAKANA_TO_HIRAGANA else c }.joinToString("")

/** 一覧の上に置く、名前の検索欄。入力のたびに [onQueryChange] で絞り込む。右端の × で消せる。 */
@Composable
fun ListNameSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.fillMaxWidth(),
    singleLine = true,
    placeholder = { Text(placeholder) },
    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null) },
    trailingIcon = if (query.isNotEmpty()) {
      {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "検索語を消す")
        }
      }
    } else {
      null
    },
    // 入力するたびに絞るので、確定キーはキーボードを閉じるだけ。
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
  )
}
