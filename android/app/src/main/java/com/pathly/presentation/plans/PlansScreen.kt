package com.pathly.presentation.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.R
import com.pathly.domain.model.PlanGroup
import com.pathly.domain.model.PlanSummary
import com.pathly.presentation.common.ListGroupHeader
import com.pathly.presentation.common.ListHeader
import com.pathly.presentation.common.ScrollToTopButton
import com.pathly.presentation.common.ScrollToTopButtonClearance
import com.pathly.presentation.common.SwipeDismissSnackbarHost
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlySpacing

/**
 * 計画の一覧（一覧の型 → docs/specs/plans.md）。[deletedPlanTitle] は詳細で消して戻ってきた計画の名前で、
 * 取り消しのスナックバーを出す（出したら [onDeletedShown]）。
 */
@Composable
fun PlansRoute(
  viewModel: PlansViewModel,
  /** 計画を開く（[PlanSummary.plan] の id が 0 なら新しい計画の下書き。下書きが無ければ [PlanSummary.draftId] は null）。 */
  onOpenPlan: (planId: Long, draftId: Long?) -> Unit,
  /** 新しい計画を開く（保存するまで計画は作らない）。 */
  onCreatePlan: () -> Unit,
  deletedPlanTitle: String?,
  onDeletedShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  // 受け取った名前は画面の状態に移してから消す（消すと鍵が変わり、出している途中の処理が止まるため）。
  var undoTitle by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(deletedPlanTitle) {
    if (deletedPlanTitle != null) {
      undoTitle = deletedPlanTitle
      onDeletedShown()
    }
  }
  LaunchedEffect(undoTitle) {
    val title = undoTitle ?: return@LaunchedEffect
    val result = snackbarHostState.showSnackbar("「$title」を削除しました", actionLabel = "取り消す")
    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    undoTitle = null
  }
  Box(modifier = modifier.fillMaxSize()) {
    PlansContent(
      state = state,
      onQueryChange = viewModel::setQuery,
      onToggleGroup = viewModel::toggleGroup,
      onOpenPlan = onOpenPlan,
      onCreate = onCreatePlan,
    )
    SwipeDismissSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

@Composable
internal fun PlansContent(
  state: PlansUiState,
  onQueryChange: (String) -> Unit,
  onToggleGroup: (PlanGroup) -> Unit,
  onOpenPlan: (Long, Long?) -> Unit,
  onCreate: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  val listState = rememberLazyListState()
  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      ListHeader(
        title = "計画",
        query = state.query,
        onQueryChange = onQueryChange,
        searchPlaceholder = "計画の名前で探す",
        searchOpen = searchOpen,
        onSearchOpenChange = { searchOpen = it },
      )
      Box(modifier = Modifier.weight(1f)) {
        LazyColumn(
          state = listState,
          modifier = Modifier.testTag("planList"),
          // 最後の行が右下の（＋）と先頭へ戻るの上まで上がるよう、下に余白を足す。
          contentPadding = PaddingValues(bottom = PlanAddButtonClearance + ScrollToTopButtonClearance),
        ) {
          if (!state.isLoading && state.sections.isEmpty()) {
            item(key = "empty") {
              Text(
                text = if (state.isEmpty) "計画はまだありません\n右下の ＋ から作れます" else "名前に合う計画がありません",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.xxl),
              )
            }
          }
          // 名前の検索中は区分をすべて開いて見せるので、見出しを押しても開閉しない。
          val toggle = onToggleGroup.takeIf { state.query.isBlank() }
          state.sections.forEach { section ->
            item(key = "group:${section.group.name}") {
              ListGroupHeader(
                label = section.group.label,
                trailing = "${section.plans.size}",
                depth = 0,
                expanded = section.expanded,
                indent = 0.dp,
                testTag = "planGroup:${section.group.name}",
                onClick = toggle?.let { { it(section.group) } },
              )
            }
            if (section.expanded) {
              items(section.plans, key = { it.key }) { summary ->
                PlanRow(summary = summary, onClick = { onOpenPlan(summary.plan.id, summary.draftId) })
              }
            }
          }
        }
        ScrollToTopButton(
          listState = listState,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = PathlySpacing.screenEdge + PathlySpacing.sm, bottom = PlanAddButtonClearance),
        )
      }
    }
    FloatingActionButton(
      onClick = onCreate,
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = PathlyElevation.floating),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(PathlySpacing.screenEdge)
        .testTag("planAddButton"),
    ) {
      Icon(painter = painterResource(R.drawable.ic_add), contentDescription = "新しい計画")
    }
  }
}

/** 一覧の 1 行（1 行目に名前、2 行目に日付か日数と場所の数）。名前は 1 行で省略する。 */
@Composable
private fun PlanRow(summary: PlanSummary, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("planRow:${summary.key}"),
  ) {
    Column(modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.md)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = summary.plan.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (summary.hasDraft) DraftMark(modifier = Modifier.padding(start = PathlySpacing.sm))
        // 記録中の経路が結び付いている（→ docs/specs/plans.md の「重ねて見る」）。
        if (summary.isRecording) RecordingMark(modifier = Modifier.padding(start = PathlySpacing.sm))
      }
      Text(
        // 日付なしの区分の中なので「日付なし」は繰り返さない。
        text = (summary.plan.startDate?.let { planDateSummary(summary.plan) } ?: dayCountLabel(summary.plan.dayCount)) +
          "・" + planProgressLabel(summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/** 一覧の行の「場所 N」（結び付いた経路があれば「行った N/M」）。 */
internal fun planProgressLabel(summary: PlanSummary): String = if (summary.linkedTrackCount > 0 && summary.placeCount > 0) "行った ${summary.visitedCount}/${summary.placeCount}" else "場所 ${summary.placeCount}"

/** 右下の（＋）が下端から占める高さ（丸と下の余白）に、上に置くものとの間を足したもの。 */
private val PlanAddButtonClearance = 56.dp + PathlySpacing.screenEdge + PathlySpacing.sm

/** 下書きのある計画の印（「✎下書き」）。 */
@Composable
internal fun DraftMark(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.testTag("planDraftMark"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_edit),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.tertiary,
      modifier = Modifier.size(16.dp),
    )
    Text(text = "下書き", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
  }
}
