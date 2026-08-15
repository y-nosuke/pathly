package com.pathly.data.repository

import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.SmoothedPointEntity
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
  private val stopDao = mockk<StopDao>(relaxed = true)
  private val repository = GpsTrackRepositoryImpl(
    gpsTrackDao,
    gpsPointDao,
    smoothedPointDao,
    stopDao,
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

  // ---- 総移動距離の焼き込み（履歴一覧が点をロードせずに済むようにする） ----

  @Test
  fun updateSmoothed_whenFinal_persistsTotalDistance() = runTest {
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 0

    repository.updateSmoothedForTrack(1L, isFinal = true)

    val meters = slot<Double>()
    coVerify { gpsTrackDao.updateTotalDistance(1L, capture(meters)) }
    // 0.0001度 × 5区間 ≒ 55.6m（緯度1度 ≒ 111km）。桁が合っていることを確認する。
    assertEquals(55.6, meters.captured, 1.0)
  }

  @Test
  fun updateSmoothed_whenNotFinal_doesNotTouchTotalDistance() = runTest {
    // 記録中は距離を焼き込まない（確定していないため）。一覧は都度計算にフォールバックする。
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(6)
    coEvery { smoothedPointDao.countByTrack(1L) } returns 0

    repository.updateSmoothedForTrack(1L, isFinal = false)

    coVerify(exactly = 0) { gpsTrackDao.updateTotalDistance(any(), any()) }
  }

  @Test
  fun updateSmoothed_whenFinalWithTooFewPoints_persistsZero() = runTest {
    // 点が1つ以下でも未計算のまま残さない（一覧が毎回フォールバック計算に落ちないように）。
    coEvery { gpsPointDao.getPointsByTrackIdSync(1L) } returns straightPoints(1)

    repository.updateSmoothedForTrack(1L, isFinal = true)

    coVerify { gpsTrackDao.updateTotalDistance(1L, 0.0) }
  }

  @Test
  fun backfill_usesPersistedSmoothedPoints_withoutResmoothing() = runTest {
    // v11 以前の経路。保存済みの補正後点列があるので、生点は読まずにそれで距離を出す。
    coEvery { gpsTrackDao.getFinishedTrackIdsWithoutDistance() } returns listOf(42L)
    coEvery { smoothedPointDao.getByTrack(42L) } returns (0 until 6).map { i ->
      SmoothedPointEntity(
        trackId = 42L,
        seq = i,
        latitude = 35.0 + i * 0.0001,
        longitude = 139.0,
        timestamp = Date(i * 10_000L),
      )
    }

    repository.backfillMissingDistances()

    val meters = slot<Double>()
    coVerify { gpsTrackDao.updateTotalDistance(42L, capture(meters)) }
    assertEquals(55.6, meters.captured, 1.0)
    coVerify(exactly = 0) { gpsPointDao.getPointsByTrackIdSync(42L) }
  }

  @Test
  fun backfill_whenNothingMissing_doesNothing() = runTest {
    coEvery { gpsTrackDao.getFinishedTrackIdsWithoutDistance() } returns emptyList()

    repository.backfillMissingDistances()

    coVerify(exactly = 0) { gpsTrackDao.updateTotalDistance(any(), any()) }
  }
}
