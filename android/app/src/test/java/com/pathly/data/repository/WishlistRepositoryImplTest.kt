package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.WishlistEntity
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
  private val stopDao = mockk<StopDao>(relaxed = true)
  private val placeRepository = mockk<PlaceRepository>(relaxed = true)
  private val repository = WishlistRepositoryImpl(
    wishlistDao,
    placeDao,
    placeResolutionDao,
    stopDao,
    placeRepository,
  )

  @Test
  fun deletePlace_snapshotsThenDeletes_andUndoRestoresPlaceWishlistAndResolution() = runTest {
    val place = PlaceEntity(id = 7L, name = "カフェ", latitude = 35.0, longitude = 139.0)
    val wishlist = WishlistEntity(id = 3L, placeId = 7L, priority = 1)
    val resolution = PlaceResolutionEntity(7L, Date(0L), "gp-x")
    coEvery { placeDao.getById(7L) } returns place
    coEvery { wishlistDao.getByPlaceId(7L) } returns wishlist
    coEvery { placeResolutionDao.getByPlace(7L) } returns resolution

    repository.deletePlace(7L)
    coVerify { placeDao.deleteById(7L) }

    val undone = repository.undoLastPlaceDeletion()

    assertTrue(undone)
    // 元IDのまま place → 解決ログ → wishlist の順に復元する。
    coVerify { placeDao.insert(place) }
    coVerify { placeResolutionDao.upsert(resolution) }
    coVerify { wishlistDao.insert(wishlist) }
  }

  @Test
  fun deletePlace_withoutWishlistOrResolution_restoresPlaceOnly() = runTest {
    val place = PlaceEntity(id = 8L, latitude = 35.0, longitude = 139.0)
    coEvery { placeDao.getById(8L) } returns place
    coEvery { wishlistDao.getByPlaceId(8L) } returns null
    coEvery { placeResolutionDao.getByPlace(8L) } returns null

    repository.deletePlace(8L)
    repository.undoLastPlaceDeletion()

    coVerify { placeDao.insert(place) }
    coVerify(exactly = 0) { wishlistDao.insert(any()) }
    coVerify(exactly = 0) { placeResolutionDao.upsert(any()) }
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
}
