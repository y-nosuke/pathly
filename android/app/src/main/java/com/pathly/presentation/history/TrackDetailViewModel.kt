package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPending
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.places.PlaceDeleteUndo
import com.pathly.presentation.places.PlaceEditActions
import com.pathly.presentation.stops.ManualStopActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * 詳細画面の立ち寄り表示・編集と、補正後軌跡の表示。
 * 開いても自動検出はしない。検出は記録中（自動）と「再解析」（追加提案・非破壊）、
 * 命名は加えて「場所を取得」ボタンで行う。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackDetailViewModel @Inject constructor(
  private val placeRepository: PlaceRepository,
  private val gpsTrackRepository: GpsTrackRepository,
  private val wishlistRepository: WishlistRepository,
  private val settingsRepository: SettingsRepository,
  private val placeEditUseCase: PlaceEditUseCase,
  private val addManualStopUseCase: AddManualStopUseCase,
  // 「Googleで情報を取得」の名前検索フォールバック用（場所詳細と同じ編集機能を出すため）。
  private val placesTextSearcher: PlacesTextSearcher,
) : ViewModel() {

  private val _stops = MutableStateFlow<List<Stop>>(emptyList())
  val stops: StateFlow<List<Stop>> = _stops.asStateFlow()

  /** 「登録済みの場所」を地図に出すか（履歴詳細の画面別トグル）。 */
  val showRegisteredPlaces: StateFlow<Boolean> =
    settingsRepository.showRegisteredPlaces(MapSurface.HISTORY)

  /** 地図に出す登録済みの場所（全 place）。トグルON時に描画する。 */
  val registeredPlaces: StateFlow<List<RegisteredPlace>> = placeRepository.observeRegisteredPlaces()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun toggleShowRegisteredPlaces() {
    settingsRepository.setShowRegisteredPlaces(MapSurface.HISTORY, !showRegisteredPlaces.value)
  }

  /** この画面の地図の「登録済みの場所」の絞り込み（地図ごとに独立。場所一覧とも別）。 */
  val mapPlaceFilter: StateFlow<MapPlaceFilter> = settingsRepository.mapPlaceFilter(MapSurface.HISTORY)

  fun setMapPlaceFilter(filter: MapPlaceFilter) {
    settingsRepository.setMapPlaceFilter(MapSurface.HISTORY, filter)
  }

  // 近接確認で「見えている」とみなす場所（表示 ON で、地図の絞り込みに合うか、この経路の立ち寄りとして
  // マーカーが出ている）。立ち寄りの場所は登録済みのマーカーには出さないが、番号のマーカーで見えている。
  // 押した時点の条件で判断する。
  private fun shownOnMap(): (RegisteredPlace) -> Boolean {
    val show = showRegisteredPlaces.value
    val filter = mapPlaceFilter.value
    val stopPlaceIds = (stops.value.map { it.place.id } + listOfNotNull(currentStop.value?.place?.id)).toSet()
    return { place -> show && (place.placeId in stopPlaceIds || filter.matches(place)) }
  }

  // 保存済みの補正後点列を反映したトラック。読み込み時に一度だけ設定する。
  private val _displayTrack = MutableStateFlow<GpsTrack?>(null)
  val displayTrack: StateFlow<GpsTrack?> = _displayTrack.asStateFlow()

  // 削除失敗などの一時メッセージ（表示したらクリアする）。
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  // 再解析の候補（一覧に無い立ち寄り＋表示名）。null=非表示、空リスト=「候補なし」を表示。
  private val _reanalyzeCandidates = MutableStateFlow<List<StopCandidate>?>(null)
  val reanalyzeCandidates: StateFlow<List<StopCandidate>?> = _reanalyzeCandidates.asStateFlow()

  private val loadedTrackId = MutableStateFlow<Long?>(null)

  /**
   * 記録中に開いたときの「立ち寄り中（ライブ）」。表示中のトラックが記録中の当該トラックのときだけ流す
   * （他トラックを開いていれば null）。地図にだけ出す（一覧・保存には出さない）。
   */
  val currentStop: StateFlow<Stop?> = combine(placeRepository.currentStop, loadedTrackId) { stop, id ->
    stop?.takeIf { it.trackId == id }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** 未取得（googlePlaceId 無し）の place 件数。「場所を取得」ボタンの表示に使う。 */
  val unresolvedCount: StateFlow<Int> = loadedTrackId
    .flatMapLatest { id -> if (id == null) flowOf(0) else placeRepository.unresolvedCountForTrack(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  /** この経路の、同じ施設の確認待ち（→ adr/0037）。経路詳細を開くと「確認待ち」として出す。 */
  val sameFacilityPending: StateFlow<List<SameFacilityPending>> = loadedTrackId
    .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else placeRepository.observeSameFacilityPending(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  // 確認待ちを 1 件ずつ聞くダイアログを出しているか。
  private val _sameFacilityDialogOpen = MutableStateFlow(false)
  val sameFacilityDialogOpen: StateFlow<Boolean> = _sameFacilityDialogOpen.asStateFlow()

  /** 場所の登録・編集・削除・まとめる（場所タブ・記録画面と共有）。結果も失敗も一時メッセージに出す。 */
  val placeEdit = PlaceEditActions(
    scope = viewModelScope,
    placeEditUseCase = placeEditUseCase,
    wishlistRepository = wishlistRepository,
    placesTextSearcher = placesTextSearcher,
    shownOnMap = { shownOnMap() },
    placeNameOf = { placeId -> registeredPlaces.value.firstOrNull { it.placeId == placeId }?.displayName },
    onMessage = { _message.value = it },
    onError = { _message.value = it },
  )

  /** この経路に立ち寄りを手で足す・付け直す（記録画面と共有）。 */
  val manualStop = ManualStopActions(
    scope = viewModelScope,
    addManualStopUseCase = addManualStopUseCase,
    placeRepository = placeRepository,
    trackId = { loadedTrackId.value },
    shownOnMap = ::shownOnMap,
    onMessage = { _message.value = it },
    onError = { _message.value = it },
  )

  init {
    // 確認待ちを全部答え終えたら閉じる（開いたままだと、あとで裏の名前付けが見つけた分で勝手に開く）。
    viewModelScope.launch {
      sameFacilityPending.map { it.isEmpty() }.distinctUntilChanged().collect { empty ->
        if (empty) _sameFacilityDialogOpen.value = false
      }
    }
  }

  private var collectJob: Job? = null

  fun load(trackId: Long) {
    if (loadedTrackId.value == trackId) return
    loadedTrackId.value = trackId

    // 開いても検出はしない。保存済みの立ち寄りと補正後軌跡を表示するだけ。
    viewModelScope.launch { _displayTrack.value = gpsTrackRepository.getTrackById(trackId) }

    collectJob?.cancel()
    collectJob = viewModelScope.launch {
      placeRepository.getStopsForTrack(trackId).collect { _stops.value = it }
    }
  }

  /**
   * 経路そのものの名前を付ける・直す（空なら未命名に戻す）。
   *
   * 表示中のトラックは読み込み時の一度きりで持っているので、保存後は手元の値も合わせる
   * （再読込を待たずに見出しへ反映させるため）。
   */
  fun renameTrack(name: String) {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      try {
        gpsTrackRepository.renameTrack(trackId, name)
        _displayTrack.value = _displayTrack.value?.copy(name = name.trim().ifBlank { null })
      } catch (e: Exception) {
        _message.value = "名前の変更に失敗しました: ${e.message}"
      }
    }
  }

  fun updatePlaceName(placeId: Long, name: String) {
    viewModelScope.launch { placeRepository.updatePlaceName(placeId, name) }
  }

  /** 立ち寄り（訪問）のメモを更新する（stop 単位。空文字なら消す）。 */
  fun updateStopNote(stopId: Long, note: String?) {
    viewModelScope.launch { placeRepository.updateStopNote(stopId, note) }
  }

  /**
   * 未取得の場所を Places で取り直す（手動・googlePlaceId 無しが対象）。
   *
   * 同じ施設を既に他の場所が持っていると施設情報は付かない（→ adr/0025）。その場で操作したので、
   * まとめるか別のまま残すかをその場で聞く（→ adr/0037）。
   */
  fun resolveNames() {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      val skipped = placeRepository.resolveUnresolvedNames(trackId)
      if (skipped > 0) _sameFacilityDialogOpen.value = true
    }
  }

  /** 同じ施設の確認待ちを聞くダイアログを開く（「確認待ち」のチップ）。 */
  fun openSameFacilityDialog() {
    _sameFacilityDialogOpen.value = true
  }

  /** 「あとで」: 決めずに閉じる（確認待ちのまま残る）。 */
  fun dismissSameFacilityDialog() {
    _sameFacilityDialogOpen.value = false
  }

  /**
   * 確認待ちの場所を相手にまとめる（→ adr/0037）。スナックバーの「取り消す」で戻せる。
   * ダイアログは開いたままにし、残りがあれば次の 1 件を聞く。
   */
  fun mergeSameFacility(pending: SameFacilityPending) {
    placeEdit.mergeIntoSameFacility(pending.placeId, pending.placeName, pending.other)
  }

  /**
   * 立ち寄り（訪問）を削除する（1件でも複数でも同じ）。参照が無くなった場所は自動で場所ごと削除し、
   * 他の履歴で使われている／行きたい登録がある場所は「この訪問だけ」消して保持する。
   * 確認ダイアログは出さず即時削除し、画面側のスナックバーから [undoStopChange] で取り消せる。
   */
  fun deleteStops(stopIds: List<Long>) {
    if (stopIds.isEmpty()) return
    viewModelScope.launch { placeRepository.deleteStops(stopIds) }
  }

  /**
   * 立ち寄り（訪問）の滞在期間を手で直す。GPS 点・補正後の点は触らない（→ adr/0024）。
   * 他の立ち寄りと期間が重なっても（入れ子でも）そのまま保存する。
   */
  fun updateStopDuration(stopId: Long, arrivalTime: Date, departureTime: Date) {
    viewModelScope.launch { placeRepository.updateStopDuration(stopId, arrivalTime, departureTime) }
  }

  /**
   * 選んだ立ち寄り（訪問）を1件にまとめる。同じ場所への訪問が2件以上あるときだけ成立し、
   * 混ざっていれば何もしない。確認ダイアログは出さず即時実行し、画面側のスナックバーから
   * [undoStopChange] で取り消せる（削除と同じ流儀）。
   */
  fun mergeStops(stopIds: List<Long>) {
    if (stopIds.size < 2) return
    viewModelScope.launch {
      val result = placeRepository.mergeStops(stopIds)
      if (result == null) _message.value = "同じ場所への立ち寄りだけまとめられます"
    }
  }

  /**
   * 立ち寄りの行の旗から、その場所の「行きたい」をワンタップで付け外しする。
   * 付け外しの判断は DB の今の状態で行う（[PlaceEditUseCase.toggleWishlist]）。
   * 旗の見た目は [registeredPlaces] の更新で変わるので、成功時のメッセージは出さない（場所タブと同じ）。
   */
  fun toggleStopWishlist(placeId: Long) {
    viewModelScope.launch {
      try {
        placeEditUseCase.toggleWishlist(placeId)
      } catch (e: Exception) {
        _message.value = "行きたいの更新に失敗しました: ${e.message}"
      }
    }
  }

  /** 直近の削除・統合を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoStopChange() {
    viewModelScope.launch { placeRepository.undoLastStopChange() }
  }

  fun clearMessage() {
    _message.value = null
  }

  /**
   * 再解析: その経路を検出し直し、**既存の立ち寄りに無い候補**を地図のピンと候補オーバーレイに出す（非破壊）。
   * 実際の追加は [addStops] でユーザーが選んだ分だけ行う。
   */
  fun reanalyze() {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      _reanalyzeCandidates.value = placeRepository.detectMissingStops(trackId)
    }
  }

  /** 再解析の候補から選んだ立ち寄りだけを追加する。既存の立ち寄りには触れない。 */
  fun addStops(candidates: List<StopCandidate>) {
    val trackId = loadedTrackId.value ?: return
    _reanalyzeCandidates.value = null
    if (candidates.isEmpty()) return
    viewModelScope.launch { placeRepository.addStops(trackId, candidates) }
  }

  /** 再解析の候補表示を閉じる（追加しない）。 */
  fun dismissReanalyze() {
    _reanalyzeCandidates.value = null
  }
}
