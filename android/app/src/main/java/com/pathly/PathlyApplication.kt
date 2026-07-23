package com.pathly

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.libraries.places.api.Places
import com.pathly.util.Logger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PathlyApplication :
  Application(),
  OnMapsSdkInitializedCallback {

  private val logger = Logger("PathlyApplication")

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
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
    // Maps SDK initialized successfully
  }
}
