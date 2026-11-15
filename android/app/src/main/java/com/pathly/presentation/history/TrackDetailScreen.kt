package com.pathly.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import com.pathly.domain.model.WishlistFlag
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.SameFacilityDialog
import com.pathly.presentation.common.SheetDetent
import com.pathly.presentation.common.heightOf
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.common.rememberMapInfoWindowState
import com.pathly.presentation.places.PlaceEditHost
import com.pathly.presentation.places.PlaceEditUi
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.ManualStopHost
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.presentation.stops.ManualStopUi
import com.pathly.presentation.stops.StopDurationSheet
import com.pathly.presentation.stops.StopReassignHost
import kotlinx.coroutines.launch

private val tuningSheetPeekHeight = 360.dp

/**
 * 経路詳細。地図を全面に出し、立ち寄りの一覧・場所シート・各モードのパネルを地図の上に重ねる。
 *
 * 引数は画面のまとまりごとに渡す（立ち寄りの操作 [stopActions]・再解析 [reanalysis]・同じ施設の確認待ち
 * [sameFacility]・地図の登録済みの場所 [mapPlaces]・場所の編集 [placeEdit]・立ち寄りの手での追加 [manualStop]）。
 *
 * @param currentStop 記録中に開いたときの「立ち寄り中（ライブ）」。地図にだけ出す（一覧・保存には出さない）。
 * @param onRenameTrack 経路そのものの名前（空なら未命名に戻す）。一覧のカードのメニューと同じダイアログを使う。
 * @param onOpenTrack 場所シートの訪問履歴から、その訪問のお出掛け（別の経路詳細）を開く。
 * @param mapContent 地図スロット。null（既定）は実マップ（GoogleMap）を描画する。テストは空スロット（{}）を渡し、
 *   GMS 依存の地図描画を避けてシート・オーバーレイだけを検証する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
  track: GpsTrack,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  stops: List<Stop> = emptyList(),
  currentStop: Stop? = null,
  unresolvedCount: Int = 0,
  message: String? = null,
  onMessageShown: () -> Unit = {},
  onRenameTrack: (name: String) -> Unit = {},
  stopActions: TrackStopActions = TrackStopActions(),
  reanalysis: StopReanalysis = StopReanalysis(),
  sameFacility: SameFacilityReview = SameFacilityReview(),
  mapPlaces: MapRegisteredPlaces = MapRegisteredPlaces(),
  placeEdit: PlaceEditUi = PlaceEditUi.None,
  manualStop: ManualStopUi = ManualStopUi.None,
  onOpenTrack: (trackId: Long) -> Unit = {},
  mapContent: (@Composable () -> Unit)? = null,
) {
  val state = rememberTrackDetailScreenState()
  // 場所シートの開き具合。地図の下パディングを合わせて、選んだ地点が裏に来ないようにする。
  val placeSheetState = rememberFloatingSheetState()
  // 期間編集シートの開き具合。本体シートを畳んでいても開いた状態で出したいので別に持つ。
  val durationSheetState = rememberFloatingSheetState(peekFraction = 0.5f)
  // --- 自前フローティングシートの開閉（地図の上に重ねる。標準ボトムシートは使わない）---
  // 3段階: 隠す（全画面地図）/ ハーフ（地図＋一覧を同時に）/ フル（一覧を大きく、地図は上に少し覗く）。
  // つまみ／上部のドラッグでシートだけを開閉し、一覧（LazyColumn）は独立してスクロールする。
  val sheetState = rememberFloatingSheetState(peekFraction = 0.45f, fullFraction = 0.92f)
  // マーカーの吹き出し。シートと同時に出るので、シート側から一緒に閉じられるよう画面で持つ。
  val infoWindow = rememberMapInfoWindowState()
  // 画面高は LocalWindowInfo から取る（Configuration.screenHeightDp は非推奨で、
  // 分割画面などコンテナ実サイズと食い違う）。containerSize は px なので dp に直す。
  val screenHeightDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

  // 削除などで stops が変わったら、消えた ID を選択から外す。空になったら選択モードを抜ける。
  LaunchedEffect(stops) {
    val ids = stops.mapTo(HashSet()) { it.id }
    state.selectedStopIds.retainAll(ids)
    if (state.selectedStopIds.isEmpty()) state.selectionMode = false
  }
  // 選択モード中のシステムバックは、画面を抜けるのではなく選択を解除する。
  BackHandler(enabled = state.selectionMode) { state.clearSelection() }

  // 再解析の候補選択モード（候補があるあいだ）。地図にオレンジのピンで位置を見せ、
  // 地図の上に**高さ固定のオーバーレイ**で候補を出す（シートは畳んで地図を常に見せる）。
  val reanalyzeCandidates = reanalysis.candidates
  val candidateMode = reanalyzeCandidates?.isNotEmpty() == true
  val selectedCandidates = remember(reanalyzeCandidates) {
    mutableStateListOf<Int>().apply { reanalyzeCandidates?.let { addAll(it.indices) } }
  }
  // 候補モード中のシステムバックは、画面を抜けるのではなく候補選択をやめる。
  BackHandler(enabled = candidateMode) { reanalysis.onDismiss() }
  // 候補オーバーレイの高さ（画面の約42%）。地図はこの分だけ下を空けてピンを隠さない。
  val candidateOverlayHeight = screenHeightDp * 0.42f

  // 期間の編集中のシステムバックは、画面を抜けるのではなく編集をやめる。
  BackHandler(enabled = state.durationEditMode) { state.durationEditStop = null }
  // バックは2段階で戻す: 地点確定後は地点選択（マップ）に戻すだけ、地点選択中は追加モードを抜ける。
  BackHandler(enabled = state.manualMode) {
    if (state.manualPick != null) state.manualPick = null else state.exitManual()
  }

  // 立ち寄りの削除・統合は確認ダイアログを出さず即時実行し、スナックバーの「取り消す」で元に戻せる。
  // 場所そのものの削除（場所シート）も同じスナックバーで取り消せる（PlaceEditHost が出す）。
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showStopUndo: (String) -> Unit = { text ->
    scope.launch {
      val result = snackbarHostState.showSnackbar(message = text, actionLabel = "取り消す", duration = SnackbarDuration.Short)
      if (result == SnackbarResult.ActionPerformed) stopActions.onUndo()
    }
  }
  val deleteWithUndo: (List<Long>) -> Unit = { ids ->
    if (ids.isNotEmpty()) {
      stopActions.onDelete(ids)
      showStopUndo("${ids.size}件を削除しました")
    }
  }
  val mergeWithUndo: (List<Long>) -> Unit = { ids ->
    if (ids.size >= 2) {
      stopActions.onMerge(ids)
      showStopUndo("${ids.size}件をまとめました")
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // ---- 地図（全面／edge-to-edge。シートは地図の上に重ねる）----
    if (track.points.isEmpty()) {
      NoGpsDataMessage()
    } else if (mapContent != null) {
      mapContent()
    } else {
      TrackDetailMapLayer(
        track = track,
        stops = stops,
        currentStop = currentStop,
        candidates = if (candidateMode) reanalyzeCandidates.orEmpty() else emptyList(),
        mapPlaces = mapPlaces,
        state = state,
        infoWindow = infoWindow,
        bottomPadding = mapBottomPadding(state, candidateMode, candidateOverlayHeight, sheetState, placeSheetState, durationSheetState),
        // 通常モードの立ち寄りピン: 一覧の該当行を強調＆スクロール＋「選び直し」を出す（記録画面と統一）。
        onStopSelected = { stop ->
          state.focusOnStop(stop)
          if (sheetState.detent == SheetDetent.HIDDEN) sheetState.settleTo(SheetDetent.PEEK)
          state.reassignTarget = stop
        },
      )
    }

    TrackDetailTopButtons(
      hasPoints = track.points.isNotEmpty(),
      mapPlaces = mapPlaces,
      tuningMode = state.tuningMode,
      onBack = onBackClick,
      onFitTrack = state::fitTrack,
      onToggleTuning = { state.tuningMode = !state.tuningMode },
      onOpenDebugInfo = { state.debugInfoOpen = true },
    )

    // ---- 通常モードのフローティングシート（つまみで開閉・一覧は独立スクロール）----
    if (!state.tuningMode && !candidateMode && !state.manualMode && !state.durationEditMode && state.placeSheetTarget == null) {
      StopListSheet(
        track = track,
        stops = stops,
        unresolvedCount = unresolvedCount,
        state = state,
        sheetState = sheetState,
        stopActions = stopActions,
        sameFacility = sameFacility,
        reanalysis = reanalysis,
        mapPlaces = mapPlaces,
        onDelete = deleteWithUndo,
        onMerge = mergeWithUndo,
      )
    }

    // ---- 補正の調整（デバッグ）: 地図の上に固定高さのパネルで出す ----
    if (state.tuningMode) {
      TuningPanel(track, state)
    }

    // 再解析の候補は、地図を常に見せたまま選べるよう下部の固定オーバーレイに出す。
    if (candidateMode) {
      CandidateOverlay(
        candidates = reanalyzeCandidates.orEmpty(),
        selectedIndices = selectedCandidates,
        height = candidateOverlayHeight,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding(),
        onToggle = { index ->
          if (index in selectedCandidates) selectedCandidates.remove(index) else selectedCandidates.add(index)
        },
        onFocus = { candidate -> state.focusOn(LatLng(candidate.detected.latitude, candidate.detected.longitude)) },
        onConfirm = {
          val picked = selectedCandidates.sorted().mapNotNull { reanalyzeCandidates.getOrNull(it) }
          reanalysis.onAdd(picked)
        },
        onCancel = reanalysis.onDismiss,
      )
    }

    // 手動追加: 地点を指す前は案内、指したあとは入力＋区間調整のシートを出す。
    ManualAddPanels(track, state, placeEdit, manualStop, infoWindow)

    // 期間の編集: 地図を見ながら滞在区間を調整する（手動追加と同じレンジUI）。
    state.durationEditStop?.let { stop ->
      val points = track.smoothedPoints
      CloseInfoWindowWithSheet(infoWindow)
      StopDurationSheet(
        stop = stop,
        points = points,
        onConfirm = { arrivalIdx, departureIdx ->
          stopActions.onUpdateDuration(stop.id, points[arrivalIdx].timestamp, points[departureIdx].timestamp)
          state.durationEditStop = null
        },
        onCancel = { state.durationEditStop = null },
        onRangeChange = { start, end -> state.durationRange = start to end },
        sheetState = durationSheetState,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

    // スナックバー（最前面）。シート/オーバーレイの上に出す。
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding(),
    )

    // 通常モードの地図タップで開く統一の「場所シート」。立ち寄り追加は手動追加モード（到着/出発の調整あり）に任せるため出さない。
    PlaceEditHost(
      placeEdit = placeEdit,
      target = state.placeSheetTarget,
      onDismiss = { state.placeSheetTarget = null },
      modifier = Modifier.align(Alignment.BottomCenter),
      infoWindow = infoWindow,
      snackbarHostState = snackbarHostState,
      sheetState = placeSheetState,
      onOpenTrack = onOpenTrack,
    )
  }

  TrackDetailDialogs(
    track = track,
    stops = stops,
    state = state,
    stopActions = stopActions,
    reanalysis = reanalysis,
    sameFacility = sameFacility,
    placeEdit = placeEdit,
    manualStop = manualStop,
    onRenameTrack = onRenameTrack,
    infoWindow = infoWindow,
  )

  // 「登録しました／登録済みです」などの一時メッセージは、削除の取り消しと同じ下部スナックバーで統一する。
  LaunchedEffect(message) {
    val text = message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
    onMessageShown()
  }
}

/**
 * フォーカスがピンをシート/オーバーレイの裏に隠さないよう空ける、地図の下端の高さ。
 * 通常モードは現在の段（ハーフ/フル）の高さ分。ドラッグ中の逐次値ではなく確定値を使う。
 */
@Composable
private fun mapBottomPadding(
  state: TrackDetailScreenState,
  candidateMode: Boolean,
  candidateOverlayHeight: Dp,
  sheetState: FloatingSheetState,
  placeSheetState: FloatingSheetState,
  durationSheetState: FloatingSheetState,
): Dp = when {
  candidateMode -> candidateOverlayHeight

  state.manualMode && state.manualPick != null -> sheetState.heightOf(SheetDetent.PEEK)

  state.manualMode -> 96.dp

  state.durationEditMode -> durationSheetState.heightOf(durationSheetState.detent)

  state.tuningMode -> tuningSheetPeekHeight

  // 場所シートを開いている間は立ち寄り一覧を引っ込めるので、そちらの高さで空ける。
  state.placeSheetTarget != null -> placeSheetState.heightOf(placeSheetState.detent)

  else -> sheetState.heightOf(sheetState.detent)
}

/** 立ち寄りの一覧のシート（通常モード）。隠しているときは、下部中央に復帰ボタンが出る。 */
@Composable
private fun BoxScope.StopListSheet(
  track: GpsTrack,
  stops: List<Stop>,
  unresolvedCount: Int,
  state: TrackDetailScreenState,
  sheetState: FloatingSheetState,
  stopActions: TrackStopActions,
  sameFacility: SameFacilityReview,
  reanalysis: StopReanalysis,
  mapPlaces: MapRegisteredPlaces,
  onDelete: (List<Long>) -> Unit,
  onMerge: (List<Long>) -> Unit,
) {
  FloatingSheet(
    state = sheetState,
    modifier = Modifier.align(Alignment.BottomCenter),
    restoreLabel = "▲ 詳細を表示",
  ) {
    TrackDetailSheet(
      track = track,
      stops = stops,
      unresolvedCount = unresolvedCount,
      selectionMode = state.selectionMode,
      selectedStopIds = state.selectedStopIds,
      highlightedStopId = state.highlightedStopId,
      // 行きたいの状態は登録済みの場所の一覧（全場所・DB の変化で流れ直す）から引く。
      wishlistFlags = remember(mapPlaces.places) {
        mapPlaces.places.associate { it.placeId to WishlistFlag.of(it.isWishlisted, it.fulfilledWishCount) }
      },
      onToggleWishlist = { stopActions.onToggleWishlist(it.place.id) },
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      onFocusStop = state::focusOnStop,
      onEditStop = { state.editingStop = it },
      onEditStopNote = { state.editingNoteStop = it },
      // 期間の編集・場所の選び直しは、対象を地図に出してから始める（地図を見ながら直せるように）。
      onEditStopDuration = {
        state.focusOnStop(it)
        state.durationEditStop = it
      },
      canEditDuration = track.smoothedPoints.size >= 2,
      onReassignStop = {
        state.focusOnStop(it)
        state.reassignTarget = it
      },
      onDeleteStop = { onDelete(listOf(it.id)) },
      onResolveNames = stopActions.onResolveNames,
      sameFacilityPendingCount = sameFacility.pending.size,
      onOpenSameFacility = sameFacility.onOpen,
      onReanalyze = reanalysis.onStart,
      onManualAdd = state::startManual,
      onRenameTrack = { state.renamingTrack = true },
      onEnterSelection = { stop ->
        state.selectionMode = true
        if (stop.id !in state.selectedStopIds) state.selectedStopIds.add(stop.id)
      },
      onToggleSelect = { stop ->
        if (stop.id in state.selectedStopIds) state.selectedStopIds.remove(stop.id) else state.selectedStopIds.add(stop.id)
      },
      onSelectAll = {
        val allSelected = state.selectedStopIds.size == stops.size
        state.selectedStopIds.clear()
        if (!allSelected) state.selectedStopIds.addAll(stops.map { it.id })
      },
      onCancelSelection = state::clearSelection,
      onDeleteSelected = {
        onDelete(state.selectedStopIds.toList())
        state.clearSelection()
      },
      onMergeSelected = {
        onMerge(state.selectedStopIds.toList())
        state.clearSelection()
      },
    )
  }
}

/** 補正の調整（デバッグ）のパネル。地図の上に固定高さで出す。 */
@Composable
private fun BoxScope.TuningPanel(track: GpsTrack, state: TrackDetailScreenState) {
  Surface(
    modifier = Modifier
      .align(Alignment.BottomCenter)
      .fillMaxWidth()
      .height(tuningSheetPeekHeight),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 12.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState()),
    ) {
      TuningSheet(
        track = track,
        params = state.tuningParams,
        onParamsChange = { state.tuningParams = it },
      )
    }
  }
}

/**
 * 手動追加: 地点を指す前は案内（名前での検索もできる）、指したあとは入力＋区間調整のシートを出す。
 * 近くに既存の場所があるときの確認は、追加モードを抜けたあとに出るので [ManualStopHost] は常に置く。
 */
@Composable
private fun BoxScope.ManualAddPanels(
  track: GpsTrack,
  state: TrackDetailScreenState,
  placeEdit: PlaceEditUi,
  manualStop: ManualStopUi,
  infoWindow: MapInfoWindowState,
) {
  if (state.manualMode && state.manualPick == null) {
    ManualPickPrompt(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding(),
      onCancel = state::exitManual,
      onSearchPredictions = placeEdit::predictPlaces,
      onFetchPrediction = placeEdit::fetchPlaceResult,
      // 名前で選んだ施設は「その施設をタップした」のと同じ扱いにする（座標も施設のもの）。
      // 地図もそこへ寄せて、滞在区間をハイライトで確かめられるようにする。
      onPicked = { result ->
        state.pickManual(
          ManualStopTarget(
            latitude = result.latitude,
            longitude = result.longitude,
            origin = ManualStopOrigin.Poi(name = result.name, googlePlaceId = result.googlePlaceId),
          ),
        )
      },
    )
  }
  ManualStopHost(
    manualStop = manualStop,
    placeEdit = placeEdit,
    target = if (state.manualMode) state.manualPick else null,
    points = track.smoothedPoints,
    onAdded = state::exitManual,
    onCancel = { state.manualPick = null },
    onRangeChange = { start, end -> state.manualRange = start to end },
    modifier = Modifier.align(Alignment.BottomCenter),
    infoWindow = infoWindow,
  )
}

/** 経路詳細のダイアログ（同じ施設の確認待ち・名前・メモ・場所の選び直し・再解析の結果・GPS 詳細）。 */
@Composable
private fun TrackDetailDialogs(
  track: GpsTrack,
  stops: List<Stop>,
  state: TrackDetailScreenState,
  stopActions: TrackStopActions,
  reanalysis: StopReanalysis,
  sameFacility: SameFacilityReview,
  placeEdit: PlaceEditUi,
  manualStop: ManualStopUi,
  onRenameTrack: (String) -> Unit,
  infoWindow: MapInfoWindowState,
) {
  // 同じ施設の確認待ちを 1 件ずつ聞く。答えると一覧から外れ、次の 1 件が出る。
  // 名前は一覧の行と同じ表示名で呼ぶ（名前も施設も無い場所は座標）。
  val firstPending = sameFacility.pending.firstOrNull()?.let { pending ->
    pending.copy(placeName = stops.firstOrNull { it.place.id == pending.placeId }?.place?.displayName ?: pending.placeName)
  }
  if (sameFacility.dialogOpen && firstPending != null) {
    SameFacilityDialog(
      placeName = firstPending.placeName,
      otherName = firstPending.other.name,
      remaining = sameFacility.pending.size - 1,
      // まとめたら閉じる。開いたままだとスナックバーの「取り消す」が押せない。残りはチップから続けて聞く。
      onMerge = {
        sameFacility.onMerge(firstPending)
        sameFacility.onDismiss()
      },
      // 選び直しは、その場所の編集部の「Google施設を選び直す」を開いて行う（場所詳細と同じ）。
      onPickAnother = {
        sameFacility.onDismiss()
        state.placeSheetTarget = PlaceSheetTarget.Existing(firstPending.placeId, openLinkPicker = true)
      },
      onKeepSeparate = { placeEdit.keepAsSeparatePlace(firstPending.placeId) },
      onDismiss = sameFacility.onDismiss,
    )
  }

  if (state.renamingTrack) {
    RenameTrackDialog(
      initialName = track.name.orEmpty(),
      onDismiss = { state.renamingTrack = false },
      onConfirm = { name ->
        onRenameTrack(name)
        state.renamingTrack = false
      },
    )
  }

  state.editingStop?.let { stop ->
    PlaceNameDialog(
      stop = stop,
      onDismiss = { state.editingStop = null },
      onConfirm = { name ->
        stopActions.onEditPlaceName(stop.place.id, name)
        state.editingStop = null
      },
    )
  }

  state.editingNoteStop?.let { stop ->
    StopNoteDialog(
      stop = stop,
      onDismiss = { state.editingNoteStop = null },
      onConfirm = { note ->
        stopActions.onEditNote(stop.id, note)
        state.editingNoteStop = null
      },
    )
  }

  StopReassignHost(
    stop = state.reassignTarget,
    manualStop = manualStop,
    placeEdit = placeEdit,
    onDismiss = { state.reassignTarget = null },
    infoWindow = infoWindow,
  )

  // 候補ゼロのときだけダイアログで知らせる（候補があればシート＋地図で選ばせる）。
  if (reanalysis.candidates?.isEmpty() == true) {
    AlertDialog(
      onDismissRequest = reanalysis.onDismiss,
      title = { Text("再解析") },
      text = { Text("一覧に無い立ち寄りは見つかりませんでした。") },
      confirmButton = { TextButton(onClick = reanalysis.onDismiss) { Text("閉じる") } },
    )
  }

  if (state.debugInfoOpen) {
    GpsDebugDialog(points = track.points, onDismiss = { state.debugInfoOpen = false })
  }
}
