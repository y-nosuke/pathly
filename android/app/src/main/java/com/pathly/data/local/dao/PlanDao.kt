package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pathly.data.local.entity.PlanDraftEntity
import com.pathly.data.local.entity.PlanEntity
import com.pathly.data.local.entity.PlanItemEntity
import com.pathly.data.local.entity.PlanItemWithPlace
import com.pathly.data.local.entity.PlanListRow
import com.pathly.data.local.entity.PlanPlaceRow
import kotlinx.coroutines.flow.Flow

/** 計画・計画の行・計画の下書き（→ adr/0050）。 */
@Dao
abstract class PlanDao {

  @Query(
    "SELECT p.*, (SELECT COUNT(*) FROM plan_items i WHERE i.planId = p.id AND i.placeId IS NOT NULL) AS placeCount " +
      "FROM plans p",
  )
  abstract fun observePlans(): Flow<List<PlanListRow>>

  @Query("SELECT * FROM plan_drafts")
  abstract fun observeDrafts(): Flow<List<PlanDraftEntity>>

  @Query("SELECT * FROM plans WHERE id = :planId")
  abstract suspend fun getPlan(planId: Long): PlanEntity?

  /** 計画の行（日・順番どおり）。場所の行には名前とアンカーの座標を添える。 */
  @Query(
    "SELECT i.*, p.name AS placeName, g.name AS googleName, g.address AS googleAddress, " +
      "p.latitude AS placeLatitude, p.longitude AS placeLongitude " +
      "FROM plan_items i " +
      "LEFT JOIN places p ON p.id = i.placeId " +
      "LEFT JOIN google_places g ON g.placeId = i.placeId " +
      "WHERE i.planId = :planId ORDER BY i.dayIndex, i.position, i.id",
  )
  abstract suspend fun getItemsWithPlace(planId: Long): List<PlanItemWithPlace>

  @Query("SELECT * FROM plan_items WHERE planId = :planId ORDER BY dayIndex, position, id")
  abstract suspend fun getItems(planId: Long): List<PlanItemEntity>

  @Query("SELECT * FROM plan_items WHERE placeId = :placeId")
  abstract suspend fun getItemsByPlace(placeId: Long): List<PlanItemEntity>

  /** 場所の名前とアンカーの座標（下書きの行を場所に結び付けるため）。 */
  @Query(
    "SELECT p.id AS id, p.name AS placeName, g.name AS googleName, g.address AS googleAddress, " +
      "p.latitude AS latitude, p.longitude AS longitude " +
      "FROM places p LEFT JOIN google_places g ON g.placeId = p.id",
  )
  abstract fun observePlaces(): Flow<List<PlanPlaceRow>>

  /** 場所を使っている計画の数（同じ計画に 2 回入っていても 1 と数える）。 */
  @Query("SELECT COUNT(DISTINCT planId) FROM plan_items WHERE placeId = :placeId")
  abstract suspend fun countPlansUsingPlace(placeId: Long): Int

  @Insert
  abstract suspend fun insertPlan(plan: PlanEntity): Long

  @Update
  abstract suspend fun updatePlan(plan: PlanEntity)

  @Query("DELETE FROM plans WHERE id = :planId")
  abstract suspend fun deletePlan(planId: Long)

  @Insert
  abstract suspend fun insertItem(item: PlanItemEntity): Long

  @Insert
  abstract suspend fun insertItems(items: List<PlanItemEntity>)

  @Query("DELETE FROM plan_items WHERE planId = :planId")
  protected abstract suspend fun deleteItemsOf(planId: Long)

  // ---- 下書き ----

  @Query("SELECT * FROM plan_drafts WHERE id = :draftId")
  abstract suspend fun getDraft(draftId: Long): PlanDraftEntity?

  @Query("SELECT * FROM plan_drafts WHERE planId = :planId")
  abstract suspend fun getDraftOfPlan(planId: Long): PlanDraftEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsertDraft(draft: PlanDraftEntity): Long

  @Query("DELETE FROM plan_drafts WHERE id = :draftId")
  abstract suspend fun deleteDraft(draftId: Long)

  /**
   * 計画を保存する（新しければ作る）。行は丸ごと入れ替える。[draftId] の下書きは消す。保存した計画の id を返す。
   */
  @Transaction
  open suspend fun savePlan(plan: PlanEntity, items: List<PlanItemEntity>, draftId: Long?): Long {
    val planId = if (plan.id == 0L) insertPlan(plan) else plan.id.also { updatePlan(plan) }
    deleteItemsOf(planId)
    if (items.isNotEmpty()) insertItems(items.map { it.copy(id = 0, planId = planId) })
    if (draftId != null) deleteDraft(draftId)
    return planId
  }

  /** 消した計画を、元の id のまま行・下書きごと戻す。 */
  @Transaction
  open suspend fun restore(plan: PlanEntity, items: List<PlanItemEntity>, draft: PlanDraftEntity?) {
    insertPlan(plan)
    if (items.isNotEmpty()) insertItems(items)
    if (draft != null) upsertDraft(draft)
  }
}
