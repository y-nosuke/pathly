package com.pathly.presentation.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLng
import com.pathly.domain.model.SmoothingParams
import com.pathly.domain.model.Stop
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.ManualStopTarget

/**
 * 経路詳細の画面の中だけの状態（いまどのモードか・何を編集中か・地図をどこへ寄せるか）。
 * 地図・シート・ダイアログのそれぞれから読み書きするので、1 つにまとめて渡す。
 */
@Stable
internal class TrackDetailScreenState {
  /** 通常モードの地図タップで開く統一の「場所シート」（未登録の空き地点/POI＝登録、登録済み＝その場で編集）。 */
  var placeSheetTarget by mutableStateOf<PlaceSheetTarget?>(null)

  // 補正の調整（デバッグビルドのみ）。
  var tuningMode by mutableStateOf(false)
  var tuningParams by mutableStateOf(SmoothingParams())

  /** デバッグビルドのみ: 生 GPS 点の付随情報（provider/各種精度/MSL/extras 等）を確認するダイアログ。 */
  var debugInfoOpen by mutableStateOf(false)

  /** 場所の名前を編集中の立ち寄り。 */
  var editingStop by mutableStateOf<Stop?>(null)

  /** メモ編集中の立ち寄り（stop 単位。名前編集 [editingStop] とは別ダイアログ）。 */
  var editingNoteStop by mutableStateOf<Stop?>(null)

  /** 「場所を選び直す」対象の立ち寄り（誤検知の訂正・この訪問だけ付け替え）。 */
  var reassignTarget by mutableStateOf<Stop?>(null)

  /** 経路の名前を編集中か（見出しタップ／チップから開く）。 */
  var renamingTrack by mutableStateOf(false)

  private var durationEditStopState by mutableStateOf<Stop?>(null)

  /** 滞在期間を編集中の立ち寄り。地図を見ながら調整するので、ダイアログではなくシートで出す。 */
  var durationEditStop: Stop?
    get() = durationEditStopState
    set(value) {
      // 対象が変わったら、前の対象で選んでいた区間は捨てる。
      if (value != durationEditStopState) durationRange = null
      durationEditStopState = value
    }

  /** 編集中の滞在区間（到着〜出発の点インデックス）。手動追加と同じく地図に青くハイライトする。 */
  var durationRange by mutableStateOf<Pair<Int, Int>?>(null)

  val durationEditMode: Boolean get() = durationEditStop != null

  /** 地図↔一覧の連動用: 選択中の立ち寄り。地図のピン/一覧の行どちらから選んでも相互に強調・スクロールする。 */
  var highlightedStopId by mutableStateOf<Long?>(null)

  /** 複数選択。長押しで選択モードに入り、チェックで選ぶ。 */
  var selectionMode by mutableStateOf(false)
  val selectedStopIds = mutableStateListOf<Long>()

  // 地図を寄せる先（nonce で同じ場所の再タップにも反応）。
  var focusTarget by mutableStateOf<LatLng?>(null)
    private set
  var focusNonce by mutableIntStateOf(0)
    private set

  /** 「経路の全体を見る」の合図（押すたびに増やし、地図が経路全体に合わせ直す）。 */
  var fitNonce by mutableIntStateOf(0)
    private set

  /**
   * 手動追加モード。地図の通常タップ（POI はそのままタップ）で地点を指し、最寄り軌跡点から
   * 到着/出発を仮置きしてレンジで微調整する。
   */
  var manualMode by mutableStateOf(false)

  private var manualPickState by mutableStateOf<ManualStopTarget?>(null)

  /** 手動追加で指した地点。null の間は地点を選んでいる。 */
  var manualPick: ManualStopTarget?
    get() = manualPickState
    set(value) {
      // 地点が変わったら、前の地点で選んでいた区間は捨てる。
      if (value != manualPickState) manualRange = null
      manualPickState = value
    }

  /** 手動追加で選んでいる滞在区間（到着〜出発の点インデックス）。地図に青くハイライトする。 */
  var manualRange by mutableStateOf<Pair<Int, Int>?>(null)

  fun focusOn(latLng: LatLng) {
    focusTarget = latLng
    focusNonce++
  }

  /**
   * 立ち寄りへ地図を寄せ、一覧側の強調も合わせる。行タップだけでなく**編集を始めるとき**にも使う。
   * 直す対象が画面の外にあると、地図のハイライトを見ながら期間を調整できないため。
   */
  fun focusOnStop(stop: Stop) {
    highlightedStopId = stop.id
    focusOn(LatLng(stop.place.latitude, stop.place.longitude))
  }

  fun fitTrack() {
    fitNonce++
  }

  /** 手動追加モードで地点を指す（地図もそこへ寄せる）。 */
  fun pickManual(target: ManualStopTarget) {
    manualPick = target
    focusOn(LatLng(target.latitude, target.longitude))
  }

  fun startManual() {
    manualPick = null
    manualMode = true
  }

  fun exitManual() {
    manualMode = false
    manualPick = null
  }

  fun clearSelection() {
    selectionMode = false
    selectedStopIds.clear()
  }
}

@Composable
internal fun rememberTrackDetailScreenState(): TrackDetailScreenState = remember { TrackDetailScreenState() }
