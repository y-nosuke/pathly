package com.pathly.data.repository

import com.pathly.data.local.dao.PlanDao
import com.pathly.data.local.entity.PlanDraftEntity
import com.pathly.data.local.entity.PlanEntity
import com.pathly.data.local.entity.PlanItemEntity
import com.pathly.data.local.entity.PlanItemWithPlace
import com.pathly.data.local.entity.PlanPlaceRow
import com.pathly.data.local.entity.PlanTrackEntity
import com.pathly.data.local.entity.TrackStartRow
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanActuals
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanSummary
import com.pathly.domain.model.PlanTrackLink
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.model.StoredPlanDraft
import com.pathly.domain.model.TrackStart
import com.pathly.domain.model.TrackVisit
import com.pathly.domain.model.normalized
import com.pathly.domain.repository.LiveStopRepository
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
  // 記録中の「立ち寄り中」も「行った」に数える（立ち寄った時点で付くように → docs/specs/plans.md の「行った」）。
  private val liveStopRepository: LiveStopRepository,
) : PlanRepository {

  private val logger = Logger("PlanRepository")

  // 取り消し用の控え（直前の 1 件だけ。スナックバーは最新の 1 件しか出さない）。
  private var lastDeleted: DeletedPlan? = null

  private class DeletedPlan(val plan: PlanEntity, val items: List<PlanItemEntity>, val draft: PlanDraftEntity?, val tracks: List<PlanTrackEntity>)

  override fun observePlans(): Flow<List<PlanSummary>> {
    val base = combine(planDao.observePlans(), planDao.observeDrafts(), planDao.observePlaces(), planDao.observeItemPlaces()) { rows, drafts, places, itemPlaces ->
      // 下書きの場所の数は、今もある場所だけ数える（開くと消えた場所の行は落ちるため）。
      val placeIds = places.mapTo(HashSet()) { it.id }
      val itemPlacesByPlan = itemPlaces.groupBy({ it.planId }, { it.placeId })
      val draftsByPlan = drafts.filter { it.planId != null }.associateBy { it.planId }
      val saved = rows.map { row ->
        val draft = draftsByPlan[row.plan.id]
        val content = draft?.let { PlanDraftCodec.decode(it.json) }
        if (draft != null && content != null) {
          // 下書きがあれば、名前・日付・場所の数は下書きのもの（開くと下書きの続きから始まるため）。
          val draftPlaces = content.items.mapNotNull { it.placeId }.filter { it in placeIds }
          ListedPlan(PlanSummary(content.toPlan(row.plan.id, row.plan.createdAt, draft.updatedAt), draftPlaces.size, draft.id), draftPlaces)
        } else {
          ListedPlan(PlanSummary(row.plan.toPlan(), row.placeCount), itemPlacesByPlan[row.plan.id].orEmpty())
        }
      }
      val unsaved = drafts.filter { it.planId == null }.mapNotNull { draft ->
        val content = PlanDraftCodec.decode(draft.json) ?: return@mapNotNull null
        val draftPlaces = content.items.mapNotNull { it.placeId }.filter { it in placeIds }
        ListedPlan(PlanSummary(content.toPlan(0, draft.updatedAt, draft.updatedAt), draftPlaces.size, draft.id), draftPlaces)
      }
      saved + unsaved
    }
    // 行った・記録中は保存せず、結び付いた経路（plan_tracks）からここで求める（→ adr/0050）。
    return combine(base, planDao.observeTrackStarts(), planDao.observeAllPlanTracks(), observeTrackVisits()) { listed, startRows, links, visits ->
      val tracks = startRows.map { it.toTrackStart() }
      val linkedByPlan = links.groupBy({ it.planId }, { it.trackId })
      listed.map { (summary, itemPlaceIds) ->
        // 一度も保存していない計画（id が 0）には結び付かない。
        val linked = if (summary.plan.id != 0L) linkedByPlan[summary.plan.id].orEmpty().toSet() else emptySet()
        val visited = PlanActuals.visitedPlaceIds(linked, visits)
        summary.copy(
          linkedTrackCount = linked.size,
          visitedCount = PlanActuals.visitedItemCount(itemPlaceIds, visited),
          isRecording = PlanActuals.recordingTrackId(linked, tracks) != null,
        )
      }
    }
  }

  /** 一覧の 1 行と、その場所の行が指す場所（「行った N/M」を求めるため）。 */
  private data class ListedPlan(val summary: PlanSummary, val itemPlaceIds: List<Long>)

  override fun observeTrackStarts(): Flow<List<TrackStart>> = planDao.observeTrackStarts().map { rows -> rows.map { it.toTrackStart() } }

  /** 確定した立ち寄りと、記録中の「立ち寄り中」。 */
  override fun observeTrackVisits(): Flow<List<TrackVisit>> = combine(planDao.observeTrackVisits(), liveStopRepository.currentStop) { rows, live ->
    rows.map { TrackVisit(it.trackId, it.placeId) } + listOfNotNull(live?.let { TrackVisit(it.trackId, it.place.id) })
  }

  override fun observePlanTracks(planId: Long): Flow<List<PlanTrackLink>> = planDao.observePlanTracks(planId).map { rows -> rows.map { it.toLink() } }

  override fun observeTrackPlans(): Flow<Map<Long, Pair<Long, String>>> = planDao.observeTrackPlans().map { rows -> rows.associate { it.trackId to (it.planId to it.title) } }

  override fun observeDatedPlans(): Flow<List<Plan>> = planDao.observeDatedPlans().map { rows -> rows.map { it.toPlan() } }

  override suspend fun plansOn(date: LocalDate): List<Plan> = planDao.getDatedPlans().map { it.toPlan() }.filter { PlanActuals.covers(it, date) }

  override suspend fun linkTrack(planId: Long, trackId: Long, source: PlanTrackSource) {
    planDao.upsertPlanTrack(PlanTrackEntity(trackId = trackId, planId = planId, source = source.name))
    logger.i("Linked track $trackId to plan $planId (${source.name})")
  }

  override suspend fun unlinkTrack(planId: Long, trackId: Long) {
    planDao.deletePlanTrack(planId, trackId)
    logger.i("Unlinked track $trackId from plan $planId")
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
    return StoredPlanDraft(
      draft.id,
      PlanDetail(content.toPlan(ownerId, createdAt, draft.updatedAt), items).normalized(),
      draft.updatedAt,
      content.unlinkTrackIds.toSet(),
    )
  }

  override suspend fun saveDraft(draftId: Long?, detail: PlanDetail, unlinkTrackIds: Set<Long>): Long {
    val planId = detail.plan.id.takeIf { it != 0L }
    // 計画ごとに下書きは 1 つ。保存済みの計画なら、その計画の下書きを上書きする。
    val id = draftId ?: planId?.let { planDao.getDraftOfPlan(it)?.id } ?: 0L
    val saved = planDao.upsertDraft(PlanDraftEntity(id = id, planId = planId, json = PlanDraftCodec.encode(detail.toContent().copy(unlinkTrackIds = unlinkTrackIds.sorted())), updatedAt = Date()))
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
    lastDeleted = DeletedPlan(plan, planDao.getItems(planId), planDao.getDraftOfPlan(planId), planDao.getPlanTracks(planId))
    planDao.deletePlan(planId)
    logger.i("Deleted plan $planId")
  }

  override suspend fun undoDeletePlan(): Boolean {
    val deleted = lastDeleted ?: return false
    planDao.restore(deleted.plan, deleted.items, deleted.draft, deleted.tracks)
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

private fun TrackStartRow.toTrackStart(): TrackStart = TrackStart(id, startTime, isActive)

private fun PlanTrackEntity.toLink(): PlanTrackLink = PlanTrackLink(
  trackId = trackId,
  planId = planId,
  // 知らない値（先の版の書き出しなど）は手の結び付きとして扱う（日付を変えても外さない）。
  source = PlanTrackSource.entries.firstOrNull { it.name == source } ?: PlanTrackSource.MANUAL,
)
