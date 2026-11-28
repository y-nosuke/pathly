package com.pathly.presentation.plans

import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlanActuals
import com.pathly.domain.model.PlanTrackLink
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlanRepository
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

/** 計画に結び付いた経路の 1 本（詳細のシートの「実際のお出掛け」の行）。 */
data class LinkedTrack(
  val track: GpsTrack,
  /** 結び付き方（記録の開始で選んだ・手で）。 */
  val source: PlanTrackSource,
)

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
)

/**
 * 計画の詳細の「計画と実績」の組み立て。結び付きは保存済みの計画のもの（一度も保存していない計画には無い）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PlanDetailActuals(
  /** 保存済みの計画の id（一度も保存していなければ 0）。 */
  savedPlanId: Flow<Long>,
  private val planRepository: PlanRepository,
  private val gpsTrackRepository: GpsTrackRepository,
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
  ) { links, starts, visits, tracks ->
    val linked = links.mapTo(LinkedHashSet()) { it.trackId }
    val byId = tracks.associateBy { it.id }
    PlanActualsState(
      linked = links.mapNotNull { link -> byId[link.trackId]?.let { LinkedTrack(it, link.source) } }
        .sortedByDescending { it.track.startTime },
      linkedTrackIds = linked,
      visitedPlaceIds = PlanActuals.visitedPlaceIds(linked, visits),
      recordingTrackId = PlanActuals.recordingTrackId(linked, starts),
    )
  }.stateIn(scope, SharingStarted.Eagerly, PlanActualsState())

  /** 記録中の経路（結び付いているときだけ）。記録の更新に合わせて線が伸びる。 */
  val liveTrack: StateFlow<GpsTrack?> = state.map { it.recordingTrackId }.distinctUntilChanged().flatMapLatest { id ->
    if (id == null) flowOf(null) else gpsTrackRepository.getActiveTrackRealtime().map { it?.takeIf { t -> t.id == id } }
  }.stateIn(scope, SharingStarted.Eagerly, null)

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
