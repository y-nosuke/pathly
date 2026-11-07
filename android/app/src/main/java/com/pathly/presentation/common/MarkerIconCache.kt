package com.pathly.presentation.common

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * マーカーの画像を、**見た目ごとに 1 回だけ** Compose で描いて使い回すキャッシュ。GoogleMap 1 つにつき 1 つ。
 *
 * `MarkerComposable` はマーカー 1 つごとに ComposeView を作って画面に足し、描いてから外す。
 * 登録済みの場所はズームでまとめ直すたびにマーカーを数十個作り直すので、これがまとめて走ると
 * 地図が 1 秒近く止まる。登録済みの場所の見た目は種類が少ない（訪問状態 × 業種 × 行きたい、
 * まとまりは件数 × 行きたい）ので、同じ見た目は同じ画像で足りる。
 *
 * **[key] には見た目を決める値をすべて入れる。** 抜けると、違う見た目のマーカーに古い画像が出る。
 */
internal class MarkerIconCache(
  private val parent: ViewGroup,
  private val compositionContext: CompositionContext,
) {
  private val icons = HashMap<Any, BitmapDescriptor>()

  fun get(key: Any, content: @Composable () -> Unit): BitmapDescriptor = icons.getOrPut(key) { render(content) }

  // maps-compose の rememberComposeBitmapDescriptor と同じ描き方。描いている間だけ画面のルートに
  // ComposeView を足す（足さないと Compose が測れない）。
  private fun render(content: @Composable () -> Unit): BitmapDescriptor {
    val host = parent.rootView as? ViewGroup ?: parent
    val view = ComposeView(host.context).apply {
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      setParentCompositionContext(compositionContext)
      setContent(content)
    }
    host.addView(view)
    try {
      // ComposeView は描く段で中身のレイアウトを確定させるので、先に空のキャンバスへ一度描く。
      view.draw(Canvas())
      val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
      view.measure(unspecified, unspecified)
      view.layout(0, 0, view.measuredWidth, view.measuredHeight)
      val bitmap = createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1))
      bitmap.applyCanvas { view.draw(this) }
      return BitmapDescriptorFactory.fromBitmap(bitmap)
    } finally {
      host.removeView(view)
    }
  }
}

/** GoogleMap の content の中で呼ぶ。 */
@Composable
internal fun rememberMarkerIconCache(): MarkerIconCache {
  val parent = LocalView.current as ViewGroup
  val compositionContext = rememberCompositionContext()
  return remember(parent, compositionContext) { MarkerIconCache(parent, compositionContext) }
}
