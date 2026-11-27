package com.pathly.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.domain.model.PlaceSource
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
