package com.pathly.presentation.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.ui.theme.PathlySpacing
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * 「ほかの場所とまとめる」（→ docs/specs/places.md の「手で場所をまとめる」）。
 *
 * 近くのほかの場所を近い順に出し、選ぶと確認に進む。「まとめる」で [item] を選んだ場所にまとめる
 * （[item] は消える）。両方に施設情報があるときは、[item] の施設情報が外れることを確認に出す。
 */
@Composable
internal fun PlaceMergePickerDialog(
  item: PlaceListItem,
  onFindCandidates: suspend (PlaceListItem) -> List<PlaceWithDistance>,
  onMerge: (other: SameFacilityPlace) -> Unit,
  onDismiss: () -> Unit,
) {
  // null は読み込み中。
  var candidates by remember(item.place.id) { mutableStateOf<List<PlaceWithDistance>?>(null) }
  var chosen by remember(item.place.id) { mutableStateOf<PlaceWithDistance?>(null) }
  LaunchedEffect(item.place.id) {
    candidates = try {
      onFindCandidates(item)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      emptyList()
    }
  }

  val target = chosen
  if (target == null) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("ほかの場所とまとめる") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm)) {
          Text(
            "「${item.displayName}」と同じ場所を選んでください。" +
              "${PlaceEditUseCase.MERGE_RADIUS_METERS.roundToInt()}m 以内の場所を近い順に出します。",
            style = MaterialTheme.typography.bodyMedium,
          )
          val list = candidates
          when {
            list == null -> Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(PathlySpacing.lg),
              contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Text(
              "近くにほかの場所はありません",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
              items(list, key = { it.place.placeId }) { candidate ->
                CandidateRow(candidate = candidate, onClick = { chosen = candidate })
                HorizontalDivider()
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text("閉じる") }
      },
    )
  } else {
    val otherName = target.place.displayName
    // 施設は 1 つの場所にしか持てないので、両方にあれば残る方（選んだ方）のものだけが残る（→ adr/0037）。
    val losesFacility = item.place.googlePlaceId != null && target.place.hasGoogleInfo
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("「$otherName」にまとめますか？") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm)) {
          Text(
            "「${item.displayName}」を「$otherName」にまとめます。立ち寄り・メモ・行きたいは「$otherName」に引き継ぎます。" +
              "名前は「$otherName」に付けた名前を優先し、無ければ「${item.displayName}」の名前を使います。",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (losesFacility) {
            Text(
              "「${item.displayName}」の施設情報（${item.place.googleName ?: "Google の施設"}）は外れます。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
          Text(
            "まとめた後でも、スナックバーの「取り消す」で戻せます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onMerge(SameFacilityPlace(target.place.placeId, otherName)) }) { Text("まとめる") }
      },
      dismissButton = {
        TextButton(onClick = { chosen = null }) { Text("戻る") }
      },
    )
  }
}

@Composable
private fun CandidateRow(candidate: PlaceWithDistance, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = PathlySpacing.sm),
  ) {
    Text(candidate.place.displayName, style = MaterialTheme.typography.bodyLarge)
    Text(
      "${distanceLabel(candidate.distanceMeters)}・${candidate.place.statusLabel}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** 「約 40m」。10m 未満は「10m 以内」（座標の誤差より細かく見せない）。 */
private fun distanceLabel(meters: Double): String = if (meters < 10.0) "10m 以内" else "約 ${(meters / 10.0).roundToInt() * 10}m"
