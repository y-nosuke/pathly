package com.pathly.domain.repository

import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 計画（→ docs/specs/plans.md・adr/0050）。 */
interface PlanRepository {

  /** すべての計画（並べ方は画面が決める）。 */
  fun observePlans(): Flow<List<PlanSummary>>

  /** 計画と行。計画が無い（消した）なら null。 */
  fun observePlan(planId: Long): Flow<PlanDetail?>

  /** 新しい計画を作る（日付なし・日帰り）。作った計画の id を返す。 */
  suspend fun createPlan(title: String): Long

  /** 名前とメモを変える。 */
  suspend fun updateTitleAndNote(planId: Long, title: String, note: String?)

  /** 開始日（null は日付なし）と日数を変える。日数を減らしたら、消える日の行は最後の日の末尾へ移る。 */
  suspend fun setDates(planId: Long, startDate: LocalDate?, dayCount: Int)

  /** 計画を消す。直前の 1 件だけ [undoDeletePlan] で戻せる。 */
  suspend fun deletePlan(planId: Long)

  suspend fun undoDeletePlan(): Boolean

  /** 場所の行を [day] 日目の末尾に足す（[placeIds] の順で）。 */
  suspend fun addPlaces(planId: Long, day: Int, placeIds: List<Long>)

  /** メモの行を [day] 日目の末尾に足す。 */
  suspend fun addMemo(planId: Long, day: Int, title: String, plannedTime: Int?, note: String?)

  /** 行の予定時刻・滞在の目安・メモ（メモの行は文言も）を変える。 */
  suspend fun updateItem(itemId: Long, title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?)

  /** 行を [day] 日目の末尾へ移す。 */
  suspend fun moveItemToDay(itemId: Long, day: Int)

  /** 並べ替えた結果（上から順の行の id と、その行が入った日）を書く。 */
  suspend fun reorder(planId: Long, order: List<Pair<Long, Int>>)

  /** 行を計画から外す。直前の 1 件だけ [undoRemoveItem] で戻せる。 */
  suspend fun removeItem(itemId: Long)

  suspend fun undoRemoveItem(): Boolean
}
