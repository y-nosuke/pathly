package com.pathly.domain.repository

import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanSummary
import com.pathly.domain.model.StoredPlanDraft
import kotlinx.coroutines.flow.Flow

/**
 * 計画（→ docs/specs/plans.md・adr/0050）。編集は画面の中の下書き（[PlanDetail]）で行い、
 * 「保存」で [savePlan]、「下書きに残す」で [saveDraft] する。
 */
interface PlanRepository {

  /** すべての計画と、一度も保存していない計画の下書き（並べ方は画面が決める）。 */
  fun observePlans(): Flow<List<PlanSummary>>

  /** 保存済みの計画。無ければ null。 */
  suspend fun loadPlan(planId: Long): PlanDetail?

  /** 下書き（[draftId] があればそれ、無ければ [planId] の計画の下書き）。無い・読めなければ null。 */
  suspend fun loadDraft(planId: Long, draftId: Long?): StoredPlanDraft?

  /** 下書きに残す（[draftId] があれば上書き）。下書きの id を返す。 */
  suspend fun saveDraft(draftId: Long?, detail: PlanDetail): Long

  suspend fun deleteDraft(draftId: Long)

  /** 計画を保存する（新しければ作る）。[draftId] の下書きは消す。保存した計画の id を返す。 */
  suspend fun savePlan(detail: PlanDetail, draftId: Long?): Long

  /** 計画を消す（下書きも）。直前の 1 件だけ [undoDeletePlan] で戻せる。 */
  suspend fun deletePlan(planId: Long)

  suspend fun undoDeletePlan(): Boolean

  /** 場所の名前とアンカーの座標（id → 場所）。場所の行を名前・地図に出すのに使う。 */
  fun observePlanPlaces(): Flow<Map<Long, PlanPlace>>
}
