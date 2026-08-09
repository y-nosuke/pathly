package com.pathly.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.data.tracking.TrackingController
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.util.DateFormatters
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
  }

  /** サービスが予期せず切れたら（プロセスのクラッシュ等）、記録中のトラックを閉じて状態を戻す。 */
  private fun observeUnexpectedDisconnect() {
    viewModelScope.launch {
      trackingController.unexpectedDisconnect.collect {
        val activeTrack = gpsTrackRepository.getActiveTrack()
        if (activeTrack != null) {
          logger.d("Service disconnected unexpectedly, finishing track")
          gpsTrackRepository.finishTrack(activeTrack.id, java.util.Date())
        }
        _uiState.update { it.copy(isTracking = false, currentTrackId = null) }
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
    viewModelScope.launch {
      val activeTrack = gpsTrackRepository.getActiveTrack()

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

        trackingController.isTracking.value -> {
          // 記録中プロセスが生存している → サービスに再接続して継続
          _uiState.update {
            it.copy(
              isTracking = true,
              currentTrackId = activeTrack.id,
            )
          }
          trackingController.reattach()
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
  }

  fun startTracking() {
    logger.d("startTracking() called")

    if (!_uiState.value.hasLocationPermission) {
      logger.e("Location permission not granted")
      _uiState.update { it.copy(errorMessage = "位置情報の権限が必要です") }
      return
    }

    trackingController.start()
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
    trackingController.resume(interrupted.id)
    _uiState.update {
      it.copy(
        isTracking = true,
        currentTrackId = interrupted.id,
        interruptedTrack = null,
        errorMessage = null,
      )
    }
  }

  /** 中断されたトラックを完了として履歴に保存する */
  fun finishInterruptedTracking() {
    val interrupted = _uiState.value.interruptedTrack ?: return
    viewModelScope.launch {
      val endTime = interrupted.points.lastOrNull()?.timestamp ?: java.util.Date()
      gpsTrackRepository.finishTrack(interrupted.id, endTime)
      _uiState.update {
        it.copy(
          isTracking = false,
          currentTrackId = null,
          interruptedTrack = null,
        )
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
    memo: String?,
    googlePlaceId: String? = null,
    forceNewPlace: Boolean = false,
  ) {
    viewModelScope.launch {
      try {
        val reg = wishlistRepository.registerPlace(latitude, longitude, name, memo, googlePlaceId, forceNewPlace)
        if (wishlist) {
          wishlistRepository.addToWishlist(reg.placeId, priority)
        }
        _uiState.update {
          it.copy(
            placeRegisteredMessage = if (reg.alreadyExisted) {
              "この場所は登録済みです"
            } else {
              "「${name?.ifBlank { null } ?: "場所"}」を登録しました"
            },
          )
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "場所の登録に失敗しました: ${e.message}") }
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき: 既存 place に行きたい/メモを反映する（新規は作らない）。 */
  fun linkRegisterToPlace(placeId: Long, wishlist: Boolean, priority: Priority, memo: String?) {
    viewModelScope.launch {
      try {
        if (!memo.isNullOrBlank()) wishlistRepository.updatePlaceNote(placeId, memo)
        if (wishlist) wishlistRepository.addToWishlist(placeId, priority)
        _uiState.update { it.copy(placeRegisteredMessage = "この場所に紐付けました") }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "紐付けに失敗しました: ${e.message}") }
      }
    }
  }

  /** 登録済みマーカーをタップして記録画面のまま編集するため、単一 place の現在値を取得する。 */
  suspend fun loadPlace(placeId: Long): PlaceListItem? = wishlistRepository.getPlace(placeId)

  /**
   * 記録画面のまま既存 place を編集して保存する（名前・メモ・行きたい・優先度・訪問済みを差分適用）。
   * 場所詳細の [PlacesViewModel.savePlaceEdits] と同じ考え方（Google 紐付けはここでは扱わない）。
   */
  fun savePlaceEdits(
    item: PlaceListItem,
    name: String,
    note: String,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
  ) {
    viewModelScope.launch {
      try {
        if (name.trim() != (item.place.name ?: "").trim()) {
          wishlistRepository.renamePlace(item.place.id, name.trim())
        }
        val newNote = note.ifBlank { null }
        if (newNote != item.note) {
          wishlistRepository.updatePlaceNote(item.place.id, newNote)
        }
        val wishlistId = item.wishlistId
        when {
          wishlist && wishlistId == null -> {
            val newId = wishlistRepository.addToWishlist(item.place.id, priority)
            if (visited && item.visitCount == 0) wishlistRepository.setVisited(newId, true)
          }
          wishlist && wishlistId != null -> {
            if (priority != item.priority) wishlistRepository.updateWishlist(wishlistId, priority)
            if (item.visitCount == 0 && visited != item.isManuallyVisited) {
              wishlistRepository.setVisited(wishlistId, visited)
            }
          }
          !wishlist && wishlistId != null -> {
            wishlistRepository.removeFromWishlist(wishlistId)
          }
        }
        _uiState.update { it.copy(placeRegisteredMessage = "保存しました") }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "保存に失敗しました: ${e.message}") }
      }
    }
  }

  /** POI 登録ダイアログのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = wishlistRepository.fetchPlaceDetails(googlePlaceId)

  /** 手動追加の確認ダイアログ用: 座標の近くの POI 候補を複数取得する（取り違え回避）。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = placeRepository.nearbyPois(latitude, longitude)

  /** 手動追加の近接確認用: 近く（検出半径）に既存の場所があれば最寄り1件を返す。 */
  suspend fun nearbyPlace(latitude: Double, longitude: Double): RegisteredPlace? = placeRepository.findNearbyPlace(latitude, longitude)

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
   * 記録中に手動で立ち寄りを追加する（「今ここ」ボタン／地図タップ）。到着・出発は呼び出し側が
   * 近傍の軌跡点から決めて渡す。名前は候補選択／手入力／空（未命名）のいずれか。
   */
  fun addManualStop(
    latitude: Double,
    longitude: Double,
    arrivalTime: java.util.Date,
    departureTime: java.util.Date,
    name: String?,
    googlePlaceId: String?,
    forceNewPlace: Boolean = false,
  ) {
    val trackId = _uiState.value.currentTrackId ?: return
    viewModelScope.launch {
      try {
        placeRepository.addManualStop(trackId, latitude, longitude, arrivalTime, departureTime, name, googlePlaceId, forceNewPlace)
        _uiState.update {
          it.copy(
            placeRegisteredMessage = "立ち寄りを追加しました",
          )
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "立ち寄りの追加に失敗しました: ${e.message}") }
      }
    }
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
                  timestamp = DateFormatters.TIME_FORMAT.format(java.util.Date(loc.time)),
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
