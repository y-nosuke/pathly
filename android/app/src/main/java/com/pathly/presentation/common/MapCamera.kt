package com.pathly.presentation.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.domain.model.GeoBounds

/** 地図の既定の位置（東京駅）。現在地も、寄せる範囲もまだ無いときに出す。 */
internal val DefaultMapLocation = LatLng(35.6762, 139.6503)

/** 地図で寄せる範囲に変える。 */
internal fun GeoBounds.toLatLngBounds(): LatLngBounds = LatLngBounds(LatLng(south, west), LatLng(north, east))

/** 全部が入る範囲に変える。空なら null。 */
internal fun Iterable<GeoBounds>.toLatLngBounds(): LatLngBounds? {
  if (none()) return null
  return LatLngBounds.builder().apply {
    forEach {
      include(LatLng(it.south, it.west))
      include(LatLng(it.north, it.east))
    }
  }.build()
}

/** 地図の範囲を、探すときの範囲（[GeoBounds]）に変える。 */
internal fun LatLngBounds.toGeoBounds(): GeoBounds = GeoBounds(southwest.latitude, southwest.longitude, northeast.latitude, northeast.longitude)

/**
 * 今の現在地を一度だけ取りに行き、取れたら [onLocated] を呼ぶ（地図を現在地へ寄せるため）。
 * 権限が無い・取れなかったときは何もしない（地図は今の位置のまま）。
 */
internal fun requestCurrentLatLng(context: Context, onLocated: (LatLng) -> Unit) {
  try {
    LocationServices.getFusedLocationProviderClient(context)
      .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
      .addOnSuccessListener { location -> location?.let { onLocated(LatLng(it.latitude, it.longitude)) } }
  } catch (e: SecurityException) {
    // 権限が無ければ今の位置のまま。
  }
}

/** 位置情報の許可（正確・おおよそのどちらか）があるか。おおよその位置だけの許可でも現在地は取れる。 */
internal fun isLocationPermitted(context: Context): Boolean = listOf(
  Manifest.permission.ACCESS_FINE_LOCATION,
  Manifest.permission.ACCESS_COARSE_LOCATION,
).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * 位置情報の許可があるか。許可は記録画面などで後から出せるので、画面に戻るたびに見直す。
 * 地図の画面は、これが true のときだけ地図に現在地を出し、右下に現在地ボタンを置く（地図の画面からは許可を求めない）。
 */
@Composable
internal fun rememberLocationPermitted(): Boolean {
  val context = LocalContext.current
  var permitted by remember { mutableStateOf(isLocationPermitted(context)) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permitted = isLocationPermitted(context) }
  return permitted
}

/**
 * 一覧のタブ（場所・履歴）の地図表示のカメラ。見ていた範囲と、最後に範囲を合わせたときの印を持つ。
 * 地図表示の外（一覧と切り替える場所）で持ち、一覧表示に切り替えても・詳細から戻っても範囲を保つ。
 */
@Stable
internal class ListMapCamera(
  val position: CameraPositionState,
  fittedFor: MutableState<String?>,
  initialized: MutableState<Boolean>,
) {
  /** 最後に範囲を合わせたときの印（場所タブは絞り込んだ場所の id を並べたもの）。null はまだ一度も合わせていない。 */
  var fittedFor: String? by fittedFor

  /** 地図を一度開いて、初めの位置合わせ（現在地へ寄せるなど）を済ませたか（範囲を合わせる印を持たない画面向け）。 */
  var isInitialized: Boolean by initialized
}

/** [ListMapCamera] を作る。どちらも画面の状態として残り、詳細から戻っても保たれる。 */
@Composable
internal fun rememberListMapCamera(zoom: Float = 12f): ListMapCamera {
  val position = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(DefaultMapLocation, zoom)
  }
  val fittedFor = rememberSaveable { mutableStateOf<String?>(null) }
  val initialized = rememberSaveable { mutableStateOf(false) }
  return remember(position, fittedFor, initialized) { ListMapCamera(position, fittedFor, initialized) }
}
