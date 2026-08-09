package com.pathly.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pathly.R
import com.pathly.presentation.history.HistoryScreen
import com.pathly.presentation.history.TrackDetailScreen
import com.pathly.presentation.history.TrackDetailViewModel
import com.pathly.presentation.places.AddPlaceRoute
import com.pathly.presentation.places.PlaceDetailRoute
import com.pathly.presentation.places.PlacesListRoute
import com.pathly.presentation.places.PlacesViewModel
import com.pathly.presentation.places.SearchAddRoute
import com.pathly.presentation.settings.SettingsScreen
import com.pathly.presentation.tracking.TrackingScreen
import com.pathly.presentation.tracking.TrackingViewModel

/** アプリ全体のルート定義。引数つきの route は生成用のヘルパー関数を用意する。 */
object Routes {
  const val TRACKING = "tracking"
  const val HISTORY = "history"
  const val SETTINGS = "settings"

  // 「場所」タブはネストグラフ。配下の 4 目的地で PlacesViewModel を共有する。
  const val PLACES_GRAPH = "places"
  const val PLACES_LIST = "places_list"
  const val PLACE_ADD = "place_add"
  const val PLACE_SEARCH = "place_search"
  const val PLACE_DETAIL = "place_detail/{placeId}"

  const val TRACK_DETAIL = "track_detail/{trackId}"

  fun placeDetail(placeId: Long) = "place_detail/$placeId"
  fun trackDetail(trackId: Long) = "track_detail/$trackId"
}

/** ボトムナビの項目。route は選択判定・遷移に使う（「場所」はグラフ route）。 */
enum class BottomNavItem(
  val title: String,
  @param:DrawableRes val icon: Int,
  val route: String,
) {
  TRACKING("記録", R.drawable.ic_location_on, Routes.TRACKING),
  HISTORY("履歴", R.drawable.ic_list, Routes.HISTORY),
  PLACES("場所", R.drawable.ic_place, Routes.PLACES_GRAPH),
  SETTINGS("設定", R.drawable.ic_settings, Routes.SETTINGS),
}

@Composable
fun PathlyNavHost(
  trackingViewModel: TrackingViewModel,
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  // 経路詳細（全画面）ではボトムバーを隠す。それ以外（各タブ配下の追加・検索・場所詳細を含む）は表示。
  val hideBottomBar = currentDestination?.route == Routes.TRACK_DETAIL

  Scaffold(
    modifier = modifier.fillMaxSize(),
    bottomBar = {
      if (!hideBottomBar) {
        NavigationBar {
          BottomNavItem.entries.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(item.route) {
                  // タブごとに状態を保存・復元し、バックスタックを起点まで畳む。
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              label = { Text(item.title) },
              icon = { Icon(painterResource(item.icon), contentDescription = item.title) },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = Routes.TRACKING,
      modifier = Modifier.padding(innerPadding),
      // 以前の手書き when ナビと同じく、遷移アニメーションは付けない（瞬時に切り替える）。
      enterTransition = { EnterTransition.None },
      exitTransition = { ExitTransition.None },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { ExitTransition.None },
    ) {
      composable(Routes.TRACKING) {
        TrackingScreen(
          onRequestPermission = onRequestPermission,
          onOpenPlaceDetail = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
          viewModel = trackingViewModel,
        )
      }

      composable(Routes.HISTORY) {
        HistoryScreen(
          onTrackClick = { track -> navController.navigate(Routes.trackDetail(track.id)) },
        )
      }

      composable(Routes.SETTINGS) {
        SettingsScreen()
      }

      placesGraph(navController)

      composable(
        route = Routes.TRACK_DETAIL,
        arguments = listOf(navArgument("trackId") { type = NavType.LongType }),
      ) { entry ->
        val trackId = entry.arguments?.getLong("trackId") ?: return@composable
        TrackDetailRoute(
          trackId = trackId,
          onBack = { navController.popBackStack() },
          onOpenPlaceDetail = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
        )
      }
    }
  }
}

/** 「場所」タブのネストグラフ。配下の目的地は places グラフにスコープした PlacesViewModel を共有する。 */
private fun NavGraphBuilder.placesGraph(navController: NavController) {
  navigation(startDestination = Routes.PLACES_LIST, route = Routes.PLACES_GRAPH) {
    composable(Routes.PLACES_LIST) { entry ->
      PlacesListRoute(
        viewModel = entry.sharedPlacesViewModel(navController),
        onAddByMap = { navController.navigate(Routes.PLACE_ADD) },
        onAddBySearch = { navController.navigate(Routes.PLACE_SEARCH) },
        onItemClick = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
      )
    }

    composable(Routes.PLACE_ADD) { entry ->
      AddPlaceRoute(
        viewModel = entry.sharedPlacesViewModel(navController),
        onDone = { navController.popBackStack() },
      )
    }

    composable(Routes.PLACE_SEARCH) { entry ->
      SearchAddRoute(
        viewModel = entry.sharedPlacesViewModel(navController),
        onDone = { navController.popBackStack() },
      )
    }

    composable(
      route = Routes.PLACE_DETAIL,
      arguments = listOf(navArgument("placeId") { type = NavType.LongType }),
    ) { entry ->
      val placeId = entry.arguments?.getLong("placeId") ?: return@composable
      PlaceDetailRoute(
        viewModel = entry.sharedPlacesViewModel(navController),
        placeId = placeId,
        onBack = { navController.popBackStack() },
        onOpenTrack = { trackId -> navController.navigate(Routes.trackDetail(trackId)) },
        onOpenPlaceDetail = { pid -> navController.navigate(Routes.placeDetail(pid)) },
      )
    }
  }
}

/** places グラフのエントリにスコープした共有 PlacesViewModel を取得する。 */
@Composable
private fun NavBackStackEntry.sharedPlacesViewModel(navController: NavController): PlacesViewModel {
  val parentEntry = remember(this) { navController.getBackStackEntry(Routes.PLACES_GRAPH) }
  return hiltViewModel(parentEntry)
}

/** 経路詳細（track_detail）。trackId から自前でロードし、読み込み中はスピナーを出す。 */
@Composable
private fun TrackDetailRoute(
  trackId: Long,
  onBack: () -> Unit,
  onOpenPlaceDetail: (placeId: Long) -> Unit = {},
) {
  val viewModel: TrackDetailViewModel = hiltViewModel()
  LaunchedEffect(trackId) { viewModel.load(trackId) }

  val displayTrack by viewModel.displayTrack.collectAsStateWithLifecycle()
  val track = displayTrack
  if (track == null || track.id != trackId) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val stops by viewModel.stops.collectAsStateWithLifecycle()
  val unresolvedCount by viewModel.unresolvedCount.collectAsStateWithLifecycle()
  val message by viewModel.message.collectAsStateWithLifecycle()
  val reanalyzeCandidates by viewModel.reanalyzeCandidates.collectAsStateWithLifecycle()
  val registeredPlaces by viewModel.registeredPlaces.collectAsStateWithLifecycle()
  val showRegisteredPlaces by viewModel.showRegisteredPlaces.collectAsStateWithLifecycle()

  TrackDetailScreen(
    track = track,
    onBackClick = onBack,
    stops = stops,
    unresolvedCount = unresolvedCount,
    message = message,
    onEditPlaceName = viewModel::updatePlaceName,
    onEditStopNote = { stopId, note -> viewModel.updateStopNote(stopId, note) },
    onResolveNames = { viewModel.resolveNames() },
    onReanalyze = { viewModel.reanalyze() },
    reanalyzeCandidates = reanalyzeCandidates,
    onAddStops = { viewModel.addStops(it) },
    onDismissReanalyze = { viewModel.dismissReanalyze() },
    onDeleteStops = { stopIds -> viewModel.deleteStops(stopIds) },
    onUndoDeletion = { viewModel.undoDeletion() },
    onAddManualStop = { lat, lng, arrival, departure, name, googlePlaceId, forceNewPlace ->
      viewModel.addManualStop(lat, lng, arrival, departure, name, googlePlaceId, forceNewPlace)
    },
    onFindNearbyPlace = viewModel::nearbyPlace,
    onMessageShown = { viewModel.clearMessage() },
    onRegisterPlace = { lat, lng, name, wishlist, priority, memo, googlePlaceId, forceNewPlace ->
      viewModel.registerPlace(lat, lng, name, wishlist, priority, memo, googlePlaceId, forceNewPlace)
    },
    onLinkRegister = { placeId, wishlist, priority, memo ->
      viewModel.linkRegisterToPlace(placeId, wishlist, priority, memo)
    },
    onLoadPlace = viewModel::loadPlace,
    onSavePlaceEdits = { editItem, name, note, wishlist, priority, visited ->
      viewModel.savePlaceEdits(editItem, name, note, wishlist, priority, visited)
    },
    onFetchPoiDetails = viewModel::fetchPoiDetails,
    onFetchNearbyPois = viewModel::nearbyPois,
    onReassignStop = { stopId, chosen, customName -> viewModel.reassignStop(stopId, chosen, customName) },
    registeredPlaces = registeredPlaces,
    showRegisteredPlaces = showRegisteredPlaces,
    onToggleRegisteredPlaces = { viewModel.toggleShowRegisteredPlaces() },
    onAddManualStopForPlace = { placeId, arrival, departure ->
      viewModel.addManualStopForPlace(placeId, arrival, departure)
    },
    onOpenPlaceDetail = onOpenPlaceDetail,
  )
}
