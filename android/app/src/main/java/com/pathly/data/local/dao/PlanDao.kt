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
import com.pathly.data.local.entity.PlanItemPlaceRow
import com.pathly.data.local.entity.PlanItemWithPlace
import com.pathly.data.local.entity.PlanListRow
import com.pathly.data.local.entity.PlanPlaceRow
import com.pathly.data.local.entity.PlanTrackEntity
import com.pathly.data.local.entity.TrackPlanRow
import com.pathly.data.local.entity.TrackStartRow
import com.pathly.data.local.entity.TrackVisitRow
import kotlinx.coroutines.flow.Flow

/** 計画・計画の行・計画の下書き・手で結び付けた経路（→ adr/0050）。 */
@Dao
abstract class PlanDao {

  // ---- 計画と実績（結び付いた経路・行った） ----

  /** 経路の始まり（gps_tracks だけを見るので、記録中に点が増えても流れ直さない）。 */
  @Query("SELECT id, startTime, isActive FROM gps_tracks")
  abstract fun observeTrackStarts(): Flow<List<TrackStartRow>>

  /** 経路で立ち寄った場所の組（同じ組は 1 つ）。 */
  @Query("SELECT DISTINCT trackId, placeId FROM stops")
  abstract fun observeTrackVisits(): Flow<List<TrackVisitRow>>

  @Query("SELECT * FROM plan_tracks")
  abstract fun observeAllPlanTracks(): Flow<List<PlanTrackEntity>>

  @Query("SELECT * FROM plan_tracks WHERE planId = :planId")
  abstract fun observePlanTracks(planId: Long): Flow<List<PlanTrackEntity>>

  @Query("SELECT * FROM plan_tracks WHERE planId = :planId")
  abstract suspend fun getPlanTracks(planId: Long): List<PlanTrackEntity>

  /** 経路が結び付いている計画とその名前。 */
  @Query("SELECT t.trackId AS trackId, t.planId AS planId, p.title AS title FROM plan_tracks t JOIN plans p ON p.id = t.planId")
  abstract fun observeTrackPlans(): Flow<List<TrackPlanRow>>

  /** 計画の場所の行が指す場所（一覧の「行った N/M」）。 */
  @Query("SELECT planId, placeId FROM plan_items WHERE placeId IS NOT NULL")
  abstract fun observeItemPlaces(): Flow<List<PlanItemPlaceRow>>

  /** 日付のある保存済みの計画（日付の重なり・記録の開始で聞く計画を見るため）。 */
  @Query("SELECT * FROM plans WHERE startDate IS NOT NULL")
  abstract fun observeDatedPlans(): Flow<List<PlanEntity>>

  @Query("SELECT * FROM plans WHERE startDate IS NOT NULL")
  abstract suspend fun getDatedPlans(): List<PlanEntity>

  /** 結び付ける（経路がほかの計画に結び付いていれば付け替える）。 */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsertPlanTrack(link: PlanTrackEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertPlanTracks(links: List<PlanTrackEntity>)

  @Query("DELETE FROM plan_tracks WHERE planId = :planId AND trackId = :trackId")
  abstract suspend fun deletePlanTrack(planId: Long, trackId: Long)

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

  @Query("SELECT id FROM gps_tracks WHERE id IN (:trackIds)")
  protected abstract suspend fun existingTrackIds(trackIds: List<Long>): List<Long>

  /** 消した計画を、元の id のまま行・下書き・手で結び付けた経路ごと戻す（その間に消えた経路の結び付きは戻さない）。 */
  @Transaction
  open suspend fun restore(plan: PlanEntity, items: List<PlanItemEntity>, draft: PlanDraftEntity?, tracks: List<PlanTrackEntity> = emptyList()) {
    insertPlan(plan)
    if (items.isNotEmpty()) insertItems(items)
    if (draft != null) upsertDraft(draft)
    if (tracks.isNotEmpty()) {
      val existing = existingTrackIds(tracks.map { it.trackId }).toSet()
      insertPlanTracks(tracks.filter { it.trackId in existing })
    }
  }
}
