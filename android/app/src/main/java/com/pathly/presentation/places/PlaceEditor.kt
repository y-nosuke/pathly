package com.pathly.presentation.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.Priority
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.presentation.common.NearbyCandidatePickerDialog
import com.pathly.presentation.common.SameFacilityDialog
import com.pathly.presentation.common.SheetContentHorizontalPadding
import com.pathly.presentation.common.SheetFormLayout
import com.pathly.ui.theme.PathlySpacing
import com.pathly.util.DateFormatters

/**
 * 場所をほかの場所にまとめる操作。同じ施設と分かった場所への答え（→ adr/0037）と、
 * 手でまとめる「ほかの場所とまとめる」（→ docs/specs/places.md）の両方で使う。
 * 同じ施設のときの「別の施設を選び直す」は編集部の中で「Google施設を選び直す」を開くので、ここには持たない。
 */
class PlaceMergeActions(
  /** [item] を相手 [other] にまとめる（[item] は消える）。 */
  val onMerge: (item: PlaceListItem, other: SameFacilityPlace) -> Unit,
  /** 施設情報なしで、別の場所として残す。 */
  val onKeepSeparate: (item: PlaceListItem) -> Unit,
  /** 手でまとめる相手の候補（[item] の近くのほかの場所、近い順）。 */
  val onFindMergeCandidates: suspend (item: PlaceListItem) -> List<PlaceWithDistance>,
)

/**
 * 場所の編集部のうち、本文の外（⋮ のメニュー・ダイアログ）からも触る状態。
 * 場所詳細は ⋮ を上のバーに置くので、本文と上のバーの両方から同じものを触れるよう、画面側で作って渡す。
 */
@Stable
class PlaceEditorState internal constructor(initiallyOpenLinkPicker: Boolean) {
  /** 「Googleで情報を取得」（施設を選ぶ）ダイアログの開閉。 */
  var linkPickerOpen by mutableStateOf(initiallyOpenLinkPicker)

  /** 名前を付ける・直すダイアログの開閉（見出しを押して開く）。 */
  var renameOpen by mutableStateOf(false)

  /** 「ほかの場所とまとめる」ダイアログの開閉。 */
  var mergePickerOpen by mutableStateOf(false)

  /** 同じ施設の確認ダイアログの開閉。 */
  var sameFacilityDialogOpen by mutableStateOf(false)

  /** 「Googleで情報を取得」で選んだ施設。即保存せず、ここに溜めて「保存」で確定する。 */
  var pendingLink by mutableStateOf<PlaceSearchResult?>(null)

  /**
   * 施設を選んで保存した直後か。保存で同じ施設の相手が見つかったら、その場でダイアログを出すため
   * （保存は ViewModel で非同期に走るので、結果は購読している場所の変化で知る）。
   */
  var awaitingLinkResult by mutableStateOf(false)
}

/** [PlaceEditorState] を場所ごとに作る（別の場所に切り替わったら作り直す）。 */
@Composable
internal fun rememberPlaceEditorState(placeId: Long, initiallyOpenLinkPicker: Boolean = false): PlaceEditorState = remember(placeId) { PlaceEditorState(initiallyOpenLinkPicker) }

/**
 * **登録済みの場所を編集する唯一の本体。**
 *
 * 入口は2つある（場所一覧から選ぶ／地図上のマーカーをタップする）が、出てくる中身と
 * できることはこれ1つに統一する。入口ごとに機能が違うと、どちらから開いたかを覚えていないと
 * 何ができるか分からなくなるため（以前は地図側に Google 紐付け・訪問履歴・削除が無かった）。
 *
 * [FloatingSheet][com.pathly.presentation.common.FloatingSheet] の content に置く。並びは段（→ docs/designs/ui-direction.md）に沿う:
 * 見出しの行（[showHeader]。名前と ⋮）→ 本文（確認待ち → 主役: 行きたい・優先度・訪問済み・メモ →
 * 脇役: 業種・住所・Google マップで開く → 訪問履歴）→ 下端に固定する操作の行（閉じる・立ち寄りに追加・保存）。
 * 自分で付ける名前は、経路の名前と同じく見出しを押して開くダイアログで付け、その場で保存する（空にすると Google の名前に戻る）。
 * しまうもの（Google 施設を選び直す・ほかの場所とまとめる・削除）は ⋮（[PlaceEditorMenuItems]）の中。
 * 地図・カメラ・戻る操作は画面側が持つ。
 *
 * @param showHeader 見出しの行（名前と ⋮）を出すか。場所詳細は上のバーに見出しと ⋮ があるので出さない。
 * @param hostDialogs ダイアログをここで出すか。上のバーの ⋮ から開く場所詳細は、本文が畳まれていても
 *   出せるよう画面側で [PlaceEditorDialogs] を置く。
 */
@Composable
internal fun ColumnScope.PlaceEditorBody(
  item: PlaceListItem,
  visits: List<PlaceVisit>,
  onSave: (name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean, link: PlaceSearchResult?) -> Unit,
  onDelete: () -> Unit,
  onOpenTrack: (trackId: Long) -> Unit,
  // 「Googleで情報を取得」用: 登録座標の近くの POI 候補を取得し、選んだ施設を紐付ける。
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult>,
  // 座標がずれて周辺に出ない施設用: 名前で検索するフォールバック。
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult?,
  // 同じ施設の確認待ちへの答え・手でまとめる。null なら確認待ちとまとめるを出さない。
  mergeActions: PlaceMergeActions?,
  state: PlaceEditorState = rememberPlaceEditorState(item.place.id),
  showHeader: Boolean = true,
  hostDialogs: Boolean = true,
  // 記録中に地図から開いたときだけ出る「立ち寄りに追加」。null なら出さない。
  onAddStop: (() -> Unit)? = null,
  // シートから開いたときの「閉じる」。null なら出さない。
  onClose: (() -> Unit)? = null,
) {
  val context = LocalContext.current

  val savedNote = item.note ?: ""
  val savedPriority = item.priority ?: Priority.MEDIUM
  // 名前以外の編集はローカルに溜め、「保存」でまとめて確定する（何が未保存かを一目で分かるように）。
  var note by remember(item.place.id) { mutableStateOf(savedNote) }
  var wishlist by remember(item.wishlistId) { mutableStateOf(item.isWishlisted) }
  var priority by remember(item.wishlistId) { mutableStateOf(savedPriority) }
  // 訪問済みは行きたいとは別の軸なので、行きたい行の有無ではなく場所そのものに紐付けて覚える。
  var visited by remember(item.place.id) { mutableStateOf(item.isManuallyVisited) }

  val hasChanges = note.trim() != savedNote.trim() ||
    wishlist != item.isWishlisted ||
    (wishlist && priority != savedPriority) ||
    (item.visitCount == 0 && visited != item.isManuallyVisited) ||
    state.pendingLink != null

  if (showHeader) {
    PlaceEditorHeader(
      title = item.displayName,
      onTitleClick = { state.renameOpen = true },
      titleClickLabel = renameLabel(item),
    ) { close ->
      PlaceEditorMenuItems(item = item, state = state, canMerge = mergeActions != null, onDelete = onDelete, close = close)
    }
  }

  SheetFormLayout(
    // 「保存」しか無い行（場所詳細）は、変更があるときだけ出す（押せない保存でシートの下を取らないように）。
    // 閉じる・立ち寄りに追加があるマーカーのシートは常に出す。
    showActions = hasChanges || onClose != null || onAddStop != null,
    actions = {
      onClose?.let { TextButton(onClick = it) { Text("閉じる") } }
      // 記録中のみ「立ち寄りに追加」（この訪問を既存 place にひも付ける）。
      onAddStop?.let { OutlinedButton(onClick = it) { Text("立ち寄りに追加") } }
      Button(
        onClick = {
          if (state.pendingLink != null) state.awaitingLinkResult = true
          onSave(item.place.name.orEmpty(), note, wishlist, priority, visited, state.pendingLink)
          // 保存したら紐付け予定は消化済み。残すとボタンが活性のまま・予定表示が残るため確実にクリアする。
          state.pendingLink = null
        },
        enabled = hasChanges,
      ) {
        Text("保存")
      }
    },
  ) {
    // 同じ施設の確認待ち（場所を取得・自動の名前付けで分かったもの → adr/0037）。出ているときだけの「やること」なので先頭に。
    val sameFacility = item.sameFacility
    if (sameFacility != null && mergeActions != null) {
      SameFacilityPendingCard(otherName = sameFacility.name, onOpen = { state.sameFacilityDialogOpen = true })
      Spacer(modifier = Modifier.height(PathlySpacing.md))
    }

    // 選んだ施設は保存で確定する（即保存しない）。未保存の紐付け予定をここに見せる。
    state.pendingLink?.let { link ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = PathlySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "紐付け予定: ${link.name ?: "（名称不明）"}（保存で確定）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { state.pendingLink = null }) { Text("取消") }
      }
    }

    PlaceFormBody(
      // 場所詳細（見出しの行を出さない）は上のバーの名前の下に業種があるので、シートでは繰り返さない。
      category = if (showHeader) item.place.category else null,
      address = item.place.googleAddress,
      onOpenInMaps = {
        openPlaceInGoogleMaps(context, item.place.googlePlaceId, item.place.latitude, item.place.longitude, item.displayName)
      },
      memo = note,
      onMemoChange = { note = it },
      wishlist = wishlist,
      onWishlistChange = { wishlist = it },
      priority = priority,
      onPriorityChange = { priority = it },
      wishlistHistory = wishlistHistoryText(item),
      // 立ち寄り記録がある場所は自動で訪問済み（切替不可・件数を表示）。無ければ手動トグル。
      visitedContent = if (item.visitCount > 0) {
        { VisitedByStopsChip(item.visitCount) }
      } else {
        { VisitedToggle(visited) { visited = it } }
      },
    )

    if (visits.isNotEmpty()) {
      Spacer(modifier = Modifier.height(PathlySpacing.lg))
      HorizontalDivider()
      Spacer(modifier = Modifier.height(PathlySpacing.md))
      Text(
        text = "この場所を含むお出掛け（${visits.size}件）",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(PathlySpacing.xs))
      visits.forEach { visit ->
        VisitRow(visit = visit, onClick = { onOpenTrack(visit.trackId) })
        HorizontalDivider()
      }
    }
  }

  if (hostDialogs) {
    PlaceEditorDialogs(
      item = item,
      state = state,
      onSave = onSave,
      mergeActions = mergeActions,
      onFetchNearbyPois = onFetchNearbyPois,
      onSearchPredictions = onSearchPredictions,
      onFetchPrediction = onFetchPrediction,
    )
  }
}

/** シートの見出しの行（場所の名前と ⋮）。地図のマーカーから開くシートで使う（場所詳細は上のバーにある）。 */
@Composable
private fun PlaceEditorHeader(
  title: String,
  onTitleClick: () -> Unit,
  titleClickLabel: String,
  menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
  var menuOpen by rememberSaveable { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = SheetContentHorizontalPadding, end = PathlySpacing.xs, bottom = PathlySpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 押せる範囲は 1 行の名前でも 48dp 以上にする（右の ⋮ と同じ高さ）。
    Box(
      modifier = Modifier
        .weight(1f)
        .heightIn(min = 48.dp)
        .clickable(onClickLabel = titleClickLabel, role = Role.Button, onClick = onTitleClick),
      contentAlignment = Alignment.CenterStart,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Box {
      IconButton(onClick = { menuOpen = true }) {
        Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = "その他の操作")
      }
      DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
        modifier = Modifier
          .widthIn(min = 220.dp)
          .testTag("placeEditorMenu"),
      ) {
        menu { menuOpen = false }
      }
    }
  }
}

/**
 * 場所の ⋮ の中身（しまうもの）: Google 施設を選び直す（無ければ取得）・ほかの場所とまとめる・この場所を削除。
 * 場所詳細は上のバーの ⋮、地図のマーカーから開くシートは見出しの行の ⋮ に入れる。
 */
@Composable
internal fun ColumnScope.PlaceEditorMenuItems(
  item: PlaceListItem,
  state: PlaceEditorState,
  canMerge: Boolean,
  onDelete: () -> Unit,
  close: () -> Unit,
) {
  // Google 施設情報の取得・紐付け。ID の無い場所（オフライン記録・手動登録）に住所・カテゴリ・正確な座標を補える。
  DropdownMenuItem(
    text = { Text(if (item.place.googlePlaceId == null) "Googleで情報を取得" else "Google施設を選び直す") },
    onClick = {
      close()
      state.linkPickerOpen = true
    },
  )
  // 手でまとめる（Google の施設で見分けられない重複 → docs/specs/places.md）。
  if (canMerge) {
    DropdownMenuItem(
      text = { Text("ほかの場所とまとめる") },
      onClick = {
        close()
        state.mergePickerOpen = true
      },
    )
  }
  HorizontalDivider()
  // 立ち寄り記録がある場所は削除不可（記録を残すため）。押せない理由をその下に添える。
  val canDelete = item.visitCount == 0
  DropdownMenuItem(
    text = {
      Column {
        Text(
          text = "この場所を削除",
          color = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        if (!canDelete) {
          Text(
            text = "立ち寄り記録があるため削除できません",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else if (item.planCount > 0) {
          // 消すと計画の行も消える（取り消しで戻る → adr/0050）。
          Text(
            text = "${item.planCount} 件の計画から外れます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    enabled = canDelete,
    onClick = {
      close()
      onDelete()
    },
  )
}

/** 場所の編集部のダイアログ（施設を選ぶ・ほかの場所とまとめる・同じ施設の確認）。開閉は [state] が持つ。 */
@Composable
internal fun PlaceEditorDialogs(
  item: PlaceListItem,
  state: PlaceEditorState,
  // 名前のダイアログの保存に使う（名前だけを変え、ほかは保存済みの値のまま渡す）。
  onSave: (name: String, note: String, wishlist: Boolean, priority: Priority, visited: Boolean, link: PlaceSearchResult?) -> Unit,
  mergeActions: PlaceMergeActions?,
  onFetchNearbyPois: suspend (lat: Double, lng: Double) -> List<PlaceSearchResult>,
  onSearchPredictions: suspend (query: String) -> List<PlacePrediction>,
  onFetchPrediction: suspend (placeId: String) -> PlaceSearchResult?,
) {
  // 施設を選んで保存したあと、同じ施設の相手が見つかったらその場で確認を出す。
  LaunchedEffect(item.sameFacility, item.place.googlePlaceId) {
    if (!state.awaitingLinkResult) return@LaunchedEffect
    if (item.sameFacility != null) {
      state.sameFacilityDialogOpen = true
      state.awaitingLinkResult = false
    } else if (item.place.googlePlaceId != null) {
      state.awaitingLinkResult = false
    }
  }

  val pendingSameFacility = item.sameFacility
  if (state.sameFacilityDialogOpen && pendingSameFacility != null && mergeActions != null) {
    SameFacilityDialog(
      placeName = item.displayName,
      otherName = pendingSameFacility.name,
      onMerge = {
        state.sameFacilityDialogOpen = false
        mergeActions.onMerge(item, pendingSameFacility)
      },
      onPickAnother = {
        state.sameFacilityDialogOpen = false
        state.linkPickerOpen = true
      },
      onKeepSeparate = {
        state.sameFacilityDialogOpen = false
        mergeActions.onKeepSeparate(item)
      },
      onDismiss = { state.sameFacilityDialogOpen = false },
    )
  }

  if (state.renameOpen) {
    RenamePlaceDialog(
      initialName = item.place.name.orEmpty(),
      // 自分の名前が無いときの表示名（Google の名前・無ければ住所や座標）。
      defaultName = item.place.copy(name = null).displayName,
      googleName = item.place.googleName,
      onDismiss = { state.renameOpen = false },
      onConfirm = { typed ->
        state.renameOpen = false
        // Google の名前と同じなら、自分の名前は付けなかった扱いにする（登録のときと同じ。Google の名前が変わっても追う）。
        val name = typed.takeUnless { it.trim() == item.place.googleName?.trim() }.orEmpty()
        // 保存は変わったものだけを書く（PlaceEditUseCase.saveEdits）ので、名前以外は保存済みの値を渡す。
        onSave(name, item.note.orEmpty(), item.isWishlisted, item.priority ?: Priority.MEDIUM, item.isManuallyVisited, null)
      },
    )
  }

  if (state.mergePickerOpen && mergeActions != null) {
    PlaceMergePickerDialog(
      item = item,
      onFindCandidates = mergeActions.onFindMergeCandidates,
      onMerge = { other ->
        state.mergePickerOpen = false
        mergeActions.onMerge(item, other)
      },
      onDismiss = { state.mergePickerOpen = false },
    )
  }

  if (state.linkPickerOpen) {
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
        // 即保存せず、「保存」で確定するため溜めるだけ。
        state.pendingLink = chosen
        state.linkPickerOpen = false
      },
      onDismiss = { state.linkPickerOpen = false },
      onSearchPredictions = onSearchPredictions,
      onFetchPrediction = onFetchPrediction,
    )
  }
}

/** 見出しを押したときの読み上げ（経路の見出しと同じ言い方）。 */
internal fun renameLabel(item: PlaceListItem): String = if (item.place.name.isNullOrBlank()) "名前を付ける" else "名前を編集"

/**
 * 場所の名前を付ける・直すダイアログ（経路の名前のダイアログと同じ形）。空で保存すると自分で付けた名前が消え、
 * Google の名前（無ければ住所など）に戻る。Google の名前があれば、それを元に書き換える導線を出す。
 * 欄は自分で付ける名前だけ。欄の名札（ラベル）に、自分の名前が無いときに出る名前（[defaultName]。ふつうは Google の
 * 名前）を出す。空のうちは欄の中に薄い字で、打ち始めると枠の上に移るので、自分の名前と Google の名前を見分けられる。
 */
@Composable
private fun RenamePlaceDialog(
  initialName: String,
  defaultName: String,
  googleName: String?,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("場所の名前") },
    text = {
      Column {
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          singleLine = true,
          label = { Text(defaultName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          // 欄の中の名札（Google の名前）は、打った字（自分の名前）と見分けられるよう薄くする。
          colors = OutlinedTextFieldDefaults.colors(unfocusedLabelColor = MaterialTheme.colorScheme.outline),
          supportingText = {
            Text(if (googleName != null) "空のときは Google の名前で表示します" else "空のときは住所などで表示します")
          },
          modifier = Modifier.testTag("placeNameField"),
        )
        // 施設名を少し変えたいだけのときに打ち直さずに済むよう、Google の名前から書き換えられるようにする。
        googleName?.takeIf { it.isNotBlank() && text.isEmpty() }?.let { candidate ->
          TextButton(onClick = { text = candidate }) { Text("Google の名前から書き換える") }
        }
      }
    },
    confirmButton = {
      // 名前が変わっていないうちは押せない（自分の名前が無いまま空で保存しても何も起きないため）。
      // 自分の名前を消して空にしたときは押せる（Google の名前に戻す）。
      TextButton(onClick = { onConfirm(text) }, enabled = text.trim() != initialName.trim()) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

/** 「◯◯と同じ施設らしい」の確認待ち。押すと選択肢のダイアログを開く。 */
@Composable
private fun SameFacilityPendingCard(otherName: String?, onOpen: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(modifier = Modifier.padding(PathlySpacing.md)) {
      Text(
        text = "確認待ち: 「${otherName ?: "名前の無い場所"}」と同じ施設のようです",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Text(
        text = "まとめるか、別の場所として残すかを選んでください。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) { Text("選ぶ") }
    }
  }
}

/**
 * 行きたいの切り替えの下に出す「行きたいで 3 回行った（最後は 2026/10/02(金)）」。0 回なら null（出さない）。
 */
internal fun wishlistHistoryText(item: PlaceListItem): String? {
  if (item.fulfilledWishCount <= 0) return null
  val last = item.lastFulfilledAt?.let { "（最後は ${DateFormatters.shortDate(it)}）" } ?: ""
  return "行きたいで ${item.fulfilledWishCount} 回行った$last"
}
