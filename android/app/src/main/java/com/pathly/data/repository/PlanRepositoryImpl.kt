package com.pathly.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.pathly.data.local.dao.PlanDao
import com.pathly.data.local.entity.PlanEntity
import com.pathly.data.local.entity.PlanItemEntity
import com.pathly.data.local.entity.PlanItemWithPlace
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanArrangement
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanSummary
import com.pathly.domain.repository.PlanRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepositoryImpl @Inject constructor(
  private val planDao: PlanDao,
) : PlanRepository {

  private val logger = Logger("PlanRepository")

  // 取り消し用の控え（直前の 1 件だけ。スナックバーは最新の 1 件しか出さない）。
  private var lastDeletedPlan: Pair<PlanEntity, List<PlanItemEntity>>? = null
  private var lastRemovedItem: PlanItemEntity? = null

  override fun observePlans(): Flow<List<PlanSummary>> = planDao.observePlans().map { rows ->
    rows.map { PlanSummary(it.plan.toPlan(), it.placeCount) }
  }

  override fun observePlan(planId: Long): Flow<PlanDetail?> = combine(planDao.observePlan(planId), planDao.observeItems(planId)) { plan, items ->
    plan?.let { PlanDetail(it.toPlan(), items.map { row -> row.toPlanItem() }) }
  }

  override suspend fun createPlan(title: String): Long {
    val now = Date()
    val id = planDao.insertPlan(PlanEntity(title = title, createdAt = now, updatedAt = now))
    logger.i("Created plan $id")
    return id
  }

  override suspend fun updateTitleAndNote(planId: Long, title: String, note: String?) {
    val plan = planDao.getPlan(planId) ?: return
    planDao.updatePlan(plan.copy(title = title, note = note?.ifBlank { null }, updatedAt = Date()))
  }

  override suspend fun setDates(planId: Long, startDate: LocalDate?, dayCount: Int) {
    planDao.setDates(planId, startDate?.toEpochDay(), dayCount, Date())
  }

  override suspend fun deletePlan(planId: Long) {
    val plan = planDao.getPlan(planId) ?: return
    lastDeletedPlan = plan to planDao.getItems(planId)
    planDao.deletePlan(planId)
    logger.i("Deleted plan $planId")
  }

  override suspend fun undoDeletePlan(): Boolean {
    val (plan, items) = lastDeletedPlan ?: return false
    planDao.restore(plan, items)
    lastDeletedPlan = null
    return true
  }

  override suspend fun addPlaces(planId: Long, day: Int, placeIds: List<Long>) {
    if (placeIds.isEmpty()) return
    planDao.appendPlaces(planId, day, placeIds, Date())
  }

  override suspend fun addMemo(planId: Long, day: Int, title: String, plannedTime: Int?, note: String?) {
    planDao.append(
      PlanItemEntity(planId = planId, dayIndex = day, position = 0, title = title, plannedTime = plannedTime, note = note?.ifBlank { null }),
      day,
      Date(),
    )
  }

  override suspend fun updateItem(itemId: Long, title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?) {
    val item = planDao.getItem(itemId) ?: return
    planDao.updateItem(
      item.copy(
        // 場所の行の名前は場所のもの。文言はメモの行だけが持つ。
        title = if (item.placeId == null) title ?: item.title else null,
        plannedTime = plannedTime,
        stayMinutes = stayMinutes,
        note = note?.ifBlank { null },
      ),
    )
    planDao.touch(item.planId, Date())
  }

  override suspend fun moveItemToDay(itemId: Long, day: Int) {
    planDao.moveToDayEnd(itemId, day, Date())
  }

  override suspend fun reorder(planId: Long, order: List<Pair<Long, Int>>) {
    planDao.applySlots(planId, PlanArrangement.renumber(order), Date())
  }

  override suspend fun removeItem(itemId: Long) {
    val item = planDao.getItem(itemId) ?: return
    lastRemovedItem = item
    planDao.deleteItem(itemId)
    planDao.touch(item.planId, Date())
  }

  override suspend fun undoRemoveItem(): Boolean {
    val item = lastRemovedItem ?: return false
    // 計画か場所が先に消えていたら戻せない（外部キーで弾かれる）。
    lastRemovedItem = null
    return try {
      planDao.insertItem(item)
      true
    } catch (e: SQLiteConstraintException) {
      logger.w("Could not restore plan item ${item.id}", e)
      false
    }
  }
}

internal fun PlanEntity.toPlan(): Plan = Plan(
  id = id,
  title = title,
  note = note,
  startDate = startDate?.let { LocalDate.ofEpochDay(it) },
  dayCount = dayCount.coerceAtLeast(1),
  createdAt = createdAt,
  updatedAt = updatedAt,
)

internal fun PlanItemWithPlace.toPlanItem(): PlanItem {
  val placeId = item.placeId
  val place = if (placeId != null && placeLatitude != null && placeLongitude != null) {
    // 表示名は場所と同じ順（自分の名前 → Google 名 → 住所 → 座標 → Place.displayName）。
    val name = placeName ?: googleName ?: googleAddress ?: String.format(Locale.US, "%.5f, %.5f", placeLatitude, placeLongitude)
    PlanPlace(placeId, name, placeLatitude, placeLongitude)
  } else {
    null
  }
  return PlanItem(
    id = item.id,
    planId = item.planId,
    dayIndex = item.dayIndex,
    position = item.position,
    place = place,
    title = item.title,
    note = item.note,
    plannedTime = item.plannedTime,
    stayMinutes = item.stayMinutes,
  )
}
