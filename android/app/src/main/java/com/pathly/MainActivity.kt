package com.pathly

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.data.settings.SettingsRepository
import com.pathly.presentation.navigation.PathlyNavHost
import com.pathly.presentation.tracking.TrackingViewModel
import com.pathly.ui.theme.PathlyAndroidTheme
import com.pathly.ui.theme.PathlyBuildColors
import com.pathly.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.graphics.Color as AndroidColor

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  // 権限ランチャー（Activity 側）と記録画面で同一インスタンスを共有するため、
  // TrackingViewModel は Activity スコープで持つ。
  private val viewModel: TrackingViewModel by viewModels()

  // 画面の色（壁紙の色を使うか）は全画面にかかるので、テーマを置く Activity で読む。
  @Inject lateinit var settingsRepository: SettingsRepository

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
    if (BuildConfig.DEBUG) {
      // 開発版は上端を黄に塗るので、明暗の設定によらずステータスバーの文字・アイコンを暗くする。
      enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT))
    } else {
      enableEdgeToEdge()
    }
    setContent {
      val useDynamicColor by settingsRepository.useDynamicColor.collectAsStateWithLifecycle()
      PathlyAndroidTheme(dynamicColor = useDynamicColor) {
        Box(modifier = Modifier.fillMaxSize()) {
          PathlyNavHost(
            trackingViewModel = viewModel,
            onRequestPermission = {
              locationPermissionLauncher.launch(
                PermissionUtils.PermissionGroups.ALL_REQUIRED_PERMISSIONS,
              )
            },
          )
          if (BuildConfig.DEBUG) DevBuildStatusBar()
        }
      }
    }
  }
}

/**
 * 開発版の印。ステータスバーの地をアイコンと同じ黄に塗り、開いているのが開発版だと分かるようにする
 * （→ docs/designs/release.md）。画面の区画（地図の四隅・検索窓）は使わないので、どの画面にも重ねられる。
 */
@Composable
private fun DevBuildStatusBar() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsTopHeight(WindowInsets.statusBars)
      .background(PathlyBuildColors.DevBuild),
  )
}
