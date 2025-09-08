package com.pathly.presentation.tracking

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.repository.GpsTrackRepository
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
  private val gpsTrackRepository: GpsTrackRepository
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
            currentTrackId = activeTrack.id
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
          currentTrackId = null
        )
      }
    }
  }

  init {
    checkActiveTracking()
    checkLocationPermission()
  }

  private fun checkActiveTracking() {
    viewModelScope.launch {
      val activeTrack = gpsTrackRepository.getActiveTrack()

      if (activeTrack != null) {
        // アクティブなトラックが見つかった場合、サービスに接続を試行
        _uiState.value = _uiState.value.copy(
          isTracking = true,
          currentTrackId = activeTrack.id
        )
        bindToService()
      } else {
        // アクティブなトラックがない場合
        _uiState.value = _uiState.value.copy(
          isTracking = false,
          currentTrackId = null
        )
      }
    }
  }

  fun startTracking() {
    Log.d("TrackingViewModel", "startTracking() called")

    if (!_uiState.value.hasLocationPermission) {
      Log.e("TrackingViewModel", "Location permission not granted")
      _uiState.value = _uiState.value.copy(
        errorMessage = "位置情報の権限が必要です"
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
      errorMessage = null
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
      currentTrackId = null
    )
  }

  fun updateLocationPermission(hasPermission: Boolean) {
    _uiState.value = _uiState.value.copy(
      hasLocationPermission = hasPermission
    )
  }

  fun checkLocationPermission() {
    val hasPermission = PermissionUtils.hasAllRequiredPermissions(application)
    updateLocationPermission(hasPermission)
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
          service.locationCount
        ) { location, count ->
          location?.let { loc ->
            val locationInfo = LocationInfo(
              latitude = loc.latitude,
              longitude = loc.longitude,
              accuracy = loc.accuracy,
              timestamp = DateFormatters.TIME_FORMAT.format(java.util.Date(loc.time))
            )

            _uiState.value = _uiState.value.copy(
              currentLocation = locationInfo,
              locationCount = count
            )
          }
        }.collect { }
      }
    } ?: run {
      Log.w("TrackingViewModel", "Location service is null in observeLocationUpdates")
    }
  }


  override fun onCleared() {
    super.onCleared()
    unbindFromService()
  }
}