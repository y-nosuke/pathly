package com.pathly.presentation.plans

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.PlaceSearchRepository
import com.pathly.domain.repository.PlanRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/** 計画の詳細から新しい場所を登録して足す（→ docs/specs/plans.md の「場所を足す」）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val planRepository = mockk<PlanRepository>(relaxed = true)
  private val useCase = mockk<PlaceEditUseCase>(relaxed = true)
  private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
  private val placeSearchRepository = mockk<PlaceSearchRepository>(relaxed = true)

  // 場所の一覧（DB の購読）。登録した直後はまだ入っていない。
  private val places = MutableStateFlow<Map<Long, PlanPlace>>(emptyMap())
  private val newPlace = PlanPlace(placeId = 9L, name = "新しい店", latitude = 35.0, longitude = 139.0)
  private val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    every { planRepository.observePlanPlaces() } returns places
    coEvery { planRepository.loadPlan(1L) } returns PlanDetail(Plan(1L, "鎌倉", null, null, 3, Date(0), Date(0)), emptyList())
    coEvery { planRepository.loadDraft(any(), any()) } returns null
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = PlanDetailViewModel(
    planRepository,
    SavedStateHandle(mapOf(PlanDetailViewModel.ARG_PLAN_ID to 1L)),
    useCase,
    wishlistRepository,
    placeSearchRepository,
  )

  private fun registerReturns(result: PlaceEditUseCase.RegisterResult) {
    coEvery {
      useCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returns result
  }

  private fun PlanDetailViewModel.register() = placeEdit.registerPlaceWithNearbyCheck(35.0, 139.0, "新しい店", true, Priority.MEDIUM, false, null, null)

  private val PlanDetailViewModel.addedPlaces: List<Pair<Long, Int>>
    get() = (state.value as PlanDetailState.Loaded).edit.detail.items.mapNotNull { item -> item.place?.let { it.placeId to item.dayIndex } }

  private fun TestScope.events(viewModel: PlanDetailViewModel): List<PlanDetailEvent> {
    val events = mutableListOf<PlanDetailEvent>()
    backgroundScope.launch(testDispatcher) { viewModel.events.collect { events += it } }
    return events
  }

  @Test
  fun `登録した場所が場所の一覧に入るのを待って、選んだ日の末尾に足す`() = runTest(testDispatcher) {
    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false))
    val viewModel = viewModel()
    val events = events(viewModel)
    advanceUntilIdle()

    viewModel.prepareRegister(2)
    viewModel.register()
    // 時間を進めると待つのが時間切れになるので、今動けるものだけ動かす。
    runCurrent()
    assertTrue("一覧に入るまでは足さない", viewModel.addedPlaces.isEmpty())

    places.value = mapOf(9L to newPlace)
    runCurrent()

    assertEquals(listOf(9L to 2), viewModel.addedPlaces)
    assertTrue(PlanDetailEvent.PlaceAdded(2) in events)
    assertTrue(PlanDetailEvent.Message("2日目に足しました") in events)
    assertTrue("保存するまで計画には入らない（下書き）", (viewModel.state.value as PlanDetailState.Loaded).edit.canSave)
  }

  @Test
  fun `近くの登録済みの場所を使うと、その場所を足す`() = runTest(testDispatcher) {
    registerReturns(PlaceEditUseCase.RegisterResult.NearbyFound(nearby))
    places.value = mapOf(5L to PlanPlace(5L, "隣の店", 35.0, 139.0))
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.prepareRegister(1)
    viewModel.register()
    advanceUntilIdle()
    viewModel.placeEdit.confirmNearbyLink()
    advanceUntilIdle()

    assertEquals(listOf(5L to 1), viewModel.addedPlaces)
  }

  @Test
  fun `地図に見えている場所は近接確認で確かめない`() = runTest(testDispatcher) {
    var shown: ((RegisteredPlace) -> Boolean)? = null
    coEvery {
      useCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } answers {
      @Suppress("UNCHECKED_CAST")
      shown = args[8] as (RegisteredPlace) -> Boolean
      PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false)
    }
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.setPlacesShownOnMap(setOf(5L))
    viewModel.register()
    advanceUntilIdle()

    assertTrue(shown!!(nearby))
    assertTrue(!shown!!(nearby.copy(placeId = 6L)))
  }

  @Test
  fun `場所が一覧に入らなければ足せなかったと知らせる`() = runTest(testDispatcher) {
    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false))
    val viewModel = viewModel()
    val events = events(viewModel)
    advanceUntilIdle()

    viewModel.prepareRegister(1)
    viewModel.register()
    advanceTimeBy(6_000)
    advanceUntilIdle()

    assertTrue(viewModel.addedPlaces.isEmpty())
    assertTrue(PlanDetailEvent.Failed("場所は登録しましたが、計画に足せませんでした") in events)
  }

  @Test
  fun `続けて登録しても、それぞれ押したときの日に足す`() = runTest(testDispatcher) {
    coEvery {
      useCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returnsMany listOf(
      PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false),
      PlaceEditUseCase.RegisterResult.Registered(5L, alreadyExisted = false),
    )
    places.value = mapOf(9L to newPlace, 5L to PlanPlace(5L, "隣の店", 35.0, 139.0))
    val viewModel = viewModel()
    advanceUntilIdle()

    // 1 つ目の結果が届く前に 2 つ目を押す。
    viewModel.prepareRegister(2)
    viewModel.register()
    viewModel.prepareRegister(3)
    viewModel.register()
    advanceUntilIdle()

    assertEquals(listOf(9L to 2, 5L to 3), viewModel.addedPlaces)
  }

  @Test
  fun `近接確認を閉じたら、その日は次の登録に使わない`() = runTest(testDispatcher) {
    registerReturns(PlaceEditUseCase.RegisterResult.NearbyFound(nearby))
    places.value = mapOf(9L to newPlace)
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.prepareRegister(2)
    viewModel.register()
    advanceUntilIdle()
    viewModel.placeEdit.dismissNearbyPrompt()

    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false))
    viewModel.prepareRegister(3)
    viewModel.register()
    advanceUntilIdle()

    assertEquals(listOf(9L to 3), viewModel.addedPlaces)
  }

  @Test
  fun `足す日を決めずに登録したものは足さない`() = runTest(testDispatcher) {
    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false))
    places.value = mapOf(9L to newPlace)
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.register()
    advanceUntilIdle()

    assertTrue(viewModel.addedPlaces.isEmpty())
  }
}
