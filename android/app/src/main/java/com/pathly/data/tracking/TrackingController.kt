package com.pathly.data.tracking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import com.pathly.service.LocationTrackingService
import com.pathly.util.Logger
import com.pathly.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 記録サービス（[LocationTrackingService]）の起動・停止・バインドと、端末側の状態
 * （位置権限・位置情報の ON/OFF・電池の最適化）をまとめて扱う。
 *
 * これらはもともと TrackingViewModel が Application を持って直接叩いていたが、
 *  - ViewModel が Service 参照を保持して lint の StaticFieldLeak を出す
 *  - Android framework 依存で ViewModel のユニットテストがほぼ書けない
 * という問題があったため、アプリスコープのこのクラスへ寄せた。ViewModel は
 * ここが公開する Flow と suspend/同期メソッドだけを見る。
 *
 * バインドはアプリのプロセス寿命に紐づく（画面の生き死にでは切らない）。記録中に
 * 画面を離れても位置の更新を取りこぼさず、停止時に解除する。
 */
@Singleton
class TrackingController @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {

  private val logger = Logger("TrackingController")

  /** ServiceConnection のコールバックはメインスレッドで来るので、購読もメインで揃える。 */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var mirrorJob: Job? = null
  private var bound = false

  private val _currentLocation = MutableStateFlow<Location?>(null)

  /** サービスが受け取った最新の位置（未接続・停止後は null）。 */
  val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

  private val _locationCount = MutableStateFlow(0)

  /** 記録開始からの受信点数。 */
  val locationCount: StateFlow<Int> = _locationCount.asStateFlow()

  private val _isTracking = MutableStateFlow(LocationTrackingService.isTracking)

  /** 記録中か。サービスへ接続できたときは、サービス自身の状態で上書きする。 */
  val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

  private val _unexpectedDisconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /**
   * サービスとの接続が予期せず切れた（プロセスのクラッシュ等）。[stop] による通常の解除では流れない
   * （onServiceDisconnected は異常時にしか呼ばれない）。
   */
  val unexpectedDisconnect: SharedFlow<Unit> = _unexpectedDisconnect.asSharedFlow()

  /** 記録を開始できない理由。開始前に判定し、駄目ならサービスを起動しない。 */
  enum class StartFailure {
    /** 位置権限（と通知権限）が揃っていない。 */
    MISSING_PERMISSION,

    /** 端末の位置情報がOFF（GPS・ネットワークともに無効）。 */
    LOCATION_DISABLED,
  }

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      val service = (binder as? LocationTrackingService.LocationTrackingBinder)?.getService() ?: return
      logger.d("Service connected")
      mirrorJob?.cancel()
      mirrorJob = scope.launch {
        launch { service.currentLocation.collect { _currentLocation.value = it } }
        launch { service.locationCount.collect { _locationCount.value = it } }
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      logger.d("Service disconnected")
      mirrorJob?.cancel()
      mirrorJob = null
      _isTracking.value = false
      _unexpectedDisconnect.tryEmit(Unit)
    }
  }

  /**
   * 記録を開始する。開始できない場合は理由を返し、**サービスは起動しない**。
   *
   * 事前に権限と位置情報の ON/OFF を確かめるのが重要で、これを怠って
   * startForegroundService したうえでサービス側が startForeground せずに終了すると、
   * バインド（BIND_AUTO_CREATE）でサービスが生き残ってしまい、UI は「記録中」のまま
   * 何も記録されない状態になる（FGS の起動時間制限に抵触する恐れもある）。
   */
  fun start(): StartFailure? = launchService(LocationTrackingService.ACTION_START_TRACKING)

  /** 中断されたトラックに続けて記録を再開する。判定は [start] と同じ。 */
  fun resume(trackId: Long): StartFailure? = launchService(LocationTrackingService.ACTION_RESUME_TRACKING) {
    putExtra(LocationTrackingService.EXTRA_TRACK_ID, trackId)
  }

  private fun launchService(action: String, configure: Intent.() -> Unit = {}): StartFailure? {
    if (!hasRequiredPermissions()) {
      logger.w("Cannot start tracking: required permissions are missing")
      return StartFailure.MISSING_PERMISSION
    }
    if (!isLocationEnabled()) {
      logger.w("Cannot start tracking: location services are disabled")
      return StartFailure.LOCATION_DISABLED
    }
    context.startForegroundService(
      Intent(context, LocationTrackingService::class.java).apply {
        this.action = action
        configure()
      },
    )
    bind()
    _isTracking.value = true
    return null
  }

  /** 記録を停止する。確定処理はサービス側がキューの最後尾で行う。 */
  fun stop() {
    context.startService(
      Intent(context, LocationTrackingService::class.java).apply {
        action = LocationTrackingService.ACTION_STOP_TRACKING
      },
    )
    unbind()
    _isTracking.value = false
    // 停止後に古い現在地が残って地図がそこへ寄るのを防ぐ。
    _currentLocation.value = null
    _locationCount.value = 0
  }

  /** 記録中プロセスが生存しているときに、画面から復帰してサービスへ繋ぎ直す。 */
  fun reattach() {
    _isTracking.value = LocationTrackingService.isTracking
    if (LocationTrackingService.isTracking) bind()
  }

  private fun bind() {
    if (bound) return
    bound = context.bindService(
      Intent(context, LocationTrackingService::class.java),
      connection,
      Context.BIND_AUTO_CREATE,
    )
  }

  private fun unbind() {
    if (!bound) return
    context.unbindService(connection)
    bound = false
    mirrorJob?.cancel()
    mirrorJob = null
  }

  /** アプリで必要な権限（位置＋通知）が揃っているか。 */
  fun hasRequiredPermissions(): Boolean = PermissionUtils.hasAllRequiredPermissions(context)

  /** 端末の位置情報が有効か（GPS またはネットワーク）。 */
  fun isLocationEnabled(): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
      locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
  }

  /** 電池の最適化が無効化されているか（＝バックグラウンドで制限されないか）。 */
  fun isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
  }

  /** 電池の最適化の無効化を要求するシステム画面を開く。 */
  fun requestDisableBatteryOptimization() {
    context.startActivity(
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      },
    )
  }
}
