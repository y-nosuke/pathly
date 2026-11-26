package com.pathly.presentation.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
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
import com.pathly.ui.theme.PathlyElevation
import com.pathly.ui.theme.PathlySpacing

/**
 * 計画の一覧（一覧の型 → docs/specs/plans.md）。[deletedPlanTitle] は詳細で消して戻ってきた計画の名前で、
 * 取り消しのスナックバーを出す（出したら [onDeletedShown]）。
 */
@Composable
fun PlansRoute(
  viewModel: PlansViewModel,
  onOpenPlan: (planId: Long) -> Unit,
  deletedPlanTitle: String?,
  onDeletedShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.created.collect { onOpenPlan(it) }
  }
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
    val result = snackbarHostState.showSnackbar("「$title」を削除しました", actionLabel = "取り消す", withDismissAction = true)
    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    undoTitle = null
  }
  Box(modifier = modifier.fillMaxSize()) {
    PlansContent(
      state = state,
      onQueryChange = viewModel::setQuery,
      onToggleGroup = viewModel::toggleGroup,
      onOpenPlan = onOpenPlan,
      onCreate = viewModel::createPlan,
    )
    SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

@Composable
internal fun PlansContent(
  state: PlansUiState,
  onQueryChange: (String) -> Unit,
  onToggleGroup: (PlanGroup) -> Unit,
  onOpenPlan: (Long) -> Unit,
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
              items(section.plans, key = { "plan:${it.plan.id}" }) { summary ->
                PlanRow(summary = summary, onClick = { onOpenPlan(summary.plan.id) })
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
      .testTag("planRow:${summary.plan.id}"),
  ) {
    Column(modifier = Modifier.padding(horizontal = PathlySpacing.screenEdge, vertical = PathlySpacing.md)) {
      Text(
        text = summary.plan.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        // 日付なしの区分の中なので「日付なし」は繰り返さない。
        text = (summary.plan.startDate?.let { planDateSummary(summary.plan) } ?: dayCountLabel(summary.plan.dayCount)) +
          "・場所 ${summary.placeCount}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/** 右下の（＋）が下端から占める高さ（丸と下の余白）に、上に置くものとの間を足したもの。 */
private val PlanAddButtonClearance = 56.dp + PathlySpacing.screenEdge + PathlySpacing.sm
