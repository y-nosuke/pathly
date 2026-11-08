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
 * 全角・半角（英数字・記号・カタカナ）と英字の大文字・小文字の違いは無視する（NFKC で揃えてから小文字にする）。
 * ひらがなとカタカナは同じとみなさない。
 */
fun matchesNameQuery(text: String, query: String): Boolean {
  val q = normalizeForSearch(query.trim())
  if (q.isEmpty()) return true
  return normalizeForSearch(text).contains(q)
}

private fun normalizeForSearch(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

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
