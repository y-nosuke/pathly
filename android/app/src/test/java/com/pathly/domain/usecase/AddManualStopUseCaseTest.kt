package com.pathly.domain.usecase

import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.PlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * 手動での立ち寄り追加を検証する。
 *
 * この判断はもともと TrackingScreen と TrackDetailScreen の Composable に重複しており
 * テストが無かった。集約したのでここで2画面分をまとめて守る。
 */
class AddManualStopUseCaseTest {

  private val placeRepository = mockk<PlaceRepository>(relaxed = true)
  private val useCase = AddManualStopUseCase(placeRepository)

  private val arrival = Date(1_000L)
  private val departure = Date(2_000L)

  @Before
  fun setup() {
    coEvery {
      placeRepository.addManualStop(any(), any(), any(), any(), any(), any(), any(), any())
    } returns 55L
    coEvery { placeRepository.addManualStopForPlace(any(), any(), any(), any()) } returns 66L
  }

  @Test
  fun `POIなら近接確認せず施設同定で追加する`() = runTest {
    val result = useCase.addWithNearbyCheck(
      trackId = 1L,
      latitude = 35.0,
      longitude = 139.0,
      arrivalTime = arrival,
      departureTime = departure,
      name = "カフェ",
      googlePlaceId = "gp-1",
      nearbyAlreadyVisible = false,
    )

    assertEquals(AddManualStopUseCase.AddResult.Added(55L), result)
    coVerify(exactly = 0) { placeRepository.findNearbyPlace(any(), any()) }
    coVerify { placeRepository.addManualStop(1L, 35.0, 139.0, arrival, departure, "カフェ", "gp-1", false) }
  }

  @Test
  fun `登録済みを地図表示中なら近接確認しない`() = runTest {
    val result = useCase.addWithNearbyCheck(
      trackId = 1L,
      latitude = 35.0,
      longitude = 139.0,
      arrivalTime = arrival,
      departureTime = departure,
      name = null,
      googlePlaceId = null,
      nearbyAlreadyVisible = true,
    )

    assertEquals(AddManualStopUseCase.AddResult.Added(55L), result)
    coVerify(exactly = 0) { placeRepository.findNearbyPlace(any(), any()) }
  }

  @Test
  fun `近くに既存があれば追加せず確認を返す`() = runTest {
    val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)
    coEvery { placeRepository.findNearbyPlace(any(), any()) } returns nearby

    val result = useCase.addWithNearbyCheck(
      trackId = 1L,
      latitude = 35.0,
      longitude = 139.0,
      arrivalTime = arrival,
      departureTime = departure,
      name = null,
      googlePlaceId = null,
      nearbyAlreadyVisible = false,
    )

    assertEquals(AddManualStopUseCase.AddResult.NearbyFound(nearby), result)
    // ユーザーが選ぶまで追加しない。
    coVerify(exactly = 0) { placeRepository.addManualStop(any(), any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `近くに既存が無ければそのまま追加する`() = runTest {
    coEvery { placeRepository.findNearbyPlace(any(), any()) } returns null

    val result = useCase.addWithNearbyCheck(
      trackId = 1L,
      latitude = 35.0,
      longitude = 139.0,
      arrivalTime = arrival,
      departureTime = departure,
      name = "名無し",
      googlePlaceId = null,
      nearbyAlreadyVisible = false,
    )

    assertEquals(AddManualStopUseCase.AddResult.Added(55L), result)
    // 座標同定に任せる（近くに何も無いので相乗りする相手もいない）。
    coVerify { placeRepository.addManualStop(1L, 35.0, 139.0, arrival, departure, "名無し", null, false) }
  }

  @Test
  fun `新規で追加を選んだら座標同定せず必ず新しい場所を作る`() = runTest {
    useCase.addAsNew(1L, 35.0, 139.0, arrival, departure, "新しい場所")

    coVerify { placeRepository.addManualStop(1L, 35.0, 139.0, arrival, departure, "新しい場所", null, true) }
  }

  @Test
  fun `紐付けを選んだら既存の場所へ訪問だけ足す`() = runTest {
    val result = useCase.addForExistingPlace(1L, placeId = 9L, arrivalTime = arrival, departureTime = departure)

    assertEquals(AddManualStopUseCase.AddResult.Added(66L), result)
    coVerify { placeRepository.addManualStopForPlace(1L, 9L, arrival, departure) }
    // 新しい場所は作らない。
    coVerify(exactly = 0) { placeRepository.addManualStop(any(), any(), any(), any(), any(), any(), any(), any()) }
  }
}
