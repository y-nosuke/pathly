package com.pathly

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathly.presentation.navigation.PathlyNavHost
import com.pathly.presentation.tracking.TrackingViewModel
import com.pathly.ui.theme.PathlyAndroidTheme
import com.pathly.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private lateinit var viewModel: TrackingViewModel

  private val locationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    viewModel.updateLocationPermission(allGranted)
    // フォアグラウンド位置が許可されたら、続けて「常に許可」を要求する
    // （アプリを閉じてもバックグラウンドで記録を続けるため）
    if (allGranted) {
      requestBackgroundLocationIfNeeded()
    }
  }

  // バックグラウンド位置（「常に許可」）は前景位置とは別に要求する必要がある。
  // 許可されなくても前景では記録できるため、結果は状態更新のみに使う。
  private val backgroundLocationLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { }

  private fun requestBackgroundLocationIfNeeded() {
    if (!PermissionUtils.hasBackgroundLocationPermission(this)) {
      backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PathlyAndroidTheme {
        // 権限ランチャー（Activity 側）と記録画面で同一インスタンスを共有するため、
        // TrackingViewModel は Activity スコープで生成して NavHost へ渡す。
        viewModel = hiltViewModel()
        PathlyNavHost(
          trackingViewModel = viewModel,
          onRequestPermission = {
            locationPermissionLauncher.launch(
              PermissionUtils.PermissionGroups.ALL_REQUIRED_PERMISSIONS,
            )
          },
        )
      }
    }
  }
}
