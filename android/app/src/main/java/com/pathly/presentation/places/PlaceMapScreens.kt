package com.pathly.presentation.places

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.data.settings.MapSurface
import com.pathly.domain.model.PlaceCategoryGroup
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceVisit
import com.pathly.presentation.common.DefaultMapLocation
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.MapBackTitle
import com.pathly.presentation.common.MapControls
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapNoticeBanner
import com.pathly.presentation.common.MapOverflowMenuButton
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.common.MapRoundButton
import com.pathly.presentation.common.MapTopBar
import com.pathly.presentation.common.MapTopBarInset
import com.pathly.presentation.common.RegisteredPlaceMarker
import com.pathly.presentation.common.RegisteredPlaceMarkers
import com.pathly.presentation.common.RegisteredPlacesLayerButton
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.SheetRestoreButtonInset
import com.pathly.presentation.common.categoryGlyph
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberLocationPermitted
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.common.requestCurrentLatLng
import com.pathly.ui.theme.PathlyMapColors
import kotlinx.coroutines.launch

// 「場所」タブの地図の画面: 地図で選んで追加（place_add）と場所詳細（place_detail/{placeId}）。
// どちらも地図の画面の型（docs/designs/ui-direction.md）に載せる。

/** 地図で追加（place_add）。保存したら [onDone] で一覧へ戻す。 */
@Composable
fun AddPlaceRoute(
  viewModel: PlacesViewModel,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val showRegisteredPlaces by viewModel.showRegisteredPlaces(MapSurface.PLACE_ADD).collectAsStateWithLifecycle()
  val mapPlaceFilter by viewModel.mapPlaceFilter(MapSurface.PLACE_ADD).collectAsStateWithLifecycle()

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
    placeEdit = viewModel.placeEdit,
    registeredPlaces = MapRegisteredPlaces(
      places = uiState.items.map { it.toRegisteredPlace() },
      shown = showRegisteredPlaces,
      onToggle = { viewModel.toggleShowRegisteredPlaces(MapSurface.PLACE_ADD) },
      filter = mapPlaceFilter,
      onFilterChange = { viewModel.setMapPlaceFilter(MapSurface.PLACE_ADD, it) },
    ),
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

  val visitsFlow = remember(placeId) { viewModel.placeEdit.visitsFor(placeId) }
  val visits by visitsFlow.collectAsStateWithLifecycle(emptyList())
  val showRegisteredPlaces by viewModel.showRegisteredPlaces(MapSurface.PLACE_DETAIL).collectAsStateWithLifecycle()
  val mapPlaceFilter by viewModel.mapPlaceFilter(MapSurface.PLACE_DETAIL).collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  // 保存・紐付けなどの一言は詳細の下に出す（一覧に戻ってから出ると何のことか分からないため、出したら消化する）。
  PlaceMessageEffect(uiState.registerMessage, snackbarHostState, viewModel::consumeRegisterMessage)

  Box(modifier = modifier.fillMaxSize()) {
    PlaceDetailContent(
      item = item,
      visits = visits,
      onOpenTrack = onOpenTrack,
      onBack = onBack,
      // 保存・Google 施設の紐付け・まとめる・地図タップでの登録は場所の編集（他の画面と同じ）。
      // まとめるとこの場所は消え、一覧へ戻ってスナックバーで取り消せる（削除と同じ）。
      placeEdit = viewModel.placeEdit,
      // 登録済みの場所の地図表示（この場所自身は主マーカーと重なるので除外）。
      registeredPlaces = MapRegisteredPlaces(
        places = uiState.items
          .filter { it.place.id != placeId }
          .map { it.toRegisteredPlace() },
        shown = showRegisteredPlaces,
        onToggle = { viewModel.toggleShowRegisteredPlaces(MapSurface.PLACE_DETAIL) },
        filter = mapPlaceFilter,
        onFilterChange = { viewModel.setMapPlaceFilter(MapSurface.PLACE_DETAIL, it) },
      ),
      // 確認ダイアログは出さず即時削除。items から消えると item == null になり一覧へ戻り、
      // 取り消しスナックバーは一覧側で出る。
      onDeleteRequest = { viewModel.placeEdit.deletePlace(item.place.id) },
    )
    SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

/**
 * 地図で選んで追加。上: 戻る（＝やめる）＋見出し・レイヤー、選ぶまでは 1 行の案内の帯。右下: 現在地。
 * 下: 地図をタップしたら場所シート（登録は下端に固定）。
 */
@Composable
internal fun AddPlaceContent(
  onCancel: () -> Unit,
  // 登録と、近くに既存があるときの確認（判断は ViewModel の PlaceEditUseCase 側）。
  placeEdit: PlaceEditUi,
  registeredPlaces: MapRegisteredPlaces,
  modifier: Modifier = Modifier,
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
  val hasLocationPermission = rememberLocationPermitted()

  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(DefaultMapLocation, 12f)
  }
  val sheetState = rememberFloatingSheetState()
  // シートが出ている間は、その分を除いた範囲を地図の可視領域として扱う（段の確定値。ドラッグ中に地図を描き直さない）。
  val sheetHeight = if (target == null) 0.dp else sheetState.heightOf(sheetState.detent)

  // タップした地点がシートの下に隠れることがあるので、選んだら見える範囲へ寄せる
  // （地図の下パディングを段に合わせてあるので、パディングを除いた中央に来る）。
  LaunchedEffect(picked) {
    picked?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLng(it)) }
  }

  // 現在地へ地図を寄せる（一度だけ取って寄せる。追従はしない）。
  val scope = rememberCoroutineScope()
  val moveToCurrentLocation: () -> Unit = {
    requestCurrentLatLng(context) { latLng ->
      scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f)) }
    }
  }

  // マーカーの吹き出しをシステムバックで閉じられるようにする。
  val infoWindow = rememberMapInfoWindowState()

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(top = MapTopBarInset, bottom = sheetHeight),
      properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
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
      // 登録前に選んだ地点（青いピン）。まだ決めている最中なので丸バッジにはしない。
      picked?.let { p ->
        val markerState = remember(p) { MarkerState(position = p) }
        MapMarker("picked", p.latitude, p.longitude, infoWindow = infoWindow, state = markerState, title = "選んだ地点") {
          MapPinMarker(bg = PathlyMapColors.Picked, glyph = R.drawable.ic_add)
        }
      }
      // 登録済みの場所（レイヤーで表示中）。もう登録してある場所を見ながら選べる。タップは名前の吹き出しだけで、
      // 編集のシートは出さない（保存の一言を合図に一覧へ戻る画面なので、編集すると追加の途中で戻ってしまう）。
      RegisteredPlaceMarkers(registeredPlaces.visible, infoWindow) {}
    }

    MapTopBar(
      modifier = Modifier.align(Alignment.TopCenter),
      leading = {
        MapBackTitle(title = "地図で選んで追加", onBack = onCancel, modifier = Modifier.weight(1f))
      },
      actions = { RegisteredPlacesLayerButton(registeredPlaces) },
      status = {
        // 選ぶまでは何をすればよいかを 1 行で出す（押した先は無い）。
        if (target == null) {
          MapNoticeBanner(text = "地図をタップして場所を選ぶ", icon = R.drawable.ic_add, onClick = null)
        }
      },
    )

    // 右下: 現在地（位置情報の許可があるときだけ）。シートの上端に沿い、フルに広げている間は出さない。
    if (hasLocationPermission && (target == null || sheetState.detent != SheetDetent.FULL)) {
      MapControls(
        bottomInset = when {
          target == null -> 0.dp
          sheetState.detent == SheetDetent.HIDDEN -> SheetRestoreButtonInset
          else -> sheetHeight
        },
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {
        MapRoundButton(icon = R.drawable.ic_my_location, contentDescription = "現在地へ移動", onClick = moveToCurrentLocation)
      }
    }

    // 地図タップと同じ「場所シート」（選んだあとだけ出る）。畳めば地図を全画面で確認でき、キャンセル
    // で選択を解除して地図に戻る。システムバックもシート側が受ける。近接確認もここから出る。
    PlaceEditHost(
      placeEdit = placeEdit,
      target = target,
      onDismiss = { target = null },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      sheetState = sheetState,
    )
  }
}

/**
 * 場所詳細。上: 戻る＋見出し・レイヤー［≋］・⋮（Google 施設を選び直す・まとめる・削除）。右下: この場所へ戻す・現在地。
 * 下: 場所の編集のシート（保存は下端に固定）。地図のタップで開く場所シートは、この場所のシートと入れ替わりで出る。
 */
@Composable
internal fun PlaceDetailContent(
  item: PlaceListItem,
  visits: List<PlaceVisit>,
  onBack: () -> Unit,
  onOpenTrack: (trackId: Long) -> Unit,
  onDeleteRequest: () -> Unit,
  // この場所の保存・Google 施設の紐付け・まとめると、地図タップで開く場所シートの登録・編集。
  placeEdit: PlaceEditUi,
  // 登録済みの場所の地図表示（画面別）。この場所自身は主マーカーと重なるので呼び出し側で除外して渡す。
  registeredPlaces: MapRegisteredPlaces,
  modifier: Modifier = Modifier,
) {
  // 地図の1点タップで開く統一の「場所シート」（未登録の空き地点/POI＝登録、登録済み＝その場で編集）。
  var placeSheetTarget by remember { mutableStateOf<PlaceSheetTarget?>(null) }
  // ⋮ のメニュー・ダイアログ（上のバー）と、シートの本文の両方から触る状態。
  val editorState = rememberPlaceEditorState(item.place.id)
  val mergeActions = remember(placeEdit) { placeEdit.mergeActions() }
  val hasLocationPermission = rememberLocationPermitted()
  val context = LocalContext.current

  // 地図タップ・地図で選ぶ・検索して追加と同じ非モーダルのシートで出す（ADR-0010）。
  // 畳めば地図を全画面で確認できる。
  val sheetState = rememberFloatingSheetState(peekFraction = 0.55f)
  val placeSheetState = rememberFloatingSheetState()
  // いま出ているシート（この場所／地図タップの場所シート）。地図の下パディングと右下の操作はこれに合わせる。
  val shownSheet = if (placeSheetTarget == null) sheetState else placeSheetState
  // マップ下部の余白はシートの段に合わせる（高さそのものを読むと、ドラッグのたびに地図が再描画される）。
  val mapBottomPadding = shownSheet.heightOf(shownSheet.detent)

  val position = LatLng(item.place.latitude, item.place.longitude)
  val cameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom(position, 16f)
  }
  val scope = rememberCoroutineScope()
  // マーカーの吹き出しをシステムバックで閉じられるようにする。
  val infoWindow = rememberMapInfoWindowState()

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(top = MapTopBarInset, bottom = mapBottomPadding),
      properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
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
      // この画面が主題にしている場所。登録済み＝確定しているので、地図の他の画面と同じ丸バッジ
      // （色＝訪問状態／グリフ＝業種／右上＝行きたい）で描く。
      val markerState = remember(position) { MarkerState(position = position) }
      val group = PlaceCategoryGroup.of(item.place.category)
      MapMarker(
        "place-detail",
        item.place.id,
        item.wishlistFlag,
        item.isVisited,
        group,
        infoWindow = infoWindow,
        state = markerState,
        title = item.displayName,
        snippet = item.statusLabel,
        // この吹き出しは画面の一部（下のシートと同じ場所を指している）。自分でタップして開き直しても
        // 同じ扱いにして、バックは常に画面を戻す。
        dismissWithBack = false,
      ) {
        RegisteredPlaceMarker(
          bg = if (item.isVisited) PathlyMapColors.PlaceVisited else PathlyMapColors.PlaceUnvisited,
          glyph = categoryGlyph(group),
          wishlistFlag = item.wishlistFlag,
        )
      }
      // この画面はこの場所が主題なので、他の画面でマーカーをタップしたときと同じく吹き出しも出す。
      // 別の場所のシートを開くと地図が勝手に閉じてしまうので、閉じてこの場所に戻ったら出し直す。
      LaunchedEffect(markerState, placeSheetTarget) {
        if (placeSheetTarget == null) markerState.showInfoWindow()
      }
      // 登録済みの場所（レイヤーで表示中・地図の絞り込みに合うものだけ）。この場所自身は除外済み。
      // タップで統一シート（この画面の詳細と同じ編集ができる）。
      RegisteredPlaceMarkers(registeredPlaces.visible, infoWindow) {
        placeSheetTarget = PlaceSheetTarget.Existing(it.placeId)
      }
    }

    MapTopBar(
      modifier = Modifier.align(Alignment.TopCenter),
      leading = {
        MapBackTitle(
          title = item.displayName,
          subtitle = item.place.category?.label,
          onBack = onBack,
          modifier = Modifier.weight(1f),
        )
      },
      actions = {
        RegisteredPlacesLayerButton(registeredPlaces)
        MapOverflowMenuButton { close ->
          PlaceEditorMenuItems(
            item = item,
            state = editorState,
            canMerge = true,
            onDelete = onDeleteRequest,
            close = close,
          )
        }
      },
    )

    // 右下: この場所へ戻す・現在地（許可があるときだけ）。シートの上端に沿い、フルに広げている間は出さない。
    if (shownSheet.detent != SheetDetent.FULL) {
      MapControls(
        bottomInset = if (shownSheet.detent == SheetDetent.HIDDEN) SheetRestoreButtonInset else mapBottomPadding,
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {
        MapRoundButton(
          icon = R.drawable.ic_location_on,
          contentDescription = "この場所へ戻す",
          onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(position, 16f)) } },
        )
        if (hasLocationPermission) {
          MapRoundButton(
            icon = R.drawable.ic_my_location,
            contentDescription = "現在地へ移動",
            onClick = {
              requestCurrentLatLng(context) { latLng ->
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f)) }
              }
            },
          )
        }
      }
    }

    // 地図タップの場所シートを開いている間は重なるので引っ込める（どちらも下端に出る）。
    if (placeSheetTarget == null) {
      FloatingSheet(
        state = sheetState,
        modifier = Modifier.align(Alignment.BottomCenter),
        restoreLabel = "▲ 詳細に戻る",
      ) {
        // 地図のマーカーをタップして開くシートと同じ本体（入口が違うだけで中身は同じ）。
        // 見出しと ⋮ は上のバーにあるので、シートには出さない。
        PlaceEditorBody(
          item = item,
          visits = visits,
          onSave = { name, note, wishlist, priority, visited, link ->
            placeEdit.savePlaceEdits(item, name, note, wishlist, priority, visited, link)
          },
          onDelete = onDeleteRequest,
          onOpenTrack = onOpenTrack,
          // 「Googleで情報を取得」: 登録座標の近くの候補から選ぶ（保存で確定・選び直しと同じ仕組み）。
          onFetchNearbyPois = placeEdit::nearbyPois,
          // 座標がずれて周辺に出ないときの名前検索フォールバック。
          onSearchPredictions = placeEdit::predictPlaces,
          onFetchPrediction = placeEdit::fetchPlaceResult,
          mergeActions = mergeActions,
          state = editorState,
          showHeader = false,
          hostDialogs = false,
        )
      }
    }

    // ⋮ から開くダイアログ。地図タップの場所シートでこの場所のシートが引っ込んでいても出せるよう、画面に置く。
    PlaceEditorDialogs(
      item = item,
      state = editorState,
      mergeActions = mergeActions,
      onFetchNearbyPois = placeEdit::nearbyPois,
      onSearchPredictions = placeEdit::predictPlaces,
      onFetchPrediction = placeEdit::fetchPlaceResult,
    )

    // 地図の1点タップで開く統一の「場所シート」。記録画面と同じ挙動（この画面は記録中でないので立ち寄り追加は出さない）。
    // 削除の取り消しは一覧に戻ってから出すので、ここではスナックバーを渡さない。
    PlaceEditHost(
      placeEdit = placeEdit,
      target = placeSheetTarget,
      onDismiss = { placeSheetTarget = null },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      sheetState = placeSheetState,
      onOpenTrack = onOpenTrack,
      // この場所は主マーカーで見えているので、近接確認の対象にしない。
      mainPlaceId = item.place.id,
    )
  }
}
