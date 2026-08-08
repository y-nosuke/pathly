package com.pathly.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アプリ設定の保存・取得（SharedPreferences）。
 */
@Singleton
class SettingsRepository @Inject constructor(
  @param:ApplicationContext context: Context,
) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _gpsIntervalSeconds =
    MutableStateFlow(prefs.getInt(KEY_GPS_INTERVAL, DEFAULT_GPS_INTERVAL_SECONDS))

  /** GPS記録間隔（秒）。UI が購読する。 */
  val gpsIntervalSeconds: StateFlow<Int> = _gpsIntervalSeconds.asStateFlow()

  /** 現在の GPS 記録間隔（秒）。サービスが起動時に参照する。 */
  fun currentGpsIntervalSeconds(): Int = _gpsIntervalSeconds.value

  fun setGpsIntervalSeconds(seconds: Int) {
    prefs.edit { putInt(KEY_GPS_INTERVAL, seconds) }
    _gpsIntervalSeconds.value = seconds
  }

  // 「登録済みの場所」を地図に出すトグル。画面（記録／履歴詳細／場所詳細）ごとに独立して保持する。
  private val showRegisteredPlacesFlows: Map<MapSurface, MutableStateFlow<Boolean>> =
    MapSurface.entries.associateWith { surface ->
      MutableStateFlow(prefs.getBoolean(keyShowPlaces(surface), false))
    }

  /** その画面で「登録済みの場所」を表示するか。UI が購読する。既定は OFF。 */
  fun showRegisteredPlaces(surface: MapSurface): StateFlow<Boolean> = showRegisteredPlacesFlows.getValue(surface).asStateFlow()

  fun setShowRegisteredPlaces(surface: MapSurface, show: Boolean) {
    prefs.edit { putBoolean(keyShowPlaces(surface), show) }
    showRegisteredPlacesFlows.getValue(surface).value = show
  }

  companion object {
    const val DEFAULT_GPS_INTERVAL_SECONDS = 10

    /** 選択できる間隔（秒）。 */
    val GPS_INTERVAL_OPTIONS = listOf(5, 10, 30, 60)

    private const val PREFS_NAME = "pathly_settings"
    private const val KEY_GPS_INTERVAL = "gps_interval_seconds"

    private fun keyShowPlaces(surface: MapSurface): String = "show_registered_places_${surface.key}"
  }
}

/** 「登録済みの場所」トグルを保持する画面（それぞれ独立して ON/OFF を記憶する）。 */
enum class MapSurface(val key: String) {
  RECORDING("recording"),
  HISTORY("history"),
  PLACE_DETAIL("place_detail"),
}
