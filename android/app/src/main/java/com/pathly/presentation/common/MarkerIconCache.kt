package com.pathly.presentation.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.ceil
import kotlin.math.max

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
  private val bitmaps = HashMap<Any, Bitmap>()
  private val icons = HashMap<Any, BitmapDescriptor>()

  // 名前入りの画像は場所ごとにできるので、件数に上限を付ける（1 枚 100〜200KB。地図を広く動かすと
  // 画面に出なくなった場所の画像まで溜まる）。捨てても、表示中のマーカーは自分の画像を持ち続け、
  // 次に要るときに描き直すだけ。
  private val labeledIcons = LruCache<Pair<Any, String>, LabeledMarkerIcon>(MAX_LABELED_ICONS)

  fun get(key: Any, content: @Composable () -> Unit): BitmapDescriptor = icons.getOrPut(key) { BitmapDescriptorFactory.fromBitmap(bitmap(key, content)) }

  /**
   * [key] の画像の下に [label] を書き足した画像。名前は場所ごとに違うので、名前の部分だけは
   * Compose ではなく Canvas で直に書く（Compose で場所の数だけ描くと地図が止まる）。
   * 上の画像（バッジ）は [key] ごとに 1 回だけ描いたものを使い回す。
   */
  fun getLabeled(key: Any, label: String, content: @Composable () -> Unit): LabeledMarkerIcon {
    val cacheKey = key to label
    return labeledIcons[cacheKey] ?: drawLabeled(bitmap(key, content), label).also { labeledIcons.put(cacheKey, it) }
  }

  private fun bitmap(key: Any, content: @Composable () -> Unit): Bitmap = bitmaps.getOrPut(key) { render(content) }

  // maps-compose の rememberComposeBitmapDescriptor と同じ描き方。描いている間だけ画面のルートに
  // ComposeView を足す（足さないと Compose が測れない）。
  private fun render(content: @Composable () -> Unit): Bitmap {
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
      return createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1))
        .applyCanvas { view.draw(this) }
    } finally {
      host.removeView(view)
    }
  }

  // バッジを上・名前を下に置き、どちらも左右中央に揃える。名前は白いフチ取りの濃い文字にして、
  // 地図のどの色の上でも読めるようにする。
  private fun drawLabeled(badge: Bitmap, label: String): LabeledMarkerIcon {
    val metrics = parent.resources.displayMetrics
    fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    val outline = dp(LABEL_OUTLINE_DP)
    val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP, metrics)
      typeface = Typeface.DEFAULT_BOLD
      color = LABEL_TEXT_COLOR
      textAlign = Paint.Align.CENTER
    }
    val stroke = TextPaint(fill).apply {
      style = Paint.Style.STROKE
      strokeWidth = outline * 2
      strokeJoin = Paint.Join.ROUND
      color = android.graphics.Color.WHITE
    }
    val text = TextUtils.ellipsize(label, fill, dp(LABEL_MAX_WIDTH_DP), TextUtils.TruncateAt.END).toString()
    val textWidth = ceil(fill.measureText(text) + outline * 2).toInt()
    val fontMetrics = fill.fontMetrics
    val textHeight = ceil(fontMetrics.descent - fontMetrics.ascent + outline * 2).toInt()
    val gap = dp(LABEL_GAP_DP).toInt()

    val width = max(badge.width, textWidth)
    val height = badge.height + gap + textHeight
    val bitmap = createBitmap(width, height).applyCanvas {
      drawBitmap(badge, (width - badge.width) / 2f, 0f, null)
      val baseline = badge.height + gap + outline - fontMetrics.ascent
      drawText(text, width / 2f, baseline, stroke)
      drawText(text, width / 2f, baseline, fill)
    }
    // マーカーが指す点（バッジの下端中央）をラベルが無いときと同じにする。
    return LabeledMarkerIcon(
      icon = BitmapDescriptorFactory.fromBitmap(bitmap),
      anchor = Offset(0.5f, badge.height.toFloat() / height),
    )
  }

  private companion object {
    const val MAX_LABELED_ICONS = 300
    const val LABEL_TEXT_SP = 12f
    const val LABEL_MAX_WIDTH_DP = 120f
    const val LABEL_GAP_DP = 1f
    const val LABEL_OUTLINE_DP = 1.5f
    const val LABEL_TEXT_COLOR = 0xFF212121.toInt()
  }
}

/** 名前を添えたマーカーの画像と、マーカーが指す点（[anchor]、画像の幅・高さに対する割合）。 */
internal class LabeledMarkerIcon(val icon: BitmapDescriptor, val anchor: Offset)

/** GoogleMap の content の中で呼ぶ。 */
@Composable
internal fun rememberMarkerIconCache(): MarkerIconCache {
  val parent = LocalView.current as ViewGroup
  val compositionContext = rememberCompositionContext()
  return remember(parent, compositionContext) { MarkerIconCache(parent, compositionContext) }
}
