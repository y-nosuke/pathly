package com.pathly.presentation.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathly.R
import com.pathly.ui.theme.PathlySpacing
import com.pathly.ui.theme.PathlyTheme

// 一覧の統計の帯を押して広げる、統計のカードの部品（→ docs/specs/screens.md の「統計のカード」）。
// 中身（数字のタイル・絵 1 つ・読み物 1 つ）は一覧ごとに組み、外側と部品はここで揃える。

/**
 * 統計の帯と、押して広げた統計のカード。[expanded] なら [card] を中に持つカード、そうでなければ 1 行の帯
 * （[barText]）を出す。カードの見出しの行（「統計」・絞り込み中・▴）を押すと帯に戻る。
 */
@Composable
fun ExpandableListStats(
  barText: String,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  isFiltered: Boolean,
  modifier: Modifier = Modifier,
  card: @Composable ColumnScope.() -> Unit,
) {
  AnimatedContent(
    targetState = expanded,
    modifier = modifier,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "listStats",
  ) { isExpanded ->
    if (isExpanded) {
      ListStatsCard(isFiltered = isFiltered, onCollapse = { onExpandedChange(false) }, content = card)
    } else {
      ListStatsBar(text = barText, onClick = { onExpandedChange(true) })
    }
  }
}

/** 統計のカードの外側。帯と同じ地の色・角で、見出しの行を押すと畳む。 */
@Composable
private fun ListStatsCard(isFiltered: Boolean, onCollapse: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = PathlySpacing.screenEdge)
      .testTag("listStatsCard"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(role = Role.Button, onClick = onCollapse)
          .padding(start = PathlySpacing.md, end = PathlySpacing.md, top = PathlySpacing.sm, bottom = PathlySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (isFiltered) "統計 ・ 絞り込み中" else "統計",
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.weight(1f),
        )
        Icon(painter = painterResource(R.drawable.ic_expand_less), contentDescription = "統計を畳む", modifier = Modifier.size(20.dp))
      }
      Column(
        modifier = Modifier.padding(start = PathlySpacing.md, end = PathlySpacing.md, bottom = PathlySpacing.md),
        verticalArrangement = Arrangement.spacedBy(PathlySpacing.md),
        content = content,
      )
    }
  }
}

/** 数字のタイルを横に並べる行（高さを揃える）。 */
@Composable
fun StatsTileRow(content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
    content = content,
  )
}

/**
 * タイルの色。数字の種類ごとに変える（並べたタイルを見分けやすく）。secondary の container は primary とほぼ同じ青に
 * 見えるので、2 つ目は訪問済みの緑を薄く敷く。
 */
enum class StatsTileTone { PRIMARY, GREEN, TERTIARY }

/**
 * 数字のタイル（アイコン・値・名前を縦に）。[value] の中の数字を大きく太く、単位は小さく出す。
 * 収まらないときは字を小さくして 1 行に収める。
 */
@Composable
fun RowScope.StatsTile(
  @DrawableRes icon: Int,
  value: String,
  label: String,
  tone: StatsTileTone,
  modifier: Modifier = Modifier,
) {
  val (container, content) = when (tone) {
    StatsTileTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

    StatsTileTone.GREEN -> PathlyTheme.statusColors.visited.let { green ->
      green.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainer) to green
    }

    StatsTileTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
  }
  Column(
    modifier = modifier
      .weight(1f)
      .fillMaxHeight()
      .clip(MaterialTheme.shapes.small)
      .background(container)
      .padding(horizontal = PathlySpacing.xs, vertical = PathlySpacing.sm)
      .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(painter = painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    val numberStyle = SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold)
    BasicText(
      text = remember(value, numberStyle) { emphasizeNumbers(value, numberStyle) },
      style = MaterialTheme.typography.bodySmall.copy(color = content, textAlign = TextAlign.Center),
      maxLines = 1,
      autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.bodySmall.fontSize),
      modifier = Modifier.padding(top = PathlySpacing.xxs),
    )
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** カードの中の小見出し（左）と、添える数字（右）。 */
@Composable
fun StatsSectionTitle(title: String, trailing: String? = null) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(text = title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
    if (trailing != null) {
      Text(text = trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}

/**
 * 棒グラフ（左から順に）。高さは [values] の最大に合わせ、最後の棒（今）を強める。すべて 0 なら棒は出さない。
 * 棒の下の左右に [startLabel] と [endLabel] を出す。
 */
@Composable
fun StatsBarChart(values: List<Double>, startLabel: String, endLabel: String, modifier: Modifier = Modifier) {
  val strong = MaterialTheme.colorScheme.primary
  val weak = strong.copy(alpha = 0.35f)
  val baseline = MaterialTheme.colorScheme.outlineVariant
  val max = values.maxOrNull() ?: 0.0
  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .testTag("statsBarChart"),
    ) {
      val gap = 4.dp.toPx()
      val barWidth = (size.width - gap * (values.size - 1)) / values.size
      if (max > 0.0) {
        values.forEachIndexed { i, v ->
          // 0 でない月は、低くても見えるよう最低 2dp の高さにする。
          val h = if (v <= 0.0) 0f else ((v / max).toFloat() * size.height).coerceAtLeast(2.dp.toPx())
          drawRoundRect(
            color = if (i == values.lastIndex) strong else weak,
            topLeft = Offset(i * (barWidth + gap), size.height - h),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(2.dp.toPx()),
          )
        }
      }
      drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = PathlySpacing.xxs)) {
      Text(text = startLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
      Text(text = endLabel, style = MaterialTheme.typography.labelSmall)
    }
  }
}

/** 割合の輪（真ん中に「28%」）。[percent] は 0〜100。 */
@Composable
fun StatsRing(percent: Int, modifier: Modifier = Modifier) {
  val track = MaterialTheme.colorScheme.surfaceContainerHighest
  val progress = MaterialTheme.colorScheme.primary
  Box(modifier = modifier.size(60.dp).testTag("statsRing"), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.size(60.dp)) {
      val stroke = 7.dp.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
      if (percent > 0) {
        drawArc(
          progress,
          -90f,
          360f * percent.coerceAtMost(100) / 100f,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(stroke, cap = StrokeCap.Round),
        )
      }
    }
    Text(
      text = "$percent%",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** 内訳の 1 つ（名前・数）。 */
data class StatsShare(val label: String, val count: Int)

/**
 * 内訳を 1 本の帯（割合で区切る）と、その下の凡例（色の点・名前・数）で出す。色は前から順に
 * primary・tertiary・secondary・outline。[lastIsRest] なら最後の 1 つ（「ほかの業種」）は控えめな色にする。
 * 暗い表示の primary などは文字用の淡い色で、塗ると色が薄く見えるので、container の色との中間にして濃くする。
 */
@Composable
fun StatsShareBar(shares: List<StatsShare>, lastIsRest: Boolean, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val dark = scheme.surface.luminance() < 0.5f
  fun fill(color: Color, container: Color): Color = if (dark) lerp(color, container, 0.45f) else color
  val palette = listOf(
    fill(scheme.primary, scheme.primaryContainer),
    fill(scheme.tertiary, scheme.tertiaryContainer),
    fill(scheme.secondary, scheme.secondaryContainer),
    scheme.outline,
  )
  val rest = MaterialTheme.colorScheme.outlineVariant
  val colors = shares.indices.map { i -> if (lastIsRest && i == shares.lastIndex) rest else palette[i % palette.size] }
  val total = shares.sumOf { it.count }
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PathlySpacing.xs)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(10.dp)
        .clip(RoundedCornerShape(5.dp))
        .testTag("statsShareBar"),
    ) {
      if (total > 0) {
        shares.forEachIndexed { i, share ->
          if (share.count > 0) {
            Box(modifier = Modifier.weight(share.count.toFloat()).fillMaxHeight().background(colors[i]))
          }
        }
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.md)) {
      shares.forEachIndexed { i, share -> ShareLegend(color = colors[i], text = "${share.label} ${share.count}") }
    }
  }
}

@Composable
private fun ShareLegend(color: Color, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(modifier = Modifier.width(PathlySpacing.xs))
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
  }
}
