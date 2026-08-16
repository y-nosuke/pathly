package com.pathly.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState

// マーカーの吹き出し（InfoWindow）は Compose ではなく地図（GoogleMap）が出しているので、
// 放っておくとシステムバックの対象にならない。一度出すと地図の別の場所をタップするまで消えず、
// バックで画面を戻ってもそのままに見える。ここで開閉を捕まえて、バックで閉じられるようにする。

/**
 * 地図の吹き出しの開閉状態。**GoogleMap 1 つにつき 1 つ**持つ。
 *
 * 覚えるのは 1 件でよい。GoogleMap は吹き出しを地図全体で同時に 1 つしか出さないので、
 * 「閉じた」通知が来た時点で開いているものは無い、と扱える。
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
  fun onInfoWindowClose() {
    openMarker = null
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
  onClick: () -> Unit = {},
  // MarkerComposable と同じく、中身は地図ではなく通常の UI として描く（ビットマップに焼く）。
  content: @Composable @UiComposable () -> Unit,
) {
  DisposableEffect(state) {
    onDispose { infoWindow.onMarkerDisposed(state) }
  }
  MarkerComposable(
    *keys,
    state = state,
    title = title,
    snippet = snippet,
    onClick = {
      infoWindow.onMarkerClick(state)
      onClick()
      // false = 吹き出しを出す・カメラを寄せる、という地図の既定の挙動に任せる。
      false
    },
    onInfoWindowClose = { infoWindow.onInfoWindowClose() },
    content = content,
  )
}
