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
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
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
  private val labeledIcons = LruCache<LabeledKey, LabeledMarkerIcon>(MAX_LABELED_ICONS)

  fun get(key: Any, content: @Composable () -> Unit): BitmapDescriptor = icons.getOrPut(key) { BitmapDescriptorFactory.fromBitmap(bitmap(key, content)) }

  /**
   * [key] の画像の下に [label]（頭に [glyph] のアイコン）を書き足した画像。名前は場所ごとに違うので、名札の部分だけは
   * Compose ではなく Canvas で直に書く（Compose で場所の数だけ描くと地図が止まる）。
   * 上の画像（バッジ）は [key] ごとに 1 回だけ描いたものを使い回す。
   *
   * [label] が null なら名札なしのバッジだけ（縮尺が小さいときの立ち寄りなど）。
   * [anchorAtCenter] ならマーカーが指す点をバッジの中心にする（既定はバッジの下端中央）。
   */
  fun getLabeled(
    key: Any,
    label: String?,
    @DrawableRes glyph: Int? = null,
    anchorAtCenter: Boolean = false,
    content: @Composable () -> Unit,
  ): LabeledMarkerIcon {
    if (label == null) return LabeledMarkerIcon(get(key, content), badgeAnchor(anchorAtCenter))
    val cacheKey = LabeledKey(key, label, glyph, anchorAtCenter)
    return labeledIcons[cacheKey] ?: drawLabeled(bitmap(key, content), label, glyph, anchorAtCenter).also { labeledIcons.put(cacheKey, it) }
  }

  /**
   * キャッシュに入れずに描く [getLabeled]。見た目がすぐ変わって使い回せないもの（立ち寄り中の滞在時間）用。
   * キャッシュに入れると、変わるたびに画像が溜まり続ける。呼び出し側で `remember` して描き直しを抑える。
   */
  fun createLabeled(
    label: String?,
    @DrawableRes glyph: Int? = null,
    anchorAtCenter: Boolean = false,
    content: @Composable () -> Unit,
  ): LabeledMarkerIcon {
    val badge = render(content)
    return if (label == null) LabeledMarkerIcon(BitmapDescriptorFactory.fromBitmap(badge), badgeAnchor(anchorAtCenter)) else drawLabeled(badge, label, glyph, anchorAtCenter)
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

  // バッジを上・名札を下に置き、どちらも左右中央に揃える。名札は [glyph] のアイコン（あれば）と名前を横に並べ、
  // どちらも白いフチ取りの濃い色にして、地図のどの色の上でも読めるようにする。
  private fun drawLabeled(badge: Bitmap, label: String, @DrawableRes glyph: Int?, anchorAtCenter: Boolean): LabeledMarkerIcon {
    val metrics = parent.resources.displayMetrics
    fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    val outline = dp(LABEL_OUTLINE_DP)
    val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP, metrics)
      typeface = Typeface.DEFAULT_BOLD
      color = LABEL_TEXT_COLOR
    }
    val stroke = TextPaint(fill).apply {
      style = Paint.Style.STROKE
      strokeWidth = outline * 2
      strokeJoin = Paint.Join.ROUND
      color = android.graphics.Color.WHITE
    }
    val icon = glyph?.let { ContextCompat.getDrawable(parent.context, it)?.mutate() }
    val iconSize = if (icon != null) ceil(dp(LABEL_ICON_DP)).toInt() else 0
    val iconGap = if (icon != null) dp(LABEL_ICON_GAP_DP) else 0f
    // アイコンを足しても名札全体の幅は変えない（その分、名前を早めに省く）。
    val text = TextUtils.ellipsize(label, fill, dp(LABEL_MAX_WIDTH_DP) - iconSize - iconGap, TextUtils.TruncateAt.END).toString()
    val textWidth = fill.measureText(text)
    val lineWidth = iconSize + iconGap + textWidth
    val fontMetrics = fill.fontMetrics
    val textHeight = ceil(fontMetrics.descent - fontMetrics.ascent + outline * 2).toInt()
    val gap = dp(LABEL_GAP_DP).toInt()

    val width = max(badge.width, ceil(lineWidth + outline * 2).toInt())
    val height = badge.height + gap + textHeight
    val bitmap = createBitmap(width, height).applyCanvas {
      drawBitmap(badge, (width - badge.width) / 2f, 0f, null)
      val lineTop = badge.height + gap
      val left = (width - lineWidth) / 2f
      if (icon != null) {
        val iconLeft = left.toInt()
        val iconTop = lineTop + (textHeight - iconSize) / 2
        // フチ取り: 白く塗ったアイコンを少しずつずらして重ね、その上に濃い色で描く。
        icon.setTint(android.graphics.Color.WHITE)
        val o = outline.toInt().coerceAtLeast(1)
        for (dx in -o..o step o) {
          for (dy in -o..o step o) {
            if (dx == 0 && dy == 0) continue
            icon.setBounds(iconLeft + dx, iconTop + dy, iconLeft + dx + iconSize, iconTop + dy + iconSize)
            icon.draw(this)
          }
        }
        icon.setTint(LABEL_TEXT_COLOR)
        icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        icon.draw(this)
      }
      val textLeft = left + iconSize + iconGap
      val baseline = lineTop + outline - fontMetrics.ascent
      drawText(text, textLeft, baseline, stroke)
      drawText(text, textLeft, baseline, fill)
    }
    // マーカーが指す点（バッジの下端中央・中心）を名札が無いときと同じにする。
    val anchorY = if (anchorAtCenter) badge.height / 2f else badge.height.toFloat()
    return LabeledMarkerIcon(
      icon = BitmapDescriptorFactory.fromBitmap(bitmap),
      anchor = Offset(0.5f, anchorY / height),
    )
  }

  private fun badgeAnchor(anchorAtCenter: Boolean) = Offset(0.5f, if (anchorAtCenter) 0.5f else 1f)

  private data class LabeledKey(val key: Any, val label: String, val glyph: Int?, val anchorAtCenter: Boolean)

  private companion object {
    const val MAX_LABELED_ICONS = 300
    const val LABEL_TEXT_SP = 12f
    const val LABEL_MAX_WIDTH_DP = 120f
    const val LABEL_GAP_DP = 1f
    const val LABEL_OUTLINE_DP = 1.5f
    const val LABEL_ICON_DP = 14f
    const val LABEL_ICON_GAP_DP = 2f
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
