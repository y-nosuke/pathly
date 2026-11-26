package com.pathly.presentation.stops

import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** 立ち寄りを手で足す・付け直す部品（記録画面と経路詳細で共有）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualStopActionsTest {

  private val useCase = mockk<AddManualStopUseCase>(relaxed = true)
  private val stopRepository = mockk<StopRepository>(relaxed = true)
  private val liveStopRepository = mockk<LiveStopRepository>(relaxed = true)

  private val messages = mutableListOf<String>()
  private val errors = mutableListOf<String>()

  private val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)
  private val arrival = Date(1_000)
  private val departure = Date(2_000)

  private fun TestScope.actions(trackId: Long? = 7L) = ManualStopActions(
    scope = this,
    addManualStopUseCase = useCase,
    stopRepository = stopRepository,
    liveStopRepository = liveStopRepository,
    trackId = { trackId },
    shownOnMap = { { false } },
    onMessage = { messages += it },
    onError = { errors += it },
  )

  private fun addReturns(result: AddManualStopUseCase.AddResult) {
    coEvery {
      useCase.addWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returns result
  }

  private fun ManualStopActions.addAtPoint() = addManualStopWithNearbyCheck(35.0, 139.0, arrival, departure, "新しい場所", null, pointedOnMap = true)

  @Test
  fun `追加_足せたら知らせる`() = runTest {
    addReturns(AddManualStopUseCase.AddResult.Added(1L))
    val actions = actions()

    actions.addAtPoint()
    advanceUntilIdle()

    coVerify { useCase.addWithNearbyCheck(7L, 35.0, 139.0, arrival, departure, "新しい場所", null, any(), null, true) }
    assertNull(actions.nearbyStopPrompt.value)
    assertEquals(listOf("立ち寄りを追加しました"), messages)
  }

  @Test
  fun `追加_経路が無ければ何もしない`() = runTest {
    val actions = actions(trackId = null)

    actions.addAtPoint()
    actions.addManualStopForPlace(5L, arrival, departure)
    advanceUntilIdle()

    coVerify(exactly = 0) { useCase.addWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { useCase.addForExistingPlace(any(), any(), any(), any()) }
    assertTrue(messages.isEmpty())
  }

  @Test
  fun `追加_近くに既存があれば足さずに確認待ちにする`() = runTest {
    addReturns(AddManualStopUseCase.AddResult.NearbyFound(nearby))
    val actions = actions()

    actions.addAtPoint()
    advanceUntilIdle()

    val prompt = actions.nearbyStopPrompt.value
    assertEquals(nearby, prompt?.nearby)
    assertEquals(arrival, prompt?.arrivalTime)
    assertTrue("まだ何も知らせない", messages.isEmpty())
  }

  @Test
  fun `近接確認_紐付けを選べば既存の場所に足す`() = runTest {
    addReturns(AddManualStopUseCase.AddResult.NearbyFound(nearby))
    val actions = actions()
    actions.addAtPoint()
    advanceUntilIdle()

    actions.confirmNearbyStopLink()
    advanceUntilIdle()

    assertNull("確認は閉じる", actions.nearbyStopPrompt.value)
    coVerify { useCase.addForExistingPlace(7L, 5L, arrival, departure) }
    assertEquals(listOf("立ち寄りを追加しました"), messages)
  }

  @Test
  fun `近接確認_新規を選べば新しい場所で足す`() = runTest {
    addReturns(AddManualStopUseCase.AddResult.NearbyFound(nearby))
    val actions = actions()
    actions.addAtPoint()
    advanceUntilIdle()

    actions.confirmNearbyStopNew()
    advanceUntilIdle()

    assertNull("確認は閉じる", actions.nearbyStopPrompt.value)
    coVerify { useCase.addAsNew(7L, 35.0, 139.0, arrival, departure, "新しい場所") }
    assertEquals(listOf("立ち寄りを追加しました"), messages)
  }

  @Test
  fun `既存の場所に足すのに失敗したら知らせる`() = runTest {
    coEvery { useCase.addForExistingPlace(any(), any(), any(), any()) } throws IllegalStateException("boom")
    val actions = actions()

    actions.addManualStopForPlace(5L, arrival, departure)
    advanceUntilIdle()

    assertTrue(messages.isEmpty())
    assertEquals(listOf("立ち寄りの追加に失敗しました: boom"), errors)
  }

  @Test
  fun `付け直しに失敗したら知らせる`() = runTest {
    coEvery { stopRepository.reassignStopPlace(any(), any(), any()) } throws IllegalStateException("boom")

    actions().reassignStop(1L, null, "手入力")
    advanceUntilIdle()

    coVerify { stopRepository.reassignStopPlace(1L, null, "手入力") }
    assertEquals(listOf("場所の選び直しに失敗しました: boom"), errors)
  }
}
