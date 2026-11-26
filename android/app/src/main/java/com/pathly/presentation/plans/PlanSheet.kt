package com.pathly.presentation.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pathly.R
import com.pathly.domain.model.PlanDetail
import com.pathly.domain.model.PlanItem
import com.pathly.presentation.common.SheetContentHorizontalPadding
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlyMapColors
import com.pathly.ui.theme.PathlySpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** シートの行から呼ぶ操作。 */
internal class PlanSheetActions(
  val onAddPlaces: () -> Unit,
  val onAddMemo: () -> Unit,
  /** 行のタップ（場所の行は地図をその場所へ寄せ、メモの行は編集を開く）。 */
  val onItemClick: (PlanItem) -> Unit,
  val onEdit: (PlanItem) -> Unit,
  val onMoveToDay: (PlanItem) -> Unit,
  val onOpenPlace: (PlanItem) -> Unit,
  val onRemove: (PlanItem) -> Unit,
  /** 並べ替えた結果（上から順の行の id・入った日）。 */
  val onReorder: (List<Pair<Long, Int>>) -> Unit,
)

/**
 * 計画の詳細のシートの中身: 日付・日数の要約、場所を足す・メモを足す、日ごとの見出しの下に並ぶ行。
 * 行は左端の取っ手（⋮⋮）に触れて縦に動かすと、長押しなしで掴める（→ adr/0051）。
 */
@Composable
internal fun ColumnScope.PlanSheetContent(
  detail: PlanDetail,
  listState: LazyListState,
  actions: PlanSheetActions,
  // 並べ替えを保存できなかった回数。増えたら画面の並びを計画のとおりに戻す。
  reorderFailures: Int = 0,
) {
  val plan = detail.plan
  val original = remember(detail) { slotsOf(detail) }
  val reorder = remember(listState) { PlanReorderState(listState) }
  LaunchedEffect(original, reorderFailures) { reorder.sync(original) }
  val slots = reorder.slots.ifEmpty { original }
  val itemsById = remember(detail) { detail.items.associateBy { it.id } }
  // 並べ替えの途中は、番号も今の並びで振り直して見せる（離す前から地図と同じ番号になる）。
  val numbers = remember(slots, itemsById) { placeNumbersOf(slots, itemsById) }
  val currentActions by rememberUpdatedState(actions)
  val currentOriginal by rememberUpdatedState(original)
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current

  // 掴んだ行を一覧の上端・下端へ寄せている間は、一覧を送る。
  LaunchedEffect(reorder) {
    snapshotFlow { reorder.dragging }.collect { dragging ->
      if (dragging == null) return@collect
      val edge = with(density) { AutoScrollEdge.toPx() }
      val step = with(density) { AutoScrollStep.toPx() }
      while (reorder.dragging == dragging) {
        val center = reorder.draggedCenter()
        val info = listState.layoutInfo
        if (center != null) {
          val delta = when {
            center < info.viewportStartOffset + edge -> -step
            center > info.viewportEndOffset - edge -> step
            else -> 0f
          }
          if (delta != 0f) reorder.onScrolled(listState.scrollBy(delta))
        }
        delay(16)
      }
    }
  }

  val rendered = remember(slots) { renderedEntries(slots) }

  LazyColumn(
    state = listState,
    modifier = Modifier
      .weight(1f)
      .fillMaxWidth()
      .testTag("planSheetList"),
    contentPadding = PaddingValues(bottom = PathlySpacing.xl),
  ) {
    item(key = "summary") {
      PlanSummaryHeader(summary = planDateSummary(plan), onAddPlaces = actions.onAddPlaces, onAddMemo = actions.onAddMemo)
    }
    if (detail.items.isEmpty() && plan.isDayTrip) {
      item(key = "emptyPlan") { EmptyDayLine("場所を足すか、メモの行を足してください") }
    }
    items(rendered, key = { it.key }) { entry ->
      when (entry) {
        is SheetEntry.Header -> DayHeader(label = dayLabel(plan, entry.day), color = planDayColor(entry.day))

        is SheetEntry.Empty -> EmptyDayLine("まだ何もありません")

        is SheetEntry.Item -> {
          val item = itemsById[entry.itemId] ?: return@items
          val index = slots.indexOfFirst { it.key == entry.key }
          val lifted = reorder.dragging == item.id
          val day = dayOfSlot(slots, index)
          PlanItemRow(
            item = item,
            number = numbers[item.id],
            color = planDayColor(day),
            lifted = lifted,
            canMoveDay = !plan.isDayTrip,
            actions = actions,
            modifier = Modifier
              .zIndex(if (lifted) 1f else 0f)
              .graphicsLayer {
                if (lifted) {
                  translationY = reorder.offset
                  scaleX = LIFTED_SCALE
                  scaleY = LIFTED_SCALE
                }
              }
              .then(if (lifted) Modifier else Modifier.animateItem()),
            handleModifier = Modifier.pointerInput(item.id, reorder) {
              var holding = false
              detectVerticalDragGestures(
                onDragStart = {
                  holding = reorder.start(item.id)
                  if (holding) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                },
                onVerticalDrag = { change, amount ->
                  change.consume()
                  if (holding) reorder.dragBy(amount)
                },
                onDragEnd = {
                  if (holding) reorder.drop(currentOriginal)?.let { currentActions.onReorder(it) }
                  holding = false
                },
                onDragCancel = {
                  if (holding) reorder.drop(currentOriginal)?.let { currentActions.onReorder(it) }
                  holding = false
                },
              )
            },
            // 読み上げ（TalkBack）では「上へ移す」「下へ移す」で 1 つずつ動かす（見出しを越えれば別の日へ）。
            accessibilityActions = buildList {
              val min = if (slots.firstOrNull() is PlanSlotKey.Day) 1 else 0
              if (index > min) {
                add(
                  CustomAccessibilityAction("上へ移す") {
                    scope.launch { currentActions.onReorder(orderOf(moveSlot(slots, index, index - 1))) }
                    true
                  },
                )
              }
              if (index in 0 until slots.lastIndex) {
                add(
                  CustomAccessibilityAction("下へ移す") {
                    scope.launch { currentActions.onReorder(orderOf(moveSlot(slots, index, index + 1))) }
                    true
                  },
                )
              }
            },
          )
        }
      }
    }
  }
}

/** シートに描く 1 行（見出し・行・空の日の案内）。 */
private sealed interface SheetEntry {
  val key: String

  data class Header(val day: Int) : SheetEntry {
    override val key: String get() = PlanSlotKey.Day(day).key
  }

  data class Item(val itemId: Long) : SheetEntry {
    override val key: String get() = PlanSlotKey.Row(itemId).key
  }

  data class Empty(val day: Int) : SheetEntry {
    override val key: String get() = "empty:$day"
  }
}

/** 並びに、行の無い日の案内を挟む（掴んだ行をその日へ落とせることが分かるように）。 */
private fun renderedEntries(slots: List<PlanSlotKey>): List<SheetEntry> = buildList {
  slots.forEachIndexed { i, slot ->
    when (slot) {
      is PlanSlotKey.Day -> {
        add(SheetEntry.Header(slot.day))
        if (slots.getOrNull(i + 1) !is PlanSlotKey.Row) add(SheetEntry.Empty(slot.day))
      }

      is PlanSlotKey.Row -> add(SheetEntry.Item(slot.itemId))
    }
  }
}

/** 並びの [index] 番目が入っている日（見出しが無ければ 1 日目）。 */
private fun dayOfSlot(slots: List<PlanSlotKey>, index: Int): Int = slots.take(index + 1).filterIsInstance<PlanSlotKey.Day>().lastOrNull()?.day ?: 1

/** 並びどおりの、場所の行の日ごとの番号。 */
private fun placeNumbersOf(slots: List<PlanSlotKey>, items: Map<Long, PlanItem>): Map<Long, Int> {
  val numbers = mutableMapOf<Long, Int>()
  var count = 0
  for (slot in slots) {
    when (slot) {
      is PlanSlotKey.Day -> count = 0
      is PlanSlotKey.Row -> if (items[slot.itemId]?.isPlace == true) numbers[slot.itemId] = ++count
    }
  }
  return numbers
}

@Composable
private fun PlanSummaryHeader(summary: String, onAddPlaces: () -> Unit, onAddMemo: () -> Unit) {
  Column(modifier = Modifier.padding(horizontal = SheetContentHorizontalPadding)) {
    Text(
      text = summary,
      style = MaterialTheme.typography.titleMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.testTag("planSummary"),
    )
    Spacer(modifier = Modifier.height(PathlySpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm)) {
      OutlinedButton(onClick = onAddPlaces, modifier = Modifier.testTag("planAddPlaces")) {
        Icon(painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(PathlySpacing.xs))
        Text("場所を足す")
      }
      OutlinedButton(onClick = onAddMemo, modifier = Modifier.testTag("planAddMemo")) {
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(PathlySpacing.xs))
        Text("メモを足す")
      }
    }
    Spacer(modifier = Modifier.height(PathlySpacing.sm))
  }
}

/** 日の見出し。左端の帯をその日の色にし、地図の線・マーカーと結び付ける。 */
@Composable
private fun DayHeader(label: String, color: Color) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = SheetContentHorizontalPadding, end = SheetContentHorizontalPadding, top = PathlySpacing.md, bottom = PathlySpacing.xs)
      .testTag("planDayHeader"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .width(DayBarWidth)
        .height(20.dp)
        .clip(CircleShape)
        .background(color),
    )
    Spacer(modifier = Modifier.width(PathlySpacing.sm))
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
  HorizontalDivider(modifier = Modifier.padding(horizontal = SheetContentHorizontalPadding), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EmptyDayLine(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = SheetContentHorizontalPadding + HandleWidth, vertical = PathlySpacing.md),
  )
}

/**
 * 計画の 1 行。左から取っ手（⋮⋮）・番号の丸（場所の行。メモの行は小さな点）・名前と時刻とメモ・⋮。
 * 名前とメモは 1 行で省略する。
 */
@Composable
private fun PlanItemRow(
  item: PlanItem,
  number: Int?,
  color: Color,
  lifted: Boolean,
  canMoveDay: Boolean,
  actions: PlanSheetActions,
  handleModifier: Modifier,
  accessibilityActions: List<CustomAccessibilityAction>,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = if (lifted) PathlyElevation.floating else 0.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { actions.onItemClick(item) }
        .semantics { customActions = accessibilityActions }
        .padding(start = SheetContentHorizontalPadding - PathlySpacing.sm, end = PathlySpacing.xs)
        .testTag("planItem:${item.id}"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // 取っ手。ここに触れて縦に動かすと掴む（行のほかの所を縦に動かせばスクロール）。
      Box(
        modifier = handleModifier
          .size(width = HandleWidth, height = 48.dp)
          .semantics { contentDescription = "並べ替えの取っ手" }
          .testTag("planItemHandle:${item.id}"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_drag_indicator),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
      if (item.isPlace && number != null) {
        PlanNumberBadge(number = number, color = color)
      } else {
        Box(modifier = Modifier.size(BadgeSize), contentAlignment = Alignment.Center) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onSurfaceVariant),
          )
        }
      }
      Spacer(modifier = Modifier.width(PathlySpacing.md))
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(vertical = PathlySpacing.sm),
      ) {
        Text(
          text = item.label,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        itemTimeLine(item)?.let {
          Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        item.note?.let {
          Text(
            text = it.lineSequence().first(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      PlanItemMenu(item = item, canMoveDay = canMoveDay, actions = actions)
    }
  }
}

@Composable
private fun PlanItemMenu(item: PlanItem, canMoveDay: Boolean, actions: PlanSheetActions) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("planItemMenu:${item.id}")) {
      Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "${item.label}の操作")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(text = { Text("編集") }, onClick = {
        open = false
        actions.onEdit(item)
      })
      if (canMoveDay) {
        DropdownMenuItem(text = { Text("別の日へ移す") }, onClick = {
          open = false
          actions.onMoveToDay(item)
        })
      }
      if (item.isPlace) {
        DropdownMenuItem(text = { Text("場所の詳細を開く") }, onClick = {
          open = false
          actions.onOpenPlace(item)
        })
      }
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text("計画から外す", color = MaterialTheme.colorScheme.error) },
        onClick = {
          open = false
          actions.onRemove(item)
        },
      )
    }
  }
}

/** 番号の丸（地図のマーカーと同じ色・同じ番号）。 */
@Composable
internal fun PlanNumberBadge(number: Int, color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(BadgeSize)
      .clip(CircleShape)
      .background(color),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "$number",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = PathlyMapColors.OnMarker,
      maxLines = 1,
    )
  }
}

private val BadgeSize = 28.dp
private val HandleWidth = 32.dp
private val DayBarWidth = 6.dp
private val AutoScrollEdge = 48.dp
private val AutoScrollStep = 12.dp
private const val LIFTED_SCALE = 1.02f
