package com.pathly.presentation.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathly.R
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.MarkerStopViolet
import com.pathly.util.DateFormatters
import kotlin.math.roundToInt

// 地図の上に重ねるフローティングシートと、その中身（サマリ・立ち寄り一覧・選択バー）。

/**
 * 地図の上に重ねるフローティングシート。角丸＋影で"浮いている"見た目にし、上部のつまみを
 * 上下ドラッグして高さ（段）を変える。中身（[content]）の一覧は独立してスクロールする。
 * 高さは [heightPx]（px を返すラムダ）で受け取り、**このコンポーザブル内で読む**ことで、
 * ドラッグ/アニメの再コンポーズを地図側に波及させない（地図のカクつき防止）。
 */
@Composable
internal fun FloatingStopSheet(
  heightPx: () -> Float,
  onDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(with(density) { heightPx().toDp() }),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 12.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(),
    ) {
      SheetDragHandle(onDrag = onDrag, onDragEnd = onDragEnd)
      content()
    }
  }
}

/** シート上部のつまみ。縦ドラッグでシート高さを変える（一覧のスクロールとは独立）。 */
@Composable
internal fun SheetDragHandle(
  onDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .pointerInput(Unit) {
        detectVerticalDragGestures(
          onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
          onDragEnd = onDragEnd,
          onDragCancel = onDragEnd,
        )
      }
      .padding(vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .width(40.dp)
        .height(4.dp)
        .background(
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          RoundedCornerShape(2.dp),
        ),
    )
  }
}

@Composable
internal fun TrackDetailSheet(
  track: GpsTrack,
  stops: List<Stop>,
  unresolvedCount: Int,
  selectionMode: Boolean,
  selectedStopIds: List<Long>,
  highlightedStopId: Long?,
  onFocusStop: (Stop) -> Unit,
  onEditStop: (Stop) -> Unit,
  onEditStopNote: (Stop) -> Unit,
  onEditStopDuration: (Stop) -> Unit,
  onReassignStop: (Stop) -> Unit,
  onDeleteStop: (Stop) -> Unit,
  onResolveNames: () -> Unit,
  onReanalyze: () -> Unit,
  onManualAdd: () -> Unit,
  onEnterSelection: (Stop) -> Unit,
  onToggleSelect: (Stop) -> Unit,
  onSelectAll: () -> Unit,
  onCancelSelection: () -> Unit,
  onDeleteSelected: () -> Unit,
  onMergeSelected: () -> Unit,
  modifier: Modifier = Modifier,
  // 期間の編集は軌跡点のインデックスで調整するので、点が2つ以上ある経路でだけ出す。
  canEditDuration: Boolean = true,
) {
  val listState = rememberLazyListState()
  // 地図のピン／行タップで選ばれた立ち寄りを、一覧の見える位置へ寄せる（地図↔一覧の連動）。
  LaunchedEffect(highlightedStopId, stops) {
    val id = highlightedStopId ?: return@LaunchedEffect
    val index = stops.indexOfFirst { it.id == id }
    if (index >= 0) listState.animateScrollToItem(index)
  }

  Column(modifier = modifier.fillMaxWidth()) {
    // 固定ヘッダー（スクロールしても消えない）。選択中は選択バーへ差し替える。
    if (selectionMode) {
      // まとめられるのは同じ場所への訪問が2件以上のときだけ（違う場所が混ざると、
      // どれが正か決められない → adr/0024）。
      val selected = stops.filter { it.id in selectedStopIds }
      SelectionBar(
        selectedCount = selectedStopIds.size,
        totalCount = stops.size,
        canMerge = selected.size >= 2 && selected.distinctBy { it.place.id }.size == 1,
        onSelectAll = onSelectAll,
        onDelete = onDeleteSelected,
        onMerge = onMergeSelected,
        onCancel = onCancelSelection,
      )
    } else {
      TrackSummaryHeader(
        track = track,
        stops = stops,
        unresolvedCount = unresolvedCount,
        onResolveNames = onResolveNames,
        onReanalyze = onReanalyze,
        onManualAdd = onManualAdd,
      )
    }
    // 立ち寄り一覧（ここだけスクロール）。シートの残り領域を埋め、独立してスクロールする。
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
      contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
      itemsIndexed(stops, key = { _, stop -> stop.id }) { index, stop ->
        StopRow(
          stop = stop,
          number = index + 1,
          highlighted = stop.id == highlightedStopId,
          selectionMode = selectionMode,
          selected = stop.id in selectedStopIds,
          onFocus = { onFocusStop(stop) },
          onEdit = { onEditStop(stop) },
          onEditNote = { onEditStopNote(stop) },
          onEditDuration = { onEditStopDuration(stop) },
          canEditDuration = canEditDuration,
          onReassign = { onReassignStop(stop) },
          onDelete = { onDeleteStop(stop) },
          onToggleSelect = { onToggleSelect(stop) },
          onEnterSelection = { onEnterSelection(stop) },
        )
      }
    }
  }
}

/**
 * 経路の固定ヘッダー。スクロールしても消えないよう一覧の外に置く。
 * 大きなタイルはやめ、日付＋1行スタッツ（距離・地点数・時間・立ち寄り件数）＋操作ボタンにまとめる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrackSummaryHeader(
  track: GpsTrack,
  stops: List<Stop>,
  unresolvedCount: Int,
  onResolveNames: () -> Unit,
  onReanalyze: () -> Unit,
  onManualAdd: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(top = 8.dp, bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = DateFormatters.date(track.startTime),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      if (track.isActive) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "● 記録中",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          )
        }
      }
    }

    // 1行スタッツ: 距離 ・ 地点数 ・ 時間（記録中は「記録中」）・ 立ち寄り件数。
    val distanceKm = (track.totalDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
    val endTime = track.endTime
    val timePart = if (endTime != null) {
      val durationMinutes = ((endTime.time - track.startTime.time) / 1000 / 60).toInt()
      val hours = durationMinutes / 60
      val minutes = durationMinutes % 60
      if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
    } else {
      "記録中"
    }
    val stats = buildList {
      add("${distanceKm}km")
      add("${track.points.size}点")
      add(timePart)
      if (stops.isNotEmpty()) add("立ち寄り${stops.size}件")
    }.joinToString("　・　")
    Text(
      text = stats,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 欠落を含む経路は、距離のうちどれだけが「取れなかった区間の直線」かを出す。
    // 総距離には含めるが（補完ぶんは過大にならないので足す方が実際に近い）、実測ではないことは見せる。
    // 同じ場所で再開したときは補完ぶんが 0.0km になるので、箇所数を主語にして「0.0km」を見せない。
    if (track.hasGap) {
      val bridgedKm = (track.bridgedDistanceMeters / 1000.0 * 100).roundToInt() / 100.0
      Text(
        text = if (bridgedKm > 0.0) {
          "記録が途切れた区間が${track.gapCount}箇所（距離のうち ${bridgedKm}km は直線で補完・地図では点線）"
        } else {
          "記録が途切れた区間が${track.gapCount}箇所（ほぼ同じ場所で再開・地図では点線）"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // 再解析: 一覧に無い立ち寄りを検出して、選択式で追加する（非破壊）。記録中も表示する
      // （誤って消した立ち寄りの復旧用）。候補は境界以前＝「確定済みの過去」だけに絞られ、
      // 滞在中・末尾はライブ検出に任せる（重複防止は PlaceRepositoryImpl.detectMissingStops 側）。
      if (track.points.size >= 2) {
        ActionChip(
          text = "再解析",
          container = MaterialTheme.colorScheme.tertiaryContainer,
          content = MaterialTheme.colorScheme.onTertiaryContainer,
          onClick = onReanalyze,
        )
      }
      // 立ち寄りを追加: 検出に頼らず、地図で指した地点を立ち寄りとして足す（完全手動）。到着/出発は
      // その時点の軌跡から仮置きする。記録中も使える（末尾が伸びる分は確定時の軌跡で推定）。
      if (track.points.size >= 2) {
        ActionChip(
          text = "立ち寄りを追加",
          container = MaterialTheme.colorScheme.tertiaryContainer,
          content = MaterialTheme.colorScheme.onTertiaryContainer,
          onClick = onManualAdd,
        )
      }
      if (unresolvedCount > 0) {
        ActionChip(
          text = "場所を取得（未取得 ${unresolvedCount}件）",
          container = MaterialTheme.colorScheme.secondaryContainer,
          content = MaterialTheme.colorScheme.onSecondaryContainer,
          onClick = onResolveNames,
        )
      }
    }
  }
}

@Composable
internal fun SelectionBar(
  selectedCount: Int,
  totalCount: Int,
  canMerge: Boolean,
  onSelectAll: () -> Unit,
  onDelete: () -> Unit,
  onMerge: () -> Unit,
  onCancel: () -> Unit,
) {
  // 固定ヘッダー。地図・一覧と区別できるよう淡い背景を敷く。
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "${selectedCount}件選択中",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectAll) {
          Text(if (selectedCount == totalCount && totalCount > 0) "全解除" else "全選択")
        }
        // 同じ場所への複数の訪問を1件にまとめる（到着は最も早い・出発は最も遅い・メモは連結）。
        TextButton(onClick = onMerge, enabled = canMerge) { Text("まとめる") }
        TextButton(onClick = onDelete, enabled = selectedCount > 0) {
          Text("削除", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onCancel) { Text("キャンセル") }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StopRow(
  stop: Stop,
  number: Int,
  highlighted: Boolean,
  selectionMode: Boolean,
  selected: Boolean,
  onFocus: () -> Unit,
  onEdit: () -> Unit,
  onEditNote: () -> Unit,
  onEditDuration: () -> Unit,
  canEditDuration: Boolean,
  onReassign: () -> Unit,
  onDelete: () -> Unit,
  onToggleSelect: () -> Unit,
  onEnterSelection: () -> Unit,
) {
  val title = stop.place.displayName
  val note = stop.note?.takeIf { it.isNotBlank() }
  Row(
    // 通常はタップでフォーカス、長押しで選択モード。選択モード中はタップで選択トグル。
    // 地図で選ばれている立ち寄りは薄く強調する（地図↔一覧の連動）。
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(
        if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
      )
      .combinedClickable(
        onClick = { if (selectionMode) onToggleSelect() else onFocus() },
        onLongClick = { if (!selectionMode) onEnterSelection() },
      )
      .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (selectionMode) {
      Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
    } else {
      // 訪問順の番号バッジ（地図のピン番号と対応）。
      Box(
        modifier = Modifier
          .padding(end = 10.dp)
          .size(26.dp)
          .background(MarkerStopViolet, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = "$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }
    }
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "${DateFormatters.shortTime(stop.arrivalTime)}" +
          " – ${DateFormatters.shortTime(stop.departureTime)}" +
          " ・ 滞在${stop.durationMinutes}分",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // メモがあれば時刻の下にインライン表示（長文は省略）。
      if (note != null) {
        Text(
          text = note,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
    if (!selectionMode) {
      // 操作はまとめてオーバーフローメニューに（行の横幅を圧迫して場所名が潰れるのを防ぐ）。
      Box {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
          Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = "操作",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(if (note != null) "メモを編集" else "メモを追加") },
            onClick = {
              menuOpen = false
              onEditNote()
            },
          )
          DropdownMenuItem(
            text = { Text("名前を編集") },
            onClick = {
              menuOpen = false
              onEdit()
            },
          )
          // 区切り方が実態とずれたとき用（信号待ちで切れる・屋内で座標が固まる）。
          if (canEditDuration) {
            DropdownMenuItem(
              text = { Text("滞在時間を編集") },
              onClick = {
                menuOpen = false
                onEditDuration()
              },
            )
          }
          // 誤検知の訂正: この訪問だけ正しい場所へ付け替える。
          DropdownMenuItem(
            text = { Text("場所を選び直す") },
            onClick = {
              menuOpen = false
              onReassign()
            },
          )
          DropdownMenuItem(
            text = { Text("削除", color = MaterialTheme.colorScheme.error) },
            onClick = {
              menuOpen = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

@Composable
internal fun ActionChip(
  text: String,
  container: Color,
  content: Color,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = container,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = content,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
  }
}
