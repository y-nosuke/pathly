package com.pathly.data.repository

import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.NamedPlaceRow
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.DetectedStop
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.StopCandidate
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class StopRepositoryImplTest : PlaceStopRepositoryTestBase() {

  // 記録中の再解析は「境界（確定済みの過去）以前」だけを候補にする。滞在中・末尾はライブ検出に
  // 任せて重複を防ぎつつ、記録中に誤って消した過去の立ち寄りは候補に出せること。
  @Test
  fun detectMissingStops_whileRecording_capsAtBoundary_offersPastExcludesTail() = runTest {
    val points = twoClustersDwellingAtTail()
    coEvery { smoothedPointDao.getByTrackAfter(1L, any()) } answers {
      val afterMillis = secondArg<Long>()
      points.filter { it.timestamp.time > afterMillis }
    }
    coEvery { smoothedPointDao.getByTrack(1L) } returns points
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 50L
    coEvery { placeDao.getById(50L) } returns
      PlaceEntity(id = 50L, latitude = 35.01, longitude = 139.01)
    coEvery { stopDao.insert(any()) } returns 500L
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()
    coEvery { placeResolutionDao.getByPlace(50L) } returns null
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted
    // 生の現在地はクラスタB（滞在中）の中心付近にある＝滞在中。
    coEvery { gpsPointDao.getLatestPoint(1L) } returns gp(35.0100, 139.0100)

    // パス1: ライブ検出でクラスタA（0-240s）を確定し、境界を A.departure(240s) まで進める
    // （末尾のクラスタB は滞在中で未確定＝currentStop）。
    liveStops.updateStopsForTrack(1L, isFinal = false)
    assertNotNull(liveStops.currentStop.value)

    // ユーザーが記録中に A を削除した想定（getByTrack は空）。再解析すると…
    val candidates = stops.detectMissingStops(1L)

    // 境界(240s)以前の A だけが候補に出る。滞在中の末尾 B（departure 540s）は出ない。
    assertEquals(1, candidates.size)
    assertEquals(Date(0L), candidates[0].detected.arrivalTime)
    assertEquals(Date(240_000L), candidates[0].detected.departureTime)
  }

  @Test
  fun detectMissingStops_noExisting_returnsAllDetected() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted

    val result = stops.detectMissingStops(1L)

    // 一覧が空なので検出した立ち寄りがそのまま候補になる。永続化はしない。
    assertEquals(1, result.size)
    coVerify(exactly = 0) { stopDao.insert(any()) }
  }

  @Test
  fun detectMissingStops_addsDisplayName_fromPlaces() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns
      PlacesNameResolver.Outcome.Found("カフェ", "住所", PlaceCategory("cafe", "カフェ・喫茶"), "gp-1", 35.5, 139.5)

    val result = stops.detectMissingStops(1L)

    // 候補には追加前の判断用に表示名が付く（永続化はしない）。
    assertEquals("カフェ", result.single().name)
    assertEquals("gp-1", result.single().googlePlaceId)
    coVerify(exactly = 0) { stopDao.insert(any()) }
    coVerify(exactly = 0) { placeDao.insert(any()) }
  }

  @Test
  fun detectMissingStops_reusesNearbyNamedPlace_withoutCallingPlaces() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns emptyList()
    // 候補座標のすぐ近くに命名済みの place がある → それを再利用し Places は叩かない。
    coEvery { placeDao.getNamedPlacesInBounds(any(), any(), any(), any()) } returns listOf(
      NamedPlaceRow(
        id = 7L,
        name = "自宅",
        latitude = 35.0,
        longitude = 139.0,
        googlePlaceId = null,
        googleName = null,
        googleAddress = null,
        googleLatitude = null,
        googleLongitude = null,
        categoryCode = null,
        categoryDisplayName = null,
      ),
    )

    val result = stops.detectMissingStops(1L)

    assertEquals("自宅", result.single().name)
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun detectMissingStops_overlappingExisting_isExcluded() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns listOf(
      StopEntity(id = 1L, placeId = 10L, trackId = 1L, arrivalTime = Date(0), departureTime = Date(240_000)),
    )

    val result = stops.detectMissingStops(1L)

    // 既存の立ち寄りと時間帯が重なる候補は「一覧に有る」として除外される。
    assertTrue(result.isEmpty())
  }

  @Test
  fun detectMissingStops_nonOverlappingExisting_isKept() = runTest {
    coEvery { smoothedPointDao.getByTrack(1L) } returns finishedVisitPoints()
    coEvery { stopDao.getByTrack(1L) } returns listOf(
      StopEntity(id = 1L, placeId = 10L, trackId = 1L, arrivalTime = Date(10_000_000), departureTime = Date(10_100_000)),
    )
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { resolver.resolve(any(), any()) } returns PlacesNameResolver.Outcome.NotAttempted

    val result = stops.detectMissingStops(1L)

    // 時間帯が重ならない既存があっても、取りこぼした候補は残す。
    assertEquals(1, result.size)
  }

  @Test
  fun addStops_reusesExistingPlaceOfSameFacility() = runTest {
    // 候補が施設 ID を持っているなら、座標が 30m 離れていても既存の place を再利用する
    // （同じ施設の 2 つ目を作らない → adr/0025）。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-zoo") } returns 7L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeResolutionDao.getByPlace(7L) } returns PlaceResolutionEntity(7L, Date(0))
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    val candidate = StopCandidate(
      detected = DetectedStop(35.0, 139.0, Date(0), Date(240_000), pointCount = 5),
      name = "動物園",
      googlePlaceId = "gp-zoo",
      googleLatitude = 35.7,
      googleLongitude = 139.7,
    )
    val added = stops.addStops(1L, listOf(candidate))

    assertEquals(1, added)
    // 新しい place は作らず、訪問だけ足す。
    coVerify(exactly = 0) { placeDao.insert(any()) }
    coVerify { stopDao.insert(match { it.placeId == 7L }) }
    // 既に解決済みの place なので、施設情報を焼き直さない。
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun addStops_persistsSelectedAndBakesInName() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    // 検出時に名前を焼き込むので、命名のための Places 呼び出しは不要。
    coEvery { placeDao.getUnresolvedPlacesForTrack(1L) } returns emptyList()

    val candidate = StopCandidate(
      detected = DetectedStop(35.0, 139.0, Date(0), Date(240_000), pointCount = 5),
      name = "カフェ",
      address = "住所",
      googlePlaceId = "gp-1",
    )
    val added = stops.addStops(1L, listOf(candidate))

    assertEquals(1, added)
    coVerify { stopDao.insert(match { it.trackId == 1L && it.placeId == 10L }) }
    // 検出時に引いた Google データは google_places に焼き込む（places.name はユーザー名専用）。
    coVerify {
      googlePlaceDao.upsert(
        match { it.placeId == 10L && it.googlePlaceId == "gp-1" && it.name == "カフェ" && it.address == "住所" },
      )
    }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun addStops_empty_addsNothing() = runTest {
    val added = stops.addStops(1L, emptyList())

    assertEquals(0, added)
    coVerify(exactly = 0) { stopDao.insert(any()) }
  }

  @Test
  fun addManualStop_typedName_setsNameAndMarksResolved() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList() // findOrCreatePlace で新規作成
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null

    val id = stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "手動カフェ", null)

    assertEquals(100L, id)
    coVerify { stopDao.insert(match { it.trackId == 1L && it.placeId == 10L }) }
    // 手入力の名前はユーザー名として未命名の place に焼き込む。googlePlaceId は無いので google_places は作らない。
    coVerify { placeDao.updateName(10L, "手動カフェ", any()) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    // 手動追加は「ユーザーが決めた」印として解決ログを残す（自動命名で上書きさせない）。
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun addManualStop_poi_bakesInNameAndGooglePlaceId() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    // その googlePlaceId の place はまだ無い → ID 同定で新規作成される。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null

    stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "スタバ", "gp-9")

    // 手入力名はユーザー名（places.name）。POI 由来の googlePlaceId は google_places に控える。
    coVerify { placeDao.updateName(10L, "スタバ", any()) }
    coVerify { googlePlaceDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-9" }) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
    coVerify(exactly = 0) { resolver.resolve(any(), any()) }
  }

  @Test
  fun addManualStop_poiName_goesToGooglePlacesNotPlaceName() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null

    // POI の名前をそのまま使った（自分では入力していない）ケース。
    stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), null, "gp-9", googleName = "スタバ")

    // Google が付けた名前は google_places.name へ。places.name（自分で付けた名前）は空のまま。
    coVerify { googlePlaceDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-9" && it.name == "スタバ" }) }
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) }
  }

  @Test
  fun addManualStop_ownNameOnFacility_keepsBothNamesAndTheLink() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getByPlace(10L) } returns null
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null

    // 「スタバだけど自分は休憩と呼ぶ」。列が分かれているので両立する。
    stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "休憩", "gp-9", googleName = "スタバ")

    coVerify { placeDao.updateName(10L, "休憩", any()) }
    // 名前を変えても施設との紐付けは外さない（カテゴリ・住所・施設ページが残る）。
    coVerify { googlePlaceDao.upsert(match { it.placeId == 10L && it.googlePlaceId == "gp-9" && it.name == "スタバ" }) }
  }

  @Test
  fun addManualStop_poiAlreadyRegistered_reusesPlaceByGoogleId() = runTest {
    // 既に同じ googlePlaceId の place(77) がある → 施設の同一性で再利用し、新規作成しない。
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns 77L
    coEvery { placeDao.getById(77L) } returns
      PlaceEntity(id = 77L, name = "スタバ", latitude = 35.0, longitude = 139.0, source = "USER")
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeResolutionDao.getByPlace(77L) } returns PlaceResolutionEntity(77L, Date(0))

    stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "スタバ", "gp-9")

    coVerify { stopDao.insert(match { it.placeId == 77L }) }
    coVerify(exactly = 0) { placeDao.insert(any()) } // 座標で新規作成しない（ID同定で再利用）
  }

  @Test
  fun addManualStop_noName_leavesUnnamedButMarksResolved() = runTest {
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns emptyList()
    coEvery { placeDao.insert(any()) } returns 10L
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(10L) } returns PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(10L) } returns null

    stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), null, null)

    // 名前無しの手動追加は place を未命名のまま残すが、自動命名で近くの別施設名に化けないよう
    // 解決ログは残す（未命名のまま維持。あとで手動「場所を取得」で命名可）。
    coVerify { stopDao.insert(any()) }
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 10L }) }
  }

  @Test
  fun addManualStop_reusesNearbyPlace_doesNotOverwriteExistingName() = runTest {
    // すぐ近く（同座標）に命名済みの place がある → 再利用し、名前は上書きしない。
    coEvery { placeDao.getInBounds(any(), any(), any(), any()) } returns listOf(
      PlaceEntity(id = 7L, name = "自宅", latitude = 35.0, longitude = 139.0),
    )
    coEvery { stopDao.insert(any()) } returns 100L
    coEvery { placeDao.getById(7L) } returns PlaceEntity(id = 7L, name = "自宅", latitude = 35.0, longitude = 139.0)
    coEvery { placeResolutionDao.getByPlace(7L) } returns null

    stops.addManualStop(1L, 35.0, 139.0, Date(0), Date(180_000), "別名", null)

    coVerify { stopDao.insert(match { it.placeId == 7L }) }
    coVerify(exactly = 0) { placeDao.insert(any()) } // 新規作成しない
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) } // 命名済みは守る
  }

  @Test
  fun reassignStopPlace_withChosenPoi_repointsStopAndRecyclesOldDetected() = runTest {
    // stop 100 は元 place 10（検出由来・孤立）。選んだ POI(gp-9) の新 place(88) へ付け替える。
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")
    coEvery { googlePlaceDao.getPlaceIdByGoogleId("gp-9") } returns null
    coEvery { placeDao.insert(any()) } returns 88L
    coEvery { googlePlaceDao.getByPlace(88L) } returns null
    coEvery { placeResolutionDao.getByPlace(88L) } returns null
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0

    val chosen = PlaceSearchResult("gp-9", "日高屋", "住所", PlaceCategory("chinese_restaurant", "中華料理"), 35.02, 139.02)
    stops.reassignStopPlace(100L, chosen, null)

    coVerify { stopDao.updatePlace(100L, 88L) } // この訪問だけ付け替え
    coVerify { googlePlaceDao.upsert(match { it.placeId == 88L && it.googlePlaceId == "gp-9" }) }
    coVerify { placeDao.deleteById(10L) } // 孤立した検出由来の元 place を回収
  }

  @Test
  fun reassignStopPlace_withCustomName_createsNewUserPlace() = runTest {
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")
    coEvery { placeDao.insert(any()) } returns 88L
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0

    stops.reassignStopPlace(100L, null, "日高屋")

    // 手入力名は新しい USER 場所として作る（座標同定せず分離）。
    coVerify { placeDao.insert(match { it.name == "日高屋" && it.source == "USER" }) }
    coVerify { stopDao.updatePlace(100L, 88L) }
    coVerify { placeDao.deleteById(10L) }
  }

  @Test
  fun deleteStops_orphanedDetectedPlaceIsDeleted_sharedPlaceIsKept() = runTest {
    // stop 100 -> place 10（検出由来・他に訪問なし＝孤立）, stop 200 -> place 20（他にも訪問が残る）
    coEvery { stopDao.getByIds(listOf(100L, 200L)) } returns listOf(stopEntity(100L, 10L), stopEntity(200L, 20L))
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { stopDao.countByPlace(20L) } returns 1
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")

    val result = stops.deleteStops(listOf(100L, 200L))

    coVerify { stopDao.deleteByIds(listOf(100L, 200L)) }
    coVerify { placeDao.deleteById(10L) } // 検出由来で孤立した場所ごと削除
    coVerify(exactly = 0) { placeDao.deleteById(20L) } // 他訪問が残る場所は保持
    assertEquals(2, result.stopsDeleted)
    assertEquals(1, result.placesDeleted)
    assertEquals(1, result.placesKept)
  }

  @Test
  fun deleteStops_orphanedUserPlace_isKept() = runTest {
    // ユーザーが明示的に登録した場所（USER）は、参照ゼロで孤立しても自動では消さない。
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0
    coEvery { placeDao.getById(10L) } returns
      PlaceEntity(id = 10L, name = "手動の場所", latitude = 35.0, longitude = 139.0, source = "USER")

    val result = stops.deleteStops(listOf(100L))

    coVerify(exactly = 0) { placeDao.deleteById(10L) } // USER 由来は保持
    assertEquals(0, result.placesDeleted)
    assertEquals(1, result.placesKept)
  }

  @Test
  fun deleteStops_placeOnWishlist_isKeptEvenWithoutRemainingStops() = runTest {
    // stop 100 -> place 10。stop は無くなるが行きたい登録があるので場所は残す。
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stopEntity(100L, 10L))
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 1

    val result = stops.deleteStops(listOf(100L))

    coVerify { stopDao.deleteByIds(listOf(100L)) }
    coVerify(exactly = 0) { placeDao.deleteById(10L) } // wishlist を巻き込まない
    assertEquals(0, result.placesDeleted)
    assertEquals(1, result.placesKept)
  }

  @Test
  fun deleteStops_emptyList_isNoOp() = runTest {
    val result = stops.deleteStops(emptyList())

    coVerify(exactly = 0) { stopDao.deleteByIds(any()) }
    assertEquals(0, result.stopsDeleted)
  }

  @Test
  fun undoLastStopChange_afterDeletion_restoresStopsAndOrphanedPlace() = runTest {
    // 孤立 place を回収する削除 → 取り消しで place・Google データ・解決ログ・stop を元IDのまま復元。
    val stop = stopEntity(100L, 10L)
    val place = PlaceEntity(id = 10L, latitude = 35.0, longitude = 139.0, source = "DETECTED")
    val resolution = PlaceResolutionEntity(10L, Date(0L))
    val google = GooglePlaceEntity(10L, "gp-1", "カフェ", "住所")
    coEvery { stopDao.getByIds(listOf(100L)) } returns listOf(stop)
    coEvery { stopDao.countByPlace(10L) } returns 0
    coEvery { wishlistDao.countByPlace(10L) } returns 0
    coEvery { placeDao.getById(10L) } returns place
    coEvery { placeResolutionDao.getByPlace(10L) } returns resolution
    coEvery { googlePlaceDao.getByPlace(10L) } returns google

    stops.deleteStops(listOf(100L))
    val undone = stops.undoLastStopChange()

    assertTrue(undone)
    coVerify { placeDao.insert(place) }
    coVerify { googlePlaceDao.upsert(google) }
    coVerify { placeResolutionDao.upsert(resolution) }
    coVerify { stopDao.insert(stop) }
  }

  @Test
  fun undoLastStopChange_withoutPriorChange_returnsFalse() = runTest {
    assertFalse(stops.undoLastStopChange())
  }

  @Test
  fun updateStopDuration_writesGivenRange() = runTest {
    stops.updateStopDuration(100L, Date(1_000L), Date(5_000L))

    coVerify { stopDao.updateDuration(100L, Date(1_000L), Date(5_000L)) }
  }

  @Test
  fun updateStopDuration_arrivalNotBeforeDeparture_isIgnored() = runTest {
    // 到着＝出発（滞在0分）と逆転は押し間違いでしか作れないので書かない。
    stops.updateStopDuration(100L, Date(5_000L), Date(5_000L))
    stops.updateStopDuration(100L, Date(5_000L), Date(1_000L))

    coVerify(exactly = 0) { stopDao.updateDuration(any(), any(), any()) }
  }

  @Test
  fun mergeStops_samePlace_keepsEarliestAndCoversWholeRange() = runTest {
    // 到着が最も早い 100 を残し、期間は 100 の到着〜101 の出発まで広げる。101 は消す。
    val first = stopEntity(100L, 10L, 0L, 60_000L)
    val second = stopEntity(101L, 10L, 120_000L, 300_000L)
    coEvery { stopDao.getByIds(listOf(100L, 101L)) } returns listOf(second, first)

    val result = stops.mergeStops(listOf(100L, 101L))

    assertEquals(100L, result?.survivingStopId)
    assertEquals(2, result?.mergedCount)
    coVerify { stopDao.deleteByIds(listOf(101L)) }
    coVerify { stopDao.update(first.copy(arrivalTime = Date(0L), departureTime = Date(300_000L))) }
  }

  @Test
  fun mergeStops_concatenatesNotesInArrivalOrder() = runTest {
    val first = stopEntity(100L, 10L, 0L, 60_000L, note = "入口")
    val second = stopEntity(101L, 10L, 120_000L, 300_000L, note = "  ゾウ舎  ")
    val third = stopEntity(102L, 10L, 400_000L, 500_000L, note = "   ")
    coEvery { stopDao.getByIds(listOf(100L, 101L, 102L)) } returns listOf(third, second, first)

    stops.mergeStops(listOf(100L, 101L, 102L))

    // 空白だけのメモは落とし、残りを到着順に連結する。
    coVerify {
      stopDao.update(
        first.copy(arrivalTime = Date(0L), departureTime = Date(500_000L), note = "入口\nゾウ舎"),
      )
    }
  }

  @Test
  fun mergeStops_differentPlaces_isRefused() = runTest {
    // 場所が混ざるとどれが正か決められないので、まとめずに null を返す。
    coEvery { stopDao.getByIds(listOf(100L, 101L)) } returns listOf(
      stopEntity(100L, 10L, 0L, 60_000L),
      stopEntity(101L, 11L, 120_000L, 300_000L),
    )

    assertNull(stops.mergeStops(listOf(100L, 101L)))

    coVerify(exactly = 0) { stopDao.deleteByIds(any()) }
    coVerify(exactly = 0) { stopDao.update(any()) }
  }

  @Test
  fun mergeStops_singleStop_isNoOp() = runTest {
    assertNull(stops.mergeStops(listOf(100L)))

    coVerify(exactly = 0) { stopDao.getByIds(any()) }
    coVerify(exactly = 0) { stopDao.deleteByIds(any()) }
  }

  @Test
  fun undoLastStopChange_afterMerge_restoresAbsorbedAndRevertsSurvivor() = runTest {
    val first = stopEntity(100L, 10L, 0L, 60_000L)
    val second = stopEntity(101L, 10L, 120_000L, 300_000L)
    coEvery { stopDao.getByIds(listOf(100L, 101L)) } returns listOf(first, second)

    stops.mergeStops(listOf(100L, 101L))
    val undone = stops.undoLastStopChange()

    assertTrue(undone)
    coVerify { stopDao.insert(second) } // 吸収した側は元IDのまま戻す
    coVerify { stopDao.update(first) } // 残した側は変更前の期間・メモへ戻す
    // 場所は全員同じで残った1件が参照し続けるので、回収も復元も起きない。
    coVerify(exactly = 0) { placeDao.insert(any()) }
  }

  @Test
  fun updateStopNote_trimsWhitespace() = runTest {
    stops.updateStopNote(100L, "  限定パフェが美味しかった  ")
    coVerify { stopDao.updateNote(100L, "限定パフェが美味しかった") }
  }

  @Test
  fun updateStopNote_blankBecomesNull() = runTest {
    stops.updateStopNote(100L, "   ")
    coVerify { stopDao.updateNote(100L, null) }
  }

  @Test
  fun addManualStop_forceNewPlace_insertsNewPlaceWithoutCoordinateDedup() = runTest {
    coEvery { placeDao.insert(any()) } returns 42L
    coEvery { stopDao.insert(any()) } returns 100L

    stops.addManualStop(1L, 35.0, 139.0, Date(), Date(), null, null, forceNewPlace = true)

    // 座標同定（近傍30mの再利用）を通らず、新しい place を挿入する。
    coVerify { placeDao.insert(match { it.latitude == 35.0 && it.longitude == 139.0 }) }
    coVerify(exactly = 0) { placeDao.getInBounds(any(), any(), any(), any()) }
    coVerify { stopDao.insert(match { it.placeId == 42L }) }
  }
}
