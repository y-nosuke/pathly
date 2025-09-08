package com.pathly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.history.HistoryScreen
import com.pathly.presentation.history.TrackDetailScreen
import com.pathly.presentation.tracking.TrackingScreen
import com.pathly.presentation.tracking.TrackingViewModel
import com.pathly.ui.theme.PathlyAndroidTheme
import com.pathly.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

enum class BottomNavItem(
  val title: String,
  val icon: ImageVector
) {
  TRACKING("記録", Icons.Filled.PlayArrow),
  HISTORY("履歴", Icons.AutoMirrored.Filled.List)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private lateinit var viewModel: TrackingViewModel

  private val locationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    viewModel.updateLocationPermission(allGranted)
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
              PermissionUtils.PermissionGroups.ALL_REQUIRED_PERMISSIONS
            )
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
  onRequestPermission: () -> Unit
) {
  var selectedTab by remember { mutableStateOf(BottomNavItem.TRACKING) }
  var selectedTrack by remember { mutableStateOf<GpsTrack?>(null) }

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
              icon = { Icon(item.icon, contentDescription = item.title) }
            )
          }
        }
      }
    }
  ) { innerPadding ->
    when {
      selectedTrack != null -> {
        TrackDetailScreen(
          track = selectedTrack!!,
          onBackClick = { selectedTrack = null },
          modifier = Modifier.padding(innerPadding)
        )
      }

      selectedTab == BottomNavItem.TRACKING -> {
        TrackingScreen(
          modifier = Modifier.padding(innerPadding),
          onRequestPermission = onRequestPermission
        )
      }

      selectedTab == BottomNavItem.HISTORY -> {
        HistoryScreen(
          modifier = Modifier.padding(innerPadding),
          onTrackClick = { track -> selectedTrack = track }
        )
      }
    }
  }
}