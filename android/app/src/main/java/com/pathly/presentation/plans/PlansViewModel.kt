package com.pathly.presentation.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.PlanGroup
import com.pathly.domain.model.PlanSummary
import com.pathly.domain.repository.PlanRepository
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 一覧の区分（[group]）と、その中の計画（並べ方は区分ごと）。 */
data class PlanSection(val group: PlanGroup, val plans: List<PlanSummary>, val expanded: Boolean)

data class PlansUiState(
  val isLoading: Boolean = true,
  val sections: List<PlanSection> = emptyList(),
  val query: String = "",
  /** 計画が 1 つも無い（検索で 0 件とは分ける）。 */
  val isEmpty: Boolean = false,
)

/** 計画の一覧（→ docs/specs/plans.md の「計画の一覧」）。 */
@HiltViewModel
class PlansViewModel @Inject constructor(
  private val planRepository: PlanRepository,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val logger = Logger("PlansViewModel")

  // 開け閉めはアプリを使っている間だけ覚える（最初は「終わった」だけ閉じる）。
  private val collapsed = savedStateHandle.getStateFlow<List<String>>(KEY_COLLAPSED, arrayListOf(PlanGroup.PAST.name))
  private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

  private val _created = MutableSharedFlow<Long>(extraBufferCapacity = 1)

  /** 新しく作った計画の id（詳細を開く合図）。 */
  val created: SharedFlow<Long> = _created.asSharedFlow()

  val uiState: StateFlow<PlansUiState> = combine(planRepository.observePlans(), query, collapsed) { plans, q, closed ->
    val today = LocalDate.now()
    val matched = if (q.isBlank()) plans else plans.filter { it.plan.title.contains(q.trim(), ignoreCase = true) }
    val sections = PlanGroup.entries.mapNotNull { group ->
      val inGroup = matched.filter { it.plan.groupOn(today) == group }
      if (inGroup.isEmpty()) return@mapNotNull null
      // 日付なしは新しく直した順、これからは近い順、終わったは新しい順。
      val sorted = when (group) {
        PlanGroup.UNDATED -> inGroup.sortedByDescending { it.plan.updatedAt }
        PlanGroup.UPCOMING -> inGroup.sortedWith(compareBy({ it.plan.startDate }, { it.plan.id }))
        PlanGroup.PAST -> inGroup.sortedWith(compareByDescending<PlanSummary> { it.plan.startDate }.thenByDescending { it.plan.id })
      }
      // 検索で絞り込んでいる間は、当たった計画が見えるようにすべて開く。
      PlanSection(group, sorted, expanded = q.isNotBlank() || group.name !in closed)
    }
    PlansUiState(isLoading = false, sections = sections, query = q, isEmpty = plans.isEmpty())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

  fun setQuery(value: String) {
    savedStateHandle[KEY_QUERY] = value
  }

  fun toggleGroup(group: PlanGroup) {
    val now = collapsed.value
    // Bundle に入る形（ArrayList）で持つ。
    savedStateHandle[KEY_COLLAPSED] = ArrayList(if (group.name in now) now - group.name else now + group.name)
  }

  /** 「新しい計画」を作って、詳細を開く（[created]）。 */
  fun createPlan() {
    viewModelScope.launch {
      runCatching { planRepository.createPlan(NEW_PLAN_TITLE) }
        .onSuccess { _created.tryEmit(it) }
        .onFailure { logger.e("Failed to create plan", it) }
    }
  }

  /** 詳細で消した計画を戻す（一覧のスナックバーの「取り消す」）。 */
  fun undoDelete() {
    viewModelScope.launch {
      runCatching { planRepository.undoDeletePlan() }.onFailure { logger.e("Failed to undo plan deletion", it) }
    }
  }

  private companion object {
    const val KEY_COLLAPSED = "collapsedGroups"
    const val KEY_QUERY = "query"
  }
}
