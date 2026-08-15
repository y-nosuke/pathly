package com.pathly

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.libraries.places.api.Places
import com.pathly.data.work.PlaceCategoryBackfillWorker
import com.pathly.data.work.PlaceNameCatchUpWorker
import com.pathly.domain.repository.GpsTrackRepository
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
  OnMapsSdkInitializedCallback,
  Configuration.Provider {

  private val logger = Logger("PathlyApplication")

  // オフライン記録などで未解決のまま残った立ち寄り場所を、起動時にまとめて名前解決する（キャッチアップ）ためのスコープ。
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Inject
  lateinit var gpsTrackRepository: GpsTrackRepository

  @Inject
  lateinit var workerFactory: HiltWorkerFactory

  /** WorkManager から Hilt でワーカーを組み立てるための設定。 */
  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
      .build()

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

    // オフライン記録で未解決のまま残った立ち寄り場所の名前解決を予約する。
    // ネットワーク接続を制約にしているので、いま繋がっていなくても復帰した時点で走る。
    PlaceNameCatchUpWorker.enqueue(this)

    // TODO(v13-backfill): 移行が済んだら PlaceCategoryBackfillWorker ごとこの行も消す。
    // v13 より前に解決した場所の業種を Google から引き直して埋める（一度きり）。
    PlaceCategoryBackfillWorker.enqueue(this)

    // v11 より前に記録した経路の総移動距離を埋める（対象が無ければ即終了する一度きりの処理）。
    appScope.launch { gpsTrackRepository.backfillMissingDistances() }
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
    // Maps SDK initialized successfully
  }
}
