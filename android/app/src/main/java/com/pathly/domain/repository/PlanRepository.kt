package com.pathly.domain.repository

import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanSummary
import com.pathly.domain.model.PlanTrackLink
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.model.StoredPlanDraft
import com.pathly.domain.model.TrackStart
import com.pathly.domain.model.TrackVisit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

  /**
   * 下書きに残す（[draftId] があれば上書き）。下書きの id を返す。
   * [unlinkTrackIds] は日付の変更で「結び付きを外す」を選んだ経路（保存したときに外すので、下書きにも持つ）。
   */
  suspend fun saveDraft(draftId: Long?, detail: PlanDetail, unlinkTrackIds: Set<Long> = emptySet()): Long

  suspend fun deleteDraft(draftId: Long)

  /** 計画を保存する（新しければ作る）。[draftId] の下書きは消す。保存した計画の id を返す。 */
  suspend fun savePlan(detail: PlanDetail, draftId: Long?): Long

  /** 計画を消す（下書きも）。直前の 1 件だけ [undoDeletePlan] で戻せる。 */
  suspend fun deletePlan(planId: Long)

  suspend fun undoDeletePlan(): Boolean

  /** 場所の名前とアンカーの座標（id → 場所）。場所の行を名前・地図に出すのに使う。 */
  fun observePlanPlaces(): Flow<Map<Long, PlanPlace>>

  // ---- 計画と実績（→ docs/specs/plans.md の「計画と実績」） ----

  /** すべての経路の始まり（日付で自動に結び付く経路を求めるため）。 */
  fun observeTrackStarts(): Flow<List<TrackStart>>

  /** 経路で立ち寄った場所の組（「行った」を求めるため）。 */
  fun observeTrackVisits(): Flow<List<TrackVisit>>

  /** [planId] の計画に結び付いた経路。 */
  fun observePlanTracks(planId: Long): Flow<List<PlanTrackLink>>

  /** 経路が結び付いている計画の名前（経路の id → 計画の id と名前）。 */
  fun observeTrackPlans(): Flow<Map<Long, Pair<Long, String>>>

  /** 日付のある保存済みの計画（日付の重なりを見るため）。 */
  fun observeDatedPlans(): Flow<List<Plan>>

  /** [date] が日付に入る保存済みの計画（記録の開始で聞くため）。 */
  suspend fun plansOn(date: LocalDate): List<Plan>

  /** 経路を結び付ける（保存を待たず、すぐ入る。ほかの計画に結び付いていれば付け替える）。 */
  suspend fun linkTrack(planId: Long, trackId: Long, source: PlanTrackSource)

  /** 結び付きを外す。 */
  suspend fun unlinkTrack(planId: Long, trackId: Long)
}
