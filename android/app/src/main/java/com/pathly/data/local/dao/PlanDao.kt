package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pathly.data.local.entity.PlanEntity
import com.pathly.data.local.entity.PlanItemEntity
import com.pathly.data.local.entity.PlanItemWithPlace
import com.pathly.data.local.entity.PlanListRow
import com.pathly.domain.model.PlanArrangement
import com.pathly.domain.model.PlanSlot
import kotlinx.coroutines.flow.Flow
import java.util.Date

/** 計画と計画の行（→ adr/0050）。 */
@Dao
abstract class PlanDao {

  @Query(
    "SELECT p.*, (SELECT COUNT(*) FROM plan_items i WHERE i.planId = p.id AND i.placeId IS NOT NULL) AS placeCount " +
      "FROM plans p",
  )
  abstract fun observePlans(): Flow<List<PlanListRow>>

  @Query("SELECT * FROM plans WHERE id = :planId")
  abstract fun observePlan(planId: Long): Flow<PlanEntity?>

  /** 計画の行（日・順番どおり）。場所の行には名前とアンカーの座標を添える。 */
  @Query(
    "SELECT i.*, p.name AS placeName, g.name AS googleName, g.address AS googleAddress, " +
      "p.latitude AS placeLatitude, p.longitude AS placeLongitude " +
      "FROM plan_items i " +
      "LEFT JOIN places p ON p.id = i.placeId " +
      "LEFT JOIN google_places g ON g.placeId = i.placeId " +
      "WHERE i.planId = :planId ORDER BY i.dayIndex, i.position, i.id",
  )
  abstract fun observeItems(planId: Long): Flow<List<PlanItemWithPlace>>

  @Query("SELECT * FROM plans WHERE id = :planId")
  abstract suspend fun getPlan(planId: Long): PlanEntity?

  @Query("SELECT * FROM plan_items WHERE planId = :planId ORDER BY dayIndex, position, id")
  abstract suspend fun getItems(planId: Long): List<PlanItemEntity>

  @Query("SELECT * FROM plan_items WHERE id = :itemId")
  abstract suspend fun getItem(itemId: Long): PlanItemEntity?

  @Query("SELECT * FROM plan_items WHERE placeId = :placeId")
  abstract suspend fun getItemsByPlace(placeId: Long): List<PlanItemEntity>

  /** 場所を使っている計画の数（同じ計画に 2 回入っていても 1 と数える）。 */
  @Query("SELECT COUNT(DISTINCT planId) FROM plan_items WHERE placeId = :placeId")
  abstract suspend fun countPlansUsingPlace(placeId: Long): Int

  @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM plan_items WHERE planId = :planId AND dayIndex = :day")
  abstract suspend fun nextPosition(planId: Long, day: Int): Int

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

  @Update
  abstract suspend fun updateItem(item: PlanItemEntity)

  @Query("DELETE FROM plan_items WHERE id = :itemId")
  abstract suspend fun deleteItem(itemId: Long)

  @Query("UPDATE plan_items SET dayIndex = :day, position = :position WHERE id = :itemId")
  protected abstract suspend fun moveItem(itemId: Long, day: Int, position: Int)

  @Query("UPDATE plans SET updatedAt = :now WHERE id = :planId")
  abstract suspend fun touch(planId: Long, now: Date)

  /** 行の並び（日・順番）をまとめて書く。 */
  @Transaction
  open suspend fun applySlots(planId: Long, slots: List<PlanSlot>, now: Date) {
    slots.forEach { moveItem(it.itemId, it.dayIndex, it.position) }
    touch(planId, now)
  }

  /** 場所の行を [day] 日目の末尾に足す（[placeIds] の順で）。 */
  @Transaction
  open suspend fun appendPlaces(planId: Long, day: Int, placeIds: List<Long>, now: Date) {
    val start = nextPosition(planId, day)
    insertItems(placeIds.mapIndexed { i, placeId -> PlanItemEntity(planId = planId, dayIndex = day, position = start + i, placeId = placeId) })
    touch(planId, now)
  }

  /** 行を [day] 日目の末尾に足す（[item] の日と順番は上書きする）。 */
  @Transaction
  open suspend fun append(item: PlanItemEntity, day: Int, now: Date): Long {
    val id = insertItem(item.copy(id = 0, dayIndex = day, position = nextPosition(item.planId, day)))
    touch(item.planId, now)
    return id
  }

  /** 行を [day] 日目の末尾へ移す。 */
  @Transaction
  open suspend fun moveToDayEnd(itemId: Long, day: Int, now: Date) {
    val item = getItem(itemId) ?: return
    if (item.dayIndex == day) return
    moveItem(itemId, day, nextPosition(item.planId, day))
    touch(item.planId, now)
  }

  /**
   * 開始日と日数を変える。日数を減らしたら、消える日の行を新しい最後の日の末尾へ移す（→ adr/0050）。
   */
  @Transaction
  open suspend fun setDates(planId: Long, startDate: Long?, dayCount: Int, now: Date) {
    val plan = getPlan(planId) ?: return
    val days = dayCount.coerceAtLeast(1)
    val slots = getItems(planId).map { PlanSlot(it.id, it.dayIndex, it.position) }
    PlanArrangement.shrink(slots, days).forEach { moveItem(it.itemId, it.dayIndex, it.position) }
    updatePlan(plan.copy(startDate = startDate, dayCount = days, updatedAt = now))
  }

  /** 消した計画を、元の id のまま行ごと戻す。 */
  @Transaction
  open suspend fun restore(plan: PlanEntity, items: List<PlanItemEntity>) {
    insertPlan(plan)
    if (items.isNotEmpty()) insertItems(items)
  }
}
