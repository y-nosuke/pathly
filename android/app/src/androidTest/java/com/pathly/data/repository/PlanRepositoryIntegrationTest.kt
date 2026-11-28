package com.pathly.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.PlanTrackLink
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.model.newPlanDetail
import com.pathly.domain.model.sameContentAs
import com.pathly.domain.model.withDates
import com.pathly.domain.model.withMemoAdded
import com.pathly.domain.model.withPlacesAdded
import com.pathly.domain.model.withTitleAndNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** 計画の窓口を Room の実 DB で通す（保存・下書き・外部キー・CASCADE・元の id のままの戻し → adr/0050）。 */
@RunWith(AndroidJUnit4::class)
class PlanRepositoryIntegrationTest {

  private lateinit var database: PathlyDatabase
  private lateinit var repository: PlanRepositoryImpl
  private var nextId = -1L

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, PathlyDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = PlanRepositoryImpl(database.planDao())
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun savePlan_createsThenUpdates_andRowsUseThePlaceNameAndAnchor() = runTest {
    val shrine = insertPlace(name = null, latitude = 35.0)
    database.googlePlaceDao().upsert(GooglePlaceEntity(shrine, "g-shrine", "鶴岡八幡宮", latitude = 36.0, longitude = 140.0))
    val temple = insertPlace(name = "長谷寺", latitude = 35.1)
    val draft = newPlanDetail("鎌倉")
      .withDates(null, 2)
      .withPlacesAdded(1, listOf(place(shrine), place(temple))) { nextId-- }
      .withMemoAdded(1, "昼食", 12 * 60, null, nextId--)
      .withPlacesAdded(2, listOf(place(shrine))) { nextId-- }

    val planId = repository.savePlan(draft, draftId = null)

    val saved = repository.loadPlan(planId)!!
    assertNull("日付なしで作れる", saved.plan.startDate)
    assertEquals(listOf("鶴岡八幡宮", "長谷寺", "昼食", "鶴岡八幡宮"), saved.items.map { it.label })
    assertEquals(listOf(1, 1, 1, 2), saved.items.map { it.dayIndex })
    assertEquals("座標は場所のアンカー（Google の座標ではない）", 35.0, saved.items[0].place!!.latitude, 0.0)
    assertEquals("同じ場所を 2 回入れてよい", 3, repository.observePlans().first().single().placeCount)

    // 後から日付を付けて保存し直す（行は入れ替わるが中身は同じ）。
    val updated = saved.withDates(LocalDate.of(2026, 11, 30), 2)
    assertEquals(planId, repository.savePlan(updated, draftId = null))
    val reloaded = repository.loadPlan(planId)!!
    assertEquals(LocalDate.of(2026, 11, 30), reloaded.plan.startDate)
    assertTrue(reloaded.sameContentAs(updated))
  }

  @Test
  fun draftOfNewPlan_isListedAsDraft_andSavingRemovesTheDraft() = runTest {
    val a = insertPlace(name = "A")
    val draft = newPlanDetail("下書きの計画").withPlacesAdded(1, listOf(place(a))) { nextId-- }

    val draftId = repository.saveDraft(null, draft)

    val listed = repository.observePlans().first().single()
    assertEquals(0L, listed.plan.id)
    assertEquals(draftId, listed.draftId)
    assertEquals("下書きの計画", listed.plan.title)
    assertEquals(1, listed.placeCount)
    val loaded = repository.loadDraft(0, draftId)!!
    assertTrue(loaded.detail.sameContentAs(draft))

    val planId = repository.savePlan(loaded.detail, draftId)

    val summary = repository.observePlans().first().single()
    assertEquals(planId, summary.plan.id)
    assertFalse("保存したら下書きは消える", summary.hasDraft)
  }

  @Test
  fun draftOfSavedPlan_isOnePerPlan_andShownInsteadOfTheSavedContent() = runTest {
    val planId = repository.savePlan(newPlanDetail("保存した名前"), null)
    val saved = repository.loadPlan(planId)!!

    val first = repository.saveDraft(null, saved.withTitleAndNote("下書きの名前", null))
    val second = repository.saveDraft(null, saved.withTitleAndNote("下書きの名前 2", null))

    assertEquals("計画ごとに下書きは 1 つ（上書き）", first, second)
    val summary = repository.observePlans().first().single()
    assertEquals("下書きの名前 2", summary.plan.title)
    assertEquals(first, summary.draftId)
    assertEquals("下書きの名前 2", repository.loadDraft(planId, null)!!.detail.plan.title)
    assertEquals("保存済みの中身は変わらない", "保存した名前", repository.loadPlan(planId)!!.plan.title)

    repository.deleteDraft(first)
    assertNull(repository.loadDraft(planId, null))
  }

  @Test
  fun draftRowsOfDeletedPlaces_areDropped() = runTest {
    val a = insertPlace(name = "A")
    val b = insertPlace(name = "B", latitude = 35.1)
    val draftId = repository.saveDraft(null, newPlanDetail("計画").withPlacesAdded(1, listOf(place(a), place(b))) { nextId-- })

    database.placeDao().deleteById(a)

    assertEquals(listOf("B"), repository.loadDraft(0, draftId)!!.detail.items.map { it.label })
  }

  @Test
  fun deletePlan_cascadesRowsAndDraft_andUndoRestoresThem() = runTest {
    val a = insertPlace(name = "A")
    val planId = repository.savePlan(newPlanDetail("旅行").withPlacesAdded(1, listOf(place(a))) { nextId-- }, null)
    val before = repository.loadPlan(planId)!!
    val draftId = repository.saveDraft(null, before.withTitleAndNote("下書き", null))

    repository.deletePlan(planId)

    assertNull(repository.loadPlan(planId))
    assertTrue(database.planDao().getItems(planId).isEmpty())
    assertNull("下書きも消える", database.planDao().getDraft(draftId))
    assertEquals("場所は残る", "A", database.placeDao().getById(a)!!.name)

    assertTrue(repository.undoDeletePlan())
    assertEquals(before, repository.loadPlan(planId))
    assertNotNull(database.planDao().getDraft(draftId))
    assertFalse("取り消しは 1 回だけ", repository.undoDeletePlan())
  }

  @Test
  fun linkedTracks_markVisited_andATrackBelongsToOnePlan() = runTest {
    val a = insertPlace(name = "A")
    val b = insertPlace(name = "B")
    val day = LocalDate.of(2026, 11, 30)
    val kamakura = repository.savePlan(newPlanDetail("鎌倉").withDates(day, 1).withPlacesAdded(1, listOf(place(a), place(b))) { nextId-- }, null)
    val enoshima = repository.savePlan(newPlanDetail("江ノ島").withDates(day, 1), null)
    val morning = insertTrack(day)
    val other = insertTrack(day.minusDays(10))
    insertStop(a, morning)
    insertStop(b, other)

    var summary = summaryOf("鎌倉")
    assertEquals("結び付けるまでは無い（日付で勝手に付かない）", 0, summary.linkedTrackCount)
    assertEquals(0, summary.visitedCount)

    repository.linkTrack(kamakura, morning, PlanTrackSource.START)
    repository.linkTrack(kamakura, other, PlanTrackSource.MANUAL)
    summary = summaryOf("鎌倉")
    assertEquals(2, summary.linkedTrackCount)
    assertEquals(2, summary.visitedCount)
    assertEquals(
      setOf(PlanTrackLink(morning, kamakura, PlanTrackSource.START), PlanTrackLink(other, kamakura, PlanTrackSource.MANUAL)),
      repository.observePlanTracks(kamakura).first().toSet(),
    )

    // 別の計画に結び付けると付け替わる（1 つの経路は 1 つの計画だけ）。
    repository.linkTrack(enoshima, morning, PlanTrackSource.MANUAL)
    assertEquals(listOf(other), repository.observePlanTracks(kamakura).first().map { it.trackId })
    assertEquals(1, summaryOf("江ノ島").linkedTrackCount)
    assertEquals("B だけ", 1, summaryOf("鎌倉").visitedCount)
    assertEquals(enoshima to "江ノ島", repository.observeTrackPlans().first()[morning])

    // 日付を変えても結び付きは勝手には変わらない（外すかは画面で聞く）。
    repository.savePlan(repository.loadPlan(kamakura)!!.withDates(day.plusDays(7), 1), null)
    assertEquals(1, summaryOf("鎌倉").linkedTrackCount)

    repository.unlinkTrack(kamakura, other)
    assertEquals(0, summaryOf("鎌倉").linkedTrackCount)
  }

  @Test
  fun draft_keepsTheTracksToUnlink() = runTest {
    val planId = repository.savePlan(newPlanDetail("鎌倉"), null)
    val detail = repository.loadPlan(planId)!!

    val draftId = repository.saveDraft(null, detail.withTitleAndNote("下書き", null), setOf(3L, 1L))

    assertEquals(setOf(1L, 3L), repository.loadDraft(planId, draftId)!!.unlinkTrackIds)
    val plain = repository.saveDraft(draftId, detail)
    assertEquals("選んでいなければ空", emptySet<Long>(), repository.loadDraft(planId, plain)!!.unlinkTrackIds)
  }

  @Test
  fun plansOn_areSavedPlansWhoseDatesCoverTheDay() = runTest {
    val day = LocalDate.of(2026, 11, 30)
    repository.savePlan(newPlanDetail("京都").withDates(day.minusDays(1), 3), null)
    repository.savePlan(newPlanDetail("嵐山").withDates(day, 1), null)
    repository.savePlan(newPlanDetail("翌週").withDates(day.plusDays(7), 1), null)
    repository.savePlan(newPlanDetail("日付なし"), null)
    repository.saveDraft(null, newPlanDetail("下書き").withDates(day, 1))

    assertEquals(setOf("京都", "嵐山"), repository.plansOn(day).map { it.title }.toSet())
  }

  @Test
  fun recordingLinkedTrack_marksThePlanAsRecording() = runTest {
    val planId = repository.savePlan(newPlanDetail("今日").withDates(LocalDate.now(), 1), null)
    val track = insertTrack(LocalDate.now(), active = true)
    assertFalse(repository.observePlans().first().single().isRecording)

    repository.linkTrack(planId, track, PlanTrackSource.START)

    assertTrue(repository.observePlans().first().single().isRecording)
  }

  @Test
  fun deletingTrack_removesTheLink_andDeletingPlan_keepsTheTrack_andUndoRestoresTheLink() = runTest {
    val planId = repository.savePlan(newPlanDetail("旅行"), null)
    val kept = insertTrack(LocalDate.of(2026, 1, 1))
    val removed = insertTrack(LocalDate.of(2026, 1, 2))
    repository.linkTrack(planId, kept, PlanTrackSource.START)
    repository.linkTrack(planId, removed, PlanTrackSource.MANUAL)

    database.gpsTrackDao().deleteTrack(database.gpsTrackDao().getTrackById(removed)!!)
    assertEquals("経路を消すと結び付きも消える", listOf(kept), repository.observePlanTracks(planId).first().map { it.trackId })

    repository.deletePlan(planId)
    assertTrue(database.planDao().getPlanTracks(planId).isEmpty())
    assertNotNull("計画を消しても経路は残る", database.gpsTrackDao().getTrackById(kept))

    assertTrue(repository.undoDeletePlan())
    assertEquals("取り消すと結び付きも戻る", listOf(PlanTrackLink(kept, planId, PlanTrackSource.START)), repository.observePlanTracks(planId).first())
  }

  private suspend fun summaryOf(title: String) = repository.observePlans().first().single { it.plan.title == title }

  private suspend fun insertTrack(day: LocalDate, active: Boolean = false): Long {
    val start = Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant())
    return database.gpsTrackDao().insertTrack(
      GpsTrackEntity(startTime = start, endTime = if (active) null else Date(start.time + 3_600_000), isActive = active, createdAt = start, updatedAt = start),
    )
  }

  private suspend fun insertStop(placeId: Long, trackId: Long) {
    database.stopDao().insert(StopEntity(placeId = placeId, trackId = trackId, arrivalTime = Date(0), departureTime = Date(1)))
  }

  private suspend fun place(placeId: Long) = repository.observePlanPlaces().first()[placeId]!!

  private suspend fun insertPlace(name: String?, latitude: Double = 35.0): Long = database.placeDao().insert(
    PlaceEntity(
      name = name,
      latitude = latitude,
      longitude = 135.0,
      source = PlaceSource.USER.name,
      createdAt = Date(0),
      updatedAt = Date(0),
    ),
  )
}
