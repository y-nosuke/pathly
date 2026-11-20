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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pathly.presentation.common.tintedColors
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlySpacing
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
  val statsExpanded by viewModel.statsExpanded.collectAsStateWithLifecycle()
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
      statsExpanded = statsExpanded,
      onStatsExpandedChange = viewModel::setStatsExpanded,
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
      .padding(PathlySpacing.lg),
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

    Spacer(modifier = Modifier.height(PathlySpacing.sm))

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
        .padding(PathlySpacing.md),
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
      .padding(vertical = PathlySpacing.md),
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
        modifier = Modifier.padding(top = PathlySpacing.xxs),
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 共通
// ---------------------------------------------------------------------------

/**
 * 場所フォームの共通本体。地図で追加・検索で追加・POI 登録・詳細編集のどこでも同じ並びで
 * 主役（行きたい・訪問済みの切り替えボタンの行 → 優先度 → メモ）→ 脇役（[nameField] → カテゴリ/住所（Google 由来・
 * 読取専用）→ Google マップで開く）を出す。
 * 送信ボタン・地図・履歴・削除などは呼び出し側が持つ。
 *
 * **行きたいと訪問済みは別の軸**なので、訪問済みは行きたいの中に入れ子にしない（adr/0020）。
 */
@Composable
internal fun PlaceFormBody(
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
  // 脇役の先頭に置く名前の欄（登録のシートの [PlaceNameField]）。登録済みの場所は見出しを押して開く
  // ダイアログで名前を付けるので渡さない。
  nameField: (@Composable () -> Unit)? = null,
  // 行きたいで行ったことがあるとき、行きたいの切り替えの下に出す一文（「行きたいで 3 回行った…」）。
  wishlistHistory: String? = null,
  // 行きたいの右に並べる訪問済みのボタン（[VisitedToggle]・[VisitedByStopsChip]）。
  visitedContent: (@Composable () -> Unit)? = null,
) {
  Column(modifier = modifier) {
    heading?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = PathlySpacing.md),
      )
    }

    // 主役: 行きたい・訪問済み・優先度・メモ（いちばんよく直すもの。シートを開いてすぐ見える位置に置く）。
    // 行きたいと訪問済みは名前付きの切り替えボタンを 1 行に並べ、ハーフのシートでもメモまで見えるようにする。
    // 色は一覧の旗・マーカーと同じ（行きたい＝紅・訪問済み＝緑）。
    Row(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
      PlaceStatusChip(
        selected = wishlist,
        onClick = { onWishlistChange(!wishlist) },
        label = "行きたい",
        icon = if (wishlist) R.drawable.ic_flag_filled else R.drawable.ic_flag,
        color = PathlyTheme.statusColors.wishlist,
      )
      visitedContent?.invoke()
    }

    // 行きたいは行けば終わるので、かつて行きたいだったことをここで見せる（→ adr/0036）。
    wishlistHistory?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = PathlyTheme.statusColors.visited,
      )
    }

    // 優先度は「行きたい」に属する情報なので、行きたいを入れたときだけ出す。
    if (wishlist) {
      Spacer(modifier = Modifier.height(PathlySpacing.xs))
      PrioritySelector(selected = priority, onSelect = onPriorityChange)
    }

    // メモは「行きたい」に関係なく常に入力できる。
    Spacer(modifier = Modifier.height(PathlySpacing.md))
    OutlinedTextField(
      value = memo,
      onValueChange = onMemoChange,
      label = { Text("メモ（任意）") },
      modifier = Modifier.fillMaxWidth(),
    )

    // 脇役: 自分で付ける名前（登録のときだけ）・業種・住所・Google マップで開く。
    Spacer(modifier = Modifier.height(PathlySpacing.lg))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(PathlySpacing.lg))
    nameField?.invoke()

    // Google 由来のカテゴリ・住所（あれば）。「どんな場所か」の手がかり。読取専用。
    category?.let { c ->
      Spacer(modifier = Modifier.height(PathlySpacing.xs))
      Text(text = c.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    address?.takeIf { it.isNotBlank() }?.let { a ->
      Spacer(modifier = Modifier.height(PathlySpacing.xs))
      Text(text = a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Google マップアプリで開く（写真・口コミ・営業時間・経路は Google に委ねる）。
    onOpenInMaps?.let { open ->
      Spacer(modifier = Modifier.height(PathlySpacing.sm))
      OutlinedButton(onClick = open) {
        Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
        Text(text = " Google マップで開く")
      }
    }
  }
}

/**
 * 登録のシートの名前の欄。欄は「自分で付ける名前」専用で、空なら Google の名前などで表示する。
 * [prefill]（Google の名前）があれば、欄が空のときだけ「この名前から書き換える」を出す（施設名を少し変えたいだけの
 * ときに打ち直さずに済むように。名前は見出しに出ているので、ここでは繰り返さない）。
 */
@Composable
internal fun PlaceNameField(name: String, onNameChange: (String) -> Unit, prefill: String?) {
  OutlinedTextField(
    value = name,
    onValueChange = onNameChange,
    label = { Text("自分で付ける名前（任意）") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  prefill?.takeIf { it.isNotBlank() && name.isEmpty() }?.let { candidate ->
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
}

/**
 * 手動の「訪問済み」の切り替えボタン。登録フォームでも編集フォームでも同じ見た目にする。
 * 訪問済みは行きたいと独立で、行きたいを切ったままでも付け外しできる。
 * 立ち寄り記録がある場所はそれ自体で訪問済みなので、こちらではなく [VisitedByStopsChip] を出す。
 */
@Composable
internal fun VisitedToggle(visited: Boolean, onVisitedChange: (Boolean) -> Unit) {
  PlaceStatusChip(
    selected = visited,
    onClick = { onVisitedChange(!visited) },
    label = "訪問済み",
    icon = R.drawable.ic_check_circle,
    color = PathlyTheme.statusColors.visited,
  )
}

/** 立ち寄り記録がある場所の訪問済み（記録から決まるので切り替えられない。一覧の「訪問1回」と同じく回数を添える）。 */
@Composable
internal fun VisitedByStopsChip(visitCount: Int) {
  PlaceStatusChip(
    selected = true,
    onClick = {},
    label = "訪問済み $visitCount 回",
    icon = R.drawable.ic_check_circle,
    color = PathlyTheme.statusColors.visited,
    enabled = false,
  )
}

/**
 * 場所の状態（行きたい・訪問済み）の切り替えボタン。入れているときは状態の色を薄く敷いて文字とアイコンをその色に、
 * 切っているときは枠だけ。[enabled] が false でも入れているときの見た目のまま（記録から決まる訪問済み）。
 */
@Composable
private fun PlaceStatusChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
  icon: Int,
  color: Color,
  enabled: Boolean = true,
) {
  val (container, content) = tintedColors(color, base = MaterialTheme.colorScheme.surface)
  FilterChip(
    selected = selected,
    onClick = onClick,
    enabled = enabled,
    label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    colors = FilterChipDefaults.filterChipColors(
      // 切っているときのアイコンは字と同じ灰色（既定の藍だと、入っているように見える）。
      iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
      selectedContainerColor = container,
      selectedLabelColor = content,
      selectedLeadingIconColor = content,
      disabledSelectedContainerColor = container,
      disabledLabelColor = content,
      disabledLeadingIconColor = content,
    ),
    border = FilterChipDefaults.filterChipBorder(
      enabled = enabled,
      selected = selected,
      selectedBorderColor = content,
      selectedBorderWidth = 1.dp,
      disabledSelectedBorderColor = content,
    ),
  )
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
    Spacer(modifier = Modifier.width(PathlySpacing.sm))
    (1..3).forEach { i ->
      Text(
        text = if (i <= level) "★" else "☆",
        style = MaterialTheme.typography.headlineSmall,
        color = PathlyTheme.statusColors.wishlist,
        // 星の押せる範囲は 48dp 以上にする（字の周りだけだと 36dp ほどで押し外しやすい）。
        textAlign = TextAlign.Center,
        modifier = Modifier
          .clip(CircleShape)
          .clickable { onSelect(Priority.fromValue(i - 1)) }
          .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
          .wrapContentSize(),
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
