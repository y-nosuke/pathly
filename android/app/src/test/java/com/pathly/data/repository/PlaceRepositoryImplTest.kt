package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceMergeDao
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.RegisteredPlaceRow
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRepositoryImplTest : PlaceStopRepositoryTestBase() {

  @Test
  fun resolveUnresolvedNames_countsPlacesSkippedBecauseFacilityIsTaken() = runTest {
    // 「押しても何も起きない」を画面で伝えられるよう、付けられなかった件数を返す（→ adr/0025）。
    coEvery { placeDao.getPlacesWithoutGoogleIdForTrack(1L) } returns listOf(
      PlaceEntity(id = 31L, name = "いつもの神社", latitude = 35.0, longitude = 139.0, source = PlaceSource.DETECTED.name),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L

    assertEquals(1, places.resolveUnresolvedNames(1L))
    // どの施設とぶつかったかを記録し、確認待ちとして出す（→ adr/0037）。施設情報は付けない。
    coVerify { placeResolutionDao.upsert(match { it.sameFacilityGooglePlaceId == "gp-same" && !it.keptSeparate }) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun mergePlaces_ranThroughDao_endsReachedWishes_andUndoRestoresSnapshot() = runTest {
    val snapshot = mockk<PlaceMergeDao.MergeSnapshot>(relaxed = true)
    coEvery { placeMergeDao.merge(3L, 7L, any()) } returns snapshot

    assertTrue(places.mergePlaces(3L, 7L))
    // 付け替えた立ち寄りで、残る方の行きたいが「行った」になることがある（→ adr/0036）。
    coVerify { wishlistFulfillmentDao.fulfillReachedWishes() }

    assertTrue(places.undoLastPlaceMerge())
    coVerify { placeMergeDao.restore(snapshot) }
    // 控えは 1 回で使い切る。
    assertFalse(places.undoLastPlaceMerge())
  }

  @Test
  fun mergePlaces_whenDaoRefuses_returnsFalseAndKeepsNoUndo() = runTest {
    coEvery { placeMergeDao.merge(any(), any(), any()) } returns null

    assertFalse(places.mergePlaces(3L, 3L))
    assertFalse(places.undoLastPlaceMerge())
  }

  @Test
  fun resolveUnresolvedNames_noMatch_recordsNullRow() = runTest {
    coEvery { placeDao.getPlacesWithoutGoogleIdForTrack(1L) } returns listOf(
      PlaceEntity(id = 5L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NoMatch

    places.resolveUnresolvedNames(1L)

    coVerify { placeResolutionDao.upsert(match { it.placeId == 5L }) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun resolveUnresolvedNames_offline_recordsNoRow() = runTest {
    coEvery { placeDao.getPlacesWithoutGoogleIdForTrack(1L) } returns listOf(
      PlaceEntity(id = 6L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted

    places.resolveUnresolvedNames(1L)

    // 未実施は行を作らない（後でキャッチアップ）。
    coVerify(exactly = 0) { placeResolutionDao.upsert(any()) }
  }

  @Test
  fun updatePlaceName_blankBecomesNull() = runTest {
    places.updatePlaceName(5L, "   ")
    coVerify { placeDao.updateName(5L, null, any()) }
  }

  @Test
  fun updatePlaceName_trimsWhitespace() = runTest {
    places.updatePlaceName(5L, "  カフェ  ")
    coVerify { placeDao.updateName(5L, "カフェ", any()) }
  }

  @Test
  fun resolveAllUnresolvedNames_propagatesFailure_soCallerCanRetry() = runTest {
    // ここで例外を握り潰すと、WorkManager は「成功」と受け取って再試行しない。
    // オンライン復帰後のキャッチアップが目的なので、失敗は呼び出し元まで伝える必要がある。
    coEvery { placeDao.getUnresolvedPlaces() } throws IllegalStateException("db down")

    val thrown = runCatching { places.resolveAllUnresolvedNames() }.exceptionOrNull()

    assertTrue("呼び出し元へ例外が伝わる（実際は $thrown）", thrown is IllegalStateException)
  }

  @Test
  fun resolveAllUnresolvedNames_whenResolverFails_propagates() = runTest {
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 30L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(any(), any()) } throws IllegalStateException("network down")

    val thrown = runCatching { places.resolveAllUnresolvedNames() }.exceptionOrNull()

    assertTrue("解決中の失敗も伝わる（実際は $thrown）", thrown is IllegalStateException)
  }

  @Test
  fun resolveAllUnresolvedNames_resolvesEachUnresolvedPlaceAndKeepsAnchor() = runTest {
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 30L, latitude = 35.0, longitude = 139.0),
    )
    coEvery { resolver.resolve(35.0, 139.0) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", PlaceCategory("shinto_shrine", "神社"), "gp-30", 35.7, 139.7)

    places.resolveAllUnresolvedNames()

    // 施設の座標は google_places に入れる（表示用）。places のアンカーは動かさない（adr/0023）。
    coVerify {
      googlePlaceDao.upsert(
        match {
          it.placeId == 30L &&
            it.googlePlaceId == "gp-30" &&
            it.name == "神社" &&
            it.latitude == 35.7 &&
            it.longitude == 139.7
        },
      )
    }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 30L }) }
  }

  @Test
  fun resolve_whenSameFacilityAlreadyHasPlace_mergesUntouchedDetectedPlace() = runTest {
    // 座標では見つけられなかったが、解決したら既存 place と同じ施設だった → そちらへ寄せる。
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 30L, latitude = 35.0, longitude = 139.0, source = PlaceSource.DETECTED.name),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L
    coEvery { wishlistDao.countByPlace(30L) } returns 0
    coEvery { visitedPlaceDao.countByPlace(30L) } returns 0

    places.resolveAllUnresolvedNames()

    // 参照を移してから消す。新しい行に Google データは書かない（寄せ先が既に持っている）。
    coVerify { stopDao.repointPlace(30L, 7L) }
    coVerify { placeDao.deleteById(30L) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun resolve_whenEmptyUserPlaceHitsSameFacility_mergesAndKeepsItUser() = runTest {
    // 自分で作った place（USER）でも、名前・メモなど中身が無ければ聞かずにまとめる（→ adr/0037）。
    // 「場所を取得」を押して同じ施設が出たら、そのまま紐付いてほしいため。違っていたら選び直せる。
    val place = PlaceEntity(id = 31L, latitude = 35.0, longitude = 139.0, source = PlaceSource.USER.name)
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(place)
    coEvery { placeDao.getById(31L) } returns place
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L
    coEvery { wishlistDao.countByPlace(31L) } returns 0
    coEvery { wishlistFulfillmentDao.countByPlace(31L) } returns 0
    coEvery { visitedPlaceDao.countByPlace(31L) } returns 0

    places.resolveAllUnresolvedNames()

    coVerify { stopDao.repointPlace(31L, 7L) }
    coVerify { placeDao.deleteById(31L) }
    // 寄せ先は自動回収されないよう USER にする。
    coVerify { placeDao.updateSource(7L, PlaceSource.USER.name) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun resolve_whenPlaceWasTouchedByUser_doesNotMerge() = runTest {
    // 自分で名前を付けた place は、同じ施設に解決されても吸収しない（誤解決で融合させない）。
    coEvery { placeDao.getUnresolvedPlaces() } returns listOf(
      PlaceEntity(id = 31L, name = "いつもの神社", latitude = 35.0, longitude = 139.0, source = PlaceSource.DETECTED.name),
    )
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("神社", "住所", null, "gp-same", 35.7, 139.7)
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-same") } returns 7L

    places.resolveAllUnresolvedNames()

    coVerify(exactly = 0) { stopDao.repointPlace(any(), any()) }
    coVerify(exactly = 0) { placeDao.deleteById(any()) }
    // 寄せられないうえに同じ施設を2つ持たせるわけにもいかないので、施設情報は付けない（→ adr/0025）。
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    // 同じ問い合わせを繰り返さないよう、解決記録は残す。
    coVerify { placeResolutionDao.upsert(match { it.placeId == 31L }) }
  }

  @Test
  fun findNearbyPlaces_nearestFirstWithinDetectionRadius() = runTest {
    coEvery { placeDao.getRegisteredPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      RegisteredPlaceRow(1L, "同じ地点", 35.0, 139.0, 0, 0, null, null, false),
      RegisteredPlaceRow(2L, "約1km先", 35.01, 139.0, 0, 0, null, null, false),
    )

    val near = places.findNearbyPlaces(35.0, 139.0)

    assertEquals(listOf(1L), near.map { it.placeId })
  }

  @Test
  fun findNearbyPlaces_noneWithinRadius_returnsEmpty() = runTest {
    coEvery { placeDao.getRegisteredPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      RegisteredPlaceRow(2L, "約1km先", 35.01, 139.0, 0, 0, null, null, false),
    )

    assertTrue(places.findNearbyPlaces(35.0, 139.0).isEmpty())
  }

  @Test
  fun findNearbyPlaces_returnsAllWithinRadiusNearestFirst() = runTest {
    coEvery { placeDao.getRegisteredPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      RegisteredPlaceRow(3L, "約30m先", 35.00027, 139.0, 0, 0, null, null, false),
      RegisteredPlaceRow(1L, "同じ地点", 35.0, 139.0, 0, 0, null, null, false),
      RegisteredPlaceRow(2L, "約1km先", 35.01, 139.0, 0, 0, null, null, false),
    )

    assertEquals(listOf(1L, 3L), places.findNearbyPlaces(35.0, 139.0).map { it.placeId })
  }

  @Test
  fun findPlacesAround_returnsWithinGivenRadiusNearestFirstWithDistance() = runTest {
    coEvery { placeDao.getRegisteredPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      RegisteredPlaceRow(3L, "約150m先", 35.00135, 139.0, 0, 0, null, null, false),
      RegisteredPlaceRow(1L, "同じ地点", 35.0, 139.0, 0, 0, null, null, false),
      RegisteredPlaceRow(2L, "約1km先", 35.01, 139.0, 0, 0, null, null, false),
    )

    val around = places.findPlacesAround(35.0, 139.0, 200.0)

    // 半径の外（約1km）は出さず、近い順に距離を付けて返す。
    assertEquals(listOf(1L, 3L), around.map { it.place.placeId })
    assertEquals(0.0, around[0].distanceMeters, 0.01)
    assertTrue(around[1].distanceMeters in 140.0..160.0)
  }
}
