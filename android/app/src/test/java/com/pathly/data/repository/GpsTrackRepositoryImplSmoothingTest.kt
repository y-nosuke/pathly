package com.pathly.data.repository

import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.util.EncryptionHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/** [GpsTrackRepositoryImpl] の補正保存ロジック（確定プレフィックスの差分INSERT）を検証する。 */
class GpsTrackRepositoryImplSmoothingTest {

  private val gpsTrackDao = mockk<GpsTrackDao>(relaxed = true)
  private val gpsPointDao = mockk<GpsPointDao>(relaxed = true)
  private val smoothedPointDao = mockk<SmoothedPointDao>(relaxed = true)
  private val encryptionHelper = mockk<EncryptionHelper>(relaxed = true)
  private val repository = GpsTrackRepositoryImpl(
    gpsTrackDao,
    gpsPointDao,
    smoothedPointDao,
    encryptionHelper,
  )

  // まっすぐ北へ等速で進む点列（ジャンプ除外に引っかからない）。
  private fun straightPoints(count: Int) = (0 until count).map { i ->
    GpsPointEntity(
      id = (i + 1).toLong(),
      trackId = 1L,
      latitude = 35.0 + i * 0.0001,
      longitude = 139.0,
      accuracy = 5f,
      timestamp = Date(i * 10_000L),
    )
  }

  @Test
  fun updateSmoothed_whenNotFinal_persistsPrefixExcludingTrailingHalf() = runTest {
    // window=5 → half=2。6点なら確定は 6-2=4 点（seq 0..3）。
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 0

    repository.updateSmoothedForTrack(1L, isFinal = false)

    val slot = slot<List<SmoothedPointEntity>>()
    coVerify { smoothedPointDao.insertAll(capture(slot)) }
    assertEquals(4, slot.captured.size)
    assertEquals(listOf(0, 1, 2, 3), slot.captured.map { it.seq })
  }

  @Test
  fun updateSmoothed_whenFinal_persistsAllRemaining() = runTest {
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 0

    repository.updateSmoothedForTrack(1L, isFinal = true)

    val slot = slot<List<SmoothedPointEntity>>()
    coVerify { smoothedPointDao.insertAll(capture(slot)) }
    assertEquals(6, slot.captured.size)
    assertEquals(listOf(0, 1, 2, 3, 4, 5), slot.captured.map { it.seq })
  }

  @Test
  fun updateSmoothed_onlyInsertsNewlyFinalizedDelta() = runTest {
    // すでに4点保存済み → isFinal で残り2点（seq 4,5）だけ追記する。
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 4

    repository.updateSmoothedForTrack(1L, isFinal = true)

    val slot = slot<List<SmoothedPointEntity>>()
    coVerify { smoothedPointDao.insertAll(capture(slot)) }
    assertEquals(listOf(4, 5), slot.captured.map { it.seq })
  }

  @Test
  fun updateSmoothed_whenNothingNewlyFinalized_doesNotInsert() = runTest {
    // 6点・非最終なら確定は4点。すでに4点保存済みなら追記なし。
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 4

    repository.updateSmoothedForTrack(1L, isFinal = false)

    coVerify(exactly = 0) { smoothedPointDao.insertAll(any()) }
  }

  @Test
  fun recomputeSmoothed_deletesThenRepersistsAll() = runTest {
    coEvery { gpsTrackDao.getTrackById(1L) } returns null // 非アクティブ扱い → isFinal=true
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 0

    repository.recomputeSmoothed(1L)

    coVerify { smoothedPointDao.deleteByTrack(1L) }
    val slot = slot<List<SmoothedPointEntity>>()
    coVerify { smoothedPointDao.insertAll(capture(slot)) }
    assertEquals(6, slot.captured.size)
  }
}
