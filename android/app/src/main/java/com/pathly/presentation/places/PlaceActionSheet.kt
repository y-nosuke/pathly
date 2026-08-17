package com.pathly.presentation.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.rememberFloatingSheetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 地図の上に出す**統一の「場所シート」**。対象（未登録の空き地点 / 未登録 POI / 検索で選んだ施設 /
 * 登録済みの場所）に応じて中身と操作を出し分ける。本体は追加・検索・編集と同じ [PlaceFormBody] を
 * 共有する。詳細は docs/designs/map-tap-behavior.md を参照。
 */
sealed interface PlaceSheetTarget {
  /** 施設ではない任意の地点。 */
  data class NewPoint(val latLng: LatLng) : PlaceSheetTarget

  /** 未登録の施設(POI)。 */
  data class NewPoi(val poi: PointOfInterest) : PlaceSheetTarget

  /** キーワード検索で選んだ施設。詳細（カテゴリ・住所）は取得済みなので引き直さない。 */
  data class NewSearchResult(val result: PlaceSearchResult) : PlaceSheetTarget

  /** 登録済みの場所（その場で編集する）。 */
  data class Existing(val placeId: Long) : PlaceSheetTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceActionSheet(
  target: PlaceSheetTarget,
  onDismiss: () -> Unit,
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult?,
  // 新規登録（空き地点/POI/検索結果）を確定する。近接確認（表示ON=新規/OFF=確認）は呼び出し側に委ねる。
  onRegisterNew: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  // 以下は [PlaceSheetTarget.Existing] を渡す画面だけが必要（新規登録専用の画面は既定のままでよい）。
  // 編集中に保存しても最新値を保てるよう、単一 place はリアクティブに購読する。
  onObservePlace: (placeId: Long) -> Flow<PlaceListItem?> = { emptyFlow() },
  onObserveVisits: (placeId: Long) -> Flow<List<PlaceVisit>> = { emptyFlow() },
  onSaveExisting: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean, link: PlaceSearchResult?) -> Unit = { _, _, _, _, _, _, _ -> },
  onDeletePlace: (placeId: Long) -> Unit = {},
  onOpenTrack: (trackId: Long) -> Unit = {},
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult> = { _, _ -> emptyList() },
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction> = { emptyList() },
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult? = { null },
  // 既存 place に「立ち寄りに追加」（記録中のみ）。null なら出さない。
  onAddStop: ((item: PlaceListItem) -> Unit)? = null,
  modifier: Modifier = Modifier,
  // 地図を見ながら操作できるよう、スクリムを持たない自前のシートで出す。
  // ModalBottomSheet はスクリムがタップを吸うため、色を透明にしても地図を動かせず、
  // 触ると閉じてしまう。ここでは地図のパン・ズーム・タップをそのまま生かす（ADR-0010）。
  // シートの下に隠れる地点があると困る画面（対象を地図で示す画面）は、地図の下パディングを
  // 合わせるためにここへ自分で作った state を渡す。
  sheetState: FloatingSheetState = rememberFloatingSheetState(),
) {
  // 非モーダルなのでバックは自分で受ける（モーダルのときは自動で閉じていた）。
  BackHandler { onDismiss() }

  // 別の地点をタップし直したら、畳んでいても開き直す（変化が見えないと操作を見失う）。
  LaunchedEffect(target) { sheetState.settleTo(SheetDetent.PEEK) }

  // 畳んでいる間は地図が全面に見える。入力内容は保持したままなので、ここから戻せる。
  FloatingSheet(state = sheetState, modifier = modifier, restoreLabel = "▲ 入力に戻る") {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
    ) {
      when (target) {
        is PlaceSheetTarget.NewPoint -> NewPlaceEditor(
          latLng = target.latLng,
          googlePlaceId = null,
          initialName = null,
          knownDetails = null,
          onFetchPoiDetails = onFetchPoiDetails,
          onRegisterNew = onRegisterNew,
          onDismiss = onDismiss,
        )

        is PlaceSheetTarget.NewPoi -> NewPlaceEditor(
          latLng = target.poi.latLng,
          googlePlaceId = target.poi.placeId,
          initialName = target.poi.name,
          knownDetails = null,
          onFetchPoiDetails = onFetchPoiDetails,
          onRegisterNew = onRegisterNew,
          onDismiss = onDismiss,
        )

        is PlaceSheetTarget.NewSearchResult -> NewPlaceEditor(
          latLng = LatLng(target.result.latitude, target.result.longitude),
          googlePlaceId = target.result.googlePlaceId,
          initialName = target.result.name,
          knownDetails = target.result,
          onFetchPoiDetails = onFetchPoiDetails,
          onRegisterNew = onRegisterNew,
          onDismiss = onDismiss,
        )

        is PlaceSheetTarget.Existing -> ExistingPlaceEditor(
          placeId = target.placeId,
          onObservePlace = onObservePlace,
          onObserveVisits = onObserveVisits,
          onSaveExisting = onSaveExisting,
          onDeletePlace = onDeletePlace,
          onOpenTrack = onOpenTrack,
          onFetchNearbyPois = onFetchNearbyPois,
          onSearchPredictions = onSearchPredictions,
          onFetchPrediction = onFetchPrediction,
          onAddStop = onAddStop,
          onDismiss = onDismiss,
        )
      }
    }
  }
}

/**
 * 未登録の地点/施設を場所として登録する編集部。施設（POI・検索結果）なら Google 情報
 * （カテゴリ/住所）をプレビューする。[knownDetails] があれば取得済みとしてそれを使い、
 * 無ければ [googlePlaceId] から引く。
 */
@Composable
private fun NewPlaceEditor(
  latLng: LatLng,
  googlePlaceId: String?,
  initialName: String?,
  knownDetails: PlaceSearchResult?,
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult?,
  onRegisterNew: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  // 名前欄は「自分で付けた名前」専用なので、施設名を初期値には入れない（薄字の候補として見せる）。
  // 入っているかどうかで「自分で付けたのか、Google の名前なのか」が一目で分かる。
  var name by remember(latLng, googlePlaceId) { mutableStateOf("") }
  var memo by remember(latLng, googlePlaceId) { mutableStateOf("") }
  var wishlist by remember(latLng, googlePlaceId) { mutableStateOf(false) }
  var priority by remember(latLng, googlePlaceId) { mutableStateOf(Priority.MEDIUM) }
  // このアプリを使う前に行った場所も、登録と同時に訪問済みにできる（行きたいとは独立）。
  var visited by remember(latLng, googlePlaceId) { mutableStateOf(false) }
  val context = LocalContext.current

  // 施設は開いたら Google から施設情報（カテゴリ・住所）を取得してプレビューする（結果は登録時に使い回し）。
  val details by produceState(knownDetails, latLng, googlePlaceId) {
    value = knownDetails ?: googlePlaceId?.let { onFetchPoiDetails(it) }
  }

  PlaceFormBody(
    name = name,
    onNameChange = { name = it },
    nameLabel = "自分で付ける名前（任意）",
    // 施設なら名前を見出しに。名前を持たない地点は、何をしようとしているかを出す。
    heading = initialName ?: "この地点を場所として登録",
    namePrefill = initialName,
    category = details?.category,
    address = details?.address,
    // 施設のときだけ。空き地点は Google に開く先（施設ページ）が無く、座標にピンが立つだけなので出さない。
    onOpenInMaps = googlePlaceId?.let { id ->
      { openPlaceInGoogleMaps(context, id, latLng.latitude, latLng.longitude, name.ifBlank { initialName ?: "選択した場所" }) }
    },
    memo = memo,
    onMemoChange = { memo = it },
    wishlist = wishlist,
    onWishlistChange = { wishlist = it },
    priority = priority,
    onPriorityChange = { priority = it },
    visitedContent = { VisitedToggle(visited) { visited = it } },
  )
  SheetActions {
    TextButton(onClick = onDismiss) { Text("キャンセル") }
    Button(onClick = {
      // 名前欄は空で始まるので、入力があれば自分で付けた名前。ただし候補を書き換えずに
      // そのまま確定したときは Google の名前と同じなので、ユーザー名としては残さない。
      val typed = name.trim().ifBlank { null }
      val googleName = initialName?.trim()?.ifBlank { null }
      val userName = typed?.takeIf { it != googleName }
      onRegisterNew(latLng.latitude, latLng.longitude, userName, wishlist, priority, visited, memo.ifBlank { null }, googlePlaceId, googleName)
      onDismiss()
    }) { Text("登録") }
  }
}

/**
 * 登録済みの場所を、地図を見ながらその場で編集する編集部。中身は場所一覧から開く詳細と
 * 同じ [PlaceEditorBody]（入口が違うだけで、できることは同じ）。
 */
@Composable
private fun ExistingPlaceEditor(
  placeId: Long,
  onObservePlace: (placeId: Long) -> Flow<PlaceListItem?>,
  onObserveVisits: (placeId: Long) -> Flow<List<PlaceVisit>>,
  onSaveExisting: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean, link: PlaceSearchResult?) -> Unit,
  onDeletePlace: (placeId: Long) -> Unit,
  onOpenTrack: (trackId: Long) -> Unit,
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult>,
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult?,
  onAddStop: ((item: PlaceListItem) -> Unit)?,
  onDismiss: () -> Unit,
) {
  // 購読なので、保存しても値が古くならない（＝詳細と同じく保存後もシートに留まれる）。
  val placeFlow = remember(placeId) { onObservePlace(placeId) }
  val item by placeFlow.collectAsStateWithLifecycle(null)
  val visitsFlow = remember(placeId) { onObserveVisits(placeId) }
  val visits by visitsFlow.collectAsStateWithLifecycle(emptyList())

  // 削除されたら（自分で消したときも含め）閉じる。読み込み前の null と区別するため、
  // 一度でも読めたかを覚えておく。
  var everLoaded by remember(placeId) { mutableStateOf(false) }
  LaunchedEffect(item) {
    if (item != null) {
      everLoaded = true
    } else if (everLoaded) {
      onDismiss()
    }
  }

  val loaded = item
  if (loaded == null) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  PlaceEditorBody(
    item = loaded,
    visits = visits,
    onSave = { name, note, wishlist, priority, visited, link ->
      onSaveExisting(loaded, name, note, wishlist, priority, visited, link)
    },
    // 消えると購読が null を流し、上の LaunchedEffect がシートを閉じる。
    onDelete = { onDeletePlace(loaded.place.id) },
    onOpenTrack = onOpenTrack,
    onFetchNearbyPois = onFetchNearbyPois,
    onSearchPredictions = onSearchPredictions,
    onFetchPrediction = onFetchPrediction,
    onAddStop = onAddStop?.let { add ->
      {
        add(loaded)
        onDismiss()
      }
    },
    trailingActions = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.align(Alignment.End),
      ) {
        Text("閉じる")
      }
    },
  )
}

/** シート下部のボタン行（右寄せ）。 */
@Composable
private fun SheetActions(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
  androidx.compose.foundation.layout.Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}
