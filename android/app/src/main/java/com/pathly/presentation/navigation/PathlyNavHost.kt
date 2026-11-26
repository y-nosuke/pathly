package com.pathly.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.history.HistoryScreen
import com.pathly.presentation.history.SameFacilityReview
import com.pathly.presentation.history.StopReanalysis
import com.pathly.presentation.history.TrackDeletionSnackbarEffect
import com.pathly.presentation.history.TrackDeletionViewModel
import com.pathly.presentation.history.TrackDetailScreen
import com.pathly.presentation.history.TrackDetailViewModel
import com.pathly.presentation.history.TrackStopActions
import com.pathly.presentation.places.AddPlaceRoute
import com.pathly.presentation.places.PlaceDetailRoute
import com.pathly.presentation.places.PlacesListRoute
import com.pathly.presentation.places.PlacesViewModel
import com.pathly.presentation.places.SearchAddRoute
import com.pathly.presentation.plans.PlanDetailRoute
import com.pathly.presentation.plans.PlanDetailViewModel
import com.pathly.presentation.plans.PlansRoute
import com.pathly.presentation.plans.PlansViewModel
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

  // 「計画」タブはネストグラフ（一覧と詳細。詳細でもタブを選んだままにする）。
  const val PLANS_GRAPH = "plans"
  const val PLANS_LIST = "plans_list"
  const val PLAN_DETAIL = "plan_detail/{planId}"

  fun planDetail(planId: Long) = "plan_detail/$planId"

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
  PLANS("計画", R.drawable.ic_event_note, Routes.PLANS_GRAPH),
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
  // 経路の削除の「取り消す」。経路詳細で消すと画面を戻るので、画面ではなくアプリ全体の器で出す。
  val snackbarHostState = remember { SnackbarHostState() }
  TrackDeletionSnackbarEffect(viewModel = hiltViewModel<TrackDeletionViewModel>(), snackbarHostState = snackbarHostState)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (!hideBottomBar) {
        NavigationBar {
          BottomNavItem.entries.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
              selected = selected,
              onClick = { navController.navigateToTab(item.route) },
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
      // 上のステータスバー・下のタブ（またはナビゲーションバー）の分はここで空けたので、中の画面の
      // statusBarsPadding・navigationBarsPadding が同じ分をもう一度空けないよう、使ったことにする。
      modifier = Modifier
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding),
      // 以前の手書き when ナビと同じく、遷移アニメーションは付けない（瞬時に切り替える）。
      enterTransition = { EnterTransition.None },
      exitTransition = { ExitTransition.None },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { ExitTransition.None },
    ) {
      composable(Routes.TRACKING) {
        TrackingScreen(
          onRequestPermission = onRequestPermission,
          onOpenTrack = { trackId -> navController.navigate(Routes.trackDetail(trackId)) },
          onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
          viewModel = trackingViewModel,
        )
      }

      composable(Routes.HISTORY) {
        BackToStartTab(navController)
        HistoryScreen(
          onTrackClick = { track -> navController.navigate(Routes.trackDetail(track.id)) },
        )
      }

      composable(Routes.SETTINGS) {
        BackToStartTab(navController)
        SettingsScreen()
      }

      placesGraph(navController)

      plansGraph(navController)

      composable(
        route = Routes.TRACK_DETAIL,
        arguments = listOf(navArgument("trackId") { type = NavType.LongType }),
      ) { entry ->
        val trackId = entry.arguments?.getLong("trackId") ?: return@composable
        TrackDetailRoute(
          trackId = trackId,
          onBack = { navController.popBackStack() },
          onOpenTrack = { id -> navController.navigate(Routes.trackDetail(id)) },
        )
      }
    }
  }
}

/** 「場所」タブのネストグラフ。配下の目的地は places グラフにスコープした PlacesViewModel を共有する。 */
private fun NavGraphBuilder.placesGraph(navController: NavController) {
  navigation(startDestination = Routes.PLACES_LIST, route = Routes.PLACES_GRAPH) {
    composable(Routes.PLACES_LIST) { entry ->
      BackToStartTab(navController)
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
      )
    }
  }
}

/** 「計画」タブのネストグラフ（→ docs/specs/plans.md）。 */
private fun NavGraphBuilder.plansGraph(navController: NavController) {
  navigation(startDestination = Routes.PLANS_LIST, route = Routes.PLANS_GRAPH) {
    composable(Routes.PLANS_LIST) { entry ->
      BackToStartTab(navController)
      // 詳細で消して戻ってきた計画の名前（取り消しのスナックバーを出す）。
      val deletedTitle by entry.savedStateHandle.getStateFlow<String?>(DELETED_PLAN_KEY, null).collectAsStateWithLifecycle()
      PlansRoute(
        viewModel = hiltViewModel<PlansViewModel>(),
        onOpenPlan = { planId -> navController.navigate(Routes.planDetail(planId)) },
        deletedPlanTitle = deletedTitle,
        onDeletedShown = { entry.savedStateHandle[DELETED_PLAN_KEY] = null },
      )
    }

    composable(
      route = Routes.PLAN_DETAIL,
      arguments = listOf(navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.LongType }),
    ) {
      PlanDetailRoute(
        viewModel = hiltViewModel<PlanDetailViewModel>(),
        onBack = { navController.popBackStack() },
        onDeleted = { title ->
          navController.previousBackStackEntry?.savedStateHandle?.set(DELETED_PLAN_KEY, title)
          navController.popBackStack()
        },
        onOpenPlace = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
      )
    }
  }
}

private const val DELETED_PLAN_KEY = "deletedPlanTitle"

/**
 * 下のタブへ移る。タブごとに状態を保存・復元し、バックスタックを起点（記録タブ）まで畳む。
 * 離れたタブの画面（ViewModel・スクロール位置）は捨てずに取っておき、戻ったときに元の状態で出す。
 */
private fun NavController.navigateToTab(route: String) {
  navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

/**
 * 記録タブ以外のタブの最初の画面で、戻るキーを「記録タブへ移る」にする。
 * 既定の戻るはこのタブの画面を保存せずに捨てるので、入り直すと絞り込みなどが初期状態に戻ってしまう。
 * タブを押したときと同じく状態を取っておく。画面の中のシートなど、後から登録された戻るの処理が先に効く。
 */
@Composable
private fun BackToStartTab(navController: NavController) {
  BackHandler { navController.navigateToTab(Routes.TRACKING) }
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
  // 場所シートの訪問履歴から、別のお出掛け（経路詳細）を開く。
  onOpenTrack: (trackId: Long) -> Unit = {},
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
  val currentStop by viewModel.currentStop.collectAsStateWithLifecycle()
  val addStopHereTarget by viewModel.addStopHereTarget.collectAsStateWithLifecycle()
  val unresolvedCount by viewModel.stopEdit.unresolvedCount.collectAsStateWithLifecycle()
  val message by viewModel.message.collectAsStateWithLifecycle()
  val reanalyzeCandidates by viewModel.reanalyzeCandidates.collectAsStateWithLifecycle()
  val registeredPlaces by viewModel.registeredPlaces.collectAsStateWithLifecycle()
  val showRegisteredPlaces by viewModel.showRegisteredPlaces.collectAsStateWithLifecycle()
  val mapPlaceFilter by viewModel.mapPlaceFilter.collectAsStateWithLifecycle()
  val sameFacilityPending by viewModel.stopEdit.sameFacilityPending.collectAsStateWithLifecycle()
  val sameFacilityDialogOpen by viewModel.stopEdit.sameFacilityDialogOpen.collectAsStateWithLifecycle()

  TrackDetailScreen(
    track = track,
    onBackClick = onBack,
    stops = stops,
    currentStop = currentStop,
    addStopHereTarget = addStopHereTarget,
    unresolvedCount = unresolvedCount,
    message = message,
    onMessageShown = viewModel::clearMessage,
    onRenameTrack = viewModel::renameTrack,
    onToggleFavorite = viewModel::toggleFavorite,
    onDeleteTrack = {
      // 消すのは「取り消す」を待ってから（スナックバーはアプリ全体の器が出す）。消す経路の画面は戻る。
      viewModel.requestDeleteTrack()
      onBack()
    },
    stopActions = viewModel.stopEdit.actions,
    reanalysis = StopReanalysis(
      candidates = reanalyzeCandidates,
      onStart = viewModel::reanalyze,
      onAdd = viewModel::addStops,
      onDismiss = viewModel::dismissReanalyze,
    ),
    sameFacility = viewModel.stopEdit.sameFacilityReview(sameFacilityPending, sameFacilityDialogOpen),
    mapPlaces = MapRegisteredPlaces(
      places = registeredPlaces,
      shown = showRegisteredPlaces,
      onToggle = viewModel::toggleShowRegisteredPlaces,
      filter = mapPlaceFilter,
      onFilterChange = viewModel::setMapPlaceFilter,
    ),
    placeEdit = viewModel.placeEdit,
    manualStop = viewModel.manualStop,
    onOpenTrack = onOpenTrack,
  )
}
