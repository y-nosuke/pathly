package com.pathly.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 地図の上に重ねる**非モーダル**のシート。
 *
 * ModalBottomSheet は背後にスクリムを敷くため、色を透明にしてもスクリムがタップを吸って
 * しまい「地図は見えるのに動かせない・触ると閉じる」状態になる。地図を見ながら操作する
 * 画面ではこれが邪魔になるので、スクリムを持たない自前のシートを使う。
 *
 * 高さは3段（隠す／ハーフ／フル）。つまみを上下にドラッグして段を変え、中身は独立して
 * スクロールする。地図はシートの外側なので、パン・ズーム・タップがそのまま効く。
 */
enum class SheetDetent { HIDDEN, PEEK, FULL }

/**
 * [FloatingSheet] の開き具合。高さは px の [Animatable] で持ち、ドラッグ中は指に追従（snapTo）、
 * 離すと最寄りの段へアニメーションする。
 */
@Stable
class FloatingSheetState internal constructor(
  internal val peekPx: Float,
  internal val fullPx: Float,
  internal val allowHidden: Boolean,
  private val scope: CoroutineScope,
  initialDetent: SheetDetent,
) {
  private val heightPx = Animatable(if (initialDetent == SheetDetent.FULL) fullPx else peekPx)

  /** 現在の段。地図の下パディングなど、確定値で判断したいときに読む。 */
  var detent: SheetDetent by mutableStateOf(initialDetent)
    private set

  /**
   * 現在の高さ（px）。**シートの内側で読むこと**。外側で読むと、ドラッグやアニメの
   * 再コンポーズが地図まで波及してカクつく。
   */
  internal fun currentHeightPx(): Float = heightPx.value

  /** 指定の段へアニメーションで落ち着かせる。 */
  fun settleTo(target: SheetDetent) {
    detent = target
    scope.launch {
      heightPx.animateTo(
        when (target) {
          SheetDetent.HIDDEN -> 0f
          SheetDetent.PEEK -> peekPx
          SheetDetent.FULL -> fullPx
        },
      )
    }
  }

  internal fun dragBy(delta: Float) {
    scope.launch {
      val lowerBound = if (allowHidden) 0f else peekPx
      heightPx.snapTo((heightPx.value - delta).coerceIn(lowerBound, fullPx))
    }
  }

  /** ドラッグを離したときに、いまの高さから最寄りの段を決めて落ち着かせる。 */
  internal fun settleToNearest() {
    val h = heightPx.value
    settleTo(
      when {
        allowHidden && h < peekPx * 0.55f -> SheetDetent.HIDDEN
        h < (peekPx + fullPx) / 2f -> SheetDetent.PEEK
        else -> SheetDetent.FULL
      },
    )
  }
}

/**
 * [FloatingSheetState] を作る。高さは画面高に対する割合で指定する。
 *
 * [allowHidden] が false なら、ドラッグで隠しきれない（フォームのように、開いている間は
 * 必ず操作対象が見えていてほしいシート向け）。
 */
@Composable
fun rememberFloatingSheetState(
  peekFraction: Float = 0.45f,
  fullFraction: Float = 0.92f,
  initialDetent: SheetDetent = SheetDetent.PEEK,
  allowHidden: Boolean = true,
): FloatingSheetState {
  // 画面高は LocalWindowInfo から取る（Configuration.screenHeightDp は非推奨）。
  val containerHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
  val scope = rememberCoroutineScope()
  return remember(containerHeightPx, peekFraction, fullFraction, allowHidden) {
    FloatingSheetState(
      peekPx = containerHeightPx * peekFraction,
      fullPx = containerHeightPx * fullFraction,
      allowHidden = allowHidden,
      scope = scope,
      initialDetent = initialDetent,
    )
  }
}

/** [FloatingSheetState] の段に対応する高さ（dp）。地図の下パディングを合わせるのに使う。 */
@Composable
fun FloatingSheetState.heightOf(detent: SheetDetent): Dp {
  val density = LocalDensity.current
  return with(density) {
    when (detent) {
      SheetDetent.HIDDEN -> 0f
      SheetDetent.PEEK -> peekPx
      SheetDetent.FULL -> fullPx
    }.toDp()
  }
}

/**
 * 地図の上に重ねるシート本体。角丸＋影で"浮いている"見た目にし、上部のつまみで段を変える。
 * 呼び出し側の Box で `.align(Alignment.BottomCenter)` して使う。
 */
@Composable
fun FloatingSheet(
  state: FloatingSheetState,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  Surface(
    modifier = modifier
      .fillMaxWidth()
      // 高さの読み取りはここ（シートの内側）だけに閉じ込める。
      .height(with(density) { state.currentHeightPx().toDp() }),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 12.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(),
    ) {
      SheetDragHandle(
        onDrag = { state.dragBy(it) },
        onDragEnd = { state.settleToNearest() },
      )
      content()
    }
  }
}

/** シート上部のつまみ。縦ドラッグでシート高さを変える（中身のスクロールとは独立）。 */
@Composable
private fun SheetDragHandle(
  onDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .pointerInput(Unit) {
        detectVerticalDragGestures(
          onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
          onDragEnd = onDragEnd,
          onDragCancel = onDragEnd,
        )
      }
      .padding(vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .width(40.dp)
        .height(4.dp)
        .background(
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          RoundedCornerShape(2.dp),
        ),
    )
  }
}
