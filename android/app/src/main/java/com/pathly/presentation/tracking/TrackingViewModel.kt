package com.pathly.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.data.tracking.TrackingController
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import com.pathly.domain.usecase.PlaceEditUseCase
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

  private fun observeCurrentStop() {
    viewModelScope.launch {
      placeRepository.currentStop.collect { stop ->
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
          if (id == null) flowOf(emptyList()) else placeRepository.getStopsForTrack(id)
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
      _uiState.update { it.copy(errorMessage = failure.message()) }
      return
    }
    _uiState.update { it.copy(isTracking = true, errorMessage = null) }
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
      _uiState.update { it.copy(errorMessage = failure.message()) }
      return
    }
    _uiState.update {
      it.copy(
        isTracking = true,
        currentTrackId = interrupted.id,
        interruptedTrack = null,
        errorMessage = null,
      )
    }
  }

  private fun TrackingController.StartFailure.message(): String = when (this) {
    TrackingController.StartFailure.MISSING_PERMISSION -> "位置情報の権限が必要です"
    TrackingController.StartFailure.LOCATION_DISABLED -> "端末の位置情報がオフです。設定でオンにしてください"
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
        placeRepository.updateStopsForTrack(interrupted.id, isFinal = true)
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

  /** 電池の最適化の無効化を要求するシステムダイアログを開く */
  fun requestDisableBatteryOptimization() {
    trackingController.requestDisableBatteryOptimization()
  }

  /**
   * 記録画面のマップで POI をタップして場所を登録する。メモは常に保存し、行きたい ON なら
   * 優先度つきで wishlist にも入れる。登録後は「場所」タブに現れる。
   */
  fun registerPlace(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    memo: String?,
    googlePlaceId: String? = null,
    forceNewPlace: Boolean = false,
  ) {
    viewModelScope.launch {
      try {
        val reg = placeEditUseCase.register(latitude, longitude, name, wishlist, priority, visited, memo, googlePlaceId, forceNewPlace)
        _uiState.update { it.copy(placeRegisteredMessage = registeredMessage(reg.alreadyExisted, name)) }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "場所の登録に失敗しました: ${e.message}") }
      }
    }
  }

  /**
   * 地図タップからの場所登録。近くに既存の場所があれば登録せず確認待ちにする。
   * 「登録済みの場所」を地図に表示中なら、ユーザーは既存を見たうえでの操作なので確認しない。
   */
  fun registerPlaceWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    memo: String?,
    googlePlaceId: String?,
    googleName: String? = null,
  ) {
    viewModelScope.launch {
      try {
        val result = placeEditUseCase.registerWithNearbyCheck(
          latitude,
          longitude,
          name,
          wishlist,
          priority,
          visited,
          memo,
          googlePlaceId,
          nearbyAlreadyVisible = _uiState.value.showRegisteredPlaces,
          googleName = googleName,
        )
        when (result) {
          is PlaceEditUseCase.RegisterResult.NearbyFound ->
            _uiState.update {
              it.copy(
                nearbyRegisterPrompt = NearbyRegisterPrompt(
                  result.nearby,
                  latitude,
                  longitude,
                  name,
                  wishlist,
                  priority,
                  visited,
                  memo,
                ),
              )
            }

          is PlaceEditUseCase.RegisterResult.Registered ->
            _uiState.update { it.copy(placeRegisteredMessage = registeredMessage(result.alreadyExisted, name)) }
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "場所の登録に失敗しました: ${e.message}") }
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。 */
  fun confirmNearbyLink() {
    val prompt = _uiState.value.nearbyRegisterPrompt ?: return
    _uiState.update { it.copy(nearbyRegisterPrompt = null) }
    linkRegisterToPlace(prompt.nearby.placeId, prompt.wishlist, prompt.priority, prompt.visited, prompt.memo)
  }

  /** 近接確認で「新規で登録」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  fun confirmNearbyNew() {
    val prompt = _uiState.value.nearbyRegisterPrompt ?: return
    _uiState.update { it.copy(nearbyRegisterPrompt = null) }
    registerPlace(
      prompt.latitude,
      prompt.longitude,
      prompt.name,
      prompt.wishlist,
      prompt.priority,
      prompt.visited,
      prompt.memo,
      googlePlaceId = null,
      forceNewPlace = true,
    )
  }

  fun dismissNearbyPrompt() {
    _uiState.update { it.copy(nearbyRegisterPrompt = null) }
  }

  private fun registeredMessage(alreadyExisted: Boolean, name: String?): String = if (alreadyExisted) {
    "この場所は登録済みです"
  } else {
    "「${name?.ifBlank { null } ?: "場所"}」を登録しました"
  }

  /** 近接確認で「この場所に紐付け」を選んだとき: 既存 place に行きたい/メモを反映する（新規は作らない）。 */
  fun linkRegisterToPlace(placeId: Long, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?) {
    viewModelScope.launch {
      try {
        placeEditUseCase.linkToExisting(placeId, wishlist, priority, visited, memo)
        _uiState.update { it.copy(placeRegisteredMessage = "この場所に紐付けました") }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "紐付けに失敗しました: ${e.message}") }
      }
    }
  }

  /** 登録済みマーカーをタップして記録画面のまま編集するため、単一 place をリアクティブに購読する。 */
  fun observePlace(placeId: Long): Flow<PlaceListItem?> = wishlistRepository.observePlace(placeId)

  /** その場所を含むお出掛けの一覧（編集シートの訪問履歴）。 */
  fun visitsFor(placeId: Long): Flow<List<PlaceVisit>> = wishlistRepository.getVisits(placeId)

  /**
   * 記録画面のまま既存 place を編集して保存する（名前・メモ・行きたい・優先度・訪問済み・
   * Google 施設の紐付けを差分適用）。場所詳細の [PlacesViewModel.savePlaceEdits] と同じ処理を
   * 同じ UseCase で行う（入口が違うだけで、できることを揃えるため）。
   */
  fun savePlaceEdits(
    item: PlaceListItem,
    name: String,
    note: String,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    link: PlaceSearchResult? = null,
  ) {
    viewModelScope.launch {
      try {
        val result = placeEditUseCase.saveEdits(item, name, note, wishlist, priority, visited, link)
        val message = if (result.linkRefused) "同じ施設の場所が既にあるため、施設情報は紐付けませんでした（他は保存しました）" else "保存しました"
        _uiState.update { it.copy(placeRegisteredMessage = message) }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "保存に失敗しました: ${e.message}") }
      }
    }
  }

  /**
   * 場所そのものを削除する。確認ダイアログは出さず即時削除し、スナックバーの「取り消す」
   * （[undoDelete]）で戻せる（場所一覧と同じ流儀）。
   */
  fun deletePlace(placeId: Long) {
    viewModelScope.launch {
      val name = _uiState.value.registeredPlaces.firstOrNull { it.placeId == placeId }?.displayName
      try {
        wishlistRepository.deletePlace(placeId)
        _uiState.update { it.copy(deleteUndo = it.deleteUndo.deleted(name)) }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "削除に失敗しました: ${e.message}") }
      }
    }
  }

  /** 削除の通知を出し終えた（画面に戻るたび再表示しないよう消化する）。 */
  fun consumeDeleteUndo() {
    _uiState.update { it.copy(deleteUndo = it.deleteUndo.shown()) }
  }

  /** 直近の削除を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoDelete() {
    viewModelScope.launch {
      try {
        wishlistRepository.undoLastPlaceDeletion()
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "取り消しに失敗しました: ${e.message}") }
      }
    }
  }

  /** 「Googleで情報を取得」の名前検索フォールバック: キーワード候補を返す。 */
  suspend fun predictPlaces(query: String): List<PlacePrediction> = placesTextSearcher.predict(query)

  /** 名前検索で選んだ候補を、座標つきの施設情報に確定する。 */
  suspend fun fetchPlaceResult(placeId: String): PlaceSearchResult? = placesTextSearcher.fetch(placeId)

  /** 場所シートのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = wishlistRepository.fetchPlaceDetails(googlePlaceId)

  /** 手動追加の確認ダイアログ用: 座標の近くの POI 候補を複数取得する（取り違え回避）。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = placeRepository.nearbyPois(latitude, longitude)

  /** 誤検知の訂正: この訪問だけを、選んだ候補／手入力名の場所へ付け替える。 */
  fun reassignStop(stopId: Long, chosen: PlaceSearchResult?, customName: String?) {
    viewModelScope.launch {
      try {
        placeRepository.reassignStopPlace(stopId, chosen, customName)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "場所の選び直しに失敗しました: ${e.message}") }
      }
    }
  }

  /**
   * 手動で立ち寄りを追加する。近くに既存の場所があれば追加せず確認待ちにする。
   * 「登録済みの場所」を地図に表示中なら、ユーザーは既存を見たうえでの操作なので確認しない。
   */
  fun addManualStopWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    arrivalTime: java.util.Date,
    departureTime: java.util.Date,
    name: String?,
    googlePlaceId: String?,
    googleName: String? = null,
  ) {
    val trackId = _uiState.value.currentTrackId ?: return
    viewModelScope.launch {
      try {
        val result = addManualStopUseCase.addWithNearbyCheck(
          trackId,
          latitude,
          longitude,
          arrivalTime,
          departureTime,
          name,
          googlePlaceId,
          nearbyAlreadyVisible = _uiState.value.showRegisteredPlaces,
          googleName = googleName,
        )
        when (result) {
          is AddManualStopUseCase.AddResult.NearbyFound ->
            _uiState.update {
              it.copy(
                nearbyStopPrompt = NearbyStopPrompt(
                  result.nearby,
                  latitude,
                  longitude,
                  arrivalTime,
                  departureTime,
                  name,
                ),
              )
            }

          is AddManualStopUseCase.AddResult.Added ->
            _uiState.update { it.copy(placeRegisteredMessage = "立ち寄りを追加しました") }
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "立ち寄りの追加に失敗しました: ${e.message}") }
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。 */
  fun confirmNearbyStopLink() {
    val prompt = _uiState.value.nearbyStopPrompt ?: return
    _uiState.update { it.copy(nearbyStopPrompt = null) }
    addManualStopForPlace(prompt.nearby.placeId, prompt.arrivalTime, prompt.departureTime)
  }

  /** 近接確認で「新規で追加」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  fun confirmNearbyStopNew() {
    val prompt = _uiState.value.nearbyStopPrompt ?: return
    _uiState.update { it.copy(nearbyStopPrompt = null) }
    val trackId = _uiState.value.currentTrackId ?: return
    viewModelScope.launch {
      try {
        addManualStopUseCase.addAsNew(
          trackId,
          prompt.latitude,
          prompt.longitude,
          prompt.arrivalTime,
          prompt.departureTime,
          prompt.name,
        )
        _uiState.update { it.copy(placeRegisteredMessage = "立ち寄りを追加しました") }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "立ち寄りの追加に失敗しました: ${e.message}") }
      }
    }
  }

  fun dismissNearbyStopPrompt() {
    _uiState.update { it.copy(nearbyStopPrompt = null) }
  }

  /** 地図の登録済みマーカーを選んで、既存 place にこの訪問を紐付ける（新規 place を作らない）。 */
  fun addManualStopForPlace(placeId: Long, arrivalTime: java.util.Date, departureTime: java.util.Date) {
    val trackId = _uiState.value.currentTrackId ?: return
    viewModelScope.launch {
      try {
        placeRepository.addManualStopForPlace(trackId, placeId, arrivalTime, departureTime)
        _uiState.update { it.copy(placeRegisteredMessage = "立ち寄りを追加しました") }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "立ち寄りの追加に失敗しました: ${e.message}") }
      }
    }
  }

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
