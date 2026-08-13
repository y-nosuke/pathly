package com.pathly.presentation.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.rememberFloatingSheetState

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
  onRegisterNew: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?) -> Unit,
  // 以下2つは [PlaceSheetTarget.Existing] を渡す画面だけが必要（新規登録専用の画面は既定のままでよい）。
  onLoadPlace: suspend (placeId: Long) -> PlaceListItem? = { null },
  onSaveExisting: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean) -> Unit = { _, _, _, _, _, _ -> },
  // 既存 place に「立ち寄りに追加」（記録中のみ）。null なら出さない。
  onAddStop: ((item: PlaceListItem) -> Unit)? = null,
  // 既存 place の詳細画面を開く。
  onOpenDetail: (placeId: Long) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  // 地図を見ながら操作できるよう、スクリムを持たない自前のシートで出す。
  // ModalBottomSheet はスクリムがタップを吸うため、色を透明にしても地図を動かせず、
  // 触ると閉じてしまう。ここでは地図のパン・ズーム・タップをそのまま生かす（ADR-0010）。
  val sheetState = rememberFloatingSheetState()

  // 非モーダルなのでバックは自分で受ける（モーダルのときは自動で閉じていた）。
  BackHandler { onDismiss() }

  // 別の地点をタップし直したら、畳んでいても開き直す（変化が見えないと操作を見失う）。
  LaunchedEffect(target) { sheetState.settleTo(SheetDetent.PEEK) }

  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    // 畳んでいる間は地図が全面に見える。入力内容は保持したままなので、ここから戻せる。
    if (sheetState.detent == SheetDetent.HIDDEN) {
      Surface(
        onClick = { sheetState.settleTo(SheetDetent.PEEK) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier
          .navigationBarsPadding()
          .padding(bottom = 24.dp),
      ) {
        Text(
          text = "▲ 入力に戻る",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
      }
    }

    FloatingSheet(state = sheetState) {
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
            onLoadPlace = onLoadPlace,
            onSaveExisting = onSaveExisting,
            onAddStop = onAddStop,
            onOpenDetail = onOpenDetail,
            onDismiss = onDismiss,
          )
        }
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
  onRegisterNew: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, memo: String?, googlePlaceId: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember(latLng, googlePlaceId) { mutableStateOf(initialName.orEmpty()) }
  var memo by remember(latLng, googlePlaceId) { mutableStateOf("") }
  var wishlist by remember(latLng, googlePlaceId) { mutableStateOf(false) }
  var priority by remember(latLng, googlePlaceId) { mutableStateOf(Priority.MEDIUM) }
  val context = LocalContext.current

  // 施設は開いたら Google から施設情報（カテゴリ・住所）を取得してプレビューする（結果は登録時に使い回し）。
  val details by produceState(knownDetails, latLng, googlePlaceId) {
    value = knownDetails ?: googlePlaceId?.let { onFetchPoiDetails(it) }
  }

  val isFacility = googlePlaceId != null
  Text(
    text = if (isFacility) "この場所を登録" else "この地点を場所として登録",
    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 12.dp),
  )
  PlaceFormBody(
    name = name,
    onNameChange = { name = it },
    nameLabel = if (isFacility) "名前" else "名前（任意）",
    category = details?.category,
    address = details?.address,
    onOpenInMaps = {
      openPlaceInGoogleMaps(context, googlePlaceId, latLng.latitude, latLng.longitude, name.ifBlank { "選択した場所" })
    },
    memo = memo,
    onMemoChange = { memo = it },
    wishlist = wishlist,
    onWishlistChange = { wishlist = it },
    priority = priority,
    onPriorityChange = { priority = it },
  )
  SheetActions {
    TextButton(onClick = onDismiss) { Text("キャンセル") }
    Button(onClick = {
      onRegisterNew(latLng.latitude, latLng.longitude, name.ifBlank { null }, wishlist, priority, memo.ifBlank { null }, googlePlaceId)
      onDismiss()
    }) { Text("登録") }
  }
}

/** 登録済みの場所を、その場で編集する編集部（記録画面のまま名前・行きたい・優先度・メモ・訪問済みを保存）。 */
@Composable
private fun ExistingPlaceEditor(
  placeId: Long,
  onLoadPlace: suspend (placeId: Long) -> PlaceListItem?,
  onSaveExisting: (item: PlaceListItem, name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean) -> Unit,
  onAddStop: ((item: PlaceListItem) -> Unit)?,
  onOpenDetail: (placeId: Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val item by produceState<PlaceListItem?>(null, placeId) { value = onLoadPlace(placeId) }
  val loaded = item
  if (loaded == null) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  var name by remember(loaded.place.id) { mutableStateOf(loaded.place.name ?: "") }
  var memo by remember(loaded.place.id) { mutableStateOf(loaded.note ?: "") }
  var wishlist by remember(loaded.wishlistId) { mutableStateOf(loaded.isWishlisted) }
  var priority by remember(loaded.wishlistId) { mutableStateOf(loaded.priority ?: Priority.MEDIUM) }
  var visited by remember(loaded.wishlistId) { mutableStateOf(loaded.isManuallyVisited) }
  val context = LocalContext.current

  Text(
    text = "場所を編集",
    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 12.dp),
  )
  PlaceFormBody(
    name = name,
    onNameChange = { name = it },
    nameLabel = "名前",
    category = loaded.place.category,
    address = loaded.place.googleAddress,
    onOpenInMaps = {
      openPlaceInGoogleMaps(context, loaded.place.googlePlaceId, loaded.place.latitude, loaded.place.longitude, loaded.displayName)
    },
    memo = memo,
    onMemoChange = { memo = it },
    wishlist = wishlist,
    onWishlistChange = { wishlist = it },
    priority = priority,
    onPriorityChange = { priority = it },
    // 実際の立ち寄り記録が無いときだけ手動の「訪問済み」を切り替えられる（記録があれば自動で訪問済み）。
    visitedContent = if (loaded.visitCount == 0) {
      {
        Column(modifier = Modifier.fillMaxWidth()) {
          androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("訪問済み")
            Switch(checked = visited, onCheckedChange = { visited = it })
          }
        }
      }
    } else {
      null
    },
  )
  SheetActions {
    TextButton(onClick = onDismiss) { Text("閉じる") }
    Button(onClick = {
      onSaveExisting(loaded, name, memo, wishlist, priority, visited)
      onDismiss()
    }) { Text("保存") }
  }
  // 記録中のみ「立ち寄りに追加」（この訪問を既存 place にひも付ける）。
  onAddStop?.let { add ->
    OutlinedButton(
      onClick = {
        add(loaded)
        onDismiss()
      },
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text("立ち寄りに追加") }
  }
  OutlinedButton(
    onClick = {
      onOpenDetail(loaded.place.id)
      onDismiss()
    },
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
  ) { Text("詳細を開く") }
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
