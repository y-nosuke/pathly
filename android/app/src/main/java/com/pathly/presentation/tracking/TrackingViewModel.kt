package com.pathly.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.data.tracking.TrackingController
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.places.PlaceEditActions
import com.pathly.presentation.stops.ManualStopActions
import com.pathly.presentation.stops.StopEditActions
import com.pathly.util.DateFormatters
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackingViewModel @Inject constructor(
  private val trackingController: TrackingController,
  private val gpsTrackRepository: GpsTrackRepository,
  private val placeRepository: PlaceRepository,
  private val stopRepository: StopRepository,
  private val liveStopRepository: LiveStopRepository,
  private val wishlistRepository: WishlistRepository,
  private val settingsRepository: SettingsRepository,
  private val placeEditUseCase: PlaceEditUseCase,
  private val addManualStopUseCase: AddManualStopUseCase,
  // 「Googleで情報を取得」の名前検索フォールバック用（場所詳細と同じ編集機能を出すため）。
  private val placesTextSearcher: PlacesTextSearcher,
) : ViewModel() {

  private val logger = Logger("TrackingViewModel")

  private val _uiState = MutableStateFlow(TrackingState())
  val uiState: StateFlow<TrackingState> = _uiState.asStateFlow()

  init {
    checkActiveTracking()
    checkLocationPermission()
    observeActiveTrack()
    observeCurrentStop()
    observeStops()
    observeRegisteredPlaces()
    observeLocationUpdates()
    observeUnexpectedDisconnect()
    observeFinalizing()
  }

  /**
   * サービスが予期せず切れたとき（サービスだけが落ちてプロセスは生きている状況）の後始末。
   *
   * **記録中のトラックは閉じない。** 以前はここで finishTrack していたが、それだと
   * START_STICKY による自己回復を自分で壊していた。サービスが再起動すると
   * restoreTrackingIfNeeded() が「アクティブなトラックがあれば続きを記録する」判断を
   * するのに、その手前でトラックを完了にしてしまうと対象が無くなり、再起動した
   * サービスはそのまま止まってしまう。
   *
   * 代わりに、OS がサービスを作り直す余地を与えてから実際の状態と突き合わせる。
   * 復帰していれば記録中に戻し、していなければ「中断された記録」としてユーザーに
   * 再開／完了を選ばせる（起動時と同じ導線）。
   */
  private fun observeUnexpectedDisconnect() {
    viewModelScope.launch {
      trackingController.unexpectedDisconnect.collect {
        logger.w("Service disconnected unexpectedly; leaving the track open for recovery")
        // 記録中表示だけ一旦下ろす。currentTrackId は残す（トラックはまだ生きており、
        // 地図の軌跡や立ち寄りの購読を切らないため）。
        _uiState.update { it.copy(isTracking = false) }
        delay(SERVICE_RESTART_GRACE_MS)
        reconcileTrackingState()
      }
    }
  }

  /**
   * 保存（確定）中かをサービスから受け取る。停止を押してから確定が終わるまでの間、
   * 画面はローディングを出してバックを塞ぐ（→ adr/0021）。
   */
  private fun observeFinalizing() {
    viewModelScope.launch {
      trackingController.isFinalizing.collect { finalizing ->
        _uiState.update { it.copy(isFinalizing = finalizing) }
      }
    }
  }

  /** 「登録済みの場所」トグルと全place を購読して地図に反映する（記録画面の画面別トグル）。 */
  private fun observeRegisteredPlaces() {
    viewModelScope.launch {
      settingsRepository.showRegisteredPlaces(MapSurface.RECORDING).collect { show ->
        _uiState.update { it.copy(showRegisteredPlaces = show) }
      }
    }
    viewModelScope.launch {
      placeRepository.observeRegisteredPlaces().collect { places ->
        _uiState.update { it.copy(registeredPlaces = places) }
      }
    }
  }

  fun toggleShowRegisteredPlaces() {
    settingsRepository.setShowRegisteredPlaces(MapSurface.RECORDING, !_uiState.value.showRegisteredPlaces)
  }

  /** この画面の地図の「登録済みの場所」の絞り込み（地図ごとに独立。場所一覧とも別）。 */
  val mapPlaceFilter: StateFlow<MapPlaceFilter> = settingsRepository.mapPlaceFilter(MapSurface.RECORDING)

  fun setMapPlaceFilter(filter: MapPlaceFilter) {
    settingsRepository.setMapPlaceFilter(MapSurface.RECORDING, filter)
  }

  // 近接確認で「見えている」とみなす場所（表示 ON で、地図の絞り込みに合うか、記録中の経路の立ち寄りとして
  // マーカーが出ている）。立ち寄りの場所は登録済みのマーカーには出さないが、番号のマーカーで見えている。
  // 押した時点の条件で判断する。
  private fun shownOnMap(): (RegisteredPlace) -> Boolean {
    val state = _uiState.value
    val filter = mapPlaceFilter.value
    val stopPlaceIds = (state.stops.map { it.place.id } + listOfNotNull(state.currentStop?.place?.id)).toSet()
    return { place -> state.showRegisteredPlaces && (place.placeId in stopPlaceIds || filter.matches(place)) }
  }

  /** 結果の一言は地図の上に少しのあいだ出す。 */
  private fun showMessage(message: String) {
    _uiState.update { it.copy(placeRegisteredMessage = message) }
  }

  private fun showError(message: String) {
    _uiState.update { it.copy(errorMessage = message) }
  }

  /** 場所の登録・編集・削除・まとめる（場所タブ・経路詳細と共有）。 */
  val placeEdit = PlaceEditActions(
    scope = viewModelScope,
    placeEditUseCase = placeEditUseCase,
    wishlistRepository = wishlistRepository,
    placesTextSearcher = placesTextSearcher,
    shownOnMap = { shownOnMap() },
    placeNameOf = { placeId -> _uiState.value.registeredPlaces.firstOrNull { it.placeId == placeId }?.displayName },
    onMessage = ::showMessage,
    onError = ::showError,
  )

  /** 記録中の経路に立ち寄りを手で足す・付け直す（経路詳細と共有）。 */
  val manualStop = ManualStopActions(
    scope = viewModelScope,
    addManualStopUseCase = addManualStopUseCase,
    stopRepository = stopRepository,
    liveStopRepository = liveStopRepository,
    trackId = { _uiState.value.currentTrackId },
    shownOnMap = ::shownOnMap,
    onMessage = ::showMessage,
    onError = ::showError,
  )

  /** 記録中の経路の立ち寄りを直す・場所を取得する・同じ施設の確認待ち（経路詳細と共有）。 */
  val stopEdit = StopEditActions(
    scope = viewModelScope,
    placeRepository = placeRepository,
    stopRepository = stopRepository,
    liveStopRepository = liveStopRepository,
    placeEditUseCase = placeEditUseCase,
    trackId = _uiState.map { it.currentTrackId },
    placeEdit = placeEdit,
    onError = ::showError,
  )

  /** 記録中の経路の名前を付ける・直す（空なら未命名に戻す）。表示は記録中の経路の購読で変わる。 */
  fun renameTrack(name: String) {
    val trackId = _uiState.value.currentTrackId ?: return
    viewModelScope.launch {
      try {
        gpsTrackRepository.renameTrack(trackId, name)
      } catch (e: Exception) {
        showError("名前の変更に失敗しました: ${e.message}")
      }
    }
  }

  /** 「地域へ移動」で出した地域の目印（複数・上限あり）。タブを行き来しても残る。 */
  val regionMarkers = RegionMarkerActions()

  private val selectedMapSearchMode = MutableStateFlow(MapSearchMode.Place)

  /** 検索窓の「場所｜地域」で最後に選んだほう。窓を開き直しても・タブを行き来しても残り、アプリが終わると「場所」に戻る。 */
  internal val mapSearchMode: StateFlow<MapSearchMode> = selectedMapSearchMode.asStateFlow()

  internal fun setMapSearchMode(mode: MapSearchMode) {
    selectedMapSearchMode.value = mode
  }

  private fun observeCurrentStop() {
    viewModelScope.launch {
      liveStopRepository.currentStop.collect { stop ->
        _uiState.update { it.copy(currentStop = stop) }
      }
    }
  }

  /** 記録中トラックが変わるたびに、その経路の確定済み立ち寄りを購読して地図マーカーに反映する。 */
  private fun observeStops() {
    viewModelScope.launch {
      _uiState.map { it.currentTrackId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
          if (id == null) flowOf(emptyList()) else stopRepository.getStopsForTrack(id)
        }
        .collect { stops ->
          _uiState.update { it.copy(stops = stops) }
        }
    }
  }

  private fun checkActiveTracking() {
    viewModelScope.launch { reconcileTrackingState() }
  }

  /**
   * DB のアクティブトラックとサービスの生死を突き合わせて、記録状態を決め直す。
   * 画面の初期化時と、予期せぬ切断からの復帰判定で共用する。
   */
  private suspend fun reconcileTrackingState() {
    val activeTrack = gpsTrackRepository.getActiveTrack()
    // サービスの実際の状態を読み直す（記録中なら接続も張り直す）。
    val serviceTracking = trackingController.reattach()

    when {
      activeTrack == null -> {
        // アクティブなトラックがない場合
        _uiState.update {
          it.copy(
            isTracking = false,
            currentTrackId = null,
          )
        }
      }

      serviceTracking -> {
        // 記録中プロセスが生存している → サービスに再接続して継続
        _uiState.update {
          it.copy(
            isTracking = true,
            currentTrackId = activeTrack.id,
          )
        }
      }

      else -> {
        // サービスが動いていないのにアクティブなトラックが残っている。
        // 前回の記録がアプリ更新やクラッシュで中断されたもの。
        // 再開するか完了にするかをユーザーに確認する。
        _uiState.update {
          it.copy(
            isTracking = false,
            interruptedTrack = activeTrack,
          )
        }
      }
    }
  }

  fun startTracking() {
    logger.d("startTracking() called")
    // 開始できない理由があればサービスは起動せず、そのままエラーを出す
    // （起動してから失敗すると「記録中なのに何も記録されない」状態になるため）。
    trackingController.start()?.let { failure ->
      reportStartFailure(failure)
      return
    }
    _uiState.update { it.copy(isTracking = true, errorMessage = null, isLocationDisabled = false) }
  }

  fun stopTracking() {
    trackingController.stop()
    _uiState.update {
      it.copy(
        isTracking = false,
        currentTrackId = null,
        // 停止後に古い現在地が残って地図がそこへ寄るのを防ぐ
        currentLocation = null,
        locationCount = 0,
        currentStop = null,
      )
    }
  }

  /** 中断されたトラックに続けて記録を再開する */
  fun resumeTracking() {
    val interrupted = _uiState.value.interruptedTrack ?: return
    trackingController.resume(interrupted.id)?.let { failure ->
      reportStartFailure(failure)
      return
    }
    _uiState.update {
      it.copy(
        isTracking = true,
        currentTrackId = interrupted.id,
        interruptedTrack = null,
        errorMessage = null,
        isLocationDisabled = false,
      )
    }
  }

  /**
   * 開始できなかった理由を出す。位置情報 OFF は設定への導線を添えた専用の案内にするため
   * [TrackingState.errorMessage] には載せない（他のエラーに上書きされてボタンだけ残るのを避ける）。
   */
  private fun reportStartFailure(failure: TrackingController.StartFailure) {
    _uiState.update {
      when (failure) {
        TrackingController.StartFailure.MISSING_PERMISSION ->
          it.copy(errorMessage = "位置情報の権限が必要です", isLocationDisabled = false)

        // 前の失敗のメッセージ（権限が無い等）が残って案内と食い違わないよう下ろす。
        TrackingController.StartFailure.LOCATION_DISABLED -> it.copy(errorMessage = null, isLocationDisabled = true)
      }
    }
  }

  /** 端末の位置情報の設定画面を開く（位置情報 OFF で開始できなかったときの導線）。 */
  fun openLocationSettings() {
    trackingController.openLocationSettings()
  }

  /**
   * 位置情報 OFF のエラーを出しているとき、設定から戻って ON になっていればエラーを下ろす。
   * 記録は自動では始めない（開始は本人が押す）。
   */
  fun checkLocationEnabled() {
    if (!_uiState.value.isLocationDisabled || !trackingController.isLocationEnabled()) return
    _uiState.update { it.copy(isLocationDisabled = false) }
  }

  /**
   * 中断されたトラックを完了として履歴に保存する。
   *
   * 停止時と**同じ確定**（全点の再平滑化・立ち寄り検出）を通してから閉じる。ここを素通しで
   * 閉じると、確定できずに中断した経路が確定済みの顔で履歴に並んでしまう（→ adr/0021）。
   * サービスは動いていないので、確定中の表示はここで立てる。
   */
  fun finishInterruptedTracking() {
    val interrupted = _uiState.value.interruptedTrack ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isFinalizing = true, interruptedTrack = null) }
      try {
        gpsTrackRepository.updateSmoothedForTrack(interrupted.id, isFinal = true)
        liveStopRepository.updateStopsForTrack(interrupted.id, isFinal = true)
        val endTime = interrupted.points.lastOrNull()?.timestamp ?: java.util.Date()
        gpsTrackRepository.finishTrack(interrupted.id, endTime)
      } finally {
        _uiState.update {
          it.copy(
            isTracking = false,
            currentTrackId = null,
            isFinalizing = false,
          )
        }
      }
    }
  }

  fun updateLocationPermission(hasPermission: Boolean) {
    _uiState.update {
      it.copy(
        hasLocationPermission = hasPermission,
      )
    }
  }

  fun checkLocationPermission() {
    updateLocationPermission(trackingController.hasRequiredPermissions())
  }

  /** 電池の最適化が無効化されているか（=バックグラウンドで制限されないか）を確認する */
  fun checkBatteryOptimization() {
    _uiState.update { it.copy(isIgnoringBatteryOptimizations = trackingController.isIgnoringBatteryOptimizations()) }
  }

  /** 地図の上の検索窓を開いたとき: 検索のセッションを始める（候補〜確定を 1 セッションにまとめて課金を抑える）。 */
  fun startPlaceSearch() {
    placesTextSearcher.startSession()
  }

  /** 地図の上の検索窓: 施設と地域の候補を 1 回で返す。開いたときに地図に映っていた範囲 [bias] の中を優先する。 */
  suspend fun predictPlacesNear(query: String, bias: GeoBounds?): List<PlacePrediction> = placesTextSearcher.predict(query, bias)

  /** 地図の上の検索窓（立ち寄りの追加中）: 地名の候補（地域だけに絞る）。[bias] の範囲の中を優先する。 */
  suspend fun predictRegions(query: String, bias: GeoBounds?): List<PlacePrediction> = placesTextSearcher.predict(query, bias, regionsOnly = true)

  /** 地域へ移動: 選んだ地名の範囲と中心。取れなければ null。 */
  suspend fun fetchRegion(placeId: String): MapRegion? = placesTextSearcher.fetchRegion(placeId)

  fun clearPlaceRegisteredMessage() {
    _uiState.update { it.copy(placeRegisteredMessage = null) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private companion object {
    /**
     * 予期せぬ切断のあと、状態を突き合わせるまでの待ち時間。
     * OS が START_STICKY でサービスを作り直す猶予を与えるため。短すぎると復帰前に
     * 「中断されました」を出してしまい、長すぎると記録中表示の復帰が遅れる。
     */
    const val SERVICE_RESTART_GRACE_MS = 5_000L
  }

  /**
   * 現在地と受信点数を購読して表示用に整える。以前はサービスへ接続できたときだけ購読を
   * 張っていたが、[TrackingController] が接続をまたいで値を保持するので、生成時に一度
   * 張れば足りる（接続前は null / 0 が流れるだけ）。
   */
  private fun observeLocationUpdates() {
    viewModelScope.launch {
      combine(
        trackingController.currentLocation,
        trackingController.locationCount,
      ) { location, count -> location to count }
        .collect { (location, count) ->
          _uiState.update {
            it.copy(
              currentLocation = location?.let { loc ->
                LocationInfo(
                  latitude = loc.latitude,
                  longitude = loc.longitude,
                  accuracy = loc.accuracy,
                  timestamp = DateFormatters.time(java.util.Date(loc.time)),
                )
              },
              locationCount = count,
            )
          }
        }
    }
  }

  private fun observeActiveTrack() {
    viewModelScope.launch {
      gpsTrackRepository.getActiveTrackRealtime().collect { activeTrack ->
        _uiState.update {
          it.copy(
            currentTrack = activeTrack,
            currentTrackId = activeTrack?.id,
          )
        }
      }
    }
  }
}
