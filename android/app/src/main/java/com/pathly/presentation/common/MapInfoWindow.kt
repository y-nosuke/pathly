package com.pathly.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerInfoWindowComposable
import com.google.maps.android.compose.MarkerState
import com.pathly.ui.theme.PathlySpacing

// マーカーの吹き出し（InfoWindow）は Compose ではなく地図（GoogleMap）が出しているので、
// 放っておくとシステムバックの対象にならない。一度出すと地図の別の場所をタップするまで消えず、
// バックで画面を戻ってもそのままに見える。ここで開閉を捕まえて、バックで閉じられるようにする。

/**
 * 地図の吹き出しの開閉状態。**GoogleMap 1 つにつき 1 つ**持つ。
 *
 * 覚えるのは 1 件でよい。GoogleMap は吹き出しを地図全体で同時に 1 つしか出さないため。
 * ただし**どのマーカーの通知かは必ず見る**。別のマーカーをタップしたときの通知は
 * 「B のタップ → B が開く → A が閉じる」の順で来るので、A の「閉じた」で無条件に忘れると
 * 開いたばかりの B を取りこぼす。
 */
@Stable
internal class MapInfoWindowState {
  private var openMarker by mutableStateOf<MarkerState?>(null)

  val isOpen: Boolean get() = openMarker != null

  /** マーカーのタップ。吹き出しを出すのは地図の既定の挙動に任せているので、開いたものとして覚える。 */
  fun onMarkerClick(state: MarkerState) {
    openMarker = state
  }

  /** 地図の他の場所をタップするなどして閉じたとき。 */
  fun onInfoWindowClose(state: MarkerState) {
    if (openMarker === state) openMarker = null
  }

  /**
   * マーカーごと消えたとき（表示トグルOFF・一覧の入れ替え）。
   * これを拾わないと、閉じるものが無いのにバックだけ吸われる。
   */
  fun onMarkerDisposed(state: MarkerState) {
    if (openMarker === state) openMarker = null
  }

  /** 開いていれば閉じる。マーカーが地図から外れていれば MarkerState 側で無視される。 */
  fun close() {
    openMarker?.hideInfoWindow()
    openMarker = null
  }
}

/**
 * マーカーの重なり順の既定。登録済みの場所（0。いちばん奥）より 1 つ上に置く。
 * 地図は負の zIndex を効かせないので、奥に置きたいものを 0 にし、他をこれで上げる。
 */
internal const val DEFAULT_MARKER_Z_INDEX = 1f

/** 吹き出しの状態を作り、システムバックで閉じられるようにする。GoogleMap を置く側で呼ぶ。 */
@Composable
internal fun rememberMapInfoWindowState(): MapInfoWindowState {
  val state = remember { MapInfoWindowState() }
  MapInfoWindowBackHandler(state)
  return state
}

// isOpen の購読をこの関数に閉じ込め、吹き出しの開閉で画面全体が再コンポーズされないようにする。
@Composable
private fun MapInfoWindowBackHandler(state: MapInfoWindowState) {
  BackHandler(enabled = state.isOpen) { state.close() }
}

/**
 * このシートを閉じるとき、地図の吹き出しも一緒に閉じる。**シートの中で呼ぶ**。
 *
 * マーカーのタップはシートと吹き出しを同時に出す。バックはいちばん後に登録されたハンドラ
 * （＝シート）が受けるので、これが無いと 1 回目でシート・2 回目で吹き出しの 2 度押しになる。
 * ひと続きの操作で出たものなので、まとめて畳む。
 */
@Composable
internal fun CloseInfoWindowWithSheet(infoWindow: MapInfoWindowState) {
  DisposableEffect(Unit) {
    onDispose { infoWindow.close() }
  }
}

/**
 * 吹き出しの開閉を [infoWindow] に伝える [MarkerComposable]。**地図のマーカーはこれで置く**
 * （素の MarkerComposable を使うと、そのマーカーの吹き出しだけバックで閉じられなくなる）。
 *
 * @param dismissWithBack この吹き出しをバックで閉じる対象にするか。**その画面が主題にしている
 *   場所のマーカー**（場所詳細）だけ false にする。吹き出しが画面の一部なので、バックは
 *   吹き出しではなく画面を戻す。
 */
@Composable
@GoogleMapComposable
@Suppress("ktlint:standard:annotation")
internal fun MapMarker(
  vararg keys: Any,
  infoWindow: MapInfoWindowState,
  state: MarkerState,
  title: String? = null,
  snippet: String? = null,
  dismissWithBack: Boolean = true,
  // マーカーが指す点。中身の幅・高さに対する割合で、既定は下端中央。
  anchor: Offset = Offset(0.5f, 1f),
  zIndex: Float = DEFAULT_MARKER_Z_INDEX,
  // 吹き出しを薄く染める色（ふつうはマーカーの地の色）。null なら染めない。
  bubbleColor: Color? = null,
  onClick: () -> Unit = {},
  // MarkerComposable と同じく、中身は地図ではなく通常の UI として描く（ビットマップに焼く）。
  content: @Composable @UiComposable () -> Unit,
) {
  DisposableEffect(state) {
    onDispose { infoWindow.onMarkerDisposed(state) }
  }
  val handleClick: (com.google.android.gms.maps.model.Marker) -> Boolean = {
    if (dismissWithBack) infoWindow.onMarkerClick(state)
    onClick()
    // false = 吹き出しを出す・カメラを寄せる、という地図の既定の挙動に任せる。
    false
  }
  // 名前の無い（空の）マーカーは吹き出しを出さない（地図の既定と同じ）。
  val bubbleTitle = title?.takeIf { it.isNotBlank() }
  if (bubbleTitle == null) {
    MarkerComposable(
      *keys,
      state = state,
      anchor = anchor,
      zIndex = zIndex,
      onClick = handleClick,
      onInfoWindowClose = { infoWindow.onInfoWindowClose(state) },
      content = content,
    )
  } else {
    MarkerInfoWindowComposable(
      *keys,
      state = state,
      title = bubbleTitle,
      snippet = snippet,
      anchor = anchor,
      zIndex = zIndex,
      onClick = handleClick,
      onInfoWindowClose = { infoWindow.onInfoWindowClose(state) },
      infoContent = { MapInfoBubble(title = bubbleTitle, snippet = snippet, color = bubbleColor) },
      content = content,
    )
  }
}

/**
 * 描き済みの画像（[icon]）で置く [MapMarker]。吹き出しの扱いは同じ。
 *
 * 数が多く、作り直しの多いマーカー（登録済みの場所）は、[MarkerIconCache] で見た目ごとに 1 回だけ
 * 描いた画像を渡す。中身を毎回 Compose で描くと、作り直すたびに地図が止まる。
 */
@Composable
@GoogleMapComposable
internal fun MapMarker(
  icon: BitmapDescriptor,
  infoWindow: MapInfoWindowState,
  state: MarkerState,
  title: String? = null,
  snippet: String? = null,
  // マーカーが指す点。画像の幅・高さに対する割合で、既定は下端中央。
  anchor: Offset = Offset(0.5f, 1f),
  zIndex: Float = DEFAULT_MARKER_Z_INDEX,
  // 吹き出しを薄く染める色（ふつうはマーカーの地の色）。null なら染めない。
  bubbleColor: Color? = null,
  onClick: () -> Unit = {},
) {
  DisposableEffect(state) {
    onDispose { infoWindow.onMarkerDisposed(state) }
  }
  val handleClick: (com.google.android.gms.maps.model.Marker) -> Boolean = {
    infoWindow.onMarkerClick(state)
    onClick()
    false
  }
  val bubbleTitle = title?.takeIf { it.isNotBlank() }
  if (bubbleTitle == null) {
    Marker(
      state = state,
      icon = icon,
      anchor = anchor,
      zIndex = zIndex,
      onClick = handleClick,
      onInfoWindowClose = { infoWindow.onInfoWindowClose(state) },
    )
  } else {
    MarkerInfoWindow(
      state = state,
      icon = icon,
      title = bubbleTitle,
      snippet = snippet,
      anchor = anchor,
      zIndex = zIndex,
      onClick = handleClick,
      onInfoWindowClose = { infoWindow.onInfoWindowClose(state) },
      content = { MapInfoBubble(title = bubbleTitle, snippet = snippet, color = bubbleColor) },
    )
  }
}

/**
 * 地図の吹き出しの見た目（どの地図でも同じ）。地図の標準の吹き出しは暗い表示でも白いので、テーマの色で自前で描く。
 * 地は上の見出しのピル（`MapBackTitle`）と同じ色に、マーカーの色（[color]）を薄く敷く（統計のタイル・場所の状態の
 * ボタンと同じ「色を薄く敷く」作り）。どの点の吹き出しかが色で分かる。名前（太字・2 行まで）と、2 行目（状態・時刻など）を
 * マーカーの色の字で。角丸、下の小さな三角でマーカーを指す。
 *
 * 吹き出しは地図が画像に焼いて出すので、影（elevation）は描かれない。地図の上で縁が溶けないよう、影の代わりに細い枠を付ける。
 */
@Composable
internal fun MapInfoBubble(title: String, snippet: String?, color: Color? = null) {
  val (container, border, accent) = bubbleColors(color)
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = container,
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(MapInfoBubbleBorder, border),
      modifier = Modifier.widthIn(max = MapInfoBubbleMaxWidth),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = PathlySpacing.md, vertical = PathlySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PathlySpacing.xxs),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        snippet?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    // マーカーを指す三角。枠の線は下の辺に重ねて隠す（地の色で塗る）。
    Canvas(modifier = Modifier.size(width = MapInfoBubblePointerWidth, height = MapInfoBubblePointerHeight)) {
      // 上の辺は枠の線に重ねる（枠の太さぶん上から描く）。内側は枠の太さの 1.5 倍だけ細くして縁を残す。
      val top = -MapInfoBubbleBorder.toPx()
      val inset = MapInfoBubbleBorder.toPx() * 1.5f
      val path = Path().apply {
        moveTo(0f, top)
        lineTo(size.width, top)
        lineTo(size.width / 2, size.height)
        close()
      }
      drawPath(path, color = border)
      val inner = Path().apply {
        moveTo(inset, top)
        lineTo(size.width - inset, top)
        lineTo(size.width / 2, size.height - inset)
        close()
      }
      drawPath(inner, color = container)
    }
  }
}

/** 吹き出しの地・枠・2 行目の字の色。 */
private data class BubbleColors(val container: Color, val border: Color, val accent: Color)

/**
 * [color] を薄く敷いた地と、それに合う枠・字の色。地図の色（`PathlyMapColors`）は明暗で同じ値なので、字は明るい表示では
 * 黒へ、暗い表示では白へ寄せて、薄い地の上で読める濃さにする。[color] が null なら染めない（地のまま・灰色の字）。
 */
@Composable
private fun bubbleColors(color: Color?): BubbleColors {
  val scheme = MaterialTheme.colorScheme
  val base = scheme.surfaceContainerLowest
  if (color == null) return BubbleColors(base, scheme.outlineVariant, scheme.onSurfaceVariant)
  val dark = scheme.surface.luminance() < 0.5f
  return BubbleColors(
    container = color.copy(alpha = BUBBLE_TINT_ALPHA).compositeOver(base),
    border = color.copy(alpha = BUBBLE_BORDER_ALPHA).compositeOver(base),
    // 半透明の地図の色（線の色など）でも、字は透かさない。
    accent = color.copy(alpha = 1f).let { if (dark) lerp(it, Color.White, BUBBLE_DARK_TEXT_LIGHTEN) else lerp(it, Color.Black, BUBBLE_LIGHT_TEXT_DARKEN) },
  )
}

/** 地に敷く濃さ（統計のタイルと同じ）。 */
private const val BUBBLE_TINT_ALPHA = 0.16f

/** 枠の濃さ。 */
private const val BUBBLE_BORDER_ALPHA = 0.45f

/** 2 行目の字を、暗い表示では白へ・明るい表示では黒へ寄せる割合。 */
private const val BUBBLE_DARK_TEXT_LIGHTEN = 0.5f
private const val BUBBLE_LIGHT_TEXT_DARKEN = 0.35f

// 吹き出しの寸法。吹き出しは地図が画像に焼いて出す地図専用の部品なので、マーカーと同じく値はここに置く（→ docs/designs/ui.md）。

/** 横幅の上限（長い名前は 2 行目で省略する）。 */
private val MapInfoBubbleMaxWidth = 240.dp

/** 枠の太さ（影が描かれないので、影の代わり）。 */
private val MapInfoBubbleBorder = 1.dp

/** マーカーを指す三角の幅と高さ。 */
private val MapInfoBubblePointerWidth = 16.dp
private val MapInfoBubblePointerHeight = 8.dp
