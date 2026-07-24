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
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.service.LocationTrackingService
import com.pathly.util.DateFormatters
import com.pathly.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
  private val application: Application,
  private val gpsTrackRepository: GpsTrackRepository,
  private val placeRepository: PlaceRepository,
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
  }

  private fun observeCurrentStop() {
    viewModelScope.launch {
      placeRepository.currentStop.collect { stop ->
        _uiState.value = _uiState.value.copy(currentStop = stop)
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
