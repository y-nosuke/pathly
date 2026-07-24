package com.pathly

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.history.HistoryScreen
import com.pathly.presentation.history.TrackDetailScreen
import com.pathly.presentation.history.TrackDetailViewModel
import com.pathly.presentation.settings.SettingsScreen
import com.pathly.presentation.tracking.TrackingScreen
import com.pathly.presentation.tracking.TrackingViewModel
import com.pathly.ui.theme.PathlyAndroidTheme
import com.pathly.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

enum class BottomNavItem(
  val title: String,
  @param:DrawableRes val icon: Int,
) {
  TRACKING("記録", R.drawable.ic_location_on),
  HISTORY("履歴", R.drawable.ic_list),
  SETTINGS("設定", R.drawable.ic_settings),
}

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
        viewModel = hiltViewModel()
        MainScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
  onRequestPermission: () -> Unit,
) {
  var selectedTab by remember { mutableStateOf(BottomNavItem.TRACKING) }
  var selectedTrack by remember { mutableStateOf<GpsTrack?>(null) }

  // 詳細表示中は一覧へ戻す
  BackHandler(enabled = selectedTrack != null) {
    selectedTrack = null
  }
  // ホーム以外のタブではホーム（記録）へ戻す。ホームで何もなければ既定動作でアプリ終了。
  BackHandler(enabled = selectedTrack == null && selectedTab != BottomNavItem.TRACKING) {
    selectedTab = BottomNavItem.TRACKING
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      if (selectedTrack == null) {
        NavigationBar {
          BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
              selected = selectedTab == item,
              onClick = { selectedTab = item },
              label = { Text(item.title) },
              icon = { Icon(painterResource(item.icon), contentDescription = item.title) },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    when {
      selectedTrack != null -> {
        val track = selectedTrack!!
        val detailViewModel: TrackDetailViewModel = hiltViewModel()
        val stops by detailViewModel.stops.collectAsState()
        val displayTrack by detailViewModel.displayTrack.collectAsState()
        val unresolvedCount by detailViewModel.unresolvedCount.collectAsState()
        LaunchedEffect(track.id) { detailViewModel.load(track) }
        TrackDetailScreen(
          // 保存済みの補正後点列を反映したトラックがあればそれを表示する。
          track = displayTrack?.takeIf { it.id == track.id } ?: track,
          onBackClick = { selectedTrack = null },
          modifier = Modifier.padding(innerPadding),
          stops = stops,
          unresolvedCount = unresolvedCount,
          onEditPlaceName = { placeId, name -> detailViewModel.updatePlaceName(placeId, name) },
          onResolveNames = { detailViewModel.resolveNames() },
          onReanalyze = { detailViewModel.reanalyze() },
        )
      }

      selectedTab == BottomNavItem.TRACKING -> {
        TrackingScreen(
          modifier = Modifier.padding(innerPadding),
          onRequestPermission = onRequestPermission,
        )
      }

      selectedTab == BottomNavItem.HISTORY -> {
        HistoryScreen(
          modifier = Modifier.padding(innerPadding),
          onTrackClick = { track -> selectedTrack = track },
        )
      }

      selectedTab == BottomNavItem.SETTINGS -> {
        SettingsScreen(
          modifier = Modifier.padding(innerPadding),
        )
      }
    }
  }
}
