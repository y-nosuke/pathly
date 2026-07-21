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
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
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
import com.pathly.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
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

  private val binder = LocationTrackingBinder()
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private var locationCallback: LocationCallback? = null
  private var currentTrackId: Long? = null

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_TRACKING -> startLocationTracking()
      ACTION_RESUME_TRACKING -> {
        val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L).takeIf { it > 0 }
        startLocationTracking(resumeTrackId = trackId)
      }
      ACTION_STOP_TRACKING -> stopLocationTracking()
      // intent が null＝START_STICKY による再起動（OSにkillされた後など）。
      // アクティブなトラックがあれば記録を再開して自己回復する。
      null -> restoreTrackingIfNeeded()
    }
    return START_STICKY
  }

  private fun startLocationTracking(resumeTrackId: Long? = null) {
    Log.d("LocationService", "startLocationTracking() called (resume=$resumeTrackId)")

    if (!hasLocationPermission()) {
      Log.e("LocationService", "Location permission not granted")
      stopSelf()
      return
    }

    if (!isLocationEnabled()) {
      Log.e("LocationService", "Location services are disabled")
      stopSelf()
      return
    }

    Log.d("LocationService", "Location permission granted, starting foreground service")

    val notification = createNotification("GPS位置を記録中...")
    startForeground(NOTIFICATION_ID, notification)
    isTracking = true

    if (resumeTrackId != null) {
      // 中断されたトラックに続けて記録する
      currentTrackId = resumeTrackId
      Log.d("LocationService", "Resuming existing track with ID: $resumeTrackId")
    } else {
      serviceScope.launch {
        // 新しいトラックを作成
        val track = GpsTrackEntity(
          startTime = Date(),
          isActive = true,
        )
        currentTrackId = gpsTrackDao.insertTrack(track)
        Log.d("LocationService", "Created new track with ID: $currentTrackId")
      }
    }

    startLocationUpdates()
  }

  /**
   * START_STICKY 再起動時に、DB にアクティブなトラックが残っていれば記録を再開する。
   * 無ければサービスを終了する。
   */
  private fun restoreTrackingIfNeeded() {
    serviceScope.launch {
      val activeTrack = gpsTrackDao.getActiveTrack()
      if (activeTrack != null) {
        Log.d("LocationService", "Restoring tracking for active track ${activeTrack.id}")
        withContext(Dispatchers.Main) {
          startLocationTracking(resumeTrackId = activeTrack.id)
        }
      } else {
        Log.d("LocationService", "No active track to restore; stopping service")
        stopSelf()
      }
    }
  }

  private fun stopLocationTracking() {
    isTracking = false
    stopLocationUpdates()

    serviceScope.launch {
      currentTrackId?.let { trackId ->
        gpsTrackDao.finishTrack(trackId, Date())
      }
      currentTrackId = null
    }

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun startLocationUpdates() {
    Log.d("LocationService", "startLocationUpdates() called")

    if (!hasLocationPermission()) {
      Log.e("LocationService", "Permission check failed in startLocationUpdates")
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

        locationResult.lastLocation?.let { location ->
          saveLocationToDatabase(location)

          // 位置情報とカウントを更新
          _currentLocation.value = location
          _locationCount.value = _locationCount.value + 1
          lastLocationTime = System.currentTimeMillis()

          // タイムアウト監視をリセット
          restartLocationTimeout()

          // 通知を更新
          val notification = createNotification(
            "GPS位置を記録中... (${
              String.format(
                "%.6f",
                location.latitude,
              )
            }, ${String.format("%.6f", location.longitude)})",
          )
          val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        } ?: run {
          Log.w("LocationService", "Location result was null")
        }
      }
    }

    // 最後の既知位置を即座に取得
    try {
      Log.d("LocationService", "Getting last known location...")
      fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
        lastLocation?.let { location ->
          Log.d(
            "LocationService",
            "Last known location found: lat=${location.latitude}, lon=${location.longitude}",
          )

          // 即座に表示用に更新（データベースには保存しない）
          _currentLocation.value = location

          // 通知を更新
          val notification = createNotification(
            "GPS位置を記録中... 最後の既知位置 (${
              String.format(
                "%.6f",
                location.latitude,
              )
            }, ${String.format("%.6f", location.longitude)})",
          )
          val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        } ?: run {
          Log.w("LocationService", "No last known location available")
        }
      }.addOnFailureListener { exception ->
        Log.w("LocationService", "Failed to get last known location", exception)
      }
    } catch (e: SecurityException) {
      Log.e("LocationService", "SecurityException when getting last known location", e)
    }

    try {
      Log.d("LocationService", "Requesting location updates...")
      fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback!!,
        Looper.getMainLooper(),
      )
      Log.d("LocationService", "Location updates requested successfully")

      // 30秒後に位置情報が取得できていない場合の監視タイマーを開始
      startLocationTimeout()
    } catch (e: SecurityException) {
      Log.e("LocationService", "SecurityException when requesting location updates", e)
      stopSelf()
    } catch (e: Exception) {
      Log.e("LocationService", "Exception when requesting location updates", e)
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
        Log.w("LocationService", "No location received after 30 seconds")

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
        Log.w(
          "LocationService",
          "No location received for ${timeSinceLastLocation / 1000} seconds",
        )

        val notification =
          createNotification("GPS位置を記録中... （位置情報の取得が遅延しています）")
        val notificationManager =
          getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
      }
    }
  }

  private fun saveLocationToDatabase(location: Location) {
    currentTrackId?.let { trackId ->
      serviceScope.launch {
        val gpsPoint = GpsPointEntity(
          trackId = trackId,
          latitude = location.latitude,
          longitude = location.longitude,
          altitude = if (location.hasAltitude()) location.altitude else null,
          accuracy = location.accuracy,
          speed = if (location.hasSpeed()) location.speed else null,
          bearing = if (location.hasBearing()) location.bearing else null,
          timestamp = Date(location.time),
        )

        gpsPointDao.insertPoint(gpsPoint)
      }
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    serviceScope.cancel()
  }
}
