package com.pathly.presentation.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.RemovedPlanItem
import com.pathly.domain.model.newPlanDetail
import com.pathly.domain.model.sameContentAs
import com.pathly.domain.model.withDates
import com.pathly.domain.model.withDayRemoved
import com.pathly.domain.model.withItemMovedToDay
import com.pathly.domain.model.withItemRemoved
import com.pathly.domain.model.withItemRestored
import com.pathly.domain.model.withItemUpdated
import com.pathly.domain.model.withMemoAdded
import com.pathly.domain.model.withOrder
import com.pathly.domain.model.withPlacesAdded
import com.pathly.domain.model.withTitleAndNote
import com.pathly.domain.repository.PlanRepository
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

/**
 * 計画の詳細で編集している中身（→ docs/specs/plans.md の「保存と下書き」）。
 *
 * 編集は [detail]（画面の中の下書き）にだけ入り、「保存」で計画に、「下書きに残す」で下書きに書く。
 */
data class PlanEditState(
  /** 今の中身。 */
  val detail: PlanDetail,
  /** 保存済みの中身（一度も保存していなければ null）。 */
  val saved: PlanDetail?,
  /** 最後に書いた中身（保存か下書き）。戻るときに聞くかの基準。 */
  val persisted: PlanDetail,
  /** 下書きの id と保存した日時（下書きが無ければ null）。 */
  val draftId: Long? = null,
  val draftUpdatedAt: Date? = null,
) {
  val isNew: Boolean get() = saved == null

  /** 保存できる（保存済みと違う。新しい計画は中身があれば）。 */
  val canSave: Boolean get() = saved?.let { !detail.sameContentAs(it) } ?: (draftId != null || !detail.sameContentAs(persisted))

  /** 戻るときに聞く（最後に書いた中身から変えた）。 */
  val hasUnsavedChanges: Boolean get() = !detail.sameContentAs(persisted)
}

/** 計画の詳細の読み込みの状態。 */
sealed interface PlanDetailState {
  data object Loading : PlanDetailState

  /** 計画も下書きも無い（消した）。 */
  data object Missing : PlanDetailState

  data class Loaded(val edit: PlanEditState) : PlanDetailState
}

/** 計画の詳細から出す一言（スナックバー）や、画面を離れる合図。 */
sealed interface PlanDetailEvent {
  /** 行を外した（取り消しで戻せる）。 */
  data class ItemRemoved(val label: String) : PlanDetailEvent

  data class Failed(val message: String) : PlanDetailEvent

  data class Message(val message: String) : PlanDetailEvent

  /** 計画を消した（一覧に戻って取り消しを出す）。 */
  data class Deleted(val title: String) : PlanDetailEvent

  /** 保存・下書き・破棄を済ませて、画面を離れる。 */
  data object Leave : PlanDetailEvent
}

/** 計画の詳細（→ docs/specs/plans.md の「計画の詳細」）。 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
  private val planRepository: PlanRepository,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val logger = Logger("PlanDetailViewModel")
  private val planIdArg: Long get() = savedStateHandle.get<Long>(ARG_PLAN_ID) ?: 0L
  private val draftIdArg: Long? get() = savedStateHandle.get<Long>(ARG_DRAFT_ID)?.takeIf { it > 0 }

  private val _state = MutableStateFlow<PlanDetailState>(PlanDetailState.Loading)
  val state: StateFlow<PlanDetailState> = _state.asStateFlow()

  /** 場所の名前とアンカーの座標（足した場所を名前・地図に出すのに使う）。 */
  val places: StateFlow<Map<Long, PlanPlace>> = planRepository.observePlanPlaces()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  /** 選んでいる日（null は「すべて」）。 */
  val selectedDay: StateFlow<Int?> = savedStateHandle.getStateFlow<Int?>(KEY_SELECTED_DAY, null)

  private val _events = MutableSharedFlow<PlanDetailEvent>(extraBufferCapacity = 4)
  val events: SharedFlow<PlanDetailEvent> = _events.asSharedFlow()

  // 足した行に振る仮の id（保存すると本当の id になる）。
  private var nextTempId = -1_000_000L
  private var lastRemoved: RemovedPlanItem? = null

  init {
    load()
  }

  private fun load() {
    viewModelScope.launch {
      val saved = if (planIdArg != 0L) planRepository.loadPlan(planIdArg) else null
      val draft = if (planIdArg != 0L || draftIdArg != null) planRepository.loadDraft(planIdArg, draftIdArg) else null
      _state.value = when {
        draft != null -> PlanDetailState.Loaded(PlanEditState(draft.detail, saved, draft.detail, draft.draftId, draft.updatedAt))
        saved != null -> PlanDetailState.Loaded(PlanEditState(saved, saved, saved))
        planIdArg == 0L && draftIdArg == null -> newPlanDetail(NEW_PLAN_TITLE).let { PlanDetailState.Loaded(PlanEditState(it, null, it)) }
        else -> PlanDetailState.Missing
      }
    }
  }

  private val edit: PlanEditState? get() = (_state.value as? PlanDetailState.Loaded)?.edit

  private fun change(transform: (PlanDetail) -> PlanDetail) {
    _state.update { s -> if (s is PlanDetailState.Loaded) s.copy(edit = s.edit.copy(detail = transform(s.edit.detail))) else s }
  }

  fun selectDay(day: Int?) {
    savedStateHandle[KEY_SELECTED_DAY] = day
  }

  fun updateTitleAndNote(title: String, note: String?) = change { it.withTitleAndNote(title.trim().ifEmpty { NEW_PLAN_TITLE }, note?.trim()) }

  fun setDates(startDate: LocalDate?, dayCount: Int) {
    change { it.withDates(startDate, dayCount) }
    // 選んでいた日が無くなったら「すべて」に戻す。
    val day = selectedDay.value
    if (day != null && day > dayCount) selectDay(null)
  }

  fun addPlaces(day: Int, placeIds: List<Long>) {
    val known = places.value
    val added = placeIds.mapNotNull { known[it] }
    change { it.withPlacesAdded(day, added) { nextTempId-- } }
  }

  fun addMemo(day: Int, title: String, plannedTime: Int?, note: String?) = change { it.withMemoAdded(day, title.trim(), plannedTime, note?.trim(), nextTempId--) }

  fun updateItem(itemId: Long, title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?) = change { it.withItemUpdated(itemId, title?.trim(), plannedTime, stayMinutes, note?.trim()) }

  fun moveItemToDay(itemId: Long, day: Int) = change { it.withItemMovedToDay(itemId, day) }

  /** 並べ替えた結果（上から順の行の id と、その行が入った日）。 */
  fun reorder(order: List<Pair<Long, Int>>) = change { it.withOrder(order) }

  fun removeItem(itemId: Long) {
    val current = edit?.detail ?: return
    val (next, removed) = current.withItemRemoved(itemId)
    if (removed == null) return
    lastRemoved = removed
    change { next }
    _events.tryEmit(PlanDetailEvent.ItemRemoved(removed.item.label))
  }

  fun undoRemoveItem() {
    val removed = lastRemoved ?: return
    lastRemoved = null
    change { it.withItemRestored(removed) }
  }

  /** [day] 日目を消す（行は前の日の末尾へ）。 */
  fun removeDay(day: Int) {
    change { it.withDayRemoved(day) }
    val selected = selectedDay.value
    if (selected != null && selected >= day) selectDay(null)
  }

  /** 保存する。[leave] なら済んだら画面を離れる。 */
  fun save(leave: Boolean = false) {
    val e = edit ?: return
    io("保存できませんでした") {
      val planId = planRepository.savePlan(e.detail, e.draftId)
      // 新しい計画を保存したら、この画面はその計画を指す（アプリが裏で落とされて戻ったときに、保存した計画を開くように）。
      savedStateHandle[ARG_PLAN_ID] = planId
      savedStateHandle[ARG_DRAFT_ID] = 0L
      val saved = planRepository.loadPlan(planId) ?: return@io
      _state.value = PlanDetailState.Loaded(PlanEditState(saved, saved, saved))
      if (leave) _events.tryEmit(PlanDetailEvent.Leave) else _events.tryEmit(PlanDetailEvent.Message("保存しました"))
    }
  }

  /** 下書きに残して、画面を離れる。 */
  fun saveDraftAndLeave() {
    val e = edit ?: return
    io("下書きに残せませんでした") {
      val draftId = planRepository.saveDraft(e.draftId, e.detail)
      savedStateHandle[ARG_DRAFT_ID] = draftId
      _state.value = PlanDetailState.Loaded(e.copy(persisted = e.detail, draftId = draftId, draftUpdatedAt = Date()))
      _events.tryEmit(PlanDetailEvent.Leave)
    }
  }

  /** 下書きを捨てて、保存済みの中身に戻す（一度も保存していなければ画面を離れる）。 */
  fun discardDraft() {
    val e = edit ?: return
    io("下書きを捨てられませんでした") {
      e.draftId?.let { planRepository.deleteDraft(it) }
      val saved = e.saved
      if (saved == null) {
        _events.tryEmit(PlanDetailEvent.Leave)
      } else {
        _state.value = PlanDetailState.Loaded(PlanEditState(saved, saved, saved))
        _events.tryEmit(PlanDetailEvent.Message("下書きを捨てました"))
      }
    }
  }

  /** 計画を消す（保存済みのとき）。 */
  fun deletePlan() {
    val e = edit ?: return
    val saved = e.saved ?: return
    io("削除できませんでした") {
      planRepository.deletePlan(saved.plan.id)
      _events.tryEmit(PlanDetailEvent.Deleted(e.detail.plan.title))
    }
  }

  private fun io(failure: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.e("Plan operation failed: $failure", e)
        _events.tryEmit(PlanDetailEvent.Failed(failure))
      }
    }
  }

  companion object {
    const val ARG_PLAN_ID = "planId"
    const val ARG_DRAFT_ID = "draftId"
    private const val KEY_SELECTED_DAY = "selectedDay"
  }
}
