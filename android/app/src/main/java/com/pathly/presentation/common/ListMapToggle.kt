package com.pathly.presentation.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.pathly.R

/**
 * 一覧表示と地図表示の切り替え（場所タブ・履歴タブで共通）。今と反対の見せ方のアイコンを出し、押すとそちらへ切り替わる。
 * [onChange] には切り替え先が地図なら true を渡す。
 */
@Composable
internal fun ListMapToggle(isMap: Boolean, onChange: (toMap: Boolean) -> Unit) {
  IconButton(onClick = { onChange(!isMap) }) {
    Icon(
      painter = painterResource(if (isMap) R.drawable.ic_list else R.drawable.ic_map),
      contentDescription = if (isMap) "一覧で見る" else "地図で見る",
    )
  }
}
