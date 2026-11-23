package com.pathly.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyShapes
import com.pathly.ui.theme.PathlySpacing
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
 * ドラッグは手間なので、つまみの行のタップ（ハーフ⇔フル）と右端の畳むボタンでも段を変えられる。
 */
enum class SheetDetent { HIDDEN, PEEK, FULL }

/**
 * [FloatingSheet] の開き具合。高さは px の [Animatable] で持ち、ドラッグ中は指に追従（snapTo）、
 * 離すと最寄りの段へアニメーションする。
 */
@Stable
class FloatingSheetState internal constructor(
  peekPx: Float,
  internal val fullPx: Float,
  internal val allowHidden: Boolean,
  private val scope: CoroutineScope,
  initialDetent: SheetDetent,
) {
  private val heightPx = Animatable(
    when (initialDetent) {
      SheetDetent.HIDDEN -> 0f
      SheetDetent.PEEK -> peekPx
      SheetDetent.FULL -> fullPx
    },
  )

  // 作ったときのハーフの高さ。[raisePeekTo] で上げたものを戻す先。
  private val basePeekPx = peekPx

  /** ハーフの高さ（px）。ふつうは作ったときの値で、[raisePeekTo] で一時的に上げられる。 */
  internal var peekPx: Float by mutableFloatStateOf(peekPx)
    private set

  /**
   * ハーフの高さを [minPx] 以上に上げる（フルより高くはしない）。null なら作ったときの高さに戻す。
   * 同じ state を中身の違うシートで使い回す画面で、中身の多いシートのときだけハーフを高くするのに使う。
   * ハーフで開いていれば、新しい高さへ動かす。
   */
  fun raisePeekTo(minPx: Float?) {
    val target = minPx?.let { maxOf(basePeekPx, minOf(it, fullPx)) } ?: basePeekPx
    if (target == peekPx) return
    peekPx = target
    if (detent == SheetDetent.PEEK) settleTo(SheetDetent.PEEK)
  }

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

  /** フルの段がハーフより高いか。同じ高さのシート（中身が少ない）では、つまみのタップで広げない。 */
  internal val hasFull: Boolean get() = fullPx > peekPx + 1f

  /** つまみのタップ。フルならハーフへ、それ以外はフルへ。 */
  internal fun toggleFull() {
    settleTo(if (detent == SheetDetent.FULL) SheetDetent.PEEK else SheetDetent.FULL)
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

/**
 * ハーフの高さを dp で決める [FloatingSheetState]（ハーフの中身の量が決まっているシート向け）。
 * [fullFraction] が null ならハーフとフルは同じ高さで、つまみのタップでは広げない。渡すと、フルは画面高に対するその割合
 * （ハーフより低くはならない）で、引き上げると続きの一覧などが見える。[allowHidden] なら畳める（戻すボタンは
 * [FloatingSheet] の restoreLabel）。文字の大きさの設定が大きいと中身が伸びるので、ハーフも同じ割合で伸ばす（小さくはしない）。
 */
@Composable
fun rememberFixedFloatingSheetState(
  height: Dp,
  allowHidden: Boolean = true,
  fullFraction: Float? = null,
  // 作り直したときの段（高さが変わって作り直しても、畳んでいたなら畳んだままにしたいときに渡す）。
  initialDetent: SheetDetent = SheetDetent.PEEK,
): FloatingSheetState {
  val density = LocalDensity.current
  val heightPx = with(density) { height.toPx() } * density.fontScale.coerceAtLeast(1f)
  val containerHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
  val fullPx = fullFraction?.let { maxOf(heightPx, containerHeightPx * it) } ?: heightPx
  val scope = rememberCoroutineScope()
  return remember(heightPx, fullPx, allowHidden) {
    FloatingSheetState(
      peekPx = heightPx,
      fullPx = fullPx,
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
 *
 * [restoreLabel] を渡すと、畳みきった（[SheetDetent.HIDDEN]）ときに戻すためのボタンを出す。
 * 畳めるシートは戻す手段が無いと操作を見失うので、[FloatingSheetState.allowHidden] が
 * true のシートでは基本的に指定する。つまみの行の右端の「畳む」ボタンも、戻すボタンがあるときだけ出す。
 */
@Composable
fun FloatingSheet(
  state: FloatingSheetState,
  modifier: Modifier = Modifier,
  restoreLabel: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  // 高さの読み取りはここ（シートの内側）だけに閉じ込める。
  val heightPx = state.currentHeightPx()
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    if (restoreLabel != null && state.detent == SheetDetent.HIDDEN) {
      Surface(
        onClick = { state.settleTo(SheetDetent.PEEK) },
        shape = PathlyShapes.pill,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = PathlyElevation.floating,
        modifier = Modifier
          .navigationBarsPadding()
          .padding(bottom = PathlySpacing.xl),
      ) {
        Text(
          text = restoreLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = PathlySpacing.lg, vertical = PathlySpacing.md),
        )
      }
    }

    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(with(density) { heightPx.toDp() }),
      shape = PathlyShapes.sheetTop,
      color = MaterialTheme.colorScheme.surface,
      // 完全に畳んだときは影を消す（高さ0の面に影が残ると画面下端に線が出る）。
      shadowElevation = if (heightPx <= 0.5f) 0.dp else PathlyElevation.sheet,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .navigationBarsPadding(),
      ) {
        SheetDragHandle(
          onDrag = { state.dragBy(it) },
          onDragEnd = { state.settleToNearest() },
          expanded = state.detent == SheetDetent.FULL,
          onToggle = if (state.hasFull) state::toggleFull else null,
          onHide = if (state.allowHidden && restoreLabel != null) {
            { state.settleTo(SheetDetent.HIDDEN) }
          } else {
            null
          },
        )
        content()
      }
    }
  }
}

/**
 * シート上部のつまみの行。縦ドラッグでシート高さを変える（中身のスクロールとは独立）。
 * 行のタップで [onToggle]（ハーフ⇔フル）、右端のボタンで [onHide]（畳む）。どちらも null なら出さない。
 */
@Composable
private fun SheetDragHandle(
  onDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
  expanded: Boolean,
  onToggle: (() -> Unit)?,
  onHide: (() -> Unit)?,
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
      .then(
        if (onToggle != null) {
          Modifier.clickable(onClickLabel = if (expanded) "シートを縮める" else "シートを広げる", onClick = onToggle)
        } else {
          Modifier
        },
      )
      .heightIn(min = if (onHide != null) 40.dp else 24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .width(40.dp)
        .height(4.dp)
        .background(
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          PathlyShapes.pill,
        ),
    )
    if (onHide != null) {
      IconButton(
        onClick = onHide,
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = PathlySpacing.xs),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_expand_more),
          contentDescription = "シートを畳む",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
