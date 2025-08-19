package com.pathly.presentation.tracking

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
      val binder = service as LocationTrackingService.LocationTrackingBinder
      locationService = binder.getService()
      isServiceBound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      locationService = null
      isServiceBound = false
    }
  }

  init {
    checkActiveTracking()
    checkLocationPermission()
  }

  private fun checkActiveTracking() {
    viewModelScope.launch {
      val activeTrack = gpsTrackRepository.getActiveTrack()
      _uiState.value = _uiState.value.copy(
        isTracking = activeTrack != null,
        currentTrackId = activeTrack?.id
      )
    }
  }

  fun startTracking() {
    if (!_uiState.value.hasLocationPermission) {
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
    val hasPermission = ContextCompat.checkSelfPermission(
      application,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
          application,
          Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

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

  override fun onCleared() {
    super.onCleared()
    unbindFromService()
  }
}