package com.pathly.presentation.places

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.presentation.common.NearbyCandidatePickerDialog
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.util.DateFormatters
import kotlinx.coroutines.launch

// 「場所」タブは Navigation-Compose の目的地に分割されている（一覧／地図で追加／検索で追加／詳細）。
// いずれも共有の [PlacesViewModel]（places グラフにスコープ）を受け取り、遷移は呼び出し側（NavHost）が担う。

/** 一覧（places_list）。 */
@Composable
fun PlacesListRoute(
  viewModel: PlacesViewModel,
  onAddByMap: () -> Unit,
  onAddBySearch: () -> Unit,
  onItemClick: (placeId: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  uiState.errorMessage?.let { message ->
    LaunchedEffect(message) { viewModel.clearError() }
  }

  // 削除（一覧・詳細どちらから消しても）を検知して取り消しスナックバーを出す。
  LaunchedEffect(uiState.undoDeleteToken) {
    if (uiState.undoDeleteToken == 0) return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
      message = "「${uiState.undoDeleteName ?: "場所"}」を削除しました",
      actionLabel = "取り消す",
      duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
  }

  // 登録（「登録しました」/「この場所は登録済みです」）を検知して、削除と同じ下部スナックバーで知らせる。
  LaunchedEffect(uiState.registerToken) {
    if (uiState.registerToken == 0) return@LaunchedEffect
    uiState.registerMessage?.let { message ->
      snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    PlacesListContent(
      state = uiState,
      onAddByMap = onAddByMap,
      onAddBySearch = onAddBySearch,
      onClearFilters = viewModel::clearFilters,
      onWishlistFilterChange = viewModel::setWishlistFilter,
      onVisitedFilterChange = viewModel::setVisitedFilter,
      onSortChange = viewModel::setSort,
      onToggleSortDirection = viewModel::toggleSortDirection,
      onItemClick = { onItemClick(it.place.id) },
      onToggleWishlist = viewModel::toggleWishlist,
      // 確認ダイアログは出さず即時削除。取り消しは上のスナックバーから。
      onDeleteRequest = { viewModel.deletePlace(it.place.id) },
    )
    SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

/** 地図で追加（place_add）。保存したら [onDone] で一覧へ戻す。 */
@Composable
fun AddPlaceRoute(
  viewModel: PlacesViewModel,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // 登録（新規／既存への紐付け）が実際に成立したら一覧へ戻す。近接確認を挟むときは
  // ユーザーが選んだあとになる。判断が ViewModel に移り、この画面からは登録が同期的に
  // 完了しなくなったため、登録通知のトークンを合図に使う。
  val tokenAtEntry = remember { viewModel.uiState.value.registerToken }
  LaunchedEffect(uiState.registerToken) {
    if (uiState.registerToken != tokenAtEntry) onDone()
  }

  AddPlaceContent(
    modifier = modifier,
    onCancel = onDone,
    onRegister = viewModel::registerPlaceWithNearbyCheck,
    nearbyRegisterPrompt = uiState.nearbyRegisterPrompt,
    onConfirmNearbyLink = viewModel::confirmNearbyLink,
    onConfirmNearbyNew = viewModel::confirmNearbyNew,
    onDismissNearbyPrompt = viewModel::dismissNearbyPrompt,
    onFetchDetails = viewModel::fetchPoiDetails,
  )
}

/** 検索して追加（place_search）。入る時に検索セッションを開始し、登録したら一覧へ戻す。 */
@Composable
fun SearchAddRoute(
  viewModel: PlacesViewModel,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { viewModel.startSearch() }
  // 候補確定後（result 表示中）の戻るは、候補一覧へ戻す（画面自体は閉じない）。
  BackHandler(enabled = uiState.search.result != null) { viewModel.clearSearchResult() }

  SearchAddContent(
    modifier = modifier,
    search = uiState.search,
    onQueryChange = viewModel::onSearchQueryChange,
    onSelectPrediction = viewModel::selectPrediction,
    onBackToPredictions = viewModel::clearSearchResult,
    onCancel = onDone,
    onRegister = { lat, lng, name, wishlist, priority, memo, googlePlaceId, googleName ->
      // 地図タップと同じ登録経路に通す（近接確認の要否も UseCase 側の判断に任せる）。
      // 施設情報は検索時に取得済みなので渡して引き直させない。
      viewModel.registerPlaceWithNearbyCheck(
        lat,
        lng,
        name,
        wishlist,
        priority,
        memo,
        googlePlaceId,
        googleName = googleName,
        knownDetails = uiState.search.result,
      )
      onDone()
    },
  )
}

/** 詳細（place_detail/{placeId}）。削除・不存在になったら [onBack] で一覧へ戻す。 */
@Composable
fun PlaceDetailRoute(
  viewModel: PlacesViewModel,
  placeId: Long,
  onBack: () -> Unit,
  onOpenTrack: (trackId: Long) -> Unit,
  modifier: Modifier = Modifier,
  onOpenPlaceDetail: (placeId: Long) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  val item = uiState.items.firstOrNull { it.place.id == placeId }
  if (item == null) {
    // 読み込み済みで見つからない（削除された）なら一覧へ戻す（取り消しは一覧のスナックバーで）。
    if (!uiState.isLoading) {
      LaunchedEffect(placeId) { onBack() }
    }
    return
  }

  val visitsFlow = remember(placeId) { viewModel.visitsFor(placeId) }
  val visits by visitsFlow.collectAsStateWithLifecycle(emptyList())

  PlaceDetailContent(
    modifier = modifier,
    item = item,
    visits = visits,
    onOpenTrack = onOpenTrack,
    onBack = onBack,
    onSave = { name, note, wishlist, priority, visited, link ->
      viewModel.savePlaceEdits(item, name, note, wishlist, priority, visited, link)
    },
    onFetchPoiDetails = viewModel::fetchPoiDetails,
    // 「Googleで情報を取得」: 登録座標の近くの候補から選ぶ（保存で確定・選び直しと同じ仕組み）。
    onFetchNearbyPois = viewModel::nearbyPois,
    // 座標がずれて周辺に出ないときの名前検索フォールバック。
    onSearchPredictions = viewModel::predictPlaces,
    onFetchPrediction = viewModel::fetchPlaceResult,
    // 登録済みの場所の地図表示（この場所自身は主マーカーと重なるので除外）。
    registeredPlaces = uiState.items
      .filter { it.place.id != placeId }
      .map {
        RegisteredPlace(
          placeId = it.place.id,
          name = it.displayName,
          latitude = it.place.latitude,
          longitude = it.place.longitude,
          isWishlisted = it.isWishlisted,
          isVisited = it.isVisited,
        )
      },
    showRegisteredPlaces = uiState.showRegisteredPlaces,
    onToggleRegisteredPlaces = { viewModel.toggleShowRegisteredPlaces() },
    // 地図タップでの場所登録（POI・空き地点とも）。近接確認の要否は ViewModel が判断する。
    onRegisterPlaceAtPoint = viewModel::registerPlaceWithNearbyCheck,
    nearbyRegisterPrompt = uiState.nearbyRegisterPrompt,
    onConfirmNearbyLink = viewModel::confirmNearbyLink,
    onConfirmNearbyNew = viewModel::confirmNearbyNew,
    onDismissNearbyPrompt = viewModel::dismissNearbyPrompt,
    onLoadPlace = viewModel::loadPlace,
    onSavePlaceEdits = { editItem, name, note, wishlist, priority, visited ->
      viewModel.savePlaceEdits(editItem, name, note, wishlist, priority, visited)
    },
    onOpenPlaceDetail = onOpenPlaceDetail,
    // 確認ダイアログは出さず即時削除。items から消えると item == null になり一覧へ戻り、
    // 取り消しスナックバーは一覧側で出る。
    onDeleteRequest = { viewModel.deletePlace(item.place.id) },
  )
}

// ---------------------------------------------------------------------------
// 一覧
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesListContent(
  state: PlacesState,
  onAddByMap: () -> Unit,
  onAddBySearch: () -> Unit,
  onClearFilters: () -> Unit,
  onWishlistFilterChange: (WishlistFilter) -> Unit,
  onVisitedFilterChange: (VisitedFilter) -> Unit,
  onSortChange: (PlaceSort) -> Unit,
  onToggleSortDirection: () -> Unit,
  onItemClick: (PlaceListItem) -> Unit,
  onToggleWishlist: (PlaceListItem) -> Unit,
  onDeleteRequest: (PlaceListItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "場所",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
      Box {
        var menuOpen by remember { mutableStateOf(false) }
        Button(onClick = { menuOpen = true }) {
          Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
          Text(text = " 追加")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text("検索して追加") },
            onClick = {
              menuOpen = false
              onAddBySearch()
            },
          )
          DropdownMenuItem(
            text = { Text("地図で選ぶ") },
            onClick = {
              menuOpen = false
              onAddByMap()
            },
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 絞り込みは2軸独立（行きたい / 訪問状況）。横に収まらない端末向けに横スクロール可。
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // すべて: 絞り込みを全解除。何も絞っていないときは選択状態にして現在地を示す。
      FilterChip(
        selected = state.noFilter,
        onClick = onClearFilters,
        label = { Text("すべて") },
      )
      // 行きたいは1つのトグル。タップで 指定なし→行きたい→行きたい以外→… と切り替わる。
      FilterChip(
        selected = state.wishlistFilter != WishlistFilter.ANY,
        onClick = {
          val next = WishlistFilter.entries[(state.wishlistFilter.ordinal + 1) % WishlistFilter.entries.size]
          onWishlistFilterChange(next)
        },
        label = { Text(state.wishlistFilter.chipLabel) },
      )
      // 訪問状況も1つのトグル。タップで 指定なし→訪問済み→未訪問→… と切り替わる。
      FilterChip(
        selected = state.visitedFilter != VisitedFilter.ANY,
        onClick = {
          val next = VisitedFilter.entries[(state.visitedFilter.ordinal + 1) % VisitedFilter.entries.size]
          onVisitedFilterChange(next)
        },
        label = { Text(state.visitedFilter.chipLabel) },
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 並べ替え（軸メニュー ＋ 昇順/降順トグル）。
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box {
        var sortOpen by remember { mutableStateOf(false) }
        TextButton(onClick = { sortOpen = true }) {
          Text(text = "並べ替え: ${state.sort.label} ▾")
        }
        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
          PlaceSort.entries.forEach { sort ->
            DropdownMenuItem(
              text = { Text(sort.label) },
              onClick = {
                sortOpen = false
                onSortChange(sort)
              },
            )
          }
        }
      }
      TextButton(onClick = onToggleSortDirection) {
        Text(text = if (state.sortDescending) "降順 ↓" else "昇順 ↑")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when {
      state.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }

      state.visibleItems.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "場所がありません\n「追加」から登録できます",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      else -> {
        val listState = rememberLazyListState()
        // 絞り込み・並べ替えを変えたとき、または先頭が変わったとき（＝追加時）に先頭へ戻す。
        val topItemId = state.visibleItems.firstOrNull()?.place?.id
        LaunchedEffect(state.wishlistFilter, state.visitedFilter, state.sort, state.sortDescending, topItemId) {
          listState.scrollToItem(0)
        }
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.visibleItems, key = { it.place.id }) { item ->
            PlaceItemRow(
              item = item,
              sort = state.sort,
              onClick = { onItemClick(item) },
              onToggleWishlist = { onToggleWishlist(item) },
              onDelete = { onDeleteRequest(item) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceItemRow(
  item: PlaceListItem,
  sort: PlaceSort,
  onClick: () -> Unit,
  onToggleWishlist: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.displayName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        // カテゴリ（業種）。あれば名前の下に小さく出す（「どんな場所か」の手がかり）。
        item.place.category?.takeIf { it.isNotBlank() }?.let { category ->
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = category,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // バッジ行（行きたい／訪問済み）。無いときは出さない。
        if (item.isWishlisted || item.isVisited) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.isWishlisted) {
              Text(
                text = "行きたい ${priorityStars(item.priority ?: Priority.MEDIUM)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            if (item.isVisited) {
              Text(
                text = if (item.visitCount > 0) "✓ 訪問${item.visitCount}回" else "✓ 訪問済み",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        }

        // 住所（1行）。メモは詳細で見る。
        item.place.googleAddress?.takeIf { it.isNotBlank() }?.let { address ->
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // 現在の並び順に対応する日付だけを小さく出す（何で並んでいるか分かる・常時全部は出さない）。
        val sortDate: String? = when (sort) {
          PlaceSort.REGISTERED -> "登録 " + DateFormatters.shortDate(item.place.createdAt)
          PlaceSort.UPDATED -> "更新 " + DateFormatters.shortDate(item.place.updatedAt)
          PlaceSort.VISITED -> item.visitRecencyAt?.let { "訪問 " + DateFormatters.shortDate(it) }
          // 訪問回数は既存の「✓ 訪問N回」バッジで分かるので追加表示しない。
          PlaceSort.VISIT_COUNT, PlaceSort.PRIORITY, PlaceSort.NAME -> null
        }
        sortDate?.let {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      WishlistFlagButton(active = item.isWishlisted, onClick = onToggleWishlist)
      // 立ち寄り記録がある場所は削除不可（記録を残すため）。
      val canDelete = item.visitCount == 0
      IconButton(onClick = onDelete, enabled = canDelete) {
        Icon(
          painter = painterResource(R.drawable.ic_delete),
          contentDescription = if (canDelete) "削除" else "立ち寄り記録があるため削除できません",
          tint = if (canDelete) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          },
        )
      }
    }
  }
}

@Composable
private fun WishlistFlagButton(
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
      painter = painterResource(
        if (active) R.drawable.ic_flag_filled else R.drawable.ic_flag,
      ),
      contentDescription = if (active) "行きたいから外す" else "行きたいに追加",
      tint = if (active) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
  }
}

// ---------------------------------------------------------------------------
// 追加（地図タップ → タップ後にフォーム表示）
// ---------------------------------------------------------------------------

@Composable
private fun AddPlaceContent(
  onCancel: () -> Unit,
  // 近くに既存があるかの判断は ViewModel（PlaceEditUseCase）側で行う。
  onRegister: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  onFetchDetails: suspend (googlePlaceId: String) -> PlaceSearchResult?,
  modifier: Modifier = Modifier,
  // 近接確認の保留状態（非nullで確認ダイアログを出す）とその選択。
  nearbyRegisterPrompt: NearbyRegisterPrompt? = null,
  onConfirmNearbyLink: () -> Unit = {},
  onConfirmNearbyNew: () -> Unit = {},
  onDismissNearbyPrompt: () -> Unit = {},
) {
  // 選んだ対象。フォームの中身（名前・メモ・行きたい・優先度）と Google 情報の取得は
  // 場所シート側が持つので、この画面は「どこを選んだか」だけを持つ。
  var target by remember { mutableStateOf<PlaceSheetTarget?>(null) }
  val picked = when (val t = target) {
    is PlaceSheetTarget.NewPoint -> t.latLng
    is PlaceSheetTarget.NewPoi -> t.poi.latLng
    else -> null
  }
  val context = LocalContext.current

  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(DEFAULT_LOCATION, 12f)
  }

  // 現在地へ地図を寄せる（記録画面の現在地ボタンと同じ挙動）。権限が無ければ何もしない。
  val scope = rememberCoroutineScope()
  val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
  val moveToCurrentLocation: () -> Unit = {
    try {
      fusedClient.getCurrentLocation(
        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        CancellationTokenSource().token,
      ).addOnSuccessListener { location ->
        location?.let {
          scope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f))
          }
        }
      }
    } catch (e: SecurityException) {
      // 権限が無ければ何もしない。
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
      ),
      // アイコン（POI）をタップしたら施設として、何もない場所なら任意の地点として扱う。
      // 名前の初期値やカテゴリ・住所の取得は場所シートに任せる。
      onPOIClick = { poi -> target = PlaceSheetTarget.NewPoi(poi) },
      onMapClick = { latLng -> target = PlaceSheetTarget.NewPoint(latLng) },
    ) {
      picked?.let { p ->
        val markerState = remember(p) { MarkerState(position = p) }
        Marker(state = markerState)
      }
    }

    // 現在地へ移動（右上）。記録画面と同じ現在地ボタン。
    Surface(
      onClick = moveToCurrentLocation,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(12.dp)
        .size(44.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          painter = painterResource(R.drawable.ic_my_location),
          contentDescription = "現在地へ移動",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp),
        )
      }
    }

    // 未タップのときは大きなヒントだけ。フォームはタップ後に出す（地図を広く使う）。
    if (picked == null) {
      Card(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(12.dp),
      ) {
        Text(
          text = "地図をタップして場所を選ぶ",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(12.dp),
      ) {
        Text("キャンセル")
      }
    } else {
      // 地図タップと同じ「場所シート」。畳めば地図を全画面で確認でき、キャンセル（＝旧「選び直す」）
      // で選択を解除して地図に戻る。システムバックもシート側が受ける。
      target?.let { t ->
        PlaceActionSheet(
          target = t,
          onDismiss = { target = null },
          onFetchPoiDetails = onFetchDetails,
          onRegisterNew = onRegister,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }

    // 近接確認: 近くの既存に紐付け／新規で登録。
    nearbyRegisterPrompt?.let { prompt ->
      NearbyPlaceConfirmDialog(
        place = prompt.nearby,
        onLink = onConfirmNearbyLink,
        onCreateNew = onConfirmNearbyNew,
        onDismiss = onDismissNearbyPrompt,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 詳細
// ---------------------------------------------------------------------------

@Composable
private fun PlaceDetailContent(
  item: PlaceListItem,
  visits: List<PlaceVisit>,
  onBack: () -> Unit,
  onSave: (name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean, link: PlaceSearchResult?) -> Unit,
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult?,
  onOpenTrack: (trackId: Long) -> Unit,
  onDeleteRequest: () -> Unit,
  // 「Googleで情報を取得」用: 登録座標の近くの POI 候補を取得し、選んだ施設を紐付ける。
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult>,
  // 座標がずれて周辺に出ない施設用: 名前で検索するフォールバック。
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult?,
  modifier: Modifier = Modifier,
  // 登録済みの場所の地図表示（画面別トグル）。この場所自身は主マーカーと重なるので呼び出し側で除外して渡す。
  registeredPlaces: List<RegisteredPlace> = emptyList(),
  showRegisteredPlaces: Boolean = false,
  onToggleRegisteredPlaces: () -> Unit = {},
  // 地図タップでの場所登録（POI・空き地点とも）。近接確認の要否は ViewModel 側で判断する。
  onRegisterPlaceAtPoint: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit =
    { _, _, _, _, _, _, _, _ -> },
  // 近接確認の保留状態（非nullで確認ダイアログを出す）とその選択。
  nearbyRegisterPrompt: NearbyRegisterPrompt? = null,
  onConfirmNearbyLink: () -> Unit = {},
  onConfirmNearbyNew: () -> Unit = {},
  onDismissNearbyPrompt: () -> Unit = {},
  // 統一シートで登録済みマーカーをその場で編集するため、単一 place を取得・保存する。
  onLoadPlace: suspend (placeId: Long) -> PlaceListItem? = { null },
  onSavePlaceEdits: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean) -> Unit = { _, _, _, _, _, _ -> },
  // 「詳細を開く」でその場所の詳細へ遷移する。
  onOpenPlaceDetail: (placeId: Long) -> Unit = {},
) {
  // 地図の1点タップで開く統一の「場所シート」（未登録の空き地点/POI＝登録、登録済み＝その場で編集）。
  var placeSheetTarget by remember { mutableStateOf<PlaceSheetTarget?>(null) }
  // 「Googleで情報を取得」ダイアログの開閉。
  var linkDialogOpen by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val savedName = item.place.name ?: ""
  val savedPriority = item.priority ?: Priority.MEDIUM
  val savedNote = item.note ?: ""
  // 編集はすべてローカルに溜め、「保存」でまとめて確定する（何が未保存かを一目で分かるように）。
  var name by remember(item.place.id) { mutableStateOf(savedName) }
  var note by remember(item.place.id) { mutableStateOf(savedNote) }
  var wishlist by remember(item.wishlistId) { mutableStateOf(item.isWishlisted) }
  var priority by remember(item.wishlistId) { mutableStateOf(savedPriority) }
  var visited by remember(item.wishlistId) { mutableStateOf(item.isManuallyVisited) }
  // 「Googleで情報を取得」で選んだ施設は即保存せず、ここに溜めて「保存」で確定する。
  var pendingLink by remember(item.place.id) { mutableStateOf<PlaceSearchResult?>(null) }
  val hasChanges = name.trim() != savedName.trim() ||
    note.trim() != savedNote.trim() ||
    wishlist != item.isWishlisted ||
    (wishlist && priority != savedPriority) ||
    (wishlist && item.visitCount == 0 && visited != item.isManuallyVisited) ||
    pendingLink != null

  // 下部カードの高さを測り、その分マップ下部に余白を入れてピンがカードに隠れないようにする。
  var sheetHeightPx by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current

  val position = LatLng(item.place.latitude, item.place.longitude)
  val cameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom(position, 16f)
  }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(bottom = with(density) { sheetHeightPx.toDp() }),
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
      ),
      // 詳細のマップで別の施設(POI)を見かけたらタップで統一シート（登録）。
      onPOIClick = { placeSheetTarget = PlaceSheetTarget.NewPoi(it) },
      // 何もない地点をタップ → 統一シート（その地点を場所として登録）。
      onMapClick = { placeSheetTarget = PlaceSheetTarget.NewPoint(it) },
    ) {
      val markerState = remember(position) { MarkerState(position = position) }
      Marker(state = markerState, title = item.displayName)
      // 登録済みの場所（トグルON）。この場所自身は主マーカーと重なるので除外済みのリストを描く。
      // タップで統一シート（その場で編集／詳細を開く）。
      if (showRegisteredPlaces) {
        RegisteredPlaceMarkers(registeredPlaces) { placeSheetTarget = PlaceSheetTarget.Existing(it.placeId) }
      }
    }

    // 登録済みの場所を出すトグル（場所詳細・画面別）。戻るボタンの下に置く。
    Surface(
      onClick = onToggleRegisteredPlaces,
      shape = CircleShape,
      color = if (showRegisteredPlaces) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      shadowElevation = 4.dp,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(12.dp),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_place),
        contentDescription = if (showRegisteredPlaces) "登録済みの場所を隠す" else "登録済みの場所を表示",
        tint = if (showRegisteredPlaces) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
      )
    }

    FilledTonalIconButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(12.dp),
    ) {
      Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "一覧に戻る")
    }

    Card(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .onSizeChanged { sheetHeightPx = it.height }
        .fillMaxWidth()
        .heightIn(max = 460.dp)
        .padding(12.dp),
      shape = RoundedCornerShape(12.dp),
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      ) {
        PlaceFormBody(
          name = name,
          onNameChange = { name = it },
          nameLabel = "名前",
          namePlaceholder = item.displayName,
          category = item.place.category,
          address = item.place.googleAddress,
          onOpenInMaps = { openInGoogleMaps(context, item.place) },
          memo = note,
          onMemoChange = { note = it },
          wishlist = wishlist,
          onWishlistChange = { wishlist = it },
          priority = priority,
          onPriorityChange = { priority = it },
          wishlistOffHint = "「行きたい」に登録すると、優先度を付けられます。",
          // 立ち寄り記録がある場所は自動で訪問済み（切替不可・件数を表示）。無ければ手動トグル。
          visitedContent = if (item.visitCount > 0) {
            {
              Text(
                text = "訪問済み（立ち寄り記録 ${item.visitCount} 件）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          } else {
            {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(text = if (visited) "訪問済み" else "未訪問")
                Switch(checked = visited, onCheckedChange = { visited = it })
              }
            }
          },
        )

        // Google 施設情報の取得・紐付け。ID の無い場所（オフライン記録・手動登録）に住所・カテゴリ・正確な座標を補える。
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = { linkDialogOpen = true },
          modifier = Modifier.align(Alignment.End),
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_place),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = if (item.place.googlePlaceId == null) " Googleで情報を取得" else " Google施設を選び直す",
          )
        }

        // 選んだ施設は保存で確定する（即保存しない）。未保存の紐付け予定をここに見せる。
        pendingLink?.let { link ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "紐付け予定: ${link.name ?: "（名称不明）"}（保存で確定）",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pendingLink = null }) { Text("取消") }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = {
            onSave(name, note, wishlist, priority, visited, pendingLink)
            // 保存したら紐付け予定は消化済み。残すとボタンが活性のまま・予定表示が残るため確実にクリアする。
            pendingLink = null
          },
          enabled = hasChanges,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("保存")
        }

        if (visits.isNotEmpty()) {
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "この場所を含むお出掛け（${visits.size}件）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          visits.forEach { visit ->
            VisitRow(visit = visit, onClick = { onOpenTrack(visit.trackId) })
            HorizontalDivider()
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val canDelete = item.visitCount == 0
        val deleteColor = if (canDelete) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        OutlinedButton(
          onClick = onDeleteRequest,
          enabled = canDelete,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = deleteColor,
          )
          Text(text = " この場所を削除", color = deleteColor)
        }
        if (!canDelete) {
          Text(
            text = "立ち寄り記録があるため削除できません（記録を残します）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // 地図の1点タップで開く統一の「場所シート」。記録画面と同じ挙動（この画面は記録中でないので立ち寄り追加は出さない）。
    placeSheetTarget?.let { target ->
      PlaceActionSheet(
        target = target,
        onDismiss = { placeSheetTarget = null },
        onFetchPoiDetails = onFetchPoiDetails,
        onLoadPlace = onLoadPlace,
        // POI か空き地点か、近接確認が要るかの判断は ViewModel（PlaceEditUseCase）が持つ。
        onRegisterNew = onRegisterPlaceAtPoint,
        onSaveExisting = onSavePlaceEdits,
        onOpenDetail = onOpenPlaceDetail,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }

  if (linkDialogOpen) {
    NearbyCandidatePickerDialog(
      latitude = item.place.latitude,
      longitude = item.place.longitude,
      reloadKey = item.place.id,
      title = "Googleで情報を取得",
      currentLabel = "現在: ${item.displayName}",
      description = "登録された座標の近くの施設から選んで、この場所に施設情報（住所・カテゴリ・座標）を紐付けます。あなたが付けた名前は変わりません。",
      confirmLabel = "この施設を選ぶ",
      allowCustomName = false,
      onFetchCandidates = onFetchNearbyPois,
      onConfirm = { chosen, _ ->
        // 即保存せず、詳細の「保存」で確定するため溜めるだけ。
        pendingLink = chosen
        linkDialogOpen = false
      },
      onDismiss = { linkDialogOpen = false },
      onSearchPredictions = onSearchPredictions,
      onFetchPrediction = onFetchPrediction,
    )
  }

  nearbyRegisterPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      onLink = onConfirmNearbyLink,
      onCreateNew = onConfirmNearbyNew,
      onDismiss = onDismissNearbyPrompt,
    )
  }
}

/** 場所詳細の空きタップ登録で、近接確認のときに保留する登録内容。 */
// ---------------------------------------------------------------------------
// 追加（キーワード検索）
// ---------------------------------------------------------------------------

@Composable
private fun SearchAddContent(
  search: SearchState,
  onQueryChange: (String) -> Unit,
  onSelectPrediction: (String) -> Unit,
  onBackToPredictions: () -> Unit,
  onCancel: () -> Unit,
  onRegister: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val result = search.result
  if (result != null) {
    // 候補を確定 → 登録フォーム
    SearchResultForm(
      result = result,
      onBack = onBackToPredictions,
      onRegister = onRegister,
      modifier = modifier,
    )
    return
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "検索して追加",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      TextButton(onClick = onCancel) { Text("閉じる") }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
      value = search.query,
      onValueChange = onQueryChange,
      label = { Text("店名・場所名で検索") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(8.dp))

    when {
      search.isSearching -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }

      search.query.isNotBlank() && search.predictions.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "候補がありません\n（オンライン・キーワードを確認）",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      else -> {
        LazyColumn {
          items(search.predictions, key = { it.placeId }) { prediction ->
            PredictionRow(prediction = prediction, onClick = { onSelectPrediction(prediction.placeId) })
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@Composable
private fun PredictionRow(
  prediction: PlacePrediction,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp),
  ) {
    Text(
      text = prediction.primaryText,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (prediction.secondaryText.isNotBlank()) {
      Text(
        text = prediction.secondaryText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SearchResultForm(
  result: PlaceSearchResult,
  onBack: () -> Unit,
  onRegister: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val position = LatLng(result.latitude, result.longitude)
  val cameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom(position, 16f)
  }

  Box(modifier = modifier.fillMaxSize()) {
    // 検索で選んだ場所を地図で示す（どこか視覚的に分かるように）。
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
      ),
    ) {
      val markerState = remember(position) { MarkerState(position = position) }
      Marker(state = markerState, title = result.name)
    }

    FilledTonalIconButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(12.dp),
    ) {
      Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "候補に戻る")
    }

    // 地図タップ・地図で選ぶと同じ「場所シート」。畳めば地図を全画面で確認できる。
    // 候補（カテゴリ・住所）は検索時に取得済みなので、シート側で引き直さない。
    PlaceActionSheet(
      target = PlaceSheetTarget.NewSearchResult(result),
      onDismiss = onBack,
      onFetchPoiDetails = { null },
      onRegisterNew = onRegister,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

@Composable
private fun VisitRow(
  visit: PlaceVisit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
  ) {
    Text(
      text = DateFormatters.shortDate(visit.outingDate),
      style = MaterialTheme.typography.bodyLarge,
    )
    Text(
      text = "${DateFormatters.shortTime(visit.arrivalTime)}–" +
        "${DateFormatters.shortTime(visit.departureTime)} ・滞在${visit.stayMinutes}分",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // その訪問（stop）のメモがあれば表示する（編集は履歴詳細で・ここは確認のみ）。
    visit.note?.takeIf { it.isNotBlank() }?.let { note ->
      Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 共通
// ---------------------------------------------------------------------------

/**
 * 場所フォームの共通本体。地図で追加・検索で追加・POI 登録・詳細編集のどこでも同じ並びで
 * 名前 → カテゴリ/住所（Google 由来・読取専用）→ Google マップで開く → メモ →
 * 行きたいに登録（ラベル付きスイッチ）→ 優先度 →（編集のみ）訪問済み を出す。
 * 送信ボタン・地図・履歴・削除などは呼び出し側が持つ。
 */
@Composable
internal fun PlaceFormBody(
  name: String,
  onNameChange: (String) -> Unit,
  nameLabel: String,
  category: String?,
  address: String?,
  onOpenInMaps: (() -> Unit)?,
  memo: String,
  onMemoChange: (String) -> Unit,
  wishlist: Boolean,
  onWishlistChange: (Boolean) -> Unit,
  priority: Priority,
  onPriorityChange: (Priority) -> Unit,
  modifier: Modifier = Modifier,
  namePlaceholder: String? = null,
  wishlistOffHint: String? = null,
  visitedContent: (@Composable () -> Unit)? = null,
) {
  Column(modifier = modifier) {
    OutlinedTextField(
      value = name,
      onValueChange = onNameChange,
      label = { Text(nameLabel) },
      placeholder = namePlaceholder?.let {
        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    // Google 由来のカテゴリ・住所（あれば）。「どんな場所か」の手がかり。読取専用。
    category?.takeIf { it.isNotBlank() }?.let { c ->
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = c, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    address?.takeIf { it.isNotBlank() }?.let { a ->
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Google マップアプリで開く（写真・口コミ・営業時間・経路は Google に委ねる）。
    onOpenInMaps?.let { open ->
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
        Text(text = " Google マップで開く")
      }
    }

    // メモは「行きたい」に関係なく常に入力できる。
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
      value = memo,
      onValueChange = onMemoChange,
      label = { Text("メモ（任意）") },
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("行きたいに登録")
      Switch(checked = wishlist, onCheckedChange = onWishlistChange)
    }

    if (wishlist) {
      Spacer(modifier = Modifier.height(12.dp))
      PrioritySelector(selected = priority, onSelect = onPriorityChange)
      visitedContent?.let { content ->
        Spacer(modifier = Modifier.height(8.dp))
        content()
      }
    } else if (wishlistOffHint != null) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = wishlistOffHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun PrioritySelector(
  selected: Priority,
  onSelect: (Priority) -> Unit,
  modifier: Modifier = Modifier,
) {
  // 星レーティング: タップした位置がそのままレベル（低=★☆☆ / 中=★★☆ / 高=★★★）。
  // 1タップで任意のレベルにでき、上げる/下げるも一発。狭いダイアログでも星1列で収まる。
  val level = selected.value + 1 // 1..3
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "優先度",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(8.dp))
    (1..3).forEach { i ->
      Text(
        text = if (i <= level) "★" else "☆",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
          .clip(CircleShape)
          .clickable { onSelect(Priority.fromValue(i - 1)) }
          .padding(6.dp),
      )
    }
  }
}

private fun priorityStars(priority: Priority): String = when (priority) {
  Priority.HIGH -> "★★★"
  Priority.MEDIUM -> "★★☆"
  Priority.LOW -> "★☆☆"
}

private val DEFAULT_LOCATION = LatLng(35.6762, 139.6503) // 東京駅

/** [Place] を Google マップで開く（詳細画面用の薄いラッパ）。 */
private fun openInGoogleMaps(context: Context, place: Place) {
  openPlaceInGoogleMaps(context, place.googlePlaceId, place.latitude, place.longitude, place.displayName)
}

/**
 * Google マップアプリ（無ければ Web）で場所を開く。[googlePlaceId] があれば施設ページを
 * ピンポイントで開き（写真・口コミ・営業時間）、無ければ座標で開く。API 呼び出しなし＝課金ゼロ。
 * 詳細は docs/designs/place-info-enrichment.md。
 */
internal fun openPlaceInGoogleMaps(
  context: Context,
  googlePlaceId: String?,
  latitude: Double,
  longitude: Double,
  label: String,
) {
  val uri = if (googlePlaceId != null) {
    val query = Uri.encode(label)
    "https://www.google.com/maps/search/?api=1&query=$query&query_place_id=$googlePlaceId".toUri()
  } else {
    val enc = Uri.encode(label)
    "geo:$latitude,$longitude?q=$latitude,$longitude($enc)".toUri()
  }
  val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    // Google マップ未インストールなら通常のハンドラ（ブラウザ等）で開く。
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
  }
}
