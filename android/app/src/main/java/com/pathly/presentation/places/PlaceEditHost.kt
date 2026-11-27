package com.pathly.presentation.places

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.Priority
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.NearbyConfirmPurpose
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.rememberFloatingSheetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 場所の編集の UI（場所シート・近くに既存の場所があるときの確認・削除の取り消し）が使う操作と状態。
 * 実体は [PlaceEditActions]（各画面の ViewModel が持つ）。画面にはこれ 1 つを渡す。
 *
 * 画面のテストは [None] から必要なものだけを差し替える（`object : PlaceEditUi by PlaceEditUi.None { ... }`）。
 */
interface PlaceEditUi {
  /** 空き地点の登録で近くに既存の場所が見つかったときの確認待ち。 */
  val nearbyRegisterPrompt: StateFlow<NearbyRegisterPrompt?>

  /** 場所を削除した（まとめた）直後の取り消し待ち。 */
  val deleteUndo: StateFlow<PlaceDeleteUndo>

  /**
   * 地図タップ・検索からの場所登録。近くに既存の場所があれば登録せず確認待ちにする。
   * [knownDetails] は検索結果のように施設情報を取得済みのとき。[mainPlaceId] は場所詳細の地図から
   * 登録するときの、その場所（主マーカーで見えているので近接確認の対象にしない）。
   */
  fun registerPlaceWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    memo: String?,
    googlePlaceId: String?,
    googleName: String? = null,
    knownDetails: PlaceSearchResult? = null,
    mainPlaceId: Long? = null,
  )

  fun confirmNearbyLink()
  fun confirmNearbyNew()
  fun dismissNearbyPrompt()

  fun observePlace(placeId: Long): Flow<PlaceListItem?>
  fun visitsFor(placeId: Long): Flow<List<PlaceVisit>>
  fun savePlaceEdits(
    item: PlaceListItem,
    name: String,
    note: String,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    link: PlaceSearchResult? = null,
  )

  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult?
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult>
  suspend fun predictPlaces(query: String): List<PlacePrediction>
  suspend fun fetchPlaceResult(placeId: String): PlaceSearchResult?

  fun deletePlace(placeId: Long)
  fun consumeDeleteUndo()
  fun undoDelete(isMerge: Boolean = false)

  fun mergeIntoSameFacility(placeId: Long, placeName: String?, other: SameFacilityPlace)
  suspend fun mergeCandidates(item: PlaceListItem): List<PlaceWithDistance>
  fun keepAsSeparatePlace(placeId: Long)

  /** 何もしない（画面のテストや、場所の編集を使わない呼び出し元の既定）。 */
  object None : PlaceEditUi {
    override val nearbyRegisterPrompt: StateFlow<NearbyRegisterPrompt?> = MutableStateFlow(null)
    override val deleteUndo: StateFlow<PlaceDeleteUndo> = MutableStateFlow(PlaceDeleteUndo())

    override fun registerPlaceWithNearbyCheck(
      latitude: Double,
      longitude: Double,
      name: String?,
      wishlist: Boolean,
      priority: Priority,
      visited: Boolean,
      memo: String?,
      googlePlaceId: String?,
      googleName: String?,
      knownDetails: PlaceSearchResult?,
      mainPlaceId: Long?,
    ) = Unit

    override fun confirmNearbyLink() = Unit
    override fun confirmNearbyNew() = Unit
    override fun dismissNearbyPrompt() = Unit
    override fun observePlace(placeId: Long): Flow<PlaceListItem?> = emptyFlow()
    override fun visitsFor(placeId: Long): Flow<List<PlaceVisit>> = emptyFlow()
    override fun savePlaceEdits(
      item: PlaceListItem,
      name: String,
      note: String,
      wishlist: Boolean,
      priority: Priority,
      visited: Boolean,
      link: PlaceSearchResult?,
    ) = Unit

    override suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = null
    override suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = emptyList()
    override suspend fun predictPlaces(query: String): List<PlacePrediction> = emptyList()
    override suspend fun fetchPlaceResult(placeId: String): PlaceSearchResult? = null
    override fun deletePlace(placeId: Long) = Unit
    override fun consumeDeleteUndo() = Unit
    override fun undoDelete(isMerge: Boolean) = Unit
    override fun mergeIntoSameFacility(placeId: Long, placeName: String?, other: SameFacilityPlace) = Unit
    override suspend fun mergeCandidates(item: PlaceListItem): List<PlaceWithDistance> = emptyList()
    override fun keepAsSeparatePlace(placeId: Long) = Unit
  }
}

/** 場所の編集部の「まとめる」操作（同じ施設への答え・手でまとめる）を [PlaceEditUi] から作る。 */
internal fun PlaceEditUi.mergeActions(): PlaceMergeActions = PlaceMergeActions(
  onMerge = { item, other -> mergeIntoSameFacility(item.place.id, item.displayName, other) },
  onKeepSeparate = { keepAsSeparatePlace(it.place.id) },
  onFindMergeCandidates = this::mergeCandidates,
)

/** [placeEdit] の削除の取り消しを [snackbarHostState] に出す（[PlaceDeleteUndoEffect] のつなぎ込み）。 */
@Composable
internal fun PlaceDeleteUndoEffect(placeEdit: PlaceEditUi, snackbarHostState: SnackbarHostState) {
  val undo by placeEdit.deleteUndo.collectAsStateWithLifecycle()
  PlaceDeleteUndoEffect(undo, snackbarHostState, placeEdit::undoDelete, placeEdit::consumeDeleteUndo)
}

/**
 * 場所の操作の UI のつなぎ込み。記録画面・経路詳細・場所タブ（詳細・地図で追加）で使う。
 *
 * - [target] があれば場所シート（[PlaceActionSheet]）を出す。シートは**この関数を置いた位置**に重なるので、
 *   画面の Box の中で、重ね順を保ちたい位置に置く。
 * - 近くに既存の場所があるときの確認ダイアログを出す。
 * - [snackbarHostState] を渡すと、場所の削除（まとめる）の「取り消す」をそこに出す。削除の取り消しを
 *   別の画面に任せるとき（場所詳細は一覧に戻ってから出す）は渡さない。
 *
 * @param infoWindow シートと一緒に閉じるマーカーの吹き出し。
 * @param sheetState 地図の下パディングをシートに合わせたい画面は、自分で作った state を渡す。
 * @param onAddStop 既存の場所を「立ち寄りに追加」する（記録中だけ）。null なら出さない。
 * @param mainPlaceId 場所詳細の地図から登録するときの、その場所（→ [PlaceEditUi.registerPlaceWithNearbyCheck]）。
 * @param registerOptions 計画の詳細から登録するときの違い（足す日・行きたいの初期・登録のボタンの文言）。
 */
@Composable
internal fun PlaceEditHost(
  placeEdit: PlaceEditUi,
  target: PlaceSheetTarget?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  infoWindow: MapInfoWindowState? = null,
  snackbarHostState: SnackbarHostState? = null,
  sheetState: FloatingSheetState? = null,
  onOpenTrack: (trackId: Long) -> Unit = {},
  onAddStop: ((item: PlaceListItem) -> Unit)? = null,
  mainPlaceId: Long? = null,
  registerOptions: PlaceRegisterOptions? = null,
) {
  if (snackbarHostState != null) PlaceDeleteUndoEffect(placeEdit, snackbarHostState)

  target?.let { t ->
    infoWindow?.let { CloseInfoWindowWithSheet(it) }
    PlaceActionSheet(
      target = t,
      onDismiss = onDismiss,
      onFetchPoiDetails = placeEdit::fetchPoiDetails,
      onObservePlace = placeEdit::observePlace,
      onObserveVisits = placeEdit::visitsFor,
      // POI か空き地点か、近接確認が要るかの判断は ViewModel（PlaceEditUseCase）が持つ。
      // 検索で選んだ場所は施設情報を取得済みなので、それを渡して引き直させない。
      onRegisterNew = { lat, lng, name, wishlist, priority, visited, memo, googlePlaceId, googleName ->
        placeEdit.registerPlaceWithNearbyCheck(
          lat,
          lng,
          name,
          wishlist,
          priority,
          visited,
          memo,
          googlePlaceId,
          googleName,
          knownDetails = (t as? PlaceSheetTarget.NewSearchResult)?.result,
          mainPlaceId = mainPlaceId,
        )
      },
      onSaveExisting = placeEdit::savePlaceEdits,
      onDeletePlace = placeEdit::deletePlace,
      onOpenTrack = onOpenTrack,
      onFetchNearbyPois = placeEdit::nearbyPois,
      onSearchPredictions = placeEdit::predictPlaces,
      onFetchPrediction = placeEdit::fetchPlaceResult,
      onAddStop = onAddStop,
      mergeActions = placeEdit.mergeActions(),
      modifier = modifier,
      sheetState = sheetState ?: rememberFloatingSheetState(peekFraction = PLACE_SHEET_PEEK_FRACTION),
      registerOptions = registerOptions,
    )
  }

  // 近接確認: 近くの既存に紐付け／新規で登録。
  val nearbyRegisterPrompt by placeEdit.nearbyRegisterPrompt.collectAsStateWithLifecycle()
  nearbyRegisterPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      purpose = NearbyConfirmPurpose.REGISTER_PLACE,
      onLink = placeEdit::confirmNearbyLink,
      onCreateNew = placeEdit::confirmNearbyNew,
      onDismiss = placeEdit::dismissNearbyPrompt,
    )
  }
}
