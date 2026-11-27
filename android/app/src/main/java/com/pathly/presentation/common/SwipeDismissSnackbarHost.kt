package com.pathly.presentation.common

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * アプリのスナックバーの出し先。**横にスワイプすると消せる**（左右どちらへでも）。
 * 取り消しのように押すまで残るスナックバーも、× のボタンを置かずにスワイプで片付けられるようにする（どの画面でも同じ消し方）。
 * 消すと、そのスナックバーを待っている側には「閉じた」（取り消さなかった）と返る。
 */
@Composable
fun SwipeDismissSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
  SnackbarHost(hostState = hostState, modifier = modifier) { data ->
    // スナックバーごとに作り直す（前のスナックバーをスワイプした位置を引き継がない）。
    key(data) {
      val dismissState = rememberSwipeToDismissBoxState()
      LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
      }
      SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = Modifier.testTag("swipeDismissSnackbar"),
      ) {
        Snackbar(data)
      }
    }
  }
}
