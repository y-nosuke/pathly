package com.pathly.presentation.stops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.presentation.common.FloatingSheet
import com.pathly.presentation.common.rememberFloatingSheetState
import java.util.Date

/**
 * 手動で立ち寄りを追加するときの「起点」。**どこから始めたか**で出す内容が変わる。
 *
 * 以前は記録画面が地点の座標（LatLng）しか持たず、POI をタップしても名前と placeId を
 * 捨てていた。そのため下流で起点を区別できず、施設を選んだ直後なのに「近くの候補から
 * 選び直す」リストが出ていた。起点を型で持つことでこれを無くす。
 */
sealed interface ManualStopOrigin {
  /** 地図の POI をタップした。施設は確定しているので候補は出さない。 */
  data class Poi(val name: String?, val googlePlaceId: String?) : ManualStopOrigin

  /** 地図の何もない場所をタップした。候補は出さず、名前は任意入力。 */
  data object MapPoint : ManualStopOrigin

  /** 「今ここ」。地点を指していないので、近くの施設候補から選ばせる。 */
  data object CurrentLocation : ManualStopOrigin

  /** 地図の登録済みマーカーから。既存の場所へ紐付ける（新しい場所は作らない）。 */
  data class ExistingPlace(val placeId: Long, val name: String?) : ManualStopOrigin
}

/** 手動追加の対象（座標＋起点）。画面側はこれ 1 つを持てばよい。 */
data class ManualStopTarget(
  val latitude: Double,
  val longitude: Double,
  val origin: ManualStopOrigin,
)

/** 手動追加で確定した内容。 */
data class ManualStopInput(
  val latitude: Double,
  val longitude: Double,
  val arrivalTime: Date,
  val departureTime: Date,
  val name: String?,
  val googlePlaceId: String?,
)

/**
 * 手動での立ち寄り追加シート。記録画面と経路詳細で共有する。
 *
 * 地図を見ながら滞在区間を調整できるよう、[FloatingSheet]（非モーダル）で出す。
 * 近くの施設候補を出すのは [ManualStopOrigin.CurrentLocation] のときだけ。
 */
@Composable
fun ManualStopSheet(
  origin: ManualStopOrigin,
  latitude: Double,
  longitude: Double,
  points: List<GpsPoint>,
  onFetchCandidates: suspend (Double, Double) -> List<PlaceSearchResult>,
  onConfirm: (ManualStopInput) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  // 滞在区間の変更を地図のハイライトへ伝える（到着〜出発のインデックス）。
  onRangeChange: (start: Int, end: Int) -> Unit = { _, _ -> },
) {
  val sheetState = rememberFloatingSheetState(peekFraction = 0.5f)

  // 滞在区間は軌跡点のインデックスで調整する。点が2つ未満なら近傍から推定する。
  val hasRange = points.size >= 2
  val lastIdx = (points.size - 1).coerceAtLeast(0)
  var arrivalIdx by remember(origin, latitude, longitude, points) {
    mutableStateOf(if (hasRange) nearestPointIndex(points, latitude, longitude) else 0)
  }
  var departureIdx by remember(origin, latitude, longitude, points) {
    mutableStateOf(if (hasRange) defaultDepartureIndex(points, arrivalIdx) else 0)
  }
  val fallback = remember(latitude, longitude, points) { deriveStopWindow(points, latitude, longitude) }
  val arrival = if (hasRange) points[arrivalIdx].timestamp else fallback.first
  val departure = if (hasRange) points[departureIdx].timestamp else fallback.second

  LaunchedEffect(arrivalIdx, departureIdx) {
    if (hasRange) onRangeChange(arrivalIdx, departureIdx)
  }

  // 候補は「今ここ」のときだけ引く（null = 読込中）。
  val needsCandidates = origin is ManualStopOrigin.CurrentLocation
  var candidates by remember(origin) { mutableStateOf<List<PlaceSearchResult>?>(null) }
  var selected by remember(origin) { mutableStateOf<PlaceSearchResult?>(null) }
  LaunchedEffect(origin, latitude, longitude) {
    if (needsCandidates) candidates = onFetchCandidates(latitude, longitude)
  }

  // 名前欄。POI から来たときはその名前を初期値にする。
  var name by remember(origin) {
    mutableStateOf((origin as? ManualStopOrigin.Poi)?.name.orEmpty())
  }

  FloatingSheet(state = sheetState, modifier = modifier) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = when (origin) {
          is ManualStopOrigin.ExistingPlace -> "この場所に立ち寄りを追加"
          else -> "立ち寄りを追加"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      when (origin) {
        // 既存の場所へ紐付ける。名前は編集しない（その場所のものを使う）。
        is ManualStopOrigin.ExistingPlace -> {
          Text(origin.name ?: "登録済みの場所", style = MaterialTheme.typography.bodyLarge)
          Text(
            text = "登録済みの場所に紐付けます（新しい場所は作りません）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // 「今ここ」は地点を指していないので、近くの施設から選ばせる。
        ManualStopOrigin.CurrentLocation -> CandidatePicker(
          candidates = candidates,
          selected = selected,
          onSelect = {
            selected = it
            name = ""
          },
          name = name,
          onNameChange = {
            name = it
            if (it.isNotEmpty()) selected = null
          },
        )

        // POI・空き地点は施設が確定済み（または名前なし）なので候補は出さない。
        else -> OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名前（任意）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (hasRange) {
        StopRangeEditor(
          arrivalTime = arrival,
          departureTime = departure,
          arrivalIdx = arrivalIdx,
          departureIdx = departureIdx,
          lastIdx = lastIdx,
          onRangeChange = { s, e ->
            arrivalIdx = s
            departureIdx = e
          },
        )
      } else {
        Text(
          text = "この地点付近（滞在時間は軌跡から推定）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(top = 8.dp, bottom = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
      Button(
        onClick = {
          val picked = selected
          val finalName = picked?.name ?: name.trim().ifBlank { null }
          // 名前を POI 名から変えたら googlePlaceId は使わない（別名で解決記録を焼き込まない）。
          val googleId = when (origin) {
            is ManualStopOrigin.Poi -> origin.googlePlaceId?.takeIf { finalName == origin.name }
            ManualStopOrigin.CurrentLocation -> picked?.googlePlaceId
            else -> null
          }
          // 候補を選んだときはその施設の座標を使う（他経路と揃える）。
          val lat = picked?.latitude ?: latitude
          val lng = picked?.longitude ?: longitude
          onConfirm(ManualStopInput(lat, lng, arrival, departure, finalName, googleId))
        },
        modifier = Modifier.weight(1f),
      ) {
        Text(
          when {
            origin is ManualStopOrigin.ExistingPlace -> "この場所に追加"
            selected == null && name.isBlank() -> "名前なしで追加"
            else -> "追加"
          },
        )
      }
    }
  }
}

/** 近くの施設候補から選ぶ（「今ここ」用）。自分で入力もできる。 */
@Composable
private fun CandidatePicker(
  candidates: List<PlaceSearchResult>?,
  selected: PlaceSearchResult?,
  onSelect: (PlaceSearchResult) -> Unit,
  name: String,
  onNameChange: (String) -> Unit,
) {
  Text("名前", style = MaterialTheme.typography.labelLarge)
  when (candidates) {
    null -> Row(verticalAlignment = Alignment.CenterVertically) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      Spacer(modifier = Modifier.width(8.dp))
      Text("近くの候補を検索中…", style = MaterialTheme.typography.bodySmall)
    }

    else -> {
      if (candidates.isEmpty()) {
        Text(
          text = "近くの候補は見つかりませんでした（名前を入力するか、名前なしで追加できます）。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        candidates.forEach { poi ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelect(poi) },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = selected?.googlePlaceId == poi.googlePlaceId,
              onClick = { onSelect(poi) },
            )
            Column(modifier = Modifier.padding(start = 4.dp)) {
              Text(poi.name ?: "（名称不明）", style = MaterialTheme.typography.bodyMedium)
              poi.category?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        singleLine = true,
        label = { Text("自分で入力（任意）") },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
