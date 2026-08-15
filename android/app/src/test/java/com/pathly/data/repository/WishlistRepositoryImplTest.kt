package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.WishlistEntity
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.repository.PlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class WishlistRepositoryImplTest {

  private val wishlistDao = mockk<WishlistDao>(relaxed = true)
  private val placeDao = mockk<PlaceDao>(relaxed = true)
  private val placeResolutionDao = mockk<PlaceResolutionDao>(relaxed = true)
  private val googlePlaceDao = mockk<GooglePlaceDao>(relaxed = true)
  private val stopDao = mockk<StopDao>(relaxed = true)
  private val placeRepository = mockk<PlaceRepository>(relaxed = true)
  private val placesTextSearcher = mockk<PlacesTextSearcher>(relaxed = true)
  private val repository = WishlistRepositoryImpl(
    wishlistDao,
    placeDao,
    placeResolutionDao,
    googlePlaceDao,
    stopDao,
    placeRepository,
    placesTextSearcher,
  )

  @Test
  fun deletePlace_snapshotsThenDeletes_andUndoRestoresPlaceWishlistResolutionAndGoogle() = runTest {
    val place = PlaceEntity(id = 7L, name = "カフェ", latitude = 35.0, longitude = 139.0)
    val wishlist = WishlistEntity(id = 3L, placeId = 7L, priority = 1)
    val resolution = PlaceResolutionEntity(7L, Date(0L))
    val google = GooglePlaceEntity(7L, "gp-x", "カフェ", "住所")
    coEvery { placeDao.getById(7L) } returns place
    coEvery { wishlistDao.getByPlaceId(7L) } returns wishlist
    coEvery { placeResolutionDao.getByPlace(7L) } returns resolution
    coEvery { googlePlaceDao.getByPlace(7L) } returns google

    repository.deletePlace(7L)
    coVerify { placeDao.deleteById(7L) }

    val undone = repository.undoLastPlaceDeletion()

    assertTrue(undone)
    // 元IDのまま place → Google データ → 解決ログ → wishlist の順に復元する。
    coVerify { placeDao.insert(place) }
    coVerify { googlePlaceDao.upsert(google) }
    coVerify { placeResolutionDao.upsert(resolution) }
    coVerify { wishlistDao.insert(wishlist) }
  }

  @Test
  fun deletePlace_withoutWishlistOrResolution_restoresPlaceOnly() = runTest {
    val place = PlaceEntity(id = 8L, latitude = 35.0, longitude = 139.0)
    coEvery { placeDao.getById(8L) } returns place
    coEvery { wishlistDao.getByPlaceId(8L) } returns null
    coEvery { placeResolutionDao.getByPlace(8L) } returns null
    coEvery { googlePlaceDao.getByPlace(8L) } returns null

    repository.deletePlace(8L)
    repository.undoLastPlaceDeletion()

    coVerify { placeDao.insert(place) }
    coVerify(exactly = 0) { wishlistDao.insert(any()) }
    coVerify(exactly = 0) { placeResolutionDao.upsert(any()) }
    coVerify(exactly = 0) { googlePlaceDao.upsert(any()) }
  }

  @Test
  fun deletePlace_missingPlace_isNoOp() = runTest {
    coEvery { placeDao.getById(9L) } returns null

    repository.deletePlace(9L)

    coVerify(exactly = 0) { placeDao.deleteById(any()) }
    assertFalse(repository.undoLastPlaceDeletion()) // 控えが無いので何もしない
  }

  @Test
  fun undoLastPlaceDeletion_withoutPriorDeletion_returnsFalse() = runTest {
    assertFalse(repository.undoLastPlaceDeletion())
  }

  @Test
  fun linkPlaceToGoogle_writesGoogleDataResolutionAndAdoptsCoordinates() = runTest {
    val result = PlaceSearchResult(
      googlePlaceId = "gp-42",
      name = "清瀧神社",
      address = "千葉県浦安市…",
      category = "神社",
      latitude = 35.65,
      longitude = 139.9,
    )

    repository.linkPlaceToGoogle(7L, result)

    // 施設情報を google_places に上書き保存する。
    coVerify {
      googlePlaceDao.upsert(
        match {
          it.placeId == 7L &&
            it.googlePlaceId == "gp-42" &&
            it.name == "清瀧神社" &&
            it.address == "千葉県浦安市…" &&
            it.category == "神社"
        },
      )
    }
    // 解決記録を残す（以後 Nearby を叩かない）。
    coVerify { placeResolutionDao.upsert(match { it.placeId == 7L }) }
    // 暫定座標を施設の正確な座標へ置き換える。
    coVerify { placeDao.updateCoordinates(7L, 35.65, 139.9, any()) }
  }

  @Test
  fun registerPlace_withKnownDetails_savesThemWithoutRefetching() = runTest {
    val known = PlaceSearchResult(
      googlePlaceId = "gp-42",
      name = "清瀧神社",
      address = "千葉県浦安市…",
      category = "神社",
      latitude = 35.65,
      longitude = 139.9,
    )
    coEvery { placeRepository.findOrCreateByGooglePlaceId("gp-42", any(), any(), any()) } returns (7L to false)
    coEvery { googlePlaceDao.getByPlace(7L) } returns null

    repository.registerPlace(35.65, 139.9, name = null, googlePlaceId = "gp-42", knownDetails = known)

    // 取得済みなので Google を引き直さない（オフラインでも施設情報が欠けない）。
    coVerify(exactly = 0) { placesTextSearcher.fetch(any()) }
    coVerify {
      googlePlaceDao.upsert(
        match { it.placeId == 7L && it.name == "清瀧神社" && it.category == "神社" },
      )
    }
    coVerify { placeResolutionDao.upsert(match { it.placeId == 7L }) }
  }

  @Test
  fun registerPlace_whenFetchFails_keepsPoiNameAsGoogleName() = runTest {
    coEvery { placeRepository.findOrCreateByGooglePlaceId("gp-9", any(), any(), any()) } returns (7L to false)
    coEvery { googlePlaceDao.getByPlace(7L) } returns null
    // オフライン等で施設情報が取れない。
    coEvery { placesTextSearcher.fetch("gp-9") } returns null

    repository.registerPlace(35.0, 139.0, name = null, googlePlaceId = "gp-9", googleName = "スタバ")

    // 住所・カテゴリは欠けても、タップ時に分かっている名前は残す（未命名にしない）。
    coVerify { googlePlaceDao.upsert(match { it.placeId == 7L && it.googlePlaceId == "gp-9" && it.name == "スタバ" }) }
    // Google が付けた名前を places.name（自分で付けた名前）には書かない。
    coVerify(exactly = 0) { placeDao.updateName(any(), any(), any()) }
  }

  @Test
  fun registerPlace_withKnownDetailsOfAnotherFacility_refetches() = runTest {
    val other = PlaceSearchResult(
      googlePlaceId = "gp-other",
      name = "別の店",
      address = null,
      category = null,
      latitude = 35.0,
      longitude = 139.0,
    )
    coEvery { placeRepository.findOrCreateByGooglePlaceId("gp-42", any(), any(), any()) } returns (7L to false)
    coEvery { googlePlaceDao.getByPlace(7L) } returns null

    repository.registerPlace(35.65, 139.9, name = null, googlePlaceId = "gp-42", knownDetails = other)

    // 別施設の情報を取り違えて焼き込まない。
    coVerify { placesTextSearcher.fetch("gp-42") }
  }
}
