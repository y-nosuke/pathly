package com.pathly.presentation.history

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pathly.BuildConfig
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.MapBackTitle
import com.pathly.presentation.common.MapOverflowMenuButton
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.common.MapTopBar
import com.pathly.presentation.common.RegisteredPlacesLayerButton
import com.pathly.presentation.tracking.RecordingStatusPill
import com.pathly.util.DateFormatters

/**
 * 経路詳細の上の区画（地図の画面の型 → docs/designs/ui-direction.md）。
 * 左は戻る＋見出し（名前。無ければ日付。押すと名前を編集）、右端はレイヤー［≋］（登録済みの場所の表示・絞り込み）と
 * ⋮（名前を編集・お気に入り・再解析、開発版の補正の調整・GPS 詳細）。記録中の経路は、その下に記録中のピルを出す。
 *
 * @param canReanalyze 再解析を始められるか（各モードや選択の途中は、そちらを終えてから）。
 * @param canTune 補正の調整を始め・やめられるか（ほかのモードの途中は、パネルが重なるので押せない）。
 */
@Composable
internal fun TrackDetailTopBar(
  track: GpsTrack,
  mapPlaces: MapRegisteredPlaces,
  tuningMode: Boolean,
  canReanalyze: Boolean,
  canTune: Boolean,
  onBack: () -> Unit,
  onRename: () -> Unit,
  onToggleFavorite: () -> Unit,
  onReanalyze: () -> Unit,
  onToggleTuning: () -> Unit,
  onOpenDebugInfo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MapTopBar(
    modifier = modifier,
    leading = {
      // 見出しは一覧のカードと同じく、名前があれば名前（下に日付）、無ければ日付。
      MapBackTitle(
        title = if (track.hasName) track.name.orEmpty() else DateFormatters.date(track.startTime),
        subtitle = if (track.hasName) DateFormatters.date(track.startTime) else null,
        onBack = onBack,
        onTitleClick = onRename,
        titleClickLabel = if (track.hasName) "名前を編集" else "名前を付ける",
        modifier = Modifier.weight(1f),
      )
    },
    actions = {
      RegisteredPlacesLayerButton(mapPlaces)
      MapOverflowMenuButton { close ->
        TrackDetailMenuItems(
          track = track,
          tuningMode = tuningMode,
          canReanalyze = canReanalyze,
          canTune = canTune,
          onRename = onRename,
          onToggleFavorite = onToggleFavorite,
          onReanalyze = onReanalyze,
          onToggleTuning = onToggleTuning,
          onOpenDebugInfo = onOpenDebugInfo,
          close = close,
        )
      }
    },
    status = {
      if (track.isActive) {
        Row { RecordingStatusPill(startTime = track.startTime) }
      }
    },
  )
}

/** ⋮ の中身。経路への操作を上に、開発版だけのものは区切り線の下に置く（文字だけ。お気に入りの状態は文言で示す）。 */
@Composable
private fun ColumnScope.TrackDetailMenuItems(
  track: GpsTrack,
  tuningMode: Boolean,
  canReanalyze: Boolean,
  canTune: Boolean,
  onRename: () -> Unit,
  onToggleFavorite: () -> Unit,
  onReanalyze: () -> Unit,
  onToggleTuning: () -> Unit,
  onOpenDebugInfo: () -> Unit,
  close: () -> Unit,
) {
  val hasPoints = track.points.isNotEmpty()
  DropdownMenuItem(
    text = { Text(if (track.hasName) "名前を編集" else "名前を付ける") },
    onClick = {
      close()
      onRename()
    },
  )
  DropdownMenuItem(
    text = { Text(if (track.isFavorite) "お気に入りから外す" else "お気に入りに入れる") },
    onClick = {
      close()
      onToggleFavorite()
    },
  )
  // 再解析: 一覧に無い立ち寄りを検出して、選択式で追加する（非破壊）。記録中も出す（誤って消した立ち寄りの復旧用）。
  // 候補は境界以前＝「確定済みの過去」だけに絞られ、滞在中・末尾はライブ検出に任せる。
  if (track.points.size >= 2) {
    DropdownMenuItem(
      text = { Text("再解析") },
      enabled = canReanalyze,
      onClick = {
        close()
        onReanalyze()
      },
    )
  }
  if (BuildConfig.DEBUG && hasPoints) {
    HorizontalDivider()
    DropdownMenuItem(
      text = { Text(if (tuningMode) "補正の調整をやめる" else "補正を調整") },
      enabled = canTune,
      onClick = {
        close()
        onToggleTuning()
      },
    )
    DropdownMenuItem(
      text = { Text("GPS詳細（デバッグ）") },
      onClick = {
        close()
        onOpenDebugInfo()
      },
    )
  }
}
