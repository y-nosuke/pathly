package com.pathly.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.PlanDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Date

/** 計画の窓口を Room の実 DB で通す（外部キー・CASCADE・元の id のままの戻し → adr/0050）。 */
@RunWith(AndroidJUnit4::class)
class PlanRepositoryIntegrationTest {

  private lateinit var database: PathlyDatabase
  private lateinit var repository: PlanRepositoryImpl

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
  fun createPlan_isUndatedDayTrip_andDatesCanBeAddedLater() = runTest {
    val planId = repository.createPlan("新しい計画")

    val created = detail(planId).plan
    assertNull("日付なしで作れる", created.startDate)
    assertEquals(1, created.dayCount)

    repository.setDates(planId, LocalDate.of(2026, 11, 30), 2)

    val dated = detail(planId).plan
    assertEquals(LocalDate.of(2026, 11, 30), dated.startDate)
    assertEquals(2, dated.dayCount)
    assertEquals(LocalDate.of(2026, 12, 1), dated.endDate)

    repository.setDates(planId, null, 2)
    assertNull("日付を外せる", detail(planId).plan.startDate)
  }

  @Test
  fun placesAndMemos_areAppendedToTheDay_andPlaceRowsUseThePlaceNameAndAnchor() = runTest {
    val shrine = insertPlace(name = null, latitude = 35.0)
    database.googlePlaceDao().upsert(GooglePlaceEntity(shrine, "g-shrine", "鶴岡八幡宮", latitude = 36.0, longitude = 140.0))
    val temple = insertPlace(name = "長谷寺", latitude = 35.1)
    val planId = repository.createPlan("鎌倉")
    repository.setDates(planId, null, 2)

    repository.addPlaces(planId, 1, listOf(shrine, temple))
    repository.addMemo(planId, 1, "昼食", plannedTime = 12 * 60, note = null)
    repository.addPlaces(planId, 2, listOf(shrine))

    val items = detail(planId).items
    assertEquals(listOf("鶴岡八幡宮", "長谷寺", "昼食", "鶴岡八幡宮"), items.map { it.label })
    assertEquals(listOf(1, 1, 1, 2), items.map { it.dayIndex })
    assertEquals("座標は場所のアンカー（Google の座標ではない）", 35.0, items[0].place!!.latitude, 0.0)
    assertEquals(12 * 60, items[2].plannedTime)
    assertEquals("同じ場所を 2 回入れてよい", 3, repository.observePlans().first().single().placeCount)
  }

  @Test
  fun shrinkingDays_movesRowsOfRemovedDaysToTheEndOfTheLastDay() = runTest {
    val a = insertPlace(name = "A")
    val b = insertPlace(name = "B", latitude = 35.1)
    val c = insertPlace(name = "C", latitude = 35.2)
    val planId = repository.createPlan("旅行")
    repository.setDates(planId, LocalDate.of(2026, 11, 30), 3)
    repository.addPlaces(planId, 1, listOf(a))
    repository.addPlaces(planId, 2, listOf(b))
    repository.addPlaces(planId, 3, listOf(c))
    repository.addMemo(planId, 3, "帰る", null, null)

    repository.setDates(planId, LocalDate.of(2026, 11, 30), 2)

    val items = detail(planId).items
    assertEquals(listOf("A", "B", "C", "帰る"), items.map { it.label })
    assertEquals(listOf(1, 2, 2, 2), items.map { it.dayIndex })
    assertEquals("消えた日の行は消えない", 4, items.size)
  }

  @Test
  fun reorder_movesRowsAcrossDays_andMoveToDayAppendsToTheEnd() = runTest {
    val a = insertPlace(name = "A")
    val b = insertPlace(name = "B", latitude = 35.1)
    val planId = repository.createPlan("旅行")
    repository.setDates(planId, null, 2)
    repository.addPlaces(planId, 1, listOf(a, b))
    repository.addMemo(planId, 2, "メモ", null, null)
    val (rowA, rowB, memo) = detail(planId).items

    // B を 2 日目のメモの前へ。
    repository.reorder(planId, listOf(rowA.id to 1, rowB.id to 2, memo.id to 2))

    assertEquals(listOf("A", "B", "メモ"), detail(planId).items.map { it.label })
    assertEquals(listOf(1, 2, 2), detail(planId).items.map { it.dayIndex })

    repository.moveItemToDay(rowA.id, 2)

    assertEquals(listOf("B", "メモ", "A"), detail(planId).items.map { it.label })
  }

  @Test
  fun updateItem_keepsPlaceRowsNamedByThePlace() = runTest {
    val a = insertPlace(name = "A")
    val planId = repository.createPlan("旅行")
    repository.addPlaces(planId, 1, listOf(a))
    repository.addMemo(planId, 1, "昼食", null, null)
    val (placeRow, memoRow) = detail(planId).items

    repository.updateItem(placeRow.id, title = "無視される", plannedTime = 600, stayMinutes = 60, note = "  ")
    repository.updateItem(memoRow.id, title = "夕食", plannedTime = null, stayMinutes = null, note = "予約")

    val (p, m) = detail(planId).items
    assertEquals("A", p.label)
    assertNull(p.title)
    assertEquals(600, p.plannedTime)
    assertEquals(60, p.stayMinutes)
    assertNull("空のメモは持たない", p.note)
    assertEquals("夕食", m.label)
    assertEquals("予約", m.note)
  }

  @Test
  fun deletePlan_cascadesRows_andUndoRestoresThemWithOriginalIds() = runTest {
    val a = insertPlace(name = "A")
    val planId = repository.createPlan("旅行")
    repository.addPlaces(planId, 1, listOf(a))
    repository.addMemo(planId, 1, "メモ", null, null)
    val before = detail(planId)

    repository.deletePlan(planId)

    assertNull(repository.observePlan(planId).first())
    assertTrue(database.planDao().getItems(planId).isEmpty())
    assertEquals("場所は残る", "A", database.placeDao().getById(a)!!.name)

    assertTrue(repository.undoDeletePlan())
    assertEquals(before, detail(planId))
    assertFalse("取り消しは 1 回だけ", repository.undoDeletePlan())
  }

  @Test
  fun removeItem_undoBringsTheRowBack() = runTest {
    val a = insertPlace(name = "A")
    val planId = repository.createPlan("旅行")
    repository.addPlaces(planId, 1, listOf(a))
    val before = detail(planId)

    repository.removeItem(before.items.single().id)
    assertTrue(detail(planId).items.isEmpty())

    assertTrue(repository.undoRemoveItem())
    assertEquals(before.items, detail(planId).items)
  }

  private suspend fun detail(planId: Long): PlanDetail = repository.observePlan(planId).first()!!

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
