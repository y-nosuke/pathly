package com.pathly.presentation.places

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pathly.R
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.presentation.common.MapMarker
import com.pathly.presentation.common.MapPinMarker
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlyTheme
import com.pathly.util.DateFormatters

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

  // 削除（詳細・地図のマーカーのシートから消したもの）を検知して、一覧に戻ったら取り消しスナックバーを出す。
  PlaceDeleteUndoEffect(viewModel.placeEdit, snackbarHostState)

  // 登録（「「名前」を登録しました」/「この場所は登録済みです」など）を、削除と同じ下部スナックバーで知らせる。
  PlaceMessageEffect(uiState.registerMessage, snackbarHostState, viewModel::consumeRegisterMessage)

  Box(modifier = modifier.fillMaxSize()) {
    PlacesListContent(
      state = uiState,
      onAddByMap = onAddByMap,
      onAddBySearch = onAddBySearch,
      onClearFilters = viewModel::clearFilters,
      onResetListOptions = viewModel::resetListOptions,
      onNameQueryChange = viewModel::setNameQuery,
      onWishlistFilterChange = viewModel::setWishlistFilter,
      onVisitedFilterChange = viewModel::setVisitedFilter,
      onToggleCategoryFilter = viewModel::toggleCategoryFilter,
      onClearCategoryFilter = viewModel::clearCategoryFilter,
      onToggleRegionFilter = viewModel::toggleRegionFilter,
      onClearRegionFilter = viewModel::clearRegionFilter,
      onSortChange = viewModel::setSort,
      onSortDescendingChange = viewModel::setSortDescending,
      onGroupingChange = viewModel::setGrouping,
      onToggleGroup = viewModel::toggleGroup,
      onViewModeChange = viewModel::setViewMode,
      onControlsCollapsedChange = viewModel::setControlsCollapsed,
      onItemClick = { onItemClick(it.place.id) },
      onMapPlaceClick = onItemClick,
      onToggleWishlist = viewModel::toggleWishlist,
    )
    // 右下の追加の丸を隠さないよう、その上に出す。
    SnackbarHost(
      snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = PlaceAddButtonClearance),
    )
  }
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
    onRegister = { lat, lng, name, wishlist, priority, visited, memo, googlePlaceId, googleName ->
      // 地図タップと同じ登録経路に通す（近接確認の要否も UseCase 側の判断に任せる）。
      // 施設情報は検索時に取得済みなので渡して引き直させない。
      viewModel.placeEdit.registerPlaceWithNearbyCheck(
        lat,
        lng,
        name,
        wishlist,
        priority,
        visited,
        memo,
        googlePlaceId,
        googleName = googleName,
        knownDetails = uiState.search.result,
      )
      onDone()
    },
  )
}

/**
 * 場所タブの一言（登録・紐付け・保存など）を下のスナックバーに出す。出したら（出している途中で画面を離れても）
 * [onShown] で消化する。消化しないと、一覧と詳細を行き来するたびに同じ一言が出てしまう。
 */
@Composable
internal fun PlaceMessageEffect(message: String?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
  LaunchedEffect(message) {
    val text = message ?: return@LaunchedEffect
    try {
      snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
    } finally {
      onShown()
    }
  }
}

// ---------------------------------------------------------------------------
// 追加（キーワード検索）。地図で選ぶ・詳細は PlaceMapScreens.kt
// ---------------------------------------------------------------------------

@Composable
private fun SearchAddContent(
  search: SearchState,
  onQueryChange: (String) -> Unit,
  onSelectPrediction: (String) -> Unit,
  onBackToPredictions: () -> Unit,
  onCancel: () -> Unit,
  onRegister: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
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

    PlaceKeywordSearchBody(
      query = search.query,
      predictions = search.predictions,
      isSearching = search.isSearching,
      onQueryChange = onQueryChange,
      onSelectPrediction = onSelectPrediction,
    )
  }
}

@Composable
private fun SearchResultForm(
  result: PlaceSearchResult,
  onBack: () -> Unit,
  onRegister: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val position = LatLng(result.latitude, result.longitude)
  val cameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom(position, 16f)
  }
  val sheetState = rememberFloatingSheetState()
  // マーカーの吹き出しをシステムバックで閉じられるようにする。
  val infoWindow = rememberMapInfoWindowState()

  Box(modifier = modifier.fillMaxSize()) {
    // 検索で選んだ場所を地図で示す（どこか視覚的に分かるように）。
    // 下パディングをシートの段に合わせて、選んだ場所がシートの下に隠れないようにする。
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(bottom = sheetState.heightOf(sheetState.detent)),
      uiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
      ),
    ) {
      // 検索結果のプレビュー。まだ登録していない＝これから決めるものなのでピン。
      val markerState = remember(position) { MarkerState(position = position) }
      MapMarker(
        "search-result",
        position.latitude,
        position.longitude,
        infoWindow = infoWindow,
        state = markerState,
        title = result.name,
      ) {
        MapPinMarker(bg = PathlyMapColors.Picked, glyph = R.drawable.ic_add)
      }
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
      sheetState = sheetState,
    )
  }
}

/** 訪問1件の行（日付・滞在・その訪問のメモ）。共通の編集ボディ [PlaceEditorBody] からも使う。 */
@Composable
internal fun VisitRow(
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
 * 行きたいに登録（ラベル付きスイッチ）→ 優先度 → 訪問済み を出す。
 * 送信ボタン・地図・履歴・削除などは呼び出し側が持つ。
 *
 * **行きたいと訪問済みは別の軸**なので、訪問済みは行きたいの中に入れ子にしない（adr/0020）。
 */
@Composable
internal fun PlaceFormBody(
  name: String,
  onNameChange: (String) -> Unit,
  nameLabel: String,
  category: PlaceCategory?,
  address: String?,
  onOpenInMaps: (() -> Unit)?,
  memo: String,
  onMemoChange: (String) -> Unit,
  wishlist: Boolean,
  onWishlistChange: (Boolean) -> Unit,
  priority: Priority,
  onPriorityChange: (Priority) -> Unit,
  modifier: Modifier = Modifier,
  // この場所の名前（表示名）。名前欄は空で始まるので、識別はこの見出しが担う。
  // 施設でない地点のように名前が無いときは、操作の説明を入れる。
  heading: String? = null,
  // Google 由来の名前。空欄のときだけ「これを直して使う」導線を出す。名前欄は「自分で付けた名前」
  // 専用にしたので、施設名を少し変えたいだけのときに打ち直さずに済むようにする。
  namePrefill: String? = null,
  wishlistOffHint: String? = null,
  // 行きたいで行ったことがあるとき、行きたいの切り替えの下に出す一文（「行きたいで 3 回行った…」）。
  wishlistHistory: String? = null,
  visitedContent: (@Composable () -> Unit)? = null,
) {
  Column(modifier = modifier) {
    heading?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp),
      )
    }

    // 主役: 行きたい・優先度・訪問済み・メモ（いちばんよく直すもの。シートを開いてすぐ見える位置に置く）。
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("行きたいに登録")
      // 下の優先度の星・一覧の旗と同じ「行きたい」の紅にする（藍だと別のものに見える）。
      Switch(
        checked = wishlist,
        onCheckedChange = onWishlistChange,
        colors = SwitchDefaults.colors(
          checkedTrackColor = PathlyTheme.statusColors.wishlist,
          checkedThumbColor = MaterialTheme.colorScheme.surface,
        ),
      )
    }

    // 行きたいは行けば終わるので、かつて行きたいだったことをここで見せる（→ adr/0036）。
    wishlistHistory?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = PathlyTheme.statusColors.visited,
      )
    }

    // 優先度は「行きたい」に属する情報なので、こちらは入れ子のままにする。
    if (wishlist) {
      Spacer(modifier = Modifier.height(4.dp))
      PrioritySelector(selected = priority, onSelect = onPriorityChange)
    } else if (wishlistOffHint != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = wishlistOffHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // 訪問済みは行きたいと独立。行きたいOFFのままでも付け外しできる。
    visitedContent?.let { content ->
      Spacer(modifier = Modifier.height(12.dp))
      content()
    }

    // メモは「行きたい」に関係なく常に入力できる。
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
      value = memo,
      onValueChange = onMemoChange,
      label = { Text("メモ（任意）") },
      modifier = Modifier.fillMaxWidth(),
    )

    // 脇役: 自分で付ける名前・業種・住所・Google マップで開く。
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
      value = name,
      onValueChange = onNameChange,
      label = { Text(nameLabel) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    // 名前は見出しに出ているので、ここでは繰り返さない（1行に収める）。
    namePrefill?.takeIf { it.isNotBlank() && name.isEmpty() }?.let { candidate ->
      TextButton(
        onClick = { onNameChange(candidate) },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
      ) {
        Text(
          text = "この名前から書き換える",
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Google 由来のカテゴリ・住所（あれば）。「どんな場所か」の手がかり。読取専用。
    category?.let { c ->
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = c.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    address?.takeIf { it.isNotBlank() }?.let { a ->
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Google マップアプリで開く（写真・口コミ・営業時間・経路は Google に委ねる）。
    onOpenInMaps?.let { open ->
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(onClick = open) {
        Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
        Text(text = " Google マップで開く")
      }
    }
  }
}

/**
 * 手動の「訪問済み」トグル。登録フォームでも編集フォームでも同じ見た目にする。
 * 立ち寄り記録がある場所はそれ自体で訪問済みなので、そちらでは出さない（件数を出す）。
 */
@Composable
internal fun VisitedToggle(visited: Boolean, onVisitedChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = if (visited) "訪問済み" else "未訪問")
    Switch(checked = visited, onCheckedChange = onVisitedChange)
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
        color = PathlyTheme.statusColors.wishlist,
        modifier = Modifier
          .clip(CircleShape)
          .clickable { onSelect(Priority.fromValue(i - 1)) }
          .padding(6.dp),
      )
    }
  }
}

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
