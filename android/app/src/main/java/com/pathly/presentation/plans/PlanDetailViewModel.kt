package com.pathly.presentation.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanActuals
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanPlace
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.model.RemovedPlanItem
import com.pathly.domain.model.newPlanDetail
import com.pathly.domain.model.sameContentAs
import com.pathly.domain.model.withDates
import com.pathly.domain.model.withDayRemoved
import com.pathly.domain.model.withItemMovedToDay
import com.pathly.domain.model.withItemRemoved
import com.pathly.domain.model.withItemRestored
import com.pathly.domain.model.withItemUpdated
import com.pathly.domain.model.withMemoAdded
import com.pathly.domain.model.withOrder
import com.pathly.domain.model.withPlacesAdded
import com.pathly.domain.model.withTitleAndNote
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.PlaceSearchRepository
import com.pathly.domain.repository.PlanRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.places.PlaceEditActions
import com.pathly.presentation.tracking.MapSearchMode
import com.pathly.presentation.tracking.RegionMarkerActions
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

/**
 * 計画の詳細で編集している中身（→ docs/specs/plans.md の「保存と下書き」）。
 *
 * 編集は [detail]（画面の中の下書き）にだけ入り、「保存」で計画に、「下書きに残す」で下書きに書く。
 */
data class PlanEditState(
  /** 今の中身。 */
  val detail: PlanDetail,
  /** 保存済みの中身（一度も保存していなければ null）。 */
  val saved: PlanDetail?,
  /** 最後に書いた中身（保存か下書き）。戻るときに聞くかの基準。 */
  val persisted: PlanDetail,
  /** 下書きの id と保存した日時（下書きが無ければ null）。 */
  val draftId: Long? = null,
  val draftUpdatedAt: Date? = null,
  /**
   * 日付の変更で「結び付きを外す」を選んだ経路（見た目では外し、「保存」で実際に外す）。結び付きの変更も編集に数える。
   */
  val unlinkTrackIds: Set<Long> = emptySet(),
  /** 最後に書いた（下書きに残した）ときの [unlinkTrackIds]。 */
  val persistedUnlinkTrackIds: Set<Long> = emptySet(),
) {
  val isNew: Boolean get() = saved == null

  /** 保存できる（保存済みと違う・外す経路がある。新しい計画は中身があれば）。 */
  val canSave: Boolean get() = saved?.let { !detail.sameContentAs(it) || unlinkTrackIds.isNotEmpty() } ?: (draftId != null || !detail.sameContentAs(persisted))

  /** 戻るときに聞く（最後に書いた中身・外す経路から変えた）。 */
  val hasUnsavedChanges: Boolean get() = !detail.sameContentAs(persisted) || unlinkTrackIds != persistedUnlinkTrackIds
}

/** 計画の詳細の読み込みの状態。 */
sealed interface PlanDetailState {
  data object Loading : PlanDetailState

  /** 計画も下書きも無い（消した）。 */
  data object Missing : PlanDetailState

  data class Loaded(val edit: PlanEditState) : PlanDetailState
}

/** 計画の詳細から出す一言（スナックバー）や、画面を離れる合図。 */
sealed interface PlanDetailEvent {
  /** 行を外した（取り消しで戻せる）。 */
  data class ItemRemoved(val label: String) : PlanDetailEvent

  data class Failed(val message: String) : PlanDetailEvent

  data class Message(val message: String) : PlanDetailEvent

  /** 新しく登録した場所を [day] 日目に足した（シートをその日へ寄せる）。 */
  data class PlaceAdded(val day: Int) : PlanDetailEvent

  /** 計画を消した（一覧に戻って取り消しを出す）。 */
  data class Deleted(val title: String) : PlanDetailEvent

  /** 保存・下書き・破棄を済ませて、画面を離れる。 */
  data object Leave : PlanDetailEvent
}

/** 計画の詳細（→ docs/specs/plans.md の「計画の詳細」）。 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
  private val planRepository: PlanRepository,
  private val savedStateHandle: SavedStateHandle,
  placeEditUseCase: PlaceEditUseCase,
  wishlistRepository: WishlistRepository,
  private val placeSearchRepository: PlaceSearchRepository,
  gpsTrackRepository: GpsTrackRepository,
  stopRepository: StopRepository,
  liveStopRepository: LiveStopRepository,
) : ViewModel() {

  private val logger = Logger("PlanDetailViewModel")
  private val planIdArg: Long get() = savedStateHandle.get<Long>(ARG_PLAN_ID) ?: 0L
  private val draftIdArg: Long? get() = savedStateHandle.get<Long>(ARG_DRAFT_ID)?.takeIf { it > 0 }

  private val _state = MutableStateFlow<PlanDetailState>(PlanDetailState.Loading)

  val state: StateFlow<PlanDetailState> = _state.asStateFlow()

  /** 場所の名前とアンカーの座標（足した場所を名前・地図に出すのに使う）。 */
  val places: StateFlow<Map<Long, PlanPlace>> = planRepository.observePlanPlaces()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  /**
   * 計画と実績: 結び付いた経路・行った場所・記録中の経路・重ねる線（→ docs/specs/plans.md の「計画と実績」）。
   * 結び付きは保存済みの計画のもの。
   */
  internal val actuals = PlanDetailActuals(
    savedPlanId = _state.map { (it as? PlanDetailState.Loaded)?.edit?.saved?.plan?.id ?: 0L },
    hiddenTrackIds = _state.map { (it as? PlanDetailState.Loaded)?.edit?.unlinkTrackIds ?: emptySet() },
    planRepository = planRepository,
    gpsTrackRepository = gpsTrackRepository,
    stopRepository = stopRepository,
    liveStopRepository = liveStopRepository,
    scope = viewModelScope,
    computeDispatcher = { Dispatchers.Default },
  )

  /** 選んでいる日（null は「すべて」）。 */
  val selectedDay: StateFlow<Int?> = savedStateHandle.getStateFlow<Int?>(KEY_SELECTED_DAY, null)

  private val _events = MutableSharedFlow<PlanDetailEvent>(extraBufferCapacity = 4)
  val events: SharedFlow<PlanDetailEvent> = _events.asSharedFlow()

  // 足した行に振る仮の id（保存すると本当の id になる）。
  private var nextTempId = -1_000_000L
  private var lastRemoved: RemovedPlanItem? = null

  // 新しく登録した場所を足す日（登録のボタンを押した順）。登録の結果が届いた順に先頭から使う。
  // 1 つの値にすると、続けて登録したときに後の日で上書きされ、先の場所が違う日に入る。
  private val registerDays = ArrayDeque<Int>()

  // 地図に見えている登録済みの場所（番号のマーカー・レイヤーで出した場所）。近接確認でこれらは確かめない。
  private var placeIdsShownOnMap: Set<Long> = emptySet()

  /**
   * 計画の詳細から場所を新しく登録する（地図のタップ・検索 → docs/specs/plans.md の「場所を足す」）。
   * 登録の結果の場所（相乗り・近接確認での紐付けも）を、[registerDay] の日の末尾に足す。
   */
  val placeEdit = PlaceEditActions(
    scope = viewModelScope,
    placeEditUseCase = placeEditUseCase,
    wishlistRepository = wishlistRepository,
    placeSearchRepository = placeSearchRepository,
    shownOnMap = { { place -> place.placeId in placeIdsShownOnMap } },
    placeNameOf = { placeId -> places.value[placeId]?.name },
    // 登録の一言は出さず、足したことを [onPlaceRegistered] で知らせる。
    onMessage = {},
    onError = {
      // 登録が失敗したら、待っている足す日を捨てる（後の登録が古い日に入らないように）。
      registerDays.clear()
      _events.tryEmit(PlanDetailEvent.Failed(it))
    },
    onRegistered = ::onPlaceRegistered,
    onRegisterCancelled = { registerDays.removeFirstOrNull() },
  )

  /** 検索窓で出した地域の目印（保存しない。計画の画面を閉じれば消える）。 */
  val regionMarkers = RegionMarkerActions()

  private val selectedMapSearchMode = MutableStateFlow(MapSearchMode.Place)

  /** 検索窓の「場所｜地域」で最後に選んだほう（この画面の間だけ）。 */
  internal val mapSearchMode: StateFlow<MapSearchMode> = selectedMapSearchMode.asStateFlow()

  internal fun setMapSearchMode(mode: MapSearchMode) {
    selectedMapSearchMode.value = mode
  }

  init {
    load()
    keepPlacesFresh()
  }

  /**
   * 行が持つ場所の名前・座標を、場所の一覧の今の値に合わせる（場所の名前は場所のもの → docs/specs/plans.md）。
   * 施設を登録すると、名前は Google から引いた後で入るので、足した直後の行は名前が無く緯度経度で出てしまう。
   * 中身の比べ方（保存できるか）は場所の id しか見ないので、取り直しても「変更あり」にはならない。
   */
  private fun keepPlacesFresh() {
    viewModelScope.launch {
      places.collect { known ->
        _state.update { s ->
          if (s !is PlanDetailState.Loaded) return@update s
          val detail = s.edit.detail
          val items = detail.items.map { item ->
            val fresh = item.place?.let { known[it.placeId] }
            if (fresh != null && fresh != item.place) item.copy(place = fresh) else item
          }
          if (items == detail.items) s else s.copy(edit = s.edit.copy(detail = detail.copy(items = items)))
        }
      }
    }
  }

  private fun load() {
    viewModelScope.launch {
      val saved = if (planIdArg != 0L) planRepository.loadPlan(planIdArg) else null
      val draft = if (planIdArg != 0L || draftIdArg != null) planRepository.loadDraft(planIdArg, draftIdArg) else null
      _state.value = when {
        draft != null -> {
          PlanDetailState.Loaded(
            PlanEditState(draft.detail, saved, draft.detail, draft.draftId, draft.updatedAt, draft.unlinkTrackIds, draft.unlinkTrackIds),
          )
        }

        saved != null -> PlanDetailState.Loaded(PlanEditState(saved, saved, saved))

        planIdArg == 0L && draftIdArg == null -> newPlanDetail(NEW_PLAN_TITLE).let { PlanDetailState.Loaded(PlanEditState(it, null, it)) }

        else -> PlanDetailState.Missing
      }
    }
  }

  private val edit: PlanEditState? get() = (_state.value as? PlanDetailState.Loaded)?.edit

  private fun change(transform: (PlanDetail) -> PlanDetail) {
    _state.update { s -> if (s is PlanDetailState.Loaded) s.copy(edit = s.edit.copy(detail = transform(s.edit.detail))) else s }
  }

  fun selectDay(day: Int?) {
    savedStateHandle[KEY_SELECTED_DAY] = day
  }

  fun updateTitleAndNote(title: String, note: String?) = change { it.withTitleAndNote(title.trim().ifEmpty { NEW_PLAN_TITLE }, note?.trim()) }

  /** 日付のある保存済みの計画（日付の重なりを見るため）。 */
  private val datedPlans: StateFlow<List<Plan>> = planRepository.observeDatedPlans()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  /** [startDate] と [dayCount] にしたとき、日が重なるほかの保存済みの計画（無ければ null）。 */
  fun overlappingPlan(startDate: LocalDate?, dayCount: Int): Plan? {
    val plan = edit?.detail?.plan ?: return null
    return PlanActuals.overlappingPlan(plan.copy(startDate = startDate, dayCount = dayCount), datedPlans.value)
  }

  /** [startDate] と [dayCount] にしたとき、日付の外になる結び付いた経路（保存済みの計画だけ。日付なしなら無い）。 */
  fun tracksOutside(startDate: LocalDate?, dayCount: Int): List<GpsTrack> {
    val plan = edit?.takeIf { !it.isNew }?.detail?.plan ?: return emptyList()
    val range = PlanActuals.rangeMillis(plan.copy(startDate = startDate, dayCount = dayCount)) ?: return emptyList()
    // 外す予定で隠したものも含めて求める（日付を選び直したら、外すかを選び直すため）。
    return actuals.state.value.savedLinked.map { it.track }.filter { it.startTime.time !in range }
  }

  fun setDates(startDate: LocalDate?, dayCount: Int, unlinkOutside: Boolean = false) {
    val unlink = if (unlinkOutside) tracksOutside(startDate, dayCount).mapTo(HashSet()) { it.id } else emptySet()
    _state.update { s -> if (s is PlanDetailState.Loaded) s.copy(edit = s.edit.copy(detail = s.edit.detail.withDates(startDate, dayCount), unlinkTrackIds = unlink)) else s }
    // 選んでいた日が無くなったら「すべて」に戻す。
    val day = selectedDay.value
    if (day != null && day > dayCount) selectDay(null)
  }

  /** 地図に見えている登録済みの場所（近接確認で確かめない場所）を画面から受ける。 */
  fun setPlacesShownOnMap(placeIds: Set<Long>) {
    placeIdsShownOnMap = placeIds
  }

  /** 登録のシートで「登録して N日目に足す」を押した（登録が済んだら [day] に足す）。 */
  fun prepareRegister(day: Int) {
    registerDays.addLast(day)
  }

  /** 地図の上の検索窓を開いたとき: 検索のセッションを始める（候補〜確定を 1 セッションにまとめて課金を抑える）。 */
  fun startPlaceSearch() {
    placeSearchRepository.startSession()
  }

  /** 検索窓: 施設の候補。開いたときに地図に映っていた範囲 [bias] の中を優先する。 */
  suspend fun predictPlacesNear(query: String, bias: GeoBounds?): List<PlacePrediction> = placeSearchRepository.predict(query, bias)

  /** 検索窓: 地名の候補（地域だけに絞る）。 */
  suspend fun predictRegions(query: String, bias: GeoBounds?): List<PlacePrediction> = placeSearchRepository.predict(query, bias, regionsOnly = true)

  /** 地域へ移動: 選んだ地名の範囲と中心。取れなければ null。 */
  suspend fun fetchRegion(placeId: String): MapRegion? = placeSearchRepository.fetchRegion(placeId)

  /**
   * 登録した場所を計画に足す。場所の一覧（[places]）は DB の購読なので、登録した直後はまだ入っていないことがあり、
   * 入るのを待ってから足す。
   */
  private fun onPlaceRegistered(placeId: Long) {
    val day = registerDays.removeFirstOrNull() ?: return
    viewModelScope.launch {
      val place = withTimeoutOrNull(PLACE_WAIT_MILLIS) { places.first { placeId in it }[placeId] }
      val plan = edit?.detail?.plan
      if (place == null || plan == null) {
        _events.tryEmit(PlanDetailEvent.Failed("場所は登録しましたが、計画に足せませんでした"))
        return@launch
      }
      val target = day.coerceIn(1, plan.dayCount)
      // 行が増えたときにその日へ寄せるので、寄せる先を先に知らせる。
      _events.tryEmit(PlanDetailEvent.PlaceAdded(target))
      change { it.withPlacesAdded(target, listOf(place)) { nextTempId-- } }
      val where = if (plan.isDayTrip) "計画" else "${target}日目"
      // 名前の無い地点は名前が座標になるので、名前は添えない（足した行はシートで見える）。
      _events.tryEmit(PlanDetailEvent.Message("${where}に足しました"))
    }
  }

  fun addPlaces(day: Int, placeIds: List<Long>) {
    val known = places.value
    val added = placeIds.mapNotNull { known[it] }
    change { it.withPlacesAdded(day, added) { nextTempId-- } }
  }

  fun addMemo(day: Int, title: String, plannedTime: Int?, note: String?) = change { it.withMemoAdded(day, title.trim(), plannedTime, note?.trim(), nextTempId--) }

  fun updateItem(itemId: Long, title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?) = change { it.withItemUpdated(itemId, title?.trim(), plannedTime, stayMinutes, note?.trim()) }

  fun moveItemToDay(itemId: Long, day: Int) = change { it.withItemMovedToDay(itemId, day) }

  /** 並べ替えた結果（上から順の行の id と、その行が入った日）。 */
  fun reorder(order: List<Pair<Long, Int>>) = change { it.withOrder(order) }

  fun removeItem(itemId: Long) {
    val current = edit?.detail ?: return
    val (next, removed) = current.withItemRemoved(itemId)
    if (removed == null) return
    lastRemoved = removed
    change { next }
    _events.tryEmit(PlanDetailEvent.ItemRemoved(removed.item.label))
  }

  fun undoRemoveItem() {
    val removed = lastRemoved ?: return
    lastRemoved = null
    change { it.withItemRestored(removed) }
  }

  /** [day] 日目を消す（行は前の日の末尾へ）。 */
  fun removeDay(day: Int) {
    change { it.withDayRemoved(day) }
    val selected = selectedDay.value
    if (selected != null && selected >= day) selectDay(null)
  }

  /** 保存する。[leave] なら済んだら画面を離れる。 */
  fun save(leave: Boolean = false) {
    val e = edit ?: return
    // 下書きの間にほかの計画が同じ日に保存されていることがあるので、保存の前にも日付の重なりを見る。
    val plan = e.detail.plan
    overlappingPlan(plan.startDate, plan.dayCount)?.let { other ->
      _events.tryEmit(PlanDetailEvent.Failed("日付が「${other.title}」と重なるので保存できません。日付と日数を変えてください"))
      return
    }
    io("保存できませんでした") {
      val planId = planRepository.savePlan(e.detail, e.draftId)
      // 日付の変更で「外す」を選んだ結び付きを外す。
      e.unlinkTrackIds.forEach { planRepository.unlinkTrack(planId, it) }
      // 新しい計画を保存したら、この画面はその計画を指す（アプリが裏で落とされて戻ったときに、保存した計画を開くように）。
      savedStateHandle[ARG_PLAN_ID] = planId
      savedStateHandle[ARG_DRAFT_ID] = 0L
      val saved = planRepository.loadPlan(planId) ?: return@io
      _state.value = PlanDetailState.Loaded(PlanEditState(saved, saved, saved))
      if (leave) _events.tryEmit(PlanDetailEvent.Leave) else _events.tryEmit(PlanDetailEvent.Message("保存しました"))
    }
  }

  /** 下書きに残す。[leave] なら済んだら画面を離れ、そうでなければ編集を続ける。 */
  fun saveDraft(leave: Boolean = false) {
    val e = edit ?: return
    io("下書きに残せませんでした") {
      val draftId = planRepository.saveDraft(e.draftId, e.detail, e.unlinkTrackIds)
      savedStateHandle[ARG_DRAFT_ID] = draftId
      // 書いている間に続けた編集は残す（detail は今のまま、persisted だけ書いた中身にする）。
      _state.update { s -> if (s is PlanDetailState.Loaded) s.copy(edit = s.edit.copy(persisted = e.detail, persistedUnlinkTrackIds = e.unlinkTrackIds, draftId = draftId, draftUpdatedAt = Date())) else s }
      _events.tryEmit(if (leave) PlanDetailEvent.Leave else PlanDetailEvent.Message("下書きに残しました"))
    }
  }

  /** 下書きを捨てて、保存済みの中身に戻す（一度も保存していなければ画面を離れる）。 */
  fun discardDraft() {
    val e = edit ?: return
    io("下書きを捨てられませんでした") {
      e.draftId?.let { planRepository.deleteDraft(it) }
      val saved = e.saved
      if (saved == null) {
        _events.tryEmit(PlanDetailEvent.Leave)
      } else {
        _state.value = PlanDetailState.Loaded(PlanEditState(saved, saved, saved))
        _events.tryEmit(PlanDetailEvent.Message("下書きを捨てました"))
      }
    }
  }

  /**
   * 経路を手で結び付ける・外す（保存を待たず、すぐ入る → docs/specs/plans.md の「計画と実績」）。
   * 一度も保存していない計画には結び付けられない（計画がまだ無いため。画面は入口を出さない）。
   */
  fun setTrackLinked(trackId: Long, linked: Boolean) {
    val planId = edit?.saved?.plan?.id ?: return
    // 外す予定で隠していた経路を結び付け直したら、外す予定からも外す（保存で外れないように）。
    if (linked) {
      _state.update { s -> if (s is PlanDetailState.Loaded) s.copy(edit = s.edit.copy(unlinkTrackIds = s.edit.unlinkTrackIds - trackId)) else s }
    }
    io(if (linked) "経路を結び付けられませんでした" else "結び付きを外せませんでした") {
      if (linked) planRepository.linkTrack(planId, trackId, PlanTrackSource.MANUAL) else planRepository.unlinkTrack(planId, trackId)
    }
  }

  /** 計画を消す（保存済みのとき）。 */
  fun deletePlan() {
    val e = edit ?: return
    val saved = e.saved ?: return
    io("削除できませんでした") {
      planRepository.deletePlan(saved.plan.id)
      _events.tryEmit(PlanDetailEvent.Deleted(e.detail.plan.title))
    }
  }

  private fun io(failure: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.e("Plan operation failed: $failure", e)
        _events.tryEmit(PlanDetailEvent.Failed(failure))
      }
    }
  }

  companion object {
    const val ARG_PLAN_ID = "planId"
    const val ARG_DRAFT_ID = "draftId"
    private const val KEY_SELECTED_DAY = "selectedDay"

    /** 登録した場所が場所の一覧に入るのを待つ長さ。 */
    private const val PLACE_WAIT_MILLIS = 5_000L
  }
}
