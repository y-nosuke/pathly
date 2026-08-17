package com.pathly.presentation.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.presentation.common.NearbyCandidatePickerDialog

/**
 * **登録済みの場所を編集する唯一の本体。**
 *
 * 入口は2つある（場所一覧から選ぶ／地図上のマーカーをタップする）が、出てくる中身と
 * できることはこれ1つに統一する。入口ごとに機能が違うと、どちらから開いたかを覚えていないと
 * 何ができるか分からなくなるため（以前は地図側に Google 紐付け・訪問履歴・削除が無かった）。
 *
 * 並びは 名前などの共通フォーム（[PlaceFormBody]）→ Google 施設の紐付け → 保存 →
 * 立ち寄りに追加（記録中のみ）→ 訪問履歴 → 削除。
 * 地図・カメラ・戻る操作は画面側が持つ。
 */
@Composable
internal fun PlaceEditorBody(
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
  modifier: Modifier = Modifier,
  // 記録中に地図から開いたときだけ出る「立ち寄りに追加」。null なら出さない。
  onAddStop: (() -> Unit)? = null,
  // シートから開いたときの「閉じる」など、保存ボタンの並びに足したいもの。
  trailingActions: (@Composable ColumnScope.() -> Unit)? = null,
) {
  val context = LocalContext.current
  // 「Googleで情報を取得」ダイアログの開閉。
  var linkDialogOpen by remember { mutableStateOf(false) }

  val savedName = item.place.name ?: ""
  val savedNote = item.note ?: ""
  val savedPriority = item.priority ?: Priority.MEDIUM
  // 編集はすべてローカルに溜め、「保存」でまとめて確定する（何が未保存かを一目で分かるように）。
  var name by remember(item.place.id) { mutableStateOf(savedName) }
  var note by remember(item.place.id) { mutableStateOf(savedNote) }
  var wishlist by remember(item.wishlistId) { mutableStateOf(item.isWishlisted) }
  var priority by remember(item.wishlistId) { mutableStateOf(savedPriority) }
  // 訪問済みは行きたいとは別の軸なので、行きたい行の有無ではなく場所そのものに紐付けて覚える。
  var visited by remember(item.place.id) { mutableStateOf(item.isManuallyVisited) }
  // 「Googleで情報を取得」で選んだ施設は即保存せず、ここに溜めて「保存」で確定する。
  var pendingLink by remember(item.place.id) { mutableStateOf<PlaceSearchResult?>(null) }

  val hasChanges = name.trim() != savedName.trim() ||
    note.trim() != savedNote.trim() ||
    wishlist != item.isWishlisted ||
    (wishlist && priority != savedPriority) ||
    (item.visitCount == 0 && visited != item.isManuallyVisited) ||
    pendingLink != null

  Column(modifier = modifier) {
    PlaceFormBody(
      name = name,
      onNameChange = { name = it },
      nameLabel = "自分で付ける名前（任意）",
      heading = item.displayName,
      // 書き換えの元にできるのは Google 名だけ（住所・座標のフォールバックは元にしない）。
      namePrefill = item.place.googleName,
      category = item.place.category,
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
        { VisitedToggle(visited) { visited = it } }
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

    trailingActions?.invoke(this)

    // 記録中のみ「立ち寄りに追加」（この訪問を既存 place にひも付ける）。
    onAddStop?.let { add ->
      OutlinedButton(
        onClick = add,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
      ) {
        Text("立ち寄りに追加")
      }
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
    // 立ち寄り記録がある場所は削除不可（記録を残すため）。
    val canDelete = item.visitCount == 0
    val deleteColor = if (canDelete) {
      MaterialTheme.colorScheme.error
    } else {
      MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    OutlinedButton(
      onClick = onDelete,
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
        // 即保存せず、「保存」で確定するため溜めるだけ。
        pendingLink = chosen
        linkDialogOpen = false
      },
      onDismiss = { linkDialogOpen = false },
      onSearchPredictions = onSearchPredictions,
      onFetchPrediction = onFetchPrediction,
    )
  }
}
