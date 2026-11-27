package com.pathly.data.repository

import com.pathly.data.local.dao.PlanDao
import com.pathly.data.local.entity.PlanDraftEntity
import com.pathly.data.local.entity.PlanEntity
import com.pathly.data.local.entity.PlanItemEntity
import com.pathly.data.local.entity.PlanItemWithPlace
import com.pathly.data.local.entity.PlanPlaceRow
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanSummary
import com.pathly.domain.model.StoredPlanDraft
import com.pathly.domain.model.normalized
import com.pathly.domain.repository.PlanRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
  private var lastDeleted: DeletedPlan? = null

  private class DeletedPlan(val plan: PlanEntity, val items: List<PlanItemEntity>, val draft: PlanDraftEntity?)

  override fun observePlans(): Flow<List<PlanSummary>> = combine(planDao.observePlans(), planDao.observeDrafts(), planDao.observePlaces()) { rows, drafts, places ->
    // 下書きの場所の数は、今もある場所だけ数える（開くと消えた場所の行は落ちるため）。
    val placeIds = places.mapTo(HashSet()) { it.id }
    val draftsByPlan = drafts.filter { it.planId != null }.associateBy { it.planId }
    val saved = rows.map { row ->
      val draft = draftsByPlan[row.plan.id]
      val content = draft?.let { PlanDraftCodec.decode(it.json) }
      if (draft != null && content != null) {
        // 下書きがあれば、名前・日付・場所の数は下書きのもの（開くと下書きの続きから始まるため）。
        PlanSummary(content.toPlan(row.plan.id, row.plan.createdAt, draft.updatedAt), content.items.count { it.placeId in placeIds }, draft.id)
      } else {
        PlanSummary(row.plan.toPlan(), row.placeCount)
      }
    }
    val unsaved = drafts.filter { it.planId == null }.mapNotNull { draft ->
      val content = PlanDraftCodec.decode(draft.json) ?: return@mapNotNull null
      PlanSummary(content.toPlan(0, draft.updatedAt, draft.updatedAt), content.items.count { it.placeId in placeIds }, draft.id)
    }
    saved + unsaved
  }

  override suspend fun loadPlan(planId: Long): PlanDetail? {
    val plan = planDao.getPlan(planId) ?: return null
    return PlanDetail(plan.toPlan(), planDao.getItemsWithPlace(planId).map { it.toPlanItem() })
  }

  override suspend fun loadDraft(planId: Long, draftId: Long?): StoredPlanDraft? {
    val draft = (if (draftId != null) planDao.getDraft(draftId) else null)
      ?: (if (planId != 0L) planDao.getDraftOfPlan(planId) else null)
      ?: return null
    val content = PlanDraftCodec.decode(draft.json) ?: return null
    val places = observePlanPlaces().first()
    val ownerId = draft.planId ?: 0L
    val createdAt = draft.planId?.let { planDao.getPlan(it)?.createdAt } ?: draft.updatedAt
    var nextId = -1L
    val items = content.items.mapIndexedNotNull { index, item ->
      // 下書きの後で消された場所の行は落とす（場所が無いと名前も座標も出せない）。
      val place = item.placeId?.let { places[it] ?: return@mapIndexedNotNull null }
      PlanItem(nextId--, ownerId, item.dayIndex.coerceIn(1, content.dayCount), index, place, item.title, item.note, item.plannedTime, item.stayMinutes)
    }
    return StoredPlanDraft(draft.id, PlanDetail(content.toPlan(ownerId, createdAt, draft.updatedAt), items).normalized(), draft.updatedAt)
  }

  override suspend fun saveDraft(draftId: Long?, detail: PlanDetail): Long {
    val planId = detail.plan.id.takeIf { it != 0L }
    // 計画ごとに下書きは 1 つ。保存済みの計画なら、その計画の下書きを上書きする。
    val id = draftId ?: planId?.let { planDao.getDraftOfPlan(it)?.id } ?: 0L
    val saved = planDao.upsertDraft(PlanDraftEntity(id = id, planId = planId, json = PlanDraftCodec.encode(detail.toContent()), updatedAt = Date()))
    logger.i("Saved plan draft $saved")
    return saved
  }

  override suspend fun deleteDraft(draftId: Long) {
    planDao.deleteDraft(draftId)
  }

  override suspend fun savePlan(detail: PlanDetail, draftId: Long?): Long {
    val now = Date()
    val plan = detail.plan
    val entity = PlanEntity(
      id = plan.id,
      title = plan.title,
      note = plan.note,
      startDate = plan.startDate?.toEpochDay(),
      dayCount = plan.dayCount.coerceAtLeast(1),
      createdAt = if (plan.id == 0L) now else (planDao.getPlan(plan.id)?.createdAt ?: now),
      updatedAt = now,
    )
    val items = detail.normalized().items.map {
      PlanItemEntity(
        planId = plan.id,
        dayIndex = it.dayIndex,
        position = it.position,
        placeId = it.place?.placeId,
        title = it.title,
        note = it.note,
        plannedTime = it.plannedTime,
        stayMinutes = it.stayMinutes,
      )
    }
    val planId = planDao.savePlan(entity, items, draftId)
    logger.i("Saved plan $planId")
    return planId
  }

  override suspend fun deletePlan(planId: Long) {
    val plan = planDao.getPlan(planId) ?: return
    lastDeleted = DeletedPlan(plan, planDao.getItems(planId), planDao.getDraftOfPlan(planId))
    planDao.deletePlan(planId)
    logger.i("Deleted plan $planId")
  }

  override suspend fun undoDeletePlan(): Boolean {
    val deleted = lastDeleted ?: return false
    planDao.restore(deleted.plan, deleted.items, deleted.draft)
    lastDeleted = null
    return true
  }

  override fun observePlanPlaces(): Flow<Map<Long, PlanPlace>> = planDao.observePlaces().map { rows -> rows.associate { it.id to it.toPlanPlace() } }
}

private fun PlanDraftCodec.Content.toPlan(id: Long, createdAt: Date, updatedAt: Date): Plan = Plan(
  id = id,
  title = title,
  note = note,
  startDate = startDate?.let { LocalDate.ofEpochDay(it) },
  dayCount = dayCount,
  createdAt = createdAt,
  updatedAt = updatedAt,
)

private fun PlanDetail.toContent(): PlanDraftCodec.Content = PlanDraftCodec.Content(
  title = plan.title,
  note = plan.note,
  startDate = plan.startDate?.toEpochDay(),
  dayCount = plan.dayCount,
  items = normalized().items.map { PlanDraftCodec.Item(it.dayIndex, it.place?.placeId, it.title, it.note, it.plannedTime, it.stayMinutes) },
)

internal fun PlanEntity.toPlan(): Plan = Plan(
  id = id,
  title = title,
  note = note,
  startDate = startDate?.let { LocalDate.ofEpochDay(it) },
  dayCount = dayCount.coerceAtLeast(1),
  createdAt = createdAt,
  updatedAt = updatedAt,
)

/** 表示名は場所と同じ順（自分の名前 → Google 名 → 住所 → 座標 → Place.displayName）。座標はアンカー。 */
private fun placeName(name: String?, googleName: String?, googleAddress: String?, latitude: Double, longitude: Double): String = name ?: googleName ?: googleAddress ?: String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

private fun PlanPlaceRow.toPlanPlace(): PlanPlace = PlanPlace(id, placeName(placeName, googleName, googleAddress, latitude, longitude), latitude, longitude)

internal fun PlanItemWithPlace.toPlanItem(): PlanItem {
  val placeId = item.placeId
  val place = if (placeId != null && placeLatitude != null && placeLongitude != null) {
    PlanPlace(placeId, placeName(placeName, googleName, googleAddress, placeLatitude, placeLongitude), placeLatitude, placeLongitude)
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
