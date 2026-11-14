package com.pathly.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.data.local.PathlyDatabase
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.VisitedPlaceEntity
import com.pathly.data.local.entity.WishlistEntity
import com.pathly.data.local.entity.WishlistFulfillmentEntity
import com.pathly.data.places.PlacesNameResolver
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.WishlistFlag
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * 立ち寄り・場所の「取り消しのある変更」を、Room の実 DB で通す。
 *
 * ユニットテストは DAO がモックなので、外部キー（stops → places は NO_ACTION）・
 * CASCADE（places → google_places / place_resolutions / wishlist / visited_places）・
 * 元の ID のままの再挿入までは見ていない。ここではそれを見る。
 *
 * どの操作も Places API を呼ばない経路だけを通す（名前解決・施設検索の部品は組み立てるだけ）。
 */
@RunWith(AndroidJUnit4::class)
class StopAndPlaceChangeIntegrationTest {

  private lateinit var database: PathlyDatabase
  private lateinit var placeRepository: PlaceRepositoryImpl
  private lateinit var wishlistRepository: WishlistRepositoryImpl

  private var trackId = 0L

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // インメモリでも Room は外部キーを有効にして開く（本番と同じ制約で通す）。
    database = Room.inMemoryDatabaseBuilder(context, PathlyDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    placeRepository = PlaceRepositoryImpl(
      database.placeDao(),
      database.stopDao(),
      database.smoothedPointDao(),
      database.gpsPointDao(),
      database.placeResolutionDao(),
      database.googlePlaceDao(),
      database.googlePlaceCategoryDao(),
      database.wishlistDao(),
      database.visitedPlaceDao(),
      database.wishlistFulfillmentDao(),
      PlacesNameResolver(context),
    )
    wishlistRepository = WishlistRepositoryImpl(
      database.wishlistDao(),
      database.visitedPlaceDao(),
      database.wishlistFulfillmentDao(),
      database.placeDao(),
      database.placeResolutionDao(),
      database.googlePlaceDao(),
      database.googlePlaceCategoryDao(),
      database.stopDao(),
      placeRepository,
      PlacesTextSearcher(context),
    )

    trackId = database.gpsTrackDao().insertTrack(
      GpsTrackEntity(
        startTime = Date(BASE),
        endTime = Date(BASE + 3 * HOUR),
        isActive = false,
        createdAt = Date(BASE),
        updatedAt = Date(BASE),
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  // ---- 統合 ----

  @Test
  fun mergeStops_keepsEarliestStop_coversWholePeriod_andJoinsNotes() = runTest {
    val placeId = insertPlace(PlaceSource.DETECTED)
    val first = insertStop(placeId, arrival = BASE, departure = BASE + 10 * MINUTE, note = "前半")
    val second = insertStop(placeId, arrival = BASE + 15 * MINUTE, departure = BASE + 30 * MINUTE, note = null)
    val third = insertStop(placeId, arrival = BASE + 40 * MINUTE, departure = BASE + 50 * MINUTE, note = "  後半  ")

    // 渡す順は到着順でなくてよい（残すのは到着が最も早い 1 件）。
    val result = placeRepository.mergeStops(listOf(third, first, second))

    assertNotNull(result)
    assertEquals(first, result!!.survivingStopId)
    assertEquals(3, result.mergedCount)

    val stops = database.stopDao().getByTrack(trackId)
    assertEquals("残るのは 1 件", 1, stops.size)
    val merged = stops.single()
    assertEquals(first, merged.id)
    assertEquals(placeId, merged.placeId)
    assertEquals("到着は最も早いもの", BASE, merged.arrivalTime.time)
    assertEquals("出発は最も遅いもの", BASE + 50 * MINUTE, merged.departureTime.time)
    assertEquals("メモは到着順に連結（空は飛ばし、前後の空白は落とす）", "前半\n後半", merged.note)
    assertNotNull("場所は残った 1 件が参照し続けるので残る", database.placeDao().getById(placeId))
  }

  @Test
  fun undoMerge_restoresAllStopsWithOriginalIdsAndContents() = runTest {
    val placeId = insertPlace(PlaceSource.DETECTED)
    val first = insertStop(placeId, arrival = BASE, departure = BASE + 10 * MINUTE, note = "前半")
    val second = insertStop(placeId, arrival = BASE + 15 * MINUTE, departure = BASE + 30 * MINUTE, note = "後半")
    val before = database.stopDao().getByTrack(trackId).sortedBy { it.id }

    placeRepository.mergeStops(listOf(first, second))
    assertTrue(placeRepository.undoLastStopChange())

    val after = database.stopDao().getByTrack(trackId).sortedBy { it.id }
    assertEquals("統合前と行ごとに一致する（ID・期間・メモ・作成日時）", before, after)
    assertFalse("取り消しは 1 回だけ", placeRepository.undoLastStopChange())
  }

  @Test
  fun mergeStops_ofDifferentPlaces_isRefusedAndChangesNothing() = runTest {
    val placeA = insertPlace(PlaceSource.DETECTED)
    val placeB = insertPlace(PlaceSource.DETECTED, latitude = LAT + 0.01)
    val a = insertStop(placeA, arrival = BASE, departure = BASE + 10 * MINUTE)
    val b = insertStop(placeB, arrival = BASE + 20 * MINUTE, departure = BASE + 30 * MINUTE)
    val before = database.stopDao().getByTrack(trackId).sortedBy { it.id }

    assertNull(placeRepository.mergeStops(listOf(a, b)))

    assertEquals(before, database.stopDao().getByTrack(trackId).sortedBy { it.id })
  }

  @Test
  fun mergeStops_ofDifferentTracks_isRefusedAndChangesNothing() = runTest {
    val otherTrackId = database.gpsTrackDao().insertTrack(
      GpsTrackEntity(
        startTime = Date(BASE + 4 * HOUR),
        endTime = Date(BASE + 5 * HOUR),
        isActive = false,
        createdAt = Date(BASE),
        updatedAt = Date(BASE),
      ),
    )
    val placeId = insertPlace(PlaceSource.DETECTED)
    val a = insertStop(placeId, arrival = BASE, departure = BASE + 10 * MINUTE)
    val b = insertStop(placeId, arrival = BASE + 4 * HOUR, departure = BASE + 4 * HOUR + 10 * MINUTE, track = otherTrackId)

    assertNull(placeRepository.mergeStops(listOf(a, b)))

    assertEquals("どちらの経路の立ち寄りも残る", listOf(a), database.stopDao().getByTrack(trackId).map { it.id })
    assertEquals(listOf(b), database.stopDao().getByTrack(otherTrackId).map { it.id })
  }

  // ---- 滞在期間の編集 ----

  @Test
  fun updateStopDuration_savesValidPeriod_andIgnoresInvalidOnes() = runTest {
    val placeId = insertPlace(PlaceSource.DETECTED)
    val stopId = insertStop(placeId, arrival = BASE, departure = BASE + 10 * MINUTE)

    placeRepository.updateStopDuration(stopId, Date(BASE + 5 * MINUTE), Date(BASE + 25 * MINUTE))
    stopOf(stopId).let {
      assertEquals(BASE + 5 * MINUTE, it.arrivalTime.time)
      assertEquals(BASE + 25 * MINUTE, it.departureTime.time)
    }

    // 逆転・0 分の滞在は書かない（直前の正しい期間のまま）。
    placeRepository.updateStopDuration(stopId, Date(BASE + 30 * MINUTE), Date(BASE + 20 * MINUTE))
    placeRepository.updateStopDuration(stopId, Date(BASE + 30 * MINUTE), Date(BASE + 30 * MINUTE))
    stopOf(stopId).let {
      assertEquals(BASE + 5 * MINUTE, it.arrivalTime.time)
      assertEquals(BASE + 25 * MINUTE, it.departureTime.time)
    }
  }

  // ---- 立ち寄りの削除 ----

  @Test
  fun deleteStops_recyclesOrphanedDetectedPlace_andUndoRestoresEverything() = runTest {
    val detected = insertPlace(PlaceSource.DETECTED)
    database.googlePlaceDao().upsert(GooglePlaceEntity(detected, "g-detected", "カフェ", "東京都"))
    database.placeResolutionDao().upsert(PlaceResolutionEntity(detected, Date(BASE)))
    val stopId = insertStop(detected, arrival = BASE, departure = BASE + 10 * MINUTE, note = "メモ")
    val placeBefore = database.placeDao().getById(detected)
    val googleBefore = database.googlePlaceDao().getByPlace(detected)
    val resolutionBefore = database.placeResolutionDao().getByPlace(detected)
    val stopBefore = stopOf(stopId)

    val result = placeRepository.deleteStops(listOf(stopId))

    assertEquals(1, result.stopsDeleted)
    assertEquals(1, result.placesDeleted)
    assertEquals(0, result.placesKept)
    assertNull("参照の無くなった検出由来の場所は回収される", database.placeDao().getById(detected))
    assertNull("Google 情報は CASCADE で消える", database.googlePlaceDao().getByPlace(detected))
    assertNull("解決記録は CASCADE で消える", database.placeResolutionDao().getByPlace(detected))

    assertTrue(placeRepository.undoLastStopChange())

    assertEquals("場所は元の ID・中身で戻る", placeBefore, database.placeDao().getById(detected))
    assertEquals(googleBefore, database.googlePlaceDao().getByPlace(detected))
    assertEquals(resolutionBefore, database.placeResolutionDao().getByPlace(detected))
    assertEquals("立ち寄りは元の ID・中身で戻る", stopBefore, stopOf(stopId))
  }

  @Test
  fun deleteStops_keepsUserPlaces_andPlacesStillReferencedOrWishlisted() = runTest {
    val user = insertPlace(PlaceSource.USER)
    val shared = insertPlace(PlaceSource.DETECTED, latitude = LAT + 0.01)
    val wished = insertPlace(PlaceSource.DETECTED, latitude = LAT + 0.02)
    database.wishlistDao().insert(WishlistEntity(placeId = wished, priority = 1))
    val s1 = insertStop(user, arrival = BASE, departure = BASE + MINUTE)
    val s2 = insertStop(shared, arrival = BASE + 2 * MINUTE, departure = BASE + 3 * MINUTE)
    insertStop(shared, arrival = BASE + 4 * MINUTE, departure = BASE + 5 * MINUTE) // 残る訪問
    val s3 = insertStop(wished, arrival = BASE + 6 * MINUTE, departure = BASE + 7 * MINUTE)
    val stopsBefore = database.stopDao().getByTrack(trackId).sortedBy { it.id }

    val result = placeRepository.deleteStops(listOf(s1, s2, s3))

    assertEquals(3, result.stopsDeleted)
    assertEquals(0, result.placesDeleted)
    assertEquals(3, result.placesKept)
    assertNotNull("ユーザーが作った場所は残る", database.placeDao().getById(user))
    assertNotNull("ほかの訪問が残る場所は残る", database.placeDao().getById(shared))
    assertNotNull("行きたいの場所は残る", database.placeDao().getById(wished))
    assertNotNull("行きたいは巻き込まれない", database.wishlistDao().getByPlaceId(wished))

    // 場所を 1 つも消していなくても、取り消しで立ち寄りは元の ID・中身で戻る。
    assertTrue(placeRepository.undoLastStopChange())
    assertEquals(stopsBefore, database.stopDao().getByTrack(trackId).sortedBy { it.id })
  }

  // ---- 行きたいの終わり（→ adr/0036） ----

  @Test
  fun stopAfterWish_fulfillsWish_movesItToHistory() = runTest {
    val placeId = insertPlace(PlaceSource.USER)
    wish(placeId, at = BASE, priority = 2)

    placeRepository.addManualStopForPlace(trackId, placeId, Date(BASE + HOUR), Date(BASE + 2 * HOUR))

    assertNull("行ったら行きたいは終わる", database.wishlistDao().getByPlaceId(placeId))
    val history = database.wishlistFulfillmentDao().getByPlaceId(placeId).single()
    assertEquals(2, history.priority)
    assertEquals("入れた日時", BASE, history.wishedAt.time)
    assertEquals("行った日時は立ち寄りの到着", BASE + HOUR, history.fulfilledAt.time)
    val item = wishlistRepository.getPlace(placeId)!!
    assertEquals(1, item.fulfilledWishCount)
    assertEquals(BASE + HOUR, item.lastFulfilledAt?.time)
    assertEquals(WishlistFlag.FULFILLED, item.wishlistFlag)
  }

  @Test
  fun stopBeforeWish_doesNotFulfillWish() = runTest {
    val placeId = insertPlace(PlaceSource.USER)
    // 過去の経路の立ち寄りから行きたいに入れた（入れた日時の方が後）。
    wish(placeId, at = BASE + 3 * HOUR)

    placeRepository.addManualStopForPlace(trackId, placeId, Date(BASE + HOUR), Date(BASE + 2 * HOUR))

    assertNotNull("入れる前の立ち寄りでは終わらない", database.wishlistDao().getByPlaceId(placeId))
    assertEquals(0, database.wishlistFulfillmentDao().countByPlace(placeId))
    assertEquals(WishlistFlag.ACTIVE, wishlistRepository.getPlace(placeId)!!.wishlistFlag)
  }

  @Test
  fun wishAgainAfterVisit_countsEachFulfilledWish() = runTest {
    val placeId = insertPlace(PlaceSource.USER)
    wish(placeId, at = BASE)
    placeRepository.addManualStopForPlace(trackId, placeId, Date(BASE + HOUR), Date(BASE + 2 * HOUR))
    // 行った後に入れ直して、また行く。
    wish(placeId, at = BASE + 5 * HOUR)
    placeRepository.addManualStopForPlace(trackId, placeId, Date(BASE + 6 * HOUR), Date(BASE + 7 * HOUR))

    val item = wishlistRepository.getPlace(placeId)!!
    assertEquals(2, item.fulfilledWishCount)
    assertEquals("最後に行った日時", BASE + 6 * HOUR, item.lastFulfilledAt?.time)
    assertNull(database.wishlistDao().getByPlaceId(placeId))
  }

  @Test
  fun reassignStopToWishedPlace_fulfillsWish() = runTest {
    val detected = insertPlace(PlaceSource.DETECTED)
    database.placeResolutionDao().upsert(PlaceResolutionEntity(detected, Date(BASE)))
    val wished = insertPlace(PlaceSource.USER, latitude = LAT + 0.001)
    database.googlePlaceDao().upsert(GooglePlaceEntity(wished, "g-wished", "本屋"))
    wish(wished, at = BASE)
    val stopId = insertStop(detected, arrival = BASE + HOUR, departure = BASE + 2 * HOUR)

    placeRepository.reassignStopPlace(stopId, PlaceSearchResult("g-wished", "本屋", null, null, LAT + 0.001, LNG), customName = null)

    assertNull("付け替えた先の行きたいは、入れた後の立ち寄りになったので終わる", database.wishlistDao().getByPlaceId(wished))
    assertEquals(1, database.wishlistFulfillmentDao().countByPlace(wished))
  }

  @Test
  fun moveArrivalAfterWish_fulfillsWish() = runTest {
    val placeId = insertPlace(PlaceSource.USER)
    val stopId = insertStop(placeId, arrival = BASE, departure = BASE + 2 * HOUR)
    wish(placeId, at = BASE + HOUR)

    placeRepository.updateStopDuration(stopId, Date(BASE + HOUR + MINUTE), Date(BASE + 2 * HOUR))

    assertNull("到着を入れた後へずらすと終わる", database.wishlistDao().getByPlaceId(placeId))
    assertEquals(BASE + HOUR + MINUTE, database.wishlistFulfillmentDao().getByPlaceId(placeId).single().fulfilledAt.time)
  }

  @Test
  fun deleteStops_keepsDetectedPlaceWithWishHistory_andHistoryStays() = runTest {
    val placeId = insertPlace(PlaceSource.DETECTED)
    wish(placeId, at = BASE)
    placeRepository.addManualStopForPlace(trackId, placeId, Date(BASE + HOUR), Date(BASE + 2 * HOUR))
    val stopId = database.stopDao().getByTrack(trackId).single().id

    val result = placeRepository.deleteStops(listOf(stopId))

    assertEquals("行きたいと思ったことがある場所は自動では消さない", 0, result.placesDeleted)
    assertNotNull(database.placeDao().getById(placeId))
    assertEquals("立ち寄りを消しても履歴は残る", 1, database.wishlistFulfillmentDao().countByPlace(placeId))
  }

  @Test
  fun deletePlace_undoRestoresWishHistory() = runTest {
    val placeId = insertPlace(PlaceSource.USER)
    database.wishlistFulfillmentDao().insertAll(
      listOf(
        WishlistFulfillmentEntity(placeId = placeId, priority = 1, wishedAt = Date(BASE), fulfilledAt = Date(BASE + HOUR)),
      ),
    )
    val before = database.wishlistFulfillmentDao().getByPlaceId(placeId)

    wishlistRepository.deletePlace(placeId)
    assertEquals("場所を消すと履歴も消える", 0, database.wishlistFulfillmentDao().countByPlace(placeId))
    assertTrue(wishlistRepository.undoLastPlaceDeletion())

    assertEquals(before, database.wishlistFulfillmentDao().getByPlaceId(placeId))
  }

  // ---- 場所の削除 ----

  @Test
  fun deletePlace_cascadesChildren_andUndoRestoresWithOriginalIds() = runTest {
    val placeId = insertPlace(PlaceSource.USER, name = "お気に入り", note = "場所のメモ")
    database.googlePlaceDao().upsert(GooglePlaceEntity(placeId, "g-place", "パン屋", "京都府"))
    database.placeResolutionDao().upsert(PlaceResolutionEntity(placeId, Date(BASE)))
    database.wishlistDao().insert(WishlistEntity(placeId = placeId, priority = 2, createdAt = Date(BASE), updatedAt = Date(BASE)))
    database.visitedPlaceDao().insert(VisitedPlaceEntity(placeId = placeId, markedAt = Date(BASE)))
    val placeBefore = database.placeDao().getById(placeId)
    val googleBefore = database.googlePlaceDao().getByPlace(placeId)
    val resolutionBefore = database.placeResolutionDao().getByPlace(placeId)
    val wishlistBefore = database.wishlistDao().getByPlaceId(placeId)
    val visitedBefore = database.visitedPlaceDao().getByPlaceId(placeId)

    wishlistRepository.deletePlace(placeId)

    assertNull(database.placeDao().getById(placeId))
    assertNull("Google 情報は CASCADE で消える", database.googlePlaceDao().getByPlace(placeId))
    assertNull("解決記録は CASCADE で消える", database.placeResolutionDao().getByPlace(placeId))
    assertNull("行きたいは CASCADE で消える", database.wishlistDao().getByPlaceId(placeId))
    assertNull("訪問済みは CASCADE で消える", database.visitedPlaceDao().getByPlaceId(placeId))

    assertTrue(wishlistRepository.undoLastPlaceDeletion())

    assertEquals(placeBefore, database.placeDao().getById(placeId))
    assertEquals(googleBefore, database.googlePlaceDao().getByPlace(placeId))
    assertEquals(resolutionBefore, database.placeResolutionDao().getByPlace(placeId))
    assertEquals("行きたいは元の ID で戻る", wishlistBefore, database.wishlistDao().getByPlaceId(placeId))
    assertEquals("訪問済みは元の ID で戻る", visitedBefore, database.visitedPlaceDao().getByPlaceId(placeId))
    assertFalse("取り消しは 1 回だけ", wishlistRepository.undoLastPlaceDeletion())
  }

  @Test
  fun deletePlace_withStops_isBlockedByForeignKey() = runTest {
    // 立ち寄りのある場所は消させない（画面では削除を非活性にしている）。画面の守りが外れても、
    // stops → places の外部キー（NO_ACTION）が削除を止め、立ち寄りを宙に浮かせない。
    val placeId = insertPlace(PlaceSource.USER)
    val stopId = insertStop(placeId, arrival = BASE, departure = BASE + 10 * MINUTE)

    val error = runCatching { wishlistRepository.deletePlace(placeId) }.exceptionOrNull()

    assertTrue("外部キー違反で止まる: $error", error is SQLiteConstraintException)
    assertNotNull("場所は残る", database.placeDao().getById(placeId))
    assertEquals("立ち寄りは残る", placeId, stopOf(stopId).placeId)
  }

  // ---- 立ち寄りの場所の付け替え ----

  @Test
  fun reassignStopPlace_toExistingFacility_recyclesOrphanedDetectedPlace() = runTest {
    val detected = insertPlace(PlaceSource.DETECTED)
    database.placeResolutionDao().upsert(PlaceResolutionEntity(detected, Date(BASE)))
    val target = insertPlace(PlaceSource.DETECTED, latitude = LAT + 0.001)
    database.googlePlaceDao().upsert(GooglePlaceEntity(target, "g-target", "本屋"))
    val stopId = insertStop(detected, arrival = BASE, departure = BASE + 10 * MINUTE)

    placeRepository.reassignStopPlace(
      stopId,
      PlaceSearchResult("g-target", "本屋", null, null, LAT + 0.001, LNG),
      customName = null,
    )

    assertEquals("同じ施設を持つ既存の場所へ付け替わる", target, stopOf(stopId).placeId)
    assertNull("参照の無くなった検出由来の場所は回収される", database.placeDao().getById(detected))
    assertNull("その子も CASCADE で消える", database.placeResolutionDao().getByPlace(detected))
    assertEquals(
      "選んだ施設の場所はユーザーが触ったので USER に上がる",
      PlaceSource.USER.name,
      database.placeDao().getById(target)!!.source,
    )
  }

  @Test
  fun reassignStopPlace_withCustomName_createsUserPlace_andKeepsOldUserPlace() = runTest {
    val oldUser = insertPlace(PlaceSource.USER)
    val stopId = insertStop(oldUser, arrival = BASE, departure = BASE + 10 * MINUTE)

    placeRepository.reassignStopPlace(stopId, chosen = null, customName = "  友人の家  ")

    val newPlaceId = stopOf(stopId).placeId
    val newPlace = database.placeDao().getById(newPlaceId)!!
    assertNotEquals("新しい場所へ付け替わる", oldUser, newPlaceId)
    assertEquals("友人の家", newPlace.name)
    assertEquals(PlaceSource.USER.name, newPlace.source)
    assertEquals("元の場所の座標で作る", LAT, newPlace.latitude, 0.0)
    assertNotNull("ユーザーが作った元の場所は、参照が無くなっても残る", database.placeDao().getById(oldUser))
  }

  // ---- helpers ----

  private suspend fun insertPlace(
    source: PlaceSource,
    latitude: Double = LAT,
    name: String? = null,
    note: String? = null,
  ): Long = database.placeDao().insert(
    PlaceEntity(
      name = name,
      latitude = latitude,
      longitude = LNG,
      note = note,
      source = source.name,
      createdAt = Date(BASE),
      updatedAt = Date(BASE),
    ),
  )

  private suspend fun insertStop(placeId: Long, arrival: Long, departure: Long, note: String? = null, track: Long = trackId): Long = database.stopDao().insert(
    StopEntity(
      placeId = placeId,
      trackId = track,
      arrivalTime = Date(arrival),
      departureTime = Date(departure),
      note = note,
      createdAt = Date(BASE),
    ),
  )

  private suspend fun wish(placeId: Long, at: Long, priority: Int = 1) {
    database.wishlistDao().insert(WishlistEntity(placeId = placeId, priority = priority, createdAt = Date(at), updatedAt = Date(at)))
  }

  private suspend fun stopOf(stopId: Long): StopEntity = database.stopDao().getByIds(listOf(stopId)).single()

  private companion object {
    const val BASE = 1_760_000_000_000L
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val LAT = 35.0
    const val LNG = 135.0
  }
}
