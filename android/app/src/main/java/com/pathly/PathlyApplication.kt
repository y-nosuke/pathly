package com.pathly

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.libraries.places.api.Places
import com.pathly.data.settings.MaintenanceStore
import com.pathly.data.work.PlaceNameCatchUpWorker
import com.pathly.domain.model.TrackSmoother
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
  lateinit var maintenanceStore: MaintenanceStore

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

    // 保存済みのデータに対する一度きりの修復。距離を埋めてから補正点を作り直す順にする
    // （作り直しは距離も焼き直すので、逆だと二度手間になる）。
    appScope.launch {
      // v11 より前に記録した経路の総移動距離を埋める（対象が無ければ即終了する）。
      gpsTrackRepository.backfillMissingDistances()
      // 欠落をまたいで平均された補正点を作り直す（→ adr/0022）。やり切れたときだけ世代を
      // 進めるので、途中で落ちても記録中でも、次の起動でやり直される。
      if (maintenanceStore.smoothingGeneration < TrackSmoother.GENERATION &&
        gpsTrackRepository.resmoothGappedTracks()
      ) {
        maintenanceStore.smoothingGeneration = TrackSmoother.GENERATION
      }
    }
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
    // Maps SDK initialized successfully
  }
}
