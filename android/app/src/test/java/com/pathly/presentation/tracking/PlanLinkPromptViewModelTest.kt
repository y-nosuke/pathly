package com.pathly.presentation.tracking

import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlanRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class PlanLinkPromptViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val planRepository = mockk<PlanRepository>(relaxed = true)
  private val gpsTrackRepository = mockk<GpsTrackRepository>(relaxed = true)
  private val activeTrack = MutableStateFlow<GpsTrack?>(null)
  private val today = LocalDate.of(2026, 11, 30)

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    every { gpsTrackRepository.getActiveTrackRealtime() } returns activeTrack
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun plan(id: Long, start: LocalDate, days: Int = 1) = Plan(id, "計画$id", null, start, days, Date(0), Date(0))

  @Test
  fun linksTheFirstPlan_afterTheTrackIsCreated_andAsks() = runTest(testDispatcher) {
    coEvery { planRepository.plansOn(today) } returns listOf(plan(2, today), plan(1, today.minusDays(1), days = 3))
    val vm = PlanLinkPromptViewModel(planRepository, gpsTrackRepository)

    vm.onTrackingStarted(today, startedAtMillis = 0L)
    // advanceUntilIdle だと待ちの上限（15 秒）まで時計が進むので、今できることだけ進める。
    runCurrent()
    assertNull("経路ができるまでは聞かない", vm.prompt.value)

    activeTrack.value = GpsTrack(id = 40L, startTime = Date(0), endTime = null, isActive = true, createdAt = Date(0), updatedAt = Date(0))
    advanceUntilIdle()
    val prompt = vm.prompt.value!!
    assertEquals(40L, prompt.trackId)
    assertEquals("開始日の早い順", listOf(1L, 2L), prompt.plans.map { it.id })
    assertEquals("最初の計画を選んだ状態", 1L, prompt.linkedPlanId)
    coVerify { planRepository.linkTrack(1L, 40L, PlanTrackSource.START) }

    // 選び直すと付け替える。
    vm.choose(2L)
    advanceUntilIdle()
    assertNull(vm.prompt.value)
    coVerify { planRepository.linkTrack(2L, 40L, PlanTrackSource.START) }
  }

  @Test
  fun doesNotAsk_withoutTodaysPlans_orWhenTheTrackNeverStarts() = runTest(testDispatcher) {
    coEvery { planRepository.plansOn(today) } returns emptyList()
    val vm = PlanLinkPromptViewModel(planRepository, gpsTrackRepository)
    activeTrack.value = GpsTrack(id = 40L, startTime = Date(0), endTime = null, isActive = true, createdAt = Date(0), updatedAt = Date(0))
    vm.onTrackingStarted(today, startedAtMillis = 0L)
    advanceUntilIdle()
    assertNull(vm.prompt.value)

    // 開始できなかった（経路ができない）ときは、待つのをやめて聞かない。
    activeTrack.value = null
    coEvery { planRepository.plansOn(today) } returns listOf(plan(1, today))
    vm.onTrackingStarted(today, startedAtMillis = 0L)
    advanceTimeBy(20_000)
    advanceUntilIdle()
    assertNull(vm.prompt.value)
  }

  @Test
  fun unlink_removesTheFirstLink_andCloseKeepsIt() = runTest(testDispatcher) {
    coEvery { planRepository.plansOn(today) } returns listOf(plan(1, today))
    activeTrack.value = GpsTrack(id = 40L, startTime = Date(0), endTime = null, isActive = true, createdAt = Date(0), updatedAt = Date(0))
    val vm = PlanLinkPromptViewModel(planRepository, gpsTrackRepository)
    vm.onTrackingStarted(today, startedAtMillis = 0L)
    advanceUntilIdle()

    vm.close()
    advanceUntilIdle()
    assertNull(vm.prompt.value)
    coVerify(exactly = 0) { planRepository.unlinkTrack(any(), any()) }

    vm.onTrackingStarted(today, startedAtMillis = 0L)
    advanceUntilIdle()
    vm.unlink()
    advanceUntilIdle()
    coVerify { planRepository.unlinkTrack(1L, 40L) }
  }

  @Test
  fun anActiveTrackFromBeforeTheStart_isNotLinked() = runTest(testDispatcher) {
    coEvery { planRepository.plansOn(today) } returns listOf(plan(1, today))
    // 押す前から残っていた記録中の経路（開始が押した時刻より前）。
    activeTrack.value = GpsTrack(id = 30L, startTime = Date(1_000L), endTime = null, isActive = true, createdAt = Date(0), updatedAt = Date(0))
    val vm = PlanLinkPromptViewModel(planRepository, gpsTrackRepository)

    vm.onTrackingStarted(today, startedAtMillis = 100_000L)
    runCurrent()
    assertNull(vm.prompt.value)

    activeTrack.value = GpsTrack(id = 40L, startTime = Date(100_500L), endTime = null, isActive = true, createdAt = Date(0), updatedAt = Date(0))
    advanceUntilIdle()
    assertEquals(40L, vm.prompt.value!!.trackId)
  }

  @Test
  fun choosingTheSamePlan_doesNotWriteAgain() = runTest(testDispatcher) {
    coEvery { planRepository.plansOn(today) } returns listOf(plan(1, today))
    activeTrack.value = GpsTrack(id = 40L, startTime = Date(0), endTime = null, isActive = true, createdAt = Date(0), updatedAt = Date(0))
    val vm = PlanLinkPromptViewModel(planRepository, gpsTrackRepository)
    vm.onTrackingStarted(today, startedAtMillis = 0L)
    advanceUntilIdle()

    vm.choose(1L)
    advanceUntilIdle()

    coVerify(exactly = 1) { planRepository.linkTrack(any(), any(), any()) }
  }
}
