package com.pathly.presentation.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.google.android.gms.maps.model.LatLng
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.TrackSmoother
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.stopSegmentPoints
import com.pathly.presentation.places.PlaceSheetTarget
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget

/**
 * 経路詳細の地図（全面／edge-to-edge。シートは地図の上に重ねる）。いまのモード（[state]）に合わせて
 * 描くものとタップの意味を切り替える:
 * - 通常: 地図・POI のタップ＝場所シート、立ち寄りのピン＝[onStopSelected]、登録済みのピン＝その場で編集。
 * - 手動追加: どのタップも、その地点を立ち寄りを足す地点にする。
 * - 補正の調整・候補選択・手動追加・期間の編集の間は、立ち寄りの滞在区間の帯を出さない（そちらのハイライトと干渉するため）。
 *
 * @param candidates 再解析の候補（候補選択モードのときだけ渡す）。
 * @param bottomPadding シート・オーバーレイの裏に隠れないよう地図の下端を空ける高さ。
 */
@Composable
internal fun TrackDetailMapLayer(
  track: GpsTrack,
  stops: List<Stop>,
  currentStop: Stop?,
  candidates: List<StopCandidate>,
  mapPlaces: MapRegisteredPlaces,
  state: TrackDetailScreenState,
  infoWindow: MapInfoWindowState,
  bottomPadding: Dp,
  onStopSelected: (Stop) -> Unit,
  modifier: Modifier = Modifier,
) {
  val manualPoints = track.smoothedPoints
  val tuningMode = state.tuningMode
  val manualMode = state.manualMode
  val candidateMode = candidates.isNotEmpty()
  val durationEditMode = state.durationEditMode
  // 補正調整・候補選択・手動追加・期間の編集の間（通常表示でない間）。
  val specialMode = tuningMode || candidateMode || manualMode || durationEditMode

  // 地図に描く点列。調整モードではスライダーの値で補正する。
  val displayPoints = remember(track, tuningMode, state.tuningParams) {
    if (tuningMode) TrackSmoother.smooth(track.points, state.tuningParams) else track.smoothedPoints
  }
  // 立ち寄りの滞在区間（到着〜出発の軌跡点）。全件を軌跡の下に常時ハイライトする。
  val stopSegments = remember(track.smoothedPoints, stops, specialMode) {
    if (specialMode) {
      emptyList()
    } else {
      stops.mapNotNull { stop ->
        stopSegmentPoints(track.smoothedPoints, stop.arrivalTime, stop.departureTime).takeIf { it.size >= 2 }
      }
    }
  }
  // 立ち寄り中（ライブ）の滞在区間。通常表示のときだけ。
  val currentStopSegment = remember(track.smoothedPoints, currentStop, specialMode) {
    if (specialMode) {
      emptyList()
    } else {
      currentStop?.let { stopSegmentPoints(track.smoothedPoints, it.arrivalTime, it.departureTime) }.orEmpty()
    }
  }
  // 記録中の地図に出すライブ立ち寄り（通常表示のときだけ）。
  val liveCurrentStop = if (specialMode) null else currentStop

  // 手動追加・期間編集で選んでいる滞在区間（到着〜出発）の点列。地図に青くハイライトする。
  val editingRange = when {
    manualMode && state.manualPick != null -> state.manualRange
    durationEditMode -> state.durationRange
    else -> null
  }
  val rangeHighlight = if (editingRange != null && manualPoints.size >= 2) {
    val (start, end) = editingRange
    manualPoints.subList(
      start.coerceIn(0, manualPoints.lastIndex),
      (end + 1).coerceIn(1, manualPoints.size),
    )
  } else {
    emptyList()
  }

  TrackMapView(
    track = track,
    displayPoints = displayPoints,
    infoWindow = infoWindow,
    stops = stops,
    currentStop = liveCurrentStop,
    currentStopSegment = currentStopSegment,
    candidates = if (!manualMode) candidates else emptyList(),
    showRawOverlay = tuningMode,
    manualPickTarget = if (manualMode) state.manualPick?.let { LatLng(it.latitude, it.longitude) } else null,
    highlightPoints = rangeHighlight,
    stopSegments = stopSegments,
    // この経路の立ち寄り（確定＝紫番号／滞在中＝ティール）と同じ場所は登録済みピンを二重に出さない。
    registeredPlaces = if (mapPlaces.shown) {
      val stopPlaceIds = (stops.map { it.place.id } + listOfNotNull(liveCurrentStop?.place?.id)).toSet()
      // 地図の絞り込みに合う場所だけを出す（まとまり・ラベルも絞った後で作られる）。
      mapPlaces.places.filter { it.placeId !in stopPlaceIds && mapPlaces.filter.matches(it) }
    } else {
      emptyList()
    },
    focusTarget = state.focusTarget,
    focusNonce = state.focusNonce,
    fitNonce = state.fitNonce,
    // フォーカスがピンをシート/オーバーレイの裏に隠さないよう、下端を空ける。
    contentPadding = PaddingValues(bottom = bottomPadding),
    onPoiClick = { poi ->
      if (manualMode) {
        state.pickManual(ManualStopTarget(poi.latLng.latitude, poi.latLng.longitude, ManualStopOrigin.Poi(poi.name, poi.placeId)))
      } else {
        state.placeSheetTarget = PlaceSheetTarget.NewPoi(poi)
        state.focusOn(poi.latLng)
      }
    },
    onMapClick = { latLng ->
      if (manualMode) {
        state.pickManual(ManualStopTarget(latLng.latitude, latLng.longitude, ManualStopOrigin.MapPoint))
      } else {
        state.placeSheetTarget = PlaceSheetTarget.NewPoint(latLng)
        state.focusOn(latLng)
      }
    },
    // 地図の立ち寄りピンをタップ: 手動追加モード＝その場所にもう 1 件立ち寄りを足す（登録済みマーカーと同じ）／
    // 通常＝一覧の該当行を強調＆スクロール＋「選び直し」を出す（記録画面と統一）。
    onStopClick = { stop ->
      if (manualMode) {
        state.pickManual(
          ManualStopTarget(
            stop.place.latitude,
            stop.place.longitude,
            ManualStopOrigin.ExistingPlace(stop.place.id, stop.place.displayName),
          ),
        )
      } else {
        onStopSelected(stop)
      }
    },
    // 登録済みマーカーのタップ: 手動追加モード＝既存placeへ紐付け（滞在調整ありのオーバーレイ）／
    // 通常モード＝場所シートでその場編集（詳細も開ける）。
    onRegisteredPlaceClick = { place ->
      if (manualMode) {
        state.pickManual(
          ManualStopTarget(place.latitude, place.longitude, ManualStopOrigin.ExistingPlace(place.placeId, place.displayName)),
        )
      } else {
        state.placeSheetTarget = PlaceSheetTarget.Existing(place.placeId)
        state.focusOn(LatLng(place.latitude, place.longitude))
      }
    },
    modifier = modifier.fillMaxSize(),
  )
}

/** GPS の点が無い経路のときに地図の代わりに出す案内。 */
@Composable
internal fun NoGpsDataMessage(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "GPSデータがありません",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
