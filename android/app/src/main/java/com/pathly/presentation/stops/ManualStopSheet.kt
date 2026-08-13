package com.pathly.presentation.stops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

/**
 * 手動追加で確定した内容。名前は出どころで分ける（[com.pathly.domain.model.Place] の設計）。
 * [name] は自分で入力したぶんだけ（→ `places.name`）、施設から来た名前は [googleName]（→ `google_places.name`）。
 */
data class ManualStopInput(
  val latitude: Double,
  val longitude: Double,
  val arrivalTime: Date,
  val departureTime: Date,
  val name: String?,
  val googlePlaceId: String?,
  val googleName: String?,
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

  // 名前欄は「自分で付けた名前」専用なので、施設名は初期値に入れず薄字の候補として見せる
  // （入っているかどうかで、自分で付けたのか Google の名前なのかが分かる）。
  var name by remember(origin) { mutableStateOf("") }

  // Google 由来の名前（POI タップならその施設、「今ここ」なら選んだ候補）。
  val googleName = (selected?.name ?: (origin as? ManualStopOrigin.Poi)?.name)?.trim()?.ifBlank { null }

  FloatingSheet(state = sheetState, modifier = modifier) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 見出しは場所の名前。名前欄は空で始まるので、識別はここが担う。
      Text(
        text = when {
          origin is ManualStopOrigin.ExistingPlace -> origin.name ?: "登録済みの場所"
          googleName != null -> googleName
          else -> "立ち寄りを追加"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      when (origin) {
        // 既存の場所へ紐付ける。名前は編集しない（その場所のものを使う）。
        is ManualStopOrigin.ExistingPlace -> Text(
          text = "登録済みの場所に紐付けます（新しい場所は作りません）。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 「今ここ」は地点を指していないので、近くの施設から選ばせる。
        ManualStopOrigin.CurrentLocation -> CandidatePicker(
          candidates = candidates,
          selected = selected,
          onSelect = { selected = it },
          name = name,
          onNameChange = { name = it },
          googleName = googleName,
        )

        // POI・空き地点は施設が確定済み（または名前なし）なので候補は出さない。
        else -> UserNameField(
          name = name,
          onNameChange = { name = it },
          googleName = googleName,
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
          // 名前欄は空で始まるので、入力があれば自分で付けた名前。候補を書き換えずにそのまま
          // 確定したときは Google の名前と同じなので、ユーザー名としては残さない。
          // 施設との紐付け（googlePlaceId）は名前を変えても外さない。列が分かれているので
          // 「スタバだけど自分は休憩と呼ぶ」がそのまま表現でき、カテゴリ・住所も残る。
          val typed = name.trim().ifBlank { null }
          val userName = typed?.takeIf { it != googleName }
          val googleId = picked?.googlePlaceId ?: (origin as? ManualStopOrigin.Poi)?.googlePlaceId
          // 候補を選んだときはその施設の座標を使う（他経路と揃える）。
          val lat = picked?.latitude ?: latitude
          val lng = picked?.longitude ?: longitude
          onConfirm(ManualStopInput(lat, lng, arrival, departure, userName, googleId, googleName))
        },
        modifier = Modifier.weight(1f),
      ) {
        Text(
          when {
            origin is ManualStopOrigin.ExistingPlace -> "この場所に追加"
            // 名前欄が空でも、施設の名前が使われるなら「名前なし」ではない。
            googleName == null && name.isBlank() -> "名前なしで追加"
            else -> "追加"
          },
        )
      }
    }
  }
}

/**
 * 「自分で付ける名前」の入力欄。名前欄は `places.name`（自分で付けた名前）専用なので、
 * Google 由来の名前は初期値に入れず薄字で見せ、欄が空のときだけ書き換えの導線を出す
 * （施設名を少し変えたいときに打ち直さずに済むように）。
 */
@Composable
private fun UserNameField(
  name: String,
  onNameChange: (String) -> Unit,
  googleName: String?,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    OutlinedTextField(
      value = name,
      onValueChange = onNameChange,
      label = { Text("自分で付ける名前（任意）") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    // 名前は見出しに出ているので、ここでは繰り返さない（1行に収める）。
    googleName?.takeIf { name.isEmpty() }?.let { candidate ->
      TextButton(
        onClick = { onNameChange(candidate) },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
      ) {
        Text("この名前から書き換える", style = MaterialTheme.typography.labelLarge, maxLines = 1)
      }
    }
  }
}

/** 近くの施設候補から選ぶ（「今ここ」用）。選んだうえで自分の名前を付けることもできる。 */
@Composable
private fun CandidatePicker(
  candidates: List<PlaceSearchResult>?,
  selected: PlaceSearchResult?,
  onSelect: (PlaceSearchResult) -> Unit,
  name: String,
  onNameChange: (String) -> Unit,
  googleName: String?,
) {
  Text("場所", style = MaterialTheme.typography.labelLarge)
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
      UserNameField(name = name, onNameChange = onNameChange, googleName = googleName)
    }
  }
}
