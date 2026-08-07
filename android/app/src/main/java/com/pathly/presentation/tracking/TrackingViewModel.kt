package com.pathly.presentation.tracking

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.service.LocationTrackingService
import com.pathly.util.DateFormatters
import com.pathly.util.PermissionUtils
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackingViewModel @Inject constructor(
  private val application: Application,
  private val gpsTrackRepository: GpsTrackRepository,
  private val placeRepository: PlaceRepository,
  private val wishlistRepository: WishlistRepository,
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(TrackingState())
  val uiState: StateFlow<TrackingState> = _uiState.asStateFlow()

  private var locationService: LocationTrackingService? = null
  private var isServiceBound = false

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      Log.d("TrackingViewModel", "Service connected")
      val binder = service as LocationTrackingService.LocationTrackingBinder
      locationService = binder.getService()
      isServiceBound = true

      // サービスに接続できた場合、追跡状態を確認・更新
      viewModelScope.launch {
        val activeTrack = gpsTrackRepository.getActiveTrack()
        if (activeTrack != null) {
          _uiState.value = _uiState.value.copy(
            isTracking = true,
            currentTrackId = activeTrack.id,
          )
        }
      }

      // サービスに接続したら、位置情報を監視開始
      observeLocationUpdates()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      Log.d("TrackingViewModel", "Service disconnected")
      locationService = null
      isServiceBound = false

      // サービスが予期せず切断された場合、追跡状態をリセット
      viewModelScope.launch {
        val activeTrack = gpsTrackRepository.getActiveTrack()
        if (activeTrack != null) {
          Log.d("TrackingViewModel", "Service disconnected unexpectedly, finishing track")
          gpsTrackRepository.finishTrack(activeTrack.id, java.util.Date())
        }
        _uiState.value = _uiState.value.copy(
          isTracking = false,
          currentTrackId = null,
        )
      }
    }
  }

  init {
    checkActiveTracking()
    checkLocationPermission()
    observeActiveTrack()
    observeCurrentStop()
    observeStops()
  }

  private fun observeCurrentStop() {
    viewModelScope.launch {
      placeRepository.currentStop.collect { stop ->
        _uiState.value = _uiState.value.copy(currentStop = stop)
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
          _uiState.value = _uiState.value.copy(stops = stops)
        }
    }
  }

  private fun checkActiveTracking() {
    viewModelScope.launch {
      val activeTrack = gpsTrackRepository.getActiveTrack()

      when {
        activeTrack == null -> {
          // アクティブなトラックがない場合
          _uiState.value = _uiState.value.copy(
            isTracking = false,
            currentTrackId = null,
          )
        }

        LocationTrackingService.isTracking -> {
          // 記録中プロセスが生存している → サービスに再接続して継続
          _uiState.value = _uiState.value.copy(
            isTracking = true,
            currentTrackId = activeTrack.id,
          )
          bindToService()
        }

        else -> {
          // サービスが動いていないのにアクティブなトラックが残っている。
          // 前回の記録がアプリ更新やクラッシュで中断されたもの。
          // 再開するか完了にするかをユーザーに確認する。
          _uiState.value = _uiState.value.copy(
            isTracking = false,
            interruptedTrack = activeTrack,
          )
        }
      }
    }
  }

  fun startTracking() {
    Log.d("TrackingViewModel", "startTracking() called")

    if (!_uiState.value.hasLocationPermission) {
      Log.e("TrackingViewModel", "Location permission not granted")
      _uiState.value = _uiState.value.copy(
        errorMessage = "位置情報の権限が必要です",
      )
      return
    }

    val intent = Intent(application, LocationTrackingService::class.java).apply {
      action = LocationTrackingService.ACTION_START_TRACKING
    }

    application.startForegroundService(intent)
    bindToService()

    _uiState.value = _uiState.value.copy(
      isTracking = true,
      errorMessage = null,
    )
  }

  fun stopTracking() {
    val intent = Intent(application, LocationTrackingService::class.java).apply {
      action = LocationTrackingService.ACTION_STOP_TRACKING
    }

    application.startService(intent)
    unbindFromService()

    _uiState.value = _uiState.value.copy(
      isTracking = false,
      currentTrackId = null,
      // 停止後に古い現在地が残って地図がそこへ寄るのを防ぐ
      currentLocation = null,
      locationCount = 0,
      currentStop = null,
    )
  }

  /** 中断されたトラックに続けて記録を再開する */
  fun resumeTracking() {
    val interrupted = _uiState.value.interruptedTrack ?: return

    val intent = Intent(application, LocationTrackingService::class.java).apply {
      action = LocationTrackingService.ACTION_RESUME_TRACKING
      putExtra(LocationTrackingService.EXTRA_TRACK_ID, interrupted.id)
    }
    application.startForegroundService(intent)
    bindToService()

    _uiState.value = _uiState.value.copy(
      isTracking = true,
      currentTrackId = interrupted.id,
      interruptedTrack = null,
      errorMessage = null,
    )
  }

  /** 中断されたトラックを完了として履歴に保存する */
  fun finishInterruptedTracking() {
    val interrupted = _uiState.value.interruptedTrack ?: return
    viewModelScope.launch {
      val endTime = interrupted.points.lastOrNull()?.timestamp ?: java.util.Date()
      gpsTrackRepository.finishTrack(interrupted.id, endTime)
      _uiState.value = _uiState.value.copy(
        isTracking = false,
        currentTrackId = null,
        interruptedTrack = null,
      )
    }
  }

  fun updateLocationPermission(hasPermission: Boolean) {
    _uiState.value = _uiState.value.copy(
      hasLocationPermission = hasPermission,
    )
  }

  fun checkLocationPermission() {
    val hasPermission = PermissionUtils.hasAllRequiredPermissions(application)
    updateLocationPermission(hasPermission)
  }

  /** 電池の最適化が無効化されているか（=バックグラウンドで制限されないか）を確認する */
  fun checkBatteryOptimization() {
    val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoring = powerManager.isIgnoringBatteryOptimizations(application.packageName)
    _uiState.value = _uiState.value.copy(isIgnoringBatteryOptimizations = ignoring)
  }

  /** 電池の最適化の無効化を要求するシステムダイアログを開く */
  fun requestDisableBatteryOptimization() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = Uri.parse("package:${application.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    application.startActivity(intent)
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
  ) {
    viewModelScope.launch {
      try {
        val reg = wishlistRepository.registerPlace(latitude, longitude, name, memo, googlePlaceId)
        if (wishlist) {
          wishlistRepository.addToWishlist(reg.placeId, priority)
        }
        _uiState.value = _uiState.value.copy(
          placeRegisteredMessage = if (reg.alreadyExisted) {
            "この場所は登録済みです"
          } else {
            "「${name?.ifBlank { null } ?: "場所"}」を登録しました"
          },
        )
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "場所の登録に失敗しました: ${e.message}")
      }
    }
  }

  /** POI 登録ダイアログのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = wishlistRepository.fetchPlaceDetails(googlePlaceId)

  /** 手動追加の確認ダイアログ用: 座標の近くの POI 候補を複数取得する（取り違え回避）。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = placeRepository.nearbyPois(latitude, longitude)

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
  ) {
    val trackId = _uiState.value.currentTrackId ?: return
    viewModelScope.launch {
      try {
        placeRepository.addManualStop(trackId, latitude, longitude, arrivalTime, departureTime, name, googlePlaceId)
        _uiState.value = _uiState.value.copy(
          placeRegisteredMessage = "立ち寄りを追加しました",
        )
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "立ち寄りの追加に失敗しました: ${e.message}")
      }
    }
  }

  fun clearPlaceRegisteredMessage() {
    _uiState.value = _uiState.value.copy(placeRegisteredMessage = null)
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }

  private fun bindToService() {
    val intent = Intent(application, LocationTrackingService::class.java)
    application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
  }

  private fun unbindFromService() {
    if (isServiceBound) {
      application.unbindService(serviceConnection)
      isServiceBound = false
    }
  }

  private fun observeLocationUpdates() {
    locationService?.let { service ->
      viewModelScope.launch {
        combine(
          service.currentLocation,
          service.locationCount,
        ) { location, count ->
          location?.let { loc ->
            val locationInfo = LocationInfo(
              latitude = loc.latitude,
              longitude = loc.longitude,
              accuracy = loc.accuracy,
              timestamp = DateFormatters.TIME_FORMAT.format(java.util.Date(loc.time)),
            )

            _uiState.value = _uiState.value.copy(
              currentLocation = locationInfo,
              locationCount = count,
            )
          }
        }.collect { }
      }
    } ?: run {
      Log.w("TrackingViewModel", "Location service is null in observeLocationUpdates")
    }
  }

  private fun observeActiveTrack() {
    viewModelScope.launch {
      gpsTrackRepository.getActiveTrackRealtime().collect { activeTrack ->
        _uiState.value = _uiState.value.copy(
          currentTrack = activeTrack,
          currentTrackId = activeTrack?.id,
        )
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    unbindFromService()
  }
}
