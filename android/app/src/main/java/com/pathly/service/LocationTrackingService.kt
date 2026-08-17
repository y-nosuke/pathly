package com.pathly.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pathly.MainActivity
import com.pathly.R
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.GpsTrackDao
import com.pathly.data.local.entity.GpsPointEntity
import com.pathly.data.local.entity.GpsTrackEntity
import com.pathly.data.settings.SettingsRepository
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import com.pathly.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

  companion object {
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "LocationTrackingChannel"
    const val ACTION_START_TRACKING = "START_TRACKING"
    const val ACTION_RESUME_TRACKING = "RESUME_TRACKING"
    const val ACTION_STOP_TRACKING = "STOP_TRACKING"
    const val EXTRA_TRACK_ID = "track_id"

    /**
     * サービスが実際に記録中かどうか。プロセス内の状態なので、アプリ更新・クラッシュ等で
     * プロセスが終了すると自動的に false に戻る。起動時に DB のアクティブトラックが
     * 宙ぶらりん（サービス停止済み）かどうかの判定に使う。
     */
    @Volatile
    var isTracking: Boolean = false
      private set
  }

  @Inject
  lateinit var gpsTrackDao: GpsTrackDao

  @Inject
  lateinit var gpsPointDao: GpsPointDao

  @Inject
  lateinit var settingsRepository: SettingsRepository

  @Inject
  lateinit var gpsTrackRepository: GpsTrackRepository

  @Inject
  lateinit var placeRepository: PlaceRepository

  private val logger = Logger("LocationService")

  private val binder = LocationTrackingBinder()
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private var locationCallback: LocationCallback? = null

  /** 記録中のトラック。[execute] だけが読み書きする（キューの唯一の消費者なので排他不要）。 */
  private var currentTrackId: Long? = null

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * 記録の副作用（トラック生成・点の保存・補正/立ち寄り検出・確定）を積むキュー。
   * [execute] という**単一の消費者**が到着順に1件ずつ処理する。
   *
   * 以前はコールバックごとに `serviceScope.launch` を投げっぱなしにしていたため、次の競合があった:
   *  1. 生点の挿入が前後すると、補正後点列の差分INSERT（保存済み件数を基準に seq を振る）が
   *     ずれ、smoothed_points が**恒久的に**壊れる。
   *  2. 停止時の確定処理が、まだ保存されていないバッチを追い越す。
   *  3. トラック生成（非同期）より先に最初の位置が届くと、trackId が null で点が捨てられる。
   *
   * すべて同じキューに積むことで、この3つをまとめて塞ぐ。
   */
  private val commands = Channel<Command>(Channel.UNLIMITED)

  private sealed interface Command {
    /** 記録開始。[resumeTrackId] があれば既存トラックに続け、無ければ新規に作る。 */
    data class Start(val resumeTrackId: Long?) : Command

    /** 受信した位置バッチを保存し、補正・立ち寄り検出を進める。 */
    data class Ingest(val locations: List<Location>) : Command

    /** 記録終了。末尾を確定してトラックを閉じ、完了を [done] で呼び出し元へ知らせる。 */
    data class Finish(val done: CompletableDeferred<Unit>) : Command
  }

  private val _currentLocation = MutableStateFlow<Location?>(null)
  val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

  private val _locationCount = MutableStateFlow(0)
  val locationCount: StateFlow<Int> = _locationCount.asStateFlow()

  private var lastLocationTime = 0L
  private var locationTimeoutJob: kotlinx.coroutines.Job? = null

  inner class LocationTrackingBinder : Binder() {
    fun getService(): LocationTrackingService = this@LocationTrackingService
  }

  override fun onCreate() {
    super.onCreate()
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    createNotificationChannel()
    // キューの唯一の消費者。ここが1本しか無いことが順序保証の根拠。
    serviceScope.launch {
      for (command in commands) execute(command)
    }
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_TRACKING -> startLocationTracking()
      ACTION_RESUME_TRACKING -> {
        val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L).takeIf { it > 0 }
        startLocationTracking(resumeTrackId = trackId)
      }
      ACTION_STOP_TRACKING -> {
        stopLocationTracking()
        // 停止を受けたあとは OS にサービスを作り直させない。確定処理の途中でプロセスが死ぬと、
        // START_STICKY の再起動が null intent で入り、restoreTrackingIfNeeded() が
        // 「アクティブなトラックがある」と見て**記録を再開してしまう**（止めたはずの記録が
        // 復活する）。最後の onStartCommand の戻り値が採用されるので、ここで打ち切る。
        return START_NOT_STICKY
      }
      // intent が null＝START_STICKY による再起動（OSにkillされた後など）。
      // アクティブなトラックがあれば記録を再開して自己回復する。
      null -> restoreTrackingIfNeeded()
    }
    return START_STICKY
  }

  private fun startLocationTracking(resumeTrackId: Long? = null) {
    logger.d("startLocationTracking() called (resume=$resumeTrackId)")

    if (!hasLocationPermission()) {
      logger.e("Location permission not granted")
      stopSelf()
      return
    }

    if (!isLocationEnabled()) {
      logger.e("Location services are disabled")
      stopSelf()
      return
    }

    logger.d("Location permission granted, starting foreground service")

    val notification = createNotification("GPS位置を記録中...")
    startForeground(NOTIFICATION_ID, notification)
    isTracking = true

    // トラックの用意もキューに積む。以降の Ingest は必ずこの後ろに並ぶので、
    // 「trackId がまだ無くて最初の点を捨てる」競合が起きない。
    commands.trySend(Command.Start(resumeTrackId))

    startLocationUpdates()
  }

  /**
   * キューの唯一の消費者。[currentTrackId] はこのコルーチンだけが触るので排他は要らない。
   * 1件の失敗でキューを止めないよう、例外はここで握って次のコマンドへ進む。
   */
  private suspend fun execute(command: Command) {
    try {
      when (command) {
        is Command.Start -> {
          currentTrackId = command.resumeTrackId
            ?: gpsTrackDao.insertTrack(GpsTrackEntity(startTime = Date(), isActive = true))
          logger.d("Tracking track $currentTrackId (resumed=${command.resumeTrackId != null})")
          // 中断中にたまった生点があれば追いつかせる（新規トラックなら点が無いので no-op）。
          currentTrackId?.let { advance(it, isFinal = false) }
        }

        is Command.Ingest -> {
          // 停止後に遅れて届いたバッチは捨てる（確定済みトラックへ追記しない）。
          val trackId = currentTrackId ?: return
          gpsPointDao.insertPoints(command.locations.map { it.toGpsPointEntity(trackId) })
          advance(trackId, isFinal = false)
        }

        is Command.Finish -> {
          currentTrackId?.let { trackId ->
            // **先にトラックを閉じる。** 確定（全点の再平滑化・立ち寄り検出）は経路が長いほど
            // 時間がかかり、その最中にプロセスが死ぬと「アクティブなまま」の経路が残る。
            // 残ると次の起動で記録中として復活するので、止めた事実を真っ先に永続化する。
            // 終了時刻も、確定が終わった時刻ではなく止めた時刻の方が実態に合う。
            gpsTrackDao.finishTrack(trackId, Date())
            // 確定が途中で終わっても、総移動距離は起動時のバックフィルが拾い、取りこぼした
            // 立ち寄りは再解析で足せる（記録が復活するより害が小さい）。
            advance(trackId, isFinal = true)
          }
          currentTrackId = null
        }
      }
    } catch (e: Exception) {
      // command はそのまま出すと座標がログに乗るので型名だけにする。
      logger.e("Failed to handle ${command::class.simpleName}", e)
    } finally {
      // 失敗しても停止側を待たせない。
      if (command is Command.Finish) command.done.complete(Unit)
    }
  }

  /** 生点から補正後点列を進め、そこから立ち寄りを検出・保存する（記録中は末尾を暫定のまま残す）。 */
  private suspend fun advance(trackId: Long, isFinal: Boolean) {
    gpsTrackRepository.updateSmoothedForTrack(trackId, isFinal)
    placeRepository.updateStopsForTrack(trackId, isFinal)
  }

  /**
   * START_STICKY 再起動時に、DB にアクティブなトラックが残っていれば記録を再開する。
   * 無ければサービスを終了する。
   */
  private fun restoreTrackingIfNeeded() {
    serviceScope.launch {
      val activeTrack = gpsTrackDao.getActiveTrack()
      if (activeTrack != null) {
        logger.d("Restoring tracking for active track ${activeTrack.id}")
        withContext(Dispatchers.Main) {
          startLocationTracking(resumeTrackId = activeTrack.id)
        }
      } else {
        logger.d("No active track to restore; stopping service")
        stopSelf()
      }
    }
  }

  private fun stopLocationTracking() {
    isTracking = false
    stopLocationUpdates()

    // 確定処理はキューの**最後尾**に積む。まだ保存されていないバッチを追い越さないので、
    // 直前に届いた点まで含めて確定できる。
    // また、確定が終わってからサービスを畳む。先に stopSelf すると onDestroy の
    // serviceScope.cancel() が確定処理を中断し、滞在中に停止した立ち寄りが経路に
    // 保存されない（場所だけ残る）ため、必ずこの順序を守る。
    val done = CompletableDeferred<Unit>()
    commands.trySend(Command.Finish(done))
    serviceScope.launch {
      done.await()
      withContext(Dispatchers.Main) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }
  }

  private fun startLocationUpdates() {
    logger.d("startLocationUpdates() called")

    if (!hasLocationPermission()) {
      logger.e("Permission check failed in startLocationUpdates")
      return
    }

    // 設定された記録間隔（秒）を使う。バッチ許容で省電力化。
    val intervalMs = settingsRepository.currentGpsIntervalSeconds() * 1000L
    val locationRequest = LocationRequest.Builder(
      Priority.PRIORITY_BALANCED_POWER_ACCURACY,
      intervalMs,
    )
      .setMinUpdateIntervalMillis(intervalMs / 2)
      .setMaxUpdateDelayMillis(intervalMs + intervalMs / 2)
      .build()

    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)

        // バッチ許容（setMaxUpdateDelayMillis）のため、1回の結果に複数点がまとまって届くことがある。
        // lastLocation だけ使うと中間点を取りこぼすので、locations（古い順）を全部保存する。
        val locations = locationResult.locations
        if (locations.isNotEmpty()) {
          enqueueLocations(locations)

          val latest = locations.last()
          // 位置情報とカウントを更新（カウントは実際に受け取った点数だけ進める）
          _currentLocation.value = latest
          _locationCount.value = _locationCount.value + locations.size
          lastLocationTime = System.currentTimeMillis()

          // タイムアウト監視をリセット
          restartLocationTimeout()

          // 通知を更新
          val notification = createNotification(
            "GPS位置を記録中... (${
              String.format(Locale.US, "%.6f", latest.latitude)
            }, ${String.format(Locale.US, "%.6f", latest.longitude)})",
          )
          val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
          logger.w("Location result had no locations")
        }
      }
    }

    // 最後の既知位置を即座に取得
    try {
      logger.d("Getting last known location...")
      fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
        lastLocation?.let { location ->
          // 座標そのものはログに出さない（位置情報が logcat に残らないようにする）。
          logger.d("Last known location found (accuracy=${location.accuracy}m)")

          // 即座に表示用に更新（データベースには保存しない）
          _currentLocation.value = location

          // 通知を更新
          val notification = createNotification(
            "GPS位置を記録中... 最後の既知位置 (${
              String.format(Locale.US, "%.6f", location.latitude)
            }, ${String.format(Locale.US, "%.6f", location.longitude)})",
          )
          val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        } ?: run {
          logger.w("No last known location available")
        }
      }.addOnFailureListener { exception ->
        logger.w("Failed to get last known location", exception)
      }
    } catch (e: SecurityException) {
      logger.e("SecurityException when getting last known location", e)
    }

    try {
      logger.d("Requesting location updates...")
      fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback!!,
        Looper.getMainLooper(),
      )
      logger.d("Location updates requested successfully")

      // 30秒後に位置情報が取得できていない場合の監視タイマーを開始
      startLocationTimeout()
    } catch (e: SecurityException) {
      logger.e("SecurityException when requesting location updates", e)
      stopSelf()
    } catch (e: Exception) {
      logger.e("Exception when requesting location updates", e)
      stopSelf()
    }
  }

  private fun stopLocationUpdates() {
    locationCallback?.let {
      fusedLocationClient.removeLocationUpdates(it)
      locationCallback = null
    }
    locationTimeoutJob?.cancel()
    locationTimeoutJob = null
  }

  private fun startLocationTimeout() {
    locationTimeoutJob?.cancel()
    locationTimeoutJob = serviceScope.launch {
      delay(30000L) // 30秒待機

      if (_locationCount.value == 0) {
        logger.w("No location received after 30 seconds")

        // 通知を更新して状態を知らせる
        val notification = createNotification("GPS位置を記録中... （位置情報を取得中です）")
        val notificationManager =
          getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
      }
    }
  }

  private fun restartLocationTimeout() {
    locationTimeoutJob?.cancel()
    locationTimeoutJob = serviceScope.launch {
      delay(30000L) // 30秒間隔で監視

      val timeSinceLastLocation = System.currentTimeMillis() - lastLocationTime
      if (timeSinceLastLocation > 60000L) { // 1分以上位置情報がない場合
        logger.w("No location received for ${timeSinceLastLocation / 1000} seconds")

        val notification =
          createNotification("GPS位置を記録中... （位置情報の取得が遅延しています）")
        val notificationManager =
          getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
      }
    }
  }

  /**
   * 受信した位置バッチをキューへ積む（保存・補正・検出は [execute] が到着順に行う）。
   * ここでは待たないので、位置コールバックを塞がない。
   */
  private fun enqueueLocations(locations: List<Location>) {
    commands.trySend(Command.Ingest(locations))
  }

  /**
   * Location を保存用エンティティへ変換する。記録時にしか取れない付随情報（精度の内訳・MSL高度・
   * provider・単調時刻・モック判定）も、提供されていれば取りこぼさず保存する（minSdk 34）。
   */
  private fun Location.toGpsPointEntity(trackId: Long): GpsPointEntity = GpsPointEntity(
    trackId = trackId,
    latitude = latitude,
    longitude = longitude,
    altitude = if (hasAltitude()) altitude else null,
    accuracy = accuracy,
    speed = if (hasSpeed()) speed else null,
    bearing = if (hasBearing()) bearing else null,
    provider = provider,
    verticalAccuracyMeters = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
    mslAltitudeMeters = if (hasMslAltitude()) mslAltitudeMeters else null,
    mslAltitudeAccuracyMeters = if (hasMslAltitudeAccuracy()) mslAltitudeAccuracyMeters else null,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    isMock = isMock,
    extrasJson = serializeExtras(extras),
    timestamp = Date(time),
  )

  /**
   * Location.extras（Bundle）をベストエフォートで JSON 文字列に直す。中身は provider 依存で不透明だが、
   * 記録時にしか取れないので丸ごと残す。スカラー/文字列はそのまま、それ以外は toString() で文字列化する
   * （バイナリでは保存しない＝将来も可読）。空/無し、または直列化に失敗したら null。
   */
  @Suppress("DEPRECATION")
  private fun serializeExtras(extras: Bundle?): String? {
    if (extras == null || extras.isEmpty) return null
    return try {
      val json = JSONObject()
      for (key in extras.keySet()) {
        try {
          when (val value = extras.get(key)) {
            null -> json.put(key, JSONObject.NULL)
            is String, is Boolean, is Int, is Long, is Double -> json.put(key, value)
            is Float -> json.put(key, value.toDouble())
            else -> json.put(key, value.toString())
          }
        } catch (e: Exception) {
          // 1キーの失敗で全体を捨てない（残せるものは残す）。
          logger.w("Failed to serialize extras key=$key", e)
        }
      }
      json.toString().takeIf { it != "{}" }
    } catch (e: Exception) {
      logger.w("Failed to serialize location extras", e)
      null
    }
  }

  private fun hasLocationPermission(): Boolean = PermissionUtils.hasLocationPermissions(this)

  private fun isLocationEnabled(): Boolean {
    val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    return gpsEnabled || networkEnabled
  }

  private fun createNotificationChannel() {
    // 通知チャンネルは API 26 以降で必須。minSdk 34 なのでバージョン分岐は不要。
    val channel = NotificationChannel(
      CHANNEL_ID,
      "位置情報記録",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "GPS位置情報を記録中です"
      setShowBadge(false)
    }

    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)
  }

  private fun createNotification(contentText: String): Notification {
    val intent = Intent(this, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Pathly - GPS記録中")
      .setContentText(contentText)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setSilent(true)
      .build()
  }

  override fun onDestroy() {
    super.onDestroy()
    isTracking = false
    stopLocationUpdates()
    commands.close()
    serviceScope.cancel()
  }
}
