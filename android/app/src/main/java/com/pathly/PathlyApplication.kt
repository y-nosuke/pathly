package com.pathly

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.libraries.places.api.Places
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PathlyApplication :
  Application(),
  OnMapsSdkInitializedCallback {

  private val logger = Logger("PathlyApplication")

  // オフライン記録などで未解決のまま残った立ち寄り場所を、起動時にまとめて名前解決する（キャッチアップ）ためのスコープ。
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Inject
  lateinit var placeRepository: PlaceRepository

  override fun onCreate() {
    super.onCreate()
    MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, this)

    // Places SDK（New）。立ち寄り場所の命名に使う。キーが空なら初期化しない。
    val placesKey = BuildConfig.GOOGLE_MAPS_API_KEY
    if (placesKey.isNotBlank()) {
      Places.initializeWithNewPlacesApiEnabled(applicationContext, placesKey)
    } else {
      logger.w("GOOGLE_MAPS_API_KEY is blank; Places naming disabled")
    }

    // オフライン記録の未解決な立ち寄りをオンライン時に一括で名前解決する（設計: オンライン復帰後キャッチアップ）。
    // オフライン時は各 place で no-op（解決ログを残さず、次回起動で再度拾う）＝無駄な課金なし。
    appScope.launch { placeRepository.resolveAllUnresolvedNames() }
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
    // Maps SDK initialized successfully
  }
}
