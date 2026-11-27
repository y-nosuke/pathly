package com.pathly.presentation.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
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
import com.pathly.presentation.common.SheetFormLayout
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.ui.theme.PathlySpacing
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
  data class Existing(
    val placeId: Long,
    // 開いてすぐ「Google施設を選び直す」を出す（同じ施設の確認待ちで「別の施設を選び直す」を選んだとき → adr/0037）。
    val openLinkPicker: Boolean = false,
  ) : PlaceSheetTarget
}

/**
 * 登録のシートを計画の詳細から開いたときの違い（→ docs/specs/plans.md の「登録のシート（計画から開いたとき）」）。
 * 場所タブ・記録画面・経路詳細では渡さない（null）。
 */
internal class PlaceRegisterOptions(
  /** 行きたいを初めから入れておく（行きたくて計画に入れることが多いため）。 */
  val initialWishlist: Boolean,
  /** 登録のボタンの文言（「登録して 2日目に足す」）。 */
  val registerLabel: String,
  /** 見出しの下に足す欄（計画の「足す日」）。 */
  val belowHeading: (@Composable () -> Unit)? = null,
  /** [belowHeading] の高さ。登録のシートのハーフの最低の高さに足す（メモの欄まで見えるように）。 */
  val belowHeadingHeight: Dp = 0.dp,
  /** 登録のボタンを押した（登録を頼む前に呼ぶ。計画はここで足す日を決める）。 */
  val onRegister: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceActionSheet(
  target: PlaceSheetTarget,
  onDismiss: () -> Unit,
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult?,
  // 新規登録（空き地点/POI/検索結果）を確定する。近接確認（表示ON=新規/OFF=確認）は呼び出し側に委ねる。
  onRegisterNew: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  modifier: Modifier = Modifier,
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
  // 既存 place の同じ施設の確認待ちへの答え（→ adr/0037）。null なら確認待ちを出さない。
  mergeActions: PlaceMergeActions? = null,
  // 地図を見ながら操作できるよう、スクリムを持たない自前のシートで出す。
  // ModalBottomSheet はスクリムがタップを吸うため、色を透明にしても地図を動かせず、
  // 触ると閉じてしまう。ここでは地図のパン・ズーム・タップをそのまま生かす（ADR-0010）。
  // シートの下に隠れる地点があると困る画面（対象を地図で示す画面）は、地図の下パディングを
  // 合わせるためにここへ自分で作った state を渡す。
  sheetState: FloatingSheetState = rememberFloatingSheetState(peekFraction = PLACE_SHEET_PEEK_FRACTION),
  // 計画の詳細から登録するときの違い（null は場所タブ・記録画面などのふつうの登録）。
  registerOptions: PlaceRegisterOptions? = null,
) {
  // 非モーダルなのでバックは自分で受ける（モーダルのときは自動で閉じていた）。
  BackHandler { onDismiss() }

  // 登録のシートだけ、ハーフをメモの欄まで見える高さ以上にする（横向き・文字が大きい設定では 4 割に収まらない）。
  // 場所詳細などと同じ state を使い回すので、登録でなくなったら・閉じたら元の高さに戻す。
  // 下の開き直しより先に置く（開き直す先のハーフを先に決める）。
  val isNew = target !is PlaceSheetTarget.Existing
  // 計画から開くと行きたいが初めから入っていて優先度の行が出るので、その分も足す。
  val registerExtra = (registerOptions?.belowHeadingHeight ?: 0.dp) +
    (if (registerOptions?.initialWishlist == true) PRIORITY_ROW_HEIGHT else 0.dp)
  val registerPeekPx = rememberRegisterSheetMinPeekPx(registerExtra)
  DisposableEffect(sheetState, isNew, registerPeekPx) {
    sheetState.raisePeekTo(if (isNew) registerPeekPx else null)
    onDispose { sheetState.raisePeekTo(null) }
  }

  // 別の地点をタップし直したら、畳んでいても開き直す（変化が見えないと操作を見失う）。
  LaunchedEffect(target) { sheetState.settleTo(SheetDetent.PEEK) }

  // 畳んでいる間は地図が全面に見える。入力内容は保持したままなので、ここから戻せる。
  // 本文はシートの中でスクロールし、登録・保存は下端に固定する（各編集部の SheetFormLayout）。
  FloatingSheet(state = sheetState, modifier = modifier, restoreLabel = "▲ 入力に戻る") {
    when (target) {
      is PlaceSheetTarget.NewPoint -> NewPlaceEditor(
        latLng = target.latLng,
        googlePlaceId = null,
        initialName = null,
        knownDetails = null,
        onFetchPoiDetails = onFetchPoiDetails,
        onRegisterNew = onRegisterNew,
        onDismiss = onDismiss,
        options = registerOptions,
      )

      is PlaceSheetTarget.NewPoi -> NewPlaceEditor(
        latLng = target.poi.latLng,
        googlePlaceId = target.poi.placeId,
        initialName = target.poi.name,
        knownDetails = null,
        onFetchPoiDetails = onFetchPoiDetails,
        onRegisterNew = onRegisterNew,
        onDismiss = onDismiss,
        options = registerOptions,
      )

      is PlaceSheetTarget.NewSearchResult -> NewPlaceEditor(
        latLng = LatLng(target.result.latitude, target.result.longitude),
        googlePlaceId = target.result.googlePlaceId,
        initialName = target.result.name,
        knownDetails = target.result,
        onFetchPoiDetails = onFetchPoiDetails,
        onRegisterNew = onRegisterNew,
        onDismiss = onDismiss,
        options = registerOptions,
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
        mergeActions = mergeActions,
        openLinkPicker = target.openLinkPicker,
        onDismiss = onDismiss,
      )
    }
  }
}

/**
 * 場所のシート（場所詳細・地図をタップして出る場所シート・登録のシート）のハーフの高さ（画面に対する割合）。
 * どの画面から出しても同じ高さにし、地図で場所の周りを見られるよう低めにする（中身はスクロールとフルで見る）。
 * 登録のシートだけは [rememberRegisterSheetMinPeekPx] より低くしない。
 */
internal const val PLACE_SHEET_PEEK_FRACTION = 0.4f

/**
 * 登録のシートのハーフの最低の高さ（px）。つまみ・見出し・行きたい／訪問済み・メモの欄・登録のボタンまでが入る高さ。
 * 縦向きのふつうの画面では 4 割（[PLACE_SHEET_PEEK_FRACTION]）の方が高く、効くのは横向き・背の低い画面・文字が大きい
 * 設定のとき。登録は入れるものが多く、メモの欄に気づけないと困るので、地図の見える広さよりこちらを優先する
 * （フルより高くはならない）。
 *
 * 文字の大きさの設定で伸びるのは字の行だけなので、余白（dp）と字の行の高さ（sp）を分けて足す。sp は文字の設定で伸びるが、
 * 大きな字ほど伸び方が小さい（Android の非線形の拡大）ので、全体を倍率で掛けると高くしすぎる。
 */
@Composable
private fun rememberRegisterSheetMinPeekPx(extra: Dp): Float {
  val density = LocalDensity.current
  val typography = MaterialTheme.typography
  return remember(density, typography, extra) {
    with(density) {
      // 見出し・行きたい／訪問済み・メモの欄の字・登録のボタン、の 4 行。
      val textLines = listOf(
        typography.titleMedium.lineHeight,
        typography.labelLarge.lineHeight,
        typography.bodyLarge.lineHeight,
        typography.labelLarge.lineHeight,
      )
      (REGISTER_SHEET_MIN_PEEK_SPACING + extra).toPx() + textLines.sumOf { it.toPx().toDouble() }.toFloat()
    }
  }
}

/** 優先度の行（星の 48dp と上の間）。行きたいが入っているときだけ出る。 */
private val PRIORITY_ROW_HEIGHT = 56.dp

/** 登録のシートのハーフのうち、字の行でない部分（つまみの行・ボタンや欄の枠と余白・登録の行の余白）。エミュレータの実測から。 */
private val REGISTER_SHEET_MIN_PEEK_SPACING = 216.dp

/**
 * 未登録の地点/施設を場所として登録する編集部。施設（POI・検索結果）なら Google 情報
 * （カテゴリ/住所）をプレビューする。[knownDetails] があれば取得済みとしてそれを使い、
 * 無ければ [googlePlaceId] から引く。
 */
@Composable
private fun ColumnScope.NewPlaceEditor(
  latLng: LatLng,
  googlePlaceId: String?,
  initialName: String?,
  knownDetails: PlaceSearchResult?,
  onFetchPoiDetails: suspend (googlePlaceId: String) -> PlaceSearchResult?,
  onRegisterNew: (lat: Double, lng: Double, name: String?, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?, googlePlaceId: String?, googleName: String?) -> Unit,
  onDismiss: () -> Unit,
  options: PlaceRegisterOptions?,
) {
  // 名前欄は「自分で付けた名前」専用なので、施設名を初期値には入れない（薄字の候補として見せる）。
  // 入っているかどうかで「自分で付けたのか、Google の名前なのか」が一目で分かる。
  var name by remember(latLng, googlePlaceId) { mutableStateOf("") }
  var memo by remember(latLng, googlePlaceId) { mutableStateOf("") }
  var wishlist by remember(latLng, googlePlaceId) { mutableStateOf(options?.initialWishlist ?: false) }
  var priority by remember(latLng, googlePlaceId) { mutableStateOf(Priority.MEDIUM) }
  // このアプリを使う前に行った場所も、登録と同時に訪問済みにできる（行きたいとは独立）。
  var visited by remember(latLng, googlePlaceId) { mutableStateOf(false) }
  val context = LocalContext.current

  // 施設は開いたら Google から施設情報（カテゴリ・住所）を取得してプレビューする（結果は登録時に使い回し）。
  val details by produceState(knownDetails, latLng, googlePlaceId) {
    value = knownDetails ?: googlePlaceId?.let { onFetchPoiDetails(it) }
  }

  SheetFormLayout(
    actions = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
      Button(onClick = {
        // 名前欄は空で始まるので、入力があれば自分で付けた名前。ただし候補を書き換えずに
        // そのまま確定したときは Google の名前と同じなので、ユーザー名としては残さない。
        val typed = name.trim().ifBlank { null }
        val googleName = initialName?.trim()?.ifBlank { null }
        val userName = typed?.takeIf { it != googleName }
        options?.onRegister?.invoke()
        onRegisterNew(latLng.latitude, latLng.longitude, userName, wishlist, priority, visited, memo.ifBlank { null }, googlePlaceId, googleName)
        onDismiss()
      }, modifier = Modifier.testTag("placeRegister")) { Text(options?.registerLabel ?: "登録") }
    },
  ) {
    PlaceFormBody(
      // 施設なら名前を見出しに。名前を持たない地点は、何をしようとしているかを出す。
      heading = initialName ?: "この地点を場所として登録",
      belowHeading = options?.belowHeading,
      nameField = { PlaceNameField(name = name, onNameChange = { name = it }, prefill = initialName) },
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
  }
}

/**
 * 登録済みの場所を、地図を見ながらその場で編集する編集部。中身は場所一覧から開く詳細と
 * 同じ [PlaceEditorBody]（入口が違うだけで、できることは同じ）。
 */
@Composable
private fun ColumnScope.ExistingPlaceEditor(
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
  mergeActions: PlaceMergeActions?,
  openLinkPicker: Boolean,
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
    Box(modifier = Modifier.fillMaxWidth().padding(PathlySpacing.xxl), contentAlignment = Alignment.Center) {
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
    mergeActions = mergeActions,
    state = rememberPlaceEditorState(placeId, initiallyOpenLinkPicker = openLinkPicker),
    onAddStop = onAddStop?.let { add ->
      {
        add(loaded)
        onDismiss()
      }
    },
    onClose = onDismiss,
  )
}
