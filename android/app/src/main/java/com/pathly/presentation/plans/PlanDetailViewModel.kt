package com.pathly.presentation.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.repository.PlanRepository
import com.pathly.domain.repository.WishlistRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 計画の詳細の読み込みの状態。 */
sealed interface PlanDetailState {
  data object Loading : PlanDetailState

  /** 計画が無い（消した）。 */
  data object Missing : PlanDetailState

  data class Loaded(val detail: PlanDetail) : PlanDetailState
}

/** 計画の詳細から出す一言（スナックバー）。 */
sealed interface PlanDetailEvent {
  /** 行を外した（取り消しで戻せる）。 */
  data class ItemRemoved(val label: String) : PlanDetailEvent

  data class Failed(val message: String) : PlanDetailEvent

  /** 計画を消した（一覧に戻って取り消しを出す）。 */
  data class Deleted(val title: String) : PlanDetailEvent
}

/** 計画の詳細（→ docs/specs/plans.md の「計画の詳細」）。 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
  private val planRepository: PlanRepository,
  wishlistRepository: WishlistRepository,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val logger = Logger("PlanDetailViewModel")
  private val planId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_PLAN_ID))

  // この画面で消している途中。消して計画が無くなっても「無い」（画面を戻す）にはせず、
  // 消した合図（[PlanDetailEvent.Deleted]）だけで戻る（二重に戻らない・取り消しの名前を渡し損ねない）。
  private val deleting = MutableStateFlow(false)

  val state: StateFlow<PlanDetailState> = combine(planRepository.observePlan(planId), deleting) { detail, isDeleting ->
    when {
      detail != null -> PlanDetailState.Loaded(detail)
      isDeleting -> PlanDetailState.Loading
      else -> PlanDetailState.Missing
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDetailState.Loading)

  private val _reorderFailures = MutableStateFlow(0)

  /** 並べ替えを保存できなかった回数。増えたらシートの並びを計画のとおりに戻す。 */
  val reorderFailures: StateFlow<Int> = _reorderFailures.asStateFlow()

  /** 登録済みの場所（「場所を足す」で選ぶ）。 */
  val places: StateFlow<List<PlaceListItem>> = wishlistRepository.getPlaces()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** 選んでいる日（null は「すべて」）。 */
  val selectedDay: StateFlow<Int?> = savedStateHandle.getStateFlow<Int?>(KEY_SELECTED_DAY, null)

  private val _events = MutableSharedFlow<PlanDetailEvent>(extraBufferCapacity = 4)
  val events: SharedFlow<PlanDetailEvent> = _events.asSharedFlow()

  fun selectDay(day: Int?) {
    savedStateHandle[KEY_SELECTED_DAY] = day
  }

  fun updateTitleAndNote(title: String, note: String?) = launch("保存できませんでした") {
    planRepository.updateTitleAndNote(planId, title.trim().ifEmpty { NEW_PLAN_TITLE }, note?.trim())
  }

  fun setDates(startDate: LocalDate?, dayCount: Int) = launch("保存できませんでした") {
    planRepository.setDates(planId, startDate, dayCount)
    // 選んでいた日が無くなったら「すべて」に戻す。
    val day = selectedDay.value
    if (day != null && day > dayCount) selectDay(null)
  }

  fun deletePlan(title: String) {
    deleting.value = true
    viewModelScope.launch {
      runCatching { planRepository.deletePlan(planId) }
        .onSuccess { _events.tryEmit(PlanDetailEvent.Deleted(title)) }
        .onFailure {
          if (it is CancellationException) throw it
          logger.e("Failed to delete plan", it)
          deleting.value = false
          _events.tryEmit(PlanDetailEvent.Failed("削除できませんでした"))
        }
    }
  }

  fun addPlaces(day: Int, placeIds: List<Long>) = launch("場所を足せませんでした") {
    planRepository.addPlaces(planId, day, placeIds)
  }

  fun addMemo(day: Int, title: String, plannedTime: Int?, note: String?) = launch("メモの行を足せませんでした") {
    planRepository.addMemo(planId, day, title.trim(), plannedTime, note?.trim())
  }

  fun updateItem(itemId: Long, title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?) = launch("保存できませんでした") {
    planRepository.updateItem(itemId, title?.trim()?.ifEmpty { null }, plannedTime, stayMinutes, note?.trim())
  }

  fun moveItemToDay(itemId: Long, day: Int) = launch("移せませんでした") {
    planRepository.moveItemToDay(itemId, day)
  }

  /** 並べ替えた結果（上から順の行の id と、その行が入った日）。 */
  fun reorder(order: List<Pair<Long, Int>>) {
    viewModelScope.launch {
      runCatching { planRepository.reorder(planId, order) }.onFailure {
        if (it is CancellationException) throw it
        logger.e("Failed to reorder plan items", it)
        _reorderFailures.value += 1
        _events.tryEmit(PlanDetailEvent.Failed("並べ替えられませんでした"))
      }
    }
  }

  fun removeItem(itemId: Long, label: String) = launch("外せませんでした") {
    planRepository.removeItem(itemId)
    _events.tryEmit(PlanDetailEvent.ItemRemoved(label))
  }

  fun undoRemoveItem() = launch("戻せませんでした") {
    if (!planRepository.undoRemoveItem()) _events.tryEmit(PlanDetailEvent.Failed("戻せませんでした"))
  }

  private fun launch(failure: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      runCatching { block() }.onFailure {
        if (it is CancellationException) throw it
        logger.e("Plan operation failed: $failure", it)
        _events.tryEmit(PlanDetailEvent.Failed(failure))
      }
    }
  }

  companion object {
    const val ARG_PLAN_ID = "planId"
    private const val KEY_SELECTED_DAY = "selectedDay"
  }
}
