package com.pathly.presentation.stops

import com.pathly.domain.model.SameFacilityPending
import com.pathly.domain.model.Stop
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.history.SameFacilityReview
import com.pathly.presentation.history.TrackStopActions
import com.pathly.presentation.places.PlaceEditActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 経路の立ち寄りを直す（経路詳細と記録画面のシートで共有する）。名前・メモ・行きたい・滞在時間・削除と取り消し・
 * まとめる・場所を取得と、場所を取得したときの同じ施設の確認待ち（→ adr/0037）。
 *
 * 画面ごとに違うところは持ち主の ViewModel から受ける:
 * - [trackId]: 対象の経路（開いている経路・記録中の経路）。null の間は何もしない。
 * - [placeEdit]: 同じ施設の確認待ちを「まとめる」（場所の編集と同じ取り消しのスナックバーに乗せる）。
 * - [onError]: 失敗を出す先。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopEditActions internal constructor(
  private val scope: CoroutineScope,
  private val placeRepository: PlaceRepository,
  private val stopRepository: StopRepository,
  private val liveStopRepository: LiveStopRepository,
  private val placeEditUseCase: PlaceEditUseCase,
  trackId: Flow<Long?>,
  private val placeEdit: PlaceEditActions,
  private val onError: (String) -> Unit,
) {
  private val trackId: StateFlow<Long?> = trackId.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

  /** 未取得（googlePlaceId 無し）の place 件数。「場所を取得」の帯を出すのに使う。 */
  val unresolvedCount: StateFlow<Int> = this.trackId
    .flatMapLatest { id -> if (id == null) flowOf(0) else placeRepository.unresolvedCountForTrack(id) }
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

  /** この経路の、同じ施設の確認待ち（→ adr/0037）。 */
  val sameFacilityPending: StateFlow<List<SameFacilityPending>> = this.trackId
    .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else placeRepository.observeSameFacilityPending(id) }
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _sameFacilityDialogOpen = MutableStateFlow(false)

  /** 確認待ちを 1 件ずつ聞くダイアログを出しているか。 */
  val sameFacilityDialogOpen: StateFlow<Boolean> = _sameFacilityDialogOpen.asStateFlow()

  /** 立ち寄りの行・まとめて選んだときの操作（画面に渡すまとまり。作り直さないよう 1 つだけ持つ）。 */
  val actions = TrackStopActions(
    onEditPlaceName = ::updatePlaceName,
    onEditNote = ::updateStopNote,
    onToggleWishlist = ::toggleWishlist,
    onUpdateDuration = ::updateStopDuration,
    onDelete = ::deleteStops,
    onMerge = ::mergeStops,
    onUndo = ::undoStopChange,
    onResolveNames = ::resolveNames,
    onEditLiveNote = ::updateLiveStopNote,
    onUpdateLiveArrival = ::updateLiveStopArrival,
    onDismissLive = ::dismissLiveStop,
  )

  init {
    // 確認待ちを全部答え終えたら閉じる（開いたままだと、あとで裏の名前付けが見つけた分で勝手に開く）。
    scope.launch {
      sameFacilityPending.map { it.isEmpty() }.distinctUntilChanged().collect { empty ->
        if (empty) _sameFacilityDialogOpen.value = false
      }
    }
  }

  /** 画面に渡す同じ施設の確認待ちのまとまり（[pending]・[dialogOpen] は画面で購読した値を渡す）。 */
  fun sameFacilityReview(pending: List<SameFacilityPending>, dialogOpen: Boolean) = SameFacilityReview(
    pending = pending,
    dialogOpen = dialogOpen,
    onOpen = ::openSameFacilityDialog,
    onDismiss = ::dismissSameFacilityDialog,
    onMerge = ::mergeSameFacility,
  )

  /** 場所の名前を変える（その場所の全ての訪問に効く）。 */
  fun updatePlaceName(placeId: Long, name: String) {
    scope.launch { placeRepository.updatePlaceName(placeId, name) }
  }

  /** 立ち寄り（訪問）のメモを更新する（stop 単位。空文字なら消す）。 */
  fun updateStopNote(stopId: Long, note: String?) {
    scope.launch { stopRepository.updateStopNote(stopId, note) }
  }

  /**
   * 立ち寄りの行の旗から、その場所の「行きたい」をワンタップで付け外しする。
   * 付け外しの判断は DB の今の状態で行う（[PlaceEditUseCase.toggleWishlist]）。
   * 旗の見た目は登録済みの場所の一覧の更新で変わるので、成功時のメッセージは出さない（場所タブと同じ）。
   */
  fun toggleWishlist(placeId: Long) {
    scope.launch {
      try {
        placeEditUseCase.toggleWishlist(placeId)
      } catch (e: Exception) {
        onError("行きたいの更新に失敗しました: ${e.message}")
      }
    }
  }

  /**
   * 立ち寄り（訪問）の滞在期間を手で直す。GPS 点・補正後の点は触らない（→ adr/0024）。
   * 他の立ち寄りと期間が重なっても（入れ子でも）そのまま保存する。
   */
  fun updateStopDuration(stopId: Long, arrivalTime: Date, departureTime: Date) {
    scope.launch { stopRepository.updateStopDuration(stopId, arrivalTime, departureTime) }
  }

  /**
   * 立ち寄り（訪問）を削除する（1件でも複数でも同じ）。参照が無くなった場所は自動で場所ごと削除し、
   * 他の履歴で使われている／行きたい登録がある場所は「この訪問だけ」消して保持する。
   * 確認ダイアログは出さず即時削除し、画面側のスナックバーから [undoStopChange] で取り消せる。
   */
  fun deleteStops(stopIds: List<Long>) {
    if (stopIds.isEmpty()) return
    scope.launch { stopRepository.deleteStops(stopIds) }
  }

  /**
   * 選んだ立ち寄り（訪問）を1件にまとめる。同じ場所への訪問が2件以上あるときだけ成立し、
   * 混ざっていれば何もしない。確認ダイアログは出さず即時実行し、画面側のスナックバーから
   * [undoStopChange] で取り消せる（削除と同じ流儀）。
   */
  fun mergeStops(stopIds: List<Long>) {
    if (stopIds.size < 2) return
    scope.launch {
      val result = stopRepository.mergeStops(stopIds)
      if (result == null) onError("同じ場所への立ち寄りだけまとめられます")
    }
  }

  /** 直近の削除・統合を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoStopChange() {
    scope.launch { stopRepository.undoLastStopChange() }
  }

  /**
   * 未取得の場所を Places で取り直す（手動・googlePlaceId 無しが対象）。
   *
   * 同じ施設を既に他の場所が持っていると施設情報は付かない（→ adr/0025）。その場で操作したので、
   * まとめるか別のまま残すかをその場で聞く（→ adr/0037）。
   */
  fun resolveNames() {
    val id = trackId.value ?: return
    scope.launch {
      val skipped = placeRepository.resolveUnresolvedNames(id)
      if (skipped > 0) _sameFacilityDialogOpen.value = true
    }
  }

  /** 訪問のメモを直す。立ち寄り中（まだ保存していない・id が 0）なら、確定した立ち寄りに入るよう覚えておく。 */
  fun updateNote(stop: Stop, note: String?) {
    if (stop.isLive) {
      updateLiveStopNote(note)
    } else {
      updateStopNote(stop.id, note)
    }
  }

  /** 立ち寄り中のメモ（確定した立ち寄りに入る。空なら消す）。 */
  fun updateLiveStopNote(note: String?) {
    val id = trackId.value ?: return
    scope.launch { liveStopRepository.updateLiveStopNote(id, note) }
  }

  /** 立ち寄り中の到着時刻を直す（出発は離れたときに決まる）。 */
  fun updateLiveStopArrival(arrivalTime: Date) {
    val id = trackId.value ?: return
    scope.launch { liveStopRepository.updateLiveStopArrival(id, arrivalTime) }
  }

  /** 立ち寄り中をやめる（誤検知の取り消し。離れるまで同じ滞在を立ち寄りにしない）。 */
  fun dismissLiveStop() {
    val id = trackId.value ?: return
    scope.launch { liveStopRepository.dismissLiveStop(id) }
  }

  /** 同じ施設の確認待ちを聞くダイアログを開く（「確認待ち」の帯）。 */
  fun openSameFacilityDialog() {
    _sameFacilityDialogOpen.value = true
  }

  /** 「あとで」: 決めずに閉じる（確認待ちのまま残る）。 */
  fun dismissSameFacilityDialog() {
    _sameFacilityDialogOpen.value = false
  }

  /**
   * 確認待ちの場所を相手にまとめる（→ adr/0037）。スナックバーの「取り消す」で戻せる。
   * ダイアログは開いたままにし、残りがあれば次の 1 件を聞く。
   */
  fun mergeSameFacility(pending: SameFacilityPending) {
    placeEdit.mergeIntoSameFacility(pending.placeId, pending.placeName, pending.other)
  }
}

/** 立ち寄り中（まだ保存していない）の行か。保存していない立ち寄りは id が 0（→ LiveStopRepository.currentStop）。 */
internal val Stop.isLive: Boolean get() = id == 0L

/**
 * 立ち寄り中の行の ⋮ の操作（記録画面と経路詳細で共通）。保存前でもできることだけ: メモ・場所の名前・到着時刻・
 * 場所の選び直し・立ち寄り中をやめる。
 */
internal data class LiveStopActions(
  val onEditNote: (Stop) -> Unit = {},
  val onEditName: (Stop) -> Unit = {},
  val onEditArrival: (Stop) -> Unit = {},
  val onReassign: (Stop) -> Unit = {},
  val onDismiss: (Stop) -> Unit = {},
)
