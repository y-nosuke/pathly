package com.pathly.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pathly.R
import kotlinx.coroutines.launch

/**
 * [ScrollToTopButton] を重ねる一覧の下に足す余白。ボタン（40dp）と下の余白（8dp）に少し足した高さ。
 * 最後の行をボタンより上までスクロールできるようにし、行の右端の操作がボタンに隠れないようにする。
 */
val ScrollToTopButtonClearance = 56.dp

/**
 * 一覧の先頭まで戻るボタン。先頭の行が画面から外れるまでスクロールしたときだけ出し、押すと先頭まで滑らかに戻す。
 * 一覧（LazyColumn）と同じ Box に重ね、右下に置く想定。
 */
@Composable
fun ScrollToTopButton(listState: LazyListState, modifier: Modifier = Modifier) {
  // スクロールのたびに組み直さないよう、出す・隠すが変わったときだけ読む。
  val visible by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
  val scope = rememberCoroutineScope()
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn() + scaleIn(),
    exit = fadeOut() + scaleOut(),
  ) {
    SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
      Icon(painter = painterResource(R.drawable.ic_arrow_upward), contentDescription = "一覧の先頭へ戻る")
    }
  }
}
