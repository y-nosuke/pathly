package com.pathly.presentation.plans

import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlanActuals
import com.pathly.domain.model.PlanTrackLink
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.model.Stop
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.PlanRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** 計画に結び付いた経路の 1 本（詳細のシートの「実際のお出掛け」の行）。 */
data class LinkedTrack(
  val track: GpsTrack,
  /** 結び付き方（記録の開始で選んだ・手で）。 */
  val source: PlanTrackSource,
)

/**
 * 地図に出す実際の立ち寄り。[onRecording] は記録中の経路のもの、[live] は立ち寄り中（まだ確定していない）。
 * [number] は日ごとに着いた順の番号（1 から）、[date] は着いた日（端末の時刻帯）。
 */
data class ActualStop(val stop: Stop, val onRecording: Boolean, val live: Boolean, val number: Int = 0, val date: LocalDate? = null)

/** 日ごとに、着いた時刻の順に番号を振る（→ docs/specs/plans.md の「重ねて見る」）。 */
internal fun List<ActualStop>.numberedByDay(zone: ZoneId = ZoneId.systemDefault()): List<ActualStop> = map { it.copy(date = it.stop.arrivalTime.toInstant().atZone(zone).toLocalDate()) }
  .groupBy { it.date }
  .values
  .flatMap { sameDay -> sameDay.sortedWith(compareBy({ it.stop.arrivalTime }, { it.stop.id })).mapIndexed { i, s -> s.copy(number = i + 1) } }

/** 計画の詳細の「計画と実績」（→ docs/specs/plans.md）。どれも保存せず、読むたびに求める。 */
data class PlanActualsState(
  /** 結び付いた経路（新しい順）。 */
  val linked: List<LinkedTrack> = emptyList(),
  /** 結び付いた経路の id（結び付ける画面のチェック）。 */
  val linkedTrackIds: Set<Long> = emptySet(),
  /** 行った場所（結び付いた経路のどれかで立ち寄った場所）。 */
  val visitedPlaceIds: Set<Long> = emptySet(),
  /** 結び付いた経路のうち記録中のもの。 */
  val recordingTrackId: Long? = null,
  /** 保存済みの結び付きのすべて（外す予定で見た目から隠したものも含む。日付の外になる経路を求めるのに使う）。 */
  val savedLinked: List<LinkedTrack> = emptyList(),
)

/**
 * 計画の詳細の「計画と実績」の組み立て。結び付きは保存済みの計画のもの（一度も保存していない計画には無い）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PlanDetailActuals(
  /** 保存済みの計画の id（一度も保存していなければ 0）。 */
  savedPlanId: Flow<Long>,
  /** 日付の変更で「外す」を選んだ経路（保存まで見た目から隠す。実際に外すのは保存のとき）。 */
  hiddenTrackIds: Flow<Set<Long>>,
  private val planRepository: PlanRepository,
  private val gpsTrackRepository: GpsTrackRepository,
  private val stopRepository: StopRepository,
  private val liveStopRepository: LiveStopRepository,
  private val scope: CoroutineScope,
  // 線の点を読んで区間に分ける計算を主スレッドから外す。テストでは差し替える。
  private val computeDispatcher: () -> CoroutineDispatcher,
) {
  private val logger = Logger("PlanDetailActuals")

  private val links: Flow<List<PlanTrackLink>> = savedPlanId.distinctUntilChanged().flatMapLatest { id ->
    if (id == 0L) flowOf(emptyList()) else planRepository.observePlanTracks(id)
  }

  /** 経路が結び付いている計画（経路の id → 計画の id と名前）。結び付ける画面で、ほかの計画からの付け替えを添える。 */
  val trackPlans: StateFlow<Map<Long, Pair<Long, String>>> = planRepository.observeTrackPlans()
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

  /** すべての経路（結び付ける画面の一覧と、結び付いた経路の名前・距離）。 */
  val allTracks: StateFlow<List<GpsTrack>> = gpsTrackRepository.getAllTracks()
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val state: StateFlow<PlanActualsState> = combine(
    links,
    planRepository.observeTrackStarts(),
    planRepository.observeTrackVisits(),
    gpsTrackRepository.getAllTracks(),
    hiddenTrackIds,
  ) { links, starts, visits, tracks, hidden ->
    val byId = tracks.associateBy { it.id }
    val saved = links.mapNotNull { link -> byId[link.trackId]?.let { LinkedTrack(it, link.source) } }
      .sortedByDescending { it.track.startTime }
    // 外す予定のものは、保存の前から見た目では外す（シートの一覧・線・立ち寄りの印・「行った」）。
    val shown = saved.filter { it.track.id !in hidden }
    val linked = links.mapTo(LinkedHashSet()) { it.trackId } - hidden
    PlanActualsState(
      linked = shown,
      linkedTrackIds = linked,
      visitedPlaceIds = PlanActuals.visitedPlaceIds(linked, visits),
      recordingTrackId = PlanActuals.recordingTrackId(linked, starts),
      savedLinked = saved,
    )
  }.stateIn(scope, SharingStarted.Eagerly, PlanActualsState())

  /** 記録中の経路（結び付いているときだけ）。記録の更新に合わせて線が伸びる。 */
  val liveTrack: StateFlow<GpsTrack?> = state.map { it.recordingTrackId }.distinctUntilChanged().flatMapLatest { id ->
    if (id == null) flowOf(null) else gpsTrackRepository.getActiveTrackRealtime().map { it?.takeIf { t -> t.id == id } }
  }.stateIn(scope, SharingStarted.Eagerly, null)

  /**
   * 地図に出す立ち寄り（結び付いた経路の確定した立ち寄りと、記録中の「立ち寄り中」）。
   * 記録中の経路のものはいつも、過去の経路のものはレイヤーで「結び付いた経路」を重ねている間だけ出す（画面で絞る）。
   */
  val stops: StateFlow<List<ActualStop>> = state.map { it.linkedTrackIds to it.recordingTrackId }.distinctUntilChanged().flatMapLatest { (ids, recording) ->
    val confirmed: Flow<List<Stop>> = if (ids.isEmpty()) {
      flowOf(emptyList())
    } else {
      combine(ids.map { stopRepository.getStopsForTrack(it) }) { lists -> lists.flatMap { it } }
    }
    combine(confirmed, liveStopRepository.currentStop) { stops, live ->
      (
        stops.map { ActualStop(it, onRecording = it.trackId == recording, live = false) } +
          listOfNotNull(live?.takeIf { it.trackId in ids }?.let { ActualStop(it, onRecording = true, live = true) })
        ).numberedByDay()
    }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  private val _showTracks = MutableStateFlow(false)

  /** レイヤー［≋］の「結び付いた経路」を重ねているか（この画面の間だけ）。 */
  val showTracks: StateFlow<Boolean> = _showTracks.asStateFlow()

  private val _lines = MutableStateFlow<Map<Long, List<List<GpsPoint>>>>(emptyMap())

  /** 重ねる過去の経路の線（経路の id → 途切れていない区間）。記録中の経路は [liveTrack] で描く。 */
  val lines: StateFlow<Map<Long, List<List<GpsPoint>>>> = _lines.asStateFlow()

  init {
    // 重ねている間は、結び付いた経路の線を読んでおく（読んだものは取っておく）。
    scope.launch {
      combine(_showTracks, state.map { s -> s.linked.filter { it.track.id != s.recordingTrackId }.map { it.track.id } }.distinctUntilChanged()) { show, ids ->
        if (show) ids else emptyList()
      }.collect { ids -> ids.filter { it !in _lines.value }.forEach { loadLine(it) } }
    }
  }

  private suspend fun loadLine(trackId: Long) {
    try {
      val segments = withContext(computeDispatcher()) { gpsTrackRepository.getDisplaySegments(trackId) } ?: return
      _lines.update { it + (trackId to segments) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logger.e("Failed to load linked track line", e)
    }
  }

  fun toggleShowTracks() {
    _showTracks.update { !it }
  }
}
