package com.pathly.presentation.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.pathly.R

/**
 * 一覧のタブ（場所・履歴）の見せ方。一覧表示か、地図表示か。
 * SavedStateHandle には enum の名前で保存しているので、値の名前を変えると前に保存した見せ方が戻らなくなる。
 */
enum class ListMapMode { LIST, MAP }

/**
 * 一覧表示と地図表示の切り替え（場所タブ・履歴タブで共通）。今と反対の見せ方のアイコンを出し、押すとそちらへ切り替わる。
 * [onChange] には切り替え先を渡す。
 */
@Composable
internal fun ListMapToggle(mode: ListMapMode, onChange: (ListMapMode) -> Unit) {
  val isMap = mode == ListMapMode.MAP
  IconButton(onClick = { onChange(if (isMap) ListMapMode.LIST else ListMapMode.MAP) }) {
    Icon(
      painter = painterResource(if (isMap) R.drawable.ic_list else R.drawable.ic_map),
      contentDescription = if (isMap) "一覧で見る" else "地図で見る",
    )
  }
}
