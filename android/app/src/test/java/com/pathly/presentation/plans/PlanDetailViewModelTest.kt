package com.pathly.presentation.plans

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanTrackLink
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StoredPlanDraft
import com.pathly.domain.model.TrackStart
import com.pathly.domain.model.TrackVisit
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.PlaceSearchRepository
import com.pathly.domain.repository.PlanRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** 計画の詳細から新しい場所を登録して足す（→ docs/specs/plans.md の「場所を足す」）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val planRepository = mockk<PlanRepository>(relaxed = true)
  private val useCase = mockk<PlaceEditUseCase>(relaxed = true)
  private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
  private val placeSearchRepository = mockk<PlaceSearchRepository>(relaxed = true)
  private val gpsTrackRepository = mockk<GpsTrackRepository>(relaxed = true)
  private val stopRepository = mockk<StopRepository>(relaxed = true)
  private val liveStop = MutableStateFlow<Stop?>(null)
  private val liveStopRepository = mockk<LiveStopRepository>(relaxed = true).also { every { it.currentStop } returns liveStop }

  // 経路・立ち寄り・手の結び付き（DB の購読）。
  private val trackStarts = MutableStateFlow<List<TrackStart>>(emptyList())
  private val visits = MutableStateFlow<List<TrackVisit>>(emptyList())
  private val links = MutableStateFlow<List<PlanTrackLink>>(emptyList())
  private val datedPlans = MutableStateFlow<List<Plan>>(emptyList())
  private val tracks = MutableStateFlow<List<GpsTrack>>(emptyList())

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
    every { planRepository.observeTrackStarts() } returns trackStarts
    every { planRepository.observeTrackVisits() } returns visits
    every { planRepository.observePlanTracks(any()) } returns links
    every { planRepository.observeDatedPlans() } returns datedPlans
    every { planRepository.observeTrackPlans() } returns MutableStateFlow(emptyMap())
    every { gpsTrackRepository.getAllTracks() } returns tracks
    every { stopRepository.getStopsForTrack(any()) } returns MutableStateFlow(emptyList())
    every { gpsTrackRepository.getActiveTrackRealtime() } returns MutableStateFlow(null)
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
    gpsTrackRepository,
    stopRepository,
    liveStopRepository,
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
  fun `施設の名前が後から入ったら、足した行の名前も変わる`() = runTest(testDispatcher) {
    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false))
    // 登録した直後は施設の名前がまだ無い（緯度経度で出る）。
    places.value = mapOf(9L to newPlace.copy(name = "35.00000, 139.00000"))
    val viewModel = viewModel()
    advanceUntilIdle()
    viewModel.prepareRegister(1)
    viewModel.register()
    runCurrent()

    places.value = mapOf(9L to newPlace)
    runCurrent()

    val edit = (viewModel.state.value as PlanDetailState.Loaded).edit
    assertEquals("新しい店", edit.detail.items.single().place?.name)
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

  @Test
  fun visited_comesFromTheSavedLinks() = runTest(testDispatcher) {
    trackStarts.value = listOf(TrackStart(20L, Date(0), isActive = false), TrackStart(21L, Date(0), isActive = true))
    tracks.value = listOf(gpsTrack(20L, Date(0)), gpsTrack(21L, Date(0)))
    visits.value = listOf(TrackVisit(20L, 7L), TrackVisit(21L, 8L), TrackVisit(22L, 9L))
    val vm = viewModel()
    backgroundScope.launch { vm.actuals.state.collect {} }
    advanceUntilIdle()
    assertEquals(emptySet<Long>(), vm.actuals.state.value.visitedPlaceIds)

    links.value = listOf(PlanTrackLink(20L, 1L, PlanTrackSource.START), PlanTrackLink(21L, 1L, PlanTrackSource.MANUAL))
    advanceUntilIdle()

    val state = vm.actuals.state.value
    assertEquals("結び付いた経路の立ち寄りだけ", setOf(7L, 8L), state.visitedPlaceIds)
    assertEquals(21L, state.recordingTrackId)
    assertEquals(setOf(PlanTrackSource.START, PlanTrackSource.MANUAL), state.linked.map { it.source }.toSet())
  }

  @Test
  fun changingDates_asksToUnlinkTracksOutside_andUnlinksThemOnSave() = runTest(testDispatcher) {
    val day = LocalDate.of(2026, 11, 30)
    coEvery { planRepository.loadPlan(1L) } returns PlanDetail(Plan(1L, "鎌倉", null, day, 1, Date(0), Date(0)), emptyList())
    coEvery { planRepository.savePlan(any(), any()) } returns 1L
    val onDay = Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant())
    tracks.value = listOf(gpsTrack(20L, onDay))
    links.value = listOf(PlanTrackLink(20L, 1L, PlanTrackSource.START))
    val vm = viewModel()
    backgroundScope.launch { vm.actuals.state.collect {} }
    advanceUntilIdle()

    assertEquals("同じ日なら外にならない", emptyList<Long>(), vm.tracksOutside(day, 1).map { it.id })
    assertEquals(listOf(20L), vm.tracksOutside(day.plusDays(7), 1).map { it.id })
    assertEquals("日付なしなら聞かない", emptyList<Long>(), vm.tracksOutside(null, 1).map { it.id })

    vm.setDates(day.plusDays(7), 1, unlinkOutside = true)
    advanceUntilIdle()
    coVerify(exactly = 0) { planRepository.unlinkTrack(any(), any()) }
    // 保存の前から見た目では外れる。日付を選び直すときは、隠したものも含めて聞く。
    assertEquals(emptyList<Long>(), vm.actuals.state.value.linked.map { it.track.id })
    assertEquals(listOf(20L), vm.tracksOutside(day.plusDays(7), 1).map { it.id })

    vm.save()
    advanceUntilIdle()
    coVerify { planRepository.unlinkTrack(1L, 20L) }
  }

  @Test
  fun unlinkChoice_isKeptInTheDraft_andAppliedWhenSavedFromTheDraft() = runTest(testDispatcher) {
    val day = LocalDate.of(2026, 11, 30)
    val saved = PlanDetail(Plan(1L, "鎌倉", null, day, 1, Date(0), Date(0)), emptyList())
    coEvery { planRepository.loadPlan(1L) } returns saved
    coEvery { planRepository.saveDraft(any(), any(), any()) } returns 5L
    coEvery { planRepository.savePlan(any(), any()) } returns 1L
    tracks.value = listOf(gpsTrack(20L, Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant())))
    links.value = listOf(PlanTrackLink(20L, 1L, PlanTrackSource.START))
    val vm = viewModel()
    backgroundScope.launch { vm.actuals.state.collect {} }
    advanceUntilIdle()

    // 「外す」を選んで下書きに残すと、下書きにも外す経路が入る。
    vm.setDates(day.plusDays(7), 1, unlinkOutside = true)
    vm.saveDraft()
    advanceUntilIdle()
    coVerify { planRepository.saveDraft(any(), any(), setOf(20L)) }
    coVerify(exactly = 0) { planRepository.unlinkTrack(any(), any()) }

    // 下書きから開き直して保存すると外れる。
    coEvery { planRepository.loadDraft(1L, any()) } returns StoredPlanDraft(5L, saved.copy(plan = saved.plan.copy(startDate = day.plusDays(7))), Date(0), setOf(20L))
    val reopened = viewModel()
    advanceUntilIdle()
    reopened.save()
    advanceUntilIdle()
    coVerify { planRepository.unlinkTrack(1L, 20L) }
  }

  @Test
  fun hiddenTracks_comeBack_whenTheChoiceIsUndone() = runTest(testDispatcher) {
    val day = LocalDate.of(2026, 11, 30)
    coEvery { planRepository.loadPlan(1L) } returns PlanDetail(Plan(1L, "鎌倉", null, day, 1, Date(0), Date(0)), emptyList())
    coEvery { planRepository.savePlan(any(), any()) } returns 1L
    tracks.value = listOf(gpsTrack(20L, Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant())))
    links.value = listOf(PlanTrackLink(20L, 1L, PlanTrackSource.START))
    val vm = viewModel()
    backgroundScope.launch { vm.actuals.state.collect {} }
    advanceUntilIdle()

    vm.setDates(day.plusDays(7), 1, unlinkOutside = true)
    advanceUntilIdle()
    assertEquals(0, vm.actuals.state.value.linked.size)

    // 日付を戻せば、外す予定も無くなって見える。
    vm.setDates(day, 1)
    advanceUntilIdle()
    assertEquals(listOf(20L), vm.actuals.state.value.linked.map { it.track.id })

    // 外す予定のものを結び付け直したら、保存しても外さない。
    vm.setDates(day.plusDays(7), 1, unlinkOutside = true)
    vm.setTrackLinked(20L, linked = true)
    vm.save()
    advanceUntilIdle()
    coVerify(exactly = 0) { planRepository.unlinkTrack(any(), any()) }
  }

  @Test
  fun keepingTracksOutside_doesNotUnlink() = runTest(testDispatcher) {
    val day = LocalDate.of(2026, 11, 30)
    coEvery { planRepository.loadPlan(1L) } returns PlanDetail(Plan(1L, "鎌倉", null, day, 1, Date(0), Date(0)), emptyList())
    coEvery { planRepository.savePlan(any(), any()) } returns 1L
    tracks.value = listOf(gpsTrack(20L, Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant())))
    links.value = listOf(PlanTrackLink(20L, 1L, PlanTrackSource.MANUAL))
    val vm = viewModel()
    backgroundScope.launch { vm.actuals.state.collect {} }
    advanceUntilIdle()

    vm.setDates(day.plusDays(7), 1, unlinkOutside = false)
    vm.save()
    advanceUntilIdle()

    coVerify(exactly = 0) { planRepository.unlinkTrack(any(), any()) }
  }

  @Test
  fun saving_isRefused_whenTheDatesOverlapAnotherMultiDayPlan() = runTest(testDispatcher) {
    val day = LocalDate.of(2026, 11, 30)
    datedPlans.value = listOf(Plan(2L, "京都旅行", null, day.plusDays(1), 3, Date(0), Date(0)))
    val vm = viewModel()
    advanceUntilIdle()

    assertEquals("京都旅行", vm.overlappingPlan(day, 3)?.title)
    assertEquals("最後の日に始まるのはよい", null, vm.overlappingPlan(day, 2))
    assertEquals("日帰りはよい", null, vm.overlappingPlan(day.plusDays(2), 1))

    vm.setDates(day, 3)
    vm.save()
    advanceUntilIdle()
    coVerify(exactly = 0) { planRepository.savePlan(any(), any()) }
  }

  @Test
  fun setTrackLinked_writesByHand() = runTest(testDispatcher) {
    val vm = viewModel()
    advanceUntilIdle()

    vm.setTrackLinked(30L, linked = true)
    vm.setTrackLinked(31L, linked = false)
    advanceUntilIdle()

    coVerify { planRepository.linkTrack(1L, 30L, PlanTrackSource.MANUAL) }
    coVerify { planRepository.unlinkTrack(1L, 31L) }
  }

  private fun gpsTrack(id: Long, start: Date) = GpsTrack(id = id, startTime = start, endTime = start, isActive = false, createdAt = start, updatedAt = start)
}
