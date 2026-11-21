package com.pathly.presentation.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pathly.domain.model.Stop
import com.pathly.domain.model.WishlistFlag
import com.pathly.presentation.common.SheetContentHorizontalPadding
import com.pathly.presentation.history.StopRow
import com.pathly.presentation.stops.CurrentStopListBadge
import com.pathly.ui.theme.PathlySpacing
import com.pathly.util.DateFormatters

/**
 * 記録中のお出掛けの立ち寄りの一覧（記録のシートの下の段）。記録しながら取りこぼしに気付けるよう、新しい順に並べる
 * （ハーフで見えるのは新しい 2 件ほど）。立ち寄り中（滞在している最中で、まだ番号が無い）があれば先頭に出す。
 * 番号は訪問順で、地図の立ち寄りのピンの番号と同じ。確定した立ち寄りの行は経路詳細と同じ（行きたいの旗と ⋮ の操作。
 * 押すと地図がその立ち寄りへ寄る → [rowActions]）。立ち寄り中の行はまだ保存されていないので操作は出さず、押すと寄るだけ。
 * 長押しのまとめて削除は経路詳細だけ。
 */
@Composable
internal fun RecordingStopList(
  stops: List<Stop>,
  currentStop: Stop?,
  rowActions: RecordingStopRowActions,
  modifier: Modifier = Modifier,
  wishlistFlags: Map<Long, WishlistFlag> = emptyMap(),
) {
  val count = stops.size + if (currentStop != null) 1 else 0
  val listState = rememberLazyListState()
  // 先頭（いちばん新しい行）が変わったら先頭へ戻す。行に key を付けているので、放っておくと一覧は前に見ていた行に
  // 留まり、増えた新しい行が上に隠れる（記録しながら見るのは新しい行なので、そちらを見せる）。
  val newestKey: Any? = if (currentStop != null) "current" else stops.lastOrNull()?.id
  LaunchedEffect(newestKey) {
    if (newestKey != null) listState.animateScrollToItem(0)
  }
  Column(modifier = modifier.fillMaxWidth().testTag("recordingStopList")) {
    Text(
      text = if (count == 0) "まだ立ち寄りはありません" else "立ち寄り ${count}件（新しい順）",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge),
    )
    if (count > 0) {
      // 番号は訪問順（古い順に 1, 2, …）のまま、並びだけ新しい順にする。
      val numbered = stops.mapIndexed { index, stop -> index + 1 to stop }.asReversed()
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = SheetContentHorizontalPadding),
        contentPadding = PaddingValues(top = PathlySpacing.xxs, bottom = PathlySpacing.lg),
      ) {
        currentStop?.let { stop ->
          item(key = "current") {
            RecordingStopRow(
              badge = { CurrentStopListBadge() },
              title = stop.place.name ?: stop.place.googleName ?: stop.place.googleAddress ?: "立ち寄り中",
              time = "${DateFormatters.shortTime(stop.arrivalTime)} – 滞在中 ・ ${stop.durationMinutes}分",
              onClick = { rowActions.onFocus(stop) },
            )
          }
        }
        items(numbered, key = { (_, stop) -> stop.id }) { (number, stop) ->
          StopRow(
            stop = stop,
            number = number,
            highlighted = false,
            selectionMode = false,
            selected = false,
            onFocus = { rowActions.onFocus(stop) },
            onEdit = { rowActions.onEditName(stop) },
            onEditNote = { rowActions.onEditNote(stop) },
            onEditDuration = { rowActions.onEditDuration(stop) },
            canEditDuration = rowActions.canEditDuration,
            onReassign = { rowActions.onReassign(stop) },
            onDelete = { rowActions.onDelete(stop) },
            onToggleSelect = {},
            onEnterSelection = {},
            wishlistFlag = wishlistFlags[stop.place.id] ?: WishlistFlag.NONE,
            onToggleWishlist = { rowActions.onToggleWishlist(stop) },
          )
        }
      }
    }
  }
}

/**
 * 記録のシートの立ち寄りの行の操作（経路詳細の行と同じ → [StopRow]）。[canEditDuration] は軌跡の点が 2 つ以上あるときだけ。
 */
internal data class RecordingStopRowActions(
  val onFocus: (Stop) -> Unit = {},
  val onToggleWishlist: (Stop) -> Unit = {},
  val onEditName: (Stop) -> Unit = {},
  val onEditNote: (Stop) -> Unit = {},
  val onEditDuration: (Stop) -> Unit = {},
  val canEditDuration: Boolean = false,
  val onReassign: (Stop) -> Unit = {},
  val onDelete: (Stop) -> Unit = {},
)

/** 立ち寄り中の行（頭の丸・名前・時刻）。まだ保存されていないので操作は持たない。 */
@Composable
private fun RecordingStopRow(
  badge: @Composable () -> Unit,
  title: String,
  time: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClickLabel = "地図で見る", onClick = onClick)
      .padding(horizontal = PathlySpacing.xs, vertical = PathlySpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.md),
  ) {
    badge()
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = time,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
