package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.tracking.TrackingController
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.MapSurface
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
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.PlaceSearchRepository
import com.pathly.domain.repository.SettingsRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.places.PlaceDeleteUndo
import com.pathly.presentation.places.PlaceEditActions
import com.pathly.presentation.stops.ManualStopActions
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.StopEditActions
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
import kotlinx.coroutines.flow.takeWhile
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
  private val stopRepository: StopRepository,
  private val liveStopRepository: LiveStopRepository,
  private val gpsTrackRepository: GpsTrackRepository,
  private val wishlistRepository: WishlistRepository,
  private val settingsRepository: SettingsRepository,
  private val placeEditUseCase: PlaceEditUseCase,
  private val addManualStopUseCase: AddManualStopUseCase,
  // 「Googleで情報を取得」の名前検索フォールバック用（場所詳細と同じ編集機能を出すため）。
  private val placeSearchRepository: PlaceSearchRepository,
  private val trackDeletionQueue: TrackDeletionQueue,
  // 記録中の経路の「今ここを立ち寄り」に使う現在地（記録サービスが受けている最新の位置）。
  private val trackingController: TrackingController,
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
  val currentStop: StateFlow<Stop?> = combine(liveStopRepository.currentStop, loadedTrackId) { stop, id ->
    stop?.takeIf { it.trackId == id }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /**
   * 記録中の経路を開いているときの「今ここを立ち寄り」の対象（現在地）。記録が終わった経路・現在地がまだ取れていない
   * ときは null。記録画面の「今ここを立ち寄り」と同じく、地点を指していないので近くの施設候補から選ばせる。
   */
  val addStopHereTarget: StateFlow<ManualStopTarget?> = combine(trackingController.currentLocation, _displayTrack) { location, track ->
    if (location == null || track?.isActive != true) {
      null
    } else {
      ManualStopTarget(location.latitude, location.longitude, ManualStopOrigin.CurrentLocation)
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** 場所の登録・編集・削除・まとめる（場所タブ・記録画面と共有）。結果も失敗も一時メッセージに出す。 */
  val placeEdit = PlaceEditActions(
    scope = viewModelScope,
    placeEditUseCase = placeEditUseCase,
    wishlistRepository = wishlistRepository,
    placeSearchRepository = placeSearchRepository,
    shownOnMap = { shownOnMap() },
    placeNameOf = { placeId -> registeredPlaces.value.firstOrNull { it.placeId == placeId }?.displayName },
    onMessage = { _message.value = it },
    onError = { _message.value = it },
  )

  /** この経路に立ち寄りを手で足す・付け直す（記録画面と共有）。 */
  val manualStop = ManualStopActions(
    scope = viewModelScope,
    addManualStopUseCase = addManualStopUseCase,
    stopRepository = stopRepository,
    liveStopRepository = liveStopRepository,
    trackId = { loadedTrackId.value },
    shownOnMap = ::shownOnMap,
    onMessage = { _message.value = it },
    onError = { _message.value = it },
  )

  /** この経路の立ち寄りを直す・場所を取得する・同じ施設の確認待ち（記録画面と共有）。 */
  val stopEdit = StopEditActions(
    scope = viewModelScope,
    placeRepository = placeRepository,
    stopRepository = stopRepository,
    liveStopRepository = liveStopRepository,
    placeEditUseCase = placeEditUseCase,
    trackId = loadedTrackId,
    placeEdit = placeEdit,
    onError = { _message.value = it },
  )

  private var collectJob: Job? = null
  private var trackJob: Job? = null

  fun load(trackId: Long) {
    if (loadedTrackId.value == trackId) return
    loadedTrackId.value = trackId

    // 開いても検出はしない。保存済みの立ち寄りと補正後軌跡を表示するだけ。
    trackJob?.cancel()
    trackJob = viewModelScope.launch {
      val track = gpsTrackRepository.getTrackById(trackId)
      _displayTrack.value = track
      if (track?.isActive != true) return@launch
      // 記録中の経路は、記録の更新（点が増える・名前が変わる）に合わせて読み直す。開いた時点のままだと、記録を始めて
      // すぐ開いたときに点が 2 つ未満のまま残り、「地図で指して追加」が押せない・線や距離が伸びない。
      // 記録が終わったら、保存済みの補正後の形で読み直す。
      gpsTrackRepository.getActiveTrackRealtime()
        .takeWhile { it?.id == trackId }
        .collect { _displayTrack.value = it }
      _displayTrack.value = gpsTrackRepository.getTrackById(trackId)
    }

    collectJob?.cancel()
    collectJob = viewModelScope.launch {
      stopRepository.getStopsForTrack(trackId).collect { _stops.value = it }
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

  /**
   * この経路を消す予定にする（上のバーの ⋮）。消すのはスナックバーの「取り消す」を待ってから（[TrackDeletionQueue]）。
   * 呼んだ側は画面を戻る。
   */
  fun requestDeleteTrack() {
    val track = _displayTrack.value ?: return
    trackDeletionQueue.request(track)
  }

  /** 経路のお気に入りを付け外しする（履歴の一覧のハートと同じ）。手元の値も合わせて、⋮ の文言をすぐ変える。 */
  fun toggleFavorite() {
    val track = _displayTrack.value ?: return
    val favorite = !track.isFavorite
    viewModelScope.launch {
      try {
        gpsTrackRepository.setFavorite(track.id, favorite)
        _displayTrack.value = _displayTrack.value?.copy(isFavorite = favorite)
      } catch (e: Exception) {
        _message.value = "お気に入りの更新に失敗しました: ${e.message}"
      }
    }
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
      _reanalyzeCandidates.value = stopRepository.detectMissingStops(trackId)
    }
  }

  /** 再解析の候補から選んだ立ち寄りだけを追加する。既存の立ち寄りには触れない。 */
  fun addStops(candidates: List<StopCandidate>) {
    val trackId = loadedTrackId.value ?: return
    _reanalyzeCandidates.value = null
    if (candidates.isEmpty()) return
    viewModelScope.launch { stopRepository.addStops(trackId, candidates) }
  }

  /** 再解析の候補表示を閉じる（追加しない）。 */
  fun dismissReanalyze() {
    _reanalyzeCandidates.value = null
  }
}
