package com.pathly.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

  companion object {
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "LocationTrackingChannel"
    const val ACTION_START_TRACKING = "START_TRACKING"
    const val ACTION_STOP_TRACKING = "STOP_TRACKING"

    private const val LOCATION_REQUEST_INTERVAL = 30000L // 30秒
    private const val LOCATION_REQUEST_FASTEST_INTERVAL = 15000L // 15秒
  }

  @Inject
  lateinit var gpsTrackDao: GpsTrackDao

  @Inject
  lateinit var gpsPointDao: GpsPointDao

  private val binder = LocationTrackingBinder()
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private var locationCallback: LocationCallback? = null
  private var currentTrackId: Long? = null

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
      ACTION_STOP_TRACKING -> stopLocationTracking()
    }
    return START_STICKY
  }

  private fun startLocationTracking() {
    if (!hasLocationPermission()) {
      stopSelf()
      return
    }

    val notification = createNotification("GPS位置を記録中...")
    startForeground(NOTIFICATION_ID, notification)

    serviceScope.launch {
      // 新しいトラックを作成
      val track = GpsTrackEntity(
        startTime = Date(),
        isActive = true
      )
      currentTrackId = gpsTrackDao.insertTrack(track)
    }

    startLocationUpdates()
  }

  private fun stopLocationTracking() {
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
    if (!hasLocationPermission()) return

    val locationRequest = LocationRequest.Builder(
      Priority.PRIORITY_BALANCED_POWER_ACCURACY,
      LOCATION_REQUEST_INTERVAL
    )
      .setMinUpdateIntervalMillis(LOCATION_REQUEST_FASTEST_INTERVAL)
      .build()

    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)

        locationResult.lastLocation?.let { location ->
          saveLocationToDatabase(location)

          // 通知を更新
          val notification = createNotification(
            "GPS位置を記録中... (${location.latitude}, ${location.longitude})"
          )
          val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        }
      }
    }

    try {
      fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback!!,
        Looper.getMainLooper()
      )
    } catch (e: SecurityException) {
      // 権限がない場合
      stopSelf()
    }
  }

  private fun stopLocationUpdates() {
    locationCallback?.let {
      fusedLocationClient.removeLocationUpdates(it)
      locationCallback = null
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
          timestamp = Date(location.time)
        )

        gpsPointDao.insertPoint(gpsPoint)
      }
    }
  }

  private fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "位置情報記録",
        NotificationManager.IMPORTANCE_LOW
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
      this, 0, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
    stopLocationUpdates()
    serviceScope.cancel()
  }
}