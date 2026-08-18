package com.pathly.presentation.settings

import androidx.lifecycle.ViewModel
import com.pathly.data.settings.LocationAccuracy
import com.pathly.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  val gpsIntervalSeconds: StateFlow<Int> = settingsRepository.gpsIntervalSeconds

  val gpsIntervalOptions: List<Int> = SettingsRepository.GPS_INTERVAL_OPTIONS

  fun setGpsIntervalSeconds(seconds: Int) {
    settingsRepository.setGpsIntervalSeconds(seconds)
  }

  val locationAccuracy: StateFlow<LocationAccuracy> = settingsRepository.locationAccuracy

  val locationAccuracyOptions: List<LocationAccuracy> = LocationAccuracy.entries

  fun setLocationAccuracy(accuracy: LocationAccuracy) {
    settingsRepository.setLocationAccuracy(accuracy)
  }
}
