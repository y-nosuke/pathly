package com.pathly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.pathly.R
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop
import com.pathly.ui.theme.TrackLineOrange
import com.pathly.util.DateFormatters
import java.util.Date

// 記録画面と履歴詳細で共通の「経路の地図描画」。カメラや周辺のUIは各画面が持ち、
// GoogleMap の中身（帯・軌跡・マーカー）だけをここで一元化して見た目を揃える。

// マーカーのバッジ色（出発=緑/終了=赤/記録中の現在地=青/立ち寄り=紫/立ち寄り中=ティール）。
internal val MarkerStartGreen = Color(0xFF2E9E5B)
internal val MarkerEndRed = Color(0xFFD24B45)
internal val MarkerActiveBlue = Color(0xFF3B82F6)
internal val MarkerStopViolet = Color(0xFF6A4BBC)
internal val MarkerCurrentStopTeal = Color(0xFF00BFA5)

// 確定した立ち寄りの滞在区間ハイライト（濃い青の帯）。軌跡（オレンジ）の下に太く敷く。
internal val StopSegmentColor = Color(0xF00D47A1)

// 立ち寄り中（ライブ）の滞在区間ハイライト。確定分（青）と見分けるティール。
internal val CurrentStopSegmentColor = Color(0xF000BFA5)

/** 立ち寄りの到着〜出発に対応する軌跡点（滞在区間）。時刻で範囲を切り出す（両端含む）。 */
internal fun stopSegmentPoints(points: List<GpsPoint>, arrival: Date, departure: Date): List<GpsPoint> = points.filter { !it.timestamp.before(arrival) && !it.timestamp.after(departure) }

/** 地図の丸バッジ型マーカー（白フチ＋中央にグリフ/番号）。出発・到着・立ち寄りで共用。 */
@Composable
internal fun RouteBadgeMarker(bg: Color, content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .size(34.dp)
      .background(bg, CircleShape)
      .border(2.dp, Color.White, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/**
 * 経路の共通描画（GoogleMap の中身）。呼び出しは GoogleMap の content 内で行う。
 *
 * 描画順（下から）: 確定立ち寄りの帯 → 立ち寄り中の帯 → 軌跡ポリライン →
 * 開始/終了マーカー → 立ち寄りマーカー（番号）→ 立ち寄り中マーカー（滞在時間ラベル付き）。
 *
 * @param showEndMarker 終了/現在地マーカーを出すか。記録画面は端末の現在地ドットがあるので false。
 */
@Composable
@GoogleMapComposable
@OptIn(MapsComposeExperimentalApi::class)
internal fun RouteMapContent(
  track: GpsTrack,
  displayPoints: List<GpsPoint>,
  stops: List<Stop> = emptyList(),
  stopSegments: List<List<GpsPoint>> = emptyList(),
  currentStop: Stop? = null,
  currentStopSegment: List<GpsPoint> = emptyList(),
  showEndMarker: Boolean = true,
  onStopClick: (Stop) -> Unit = {},
) {
  // 確定した立ち寄りの滞在区間（軌跡の下に濃い青の帯）。
  stopSegments.forEach { segment ->
    if (segment.size >= 2) {
      Polyline(
        points = segment.map { LatLng(it.latitude, it.longitude) },
        color = StopSegmentColor,
        width = 26f,
      )
    }
  }

  // 立ち寄り中の滞在区間（ティールの帯）。確定分と見分ける。
  if (currentStopSegment.size >= 2) {
    Polyline(
      points = currentStopSegment.map { LatLng(it.latitude, it.longitude) },
      color = CurrentStopSegmentColor,
      width = 26f,
    )
  }

  if (displayPoints.size >= 2) {
    Polyline(
      points = displayPoints.map { LatLng(it.latitude, it.longitude) },
      color = TrackLineOrange,
      width = 6f,
    )

    // 出発は緑の ▶ グリフ（色だけでなく形でも「開始」と分かる）。
    val startPoint = displayPoints.first()
    val startMarkerState = androidx.compose.runtime.remember(startPoint) {
      MarkerState(position = LatLng(startPoint.latitude, startPoint.longitude))
    }
    MarkerComposable(
      "start",
      startPoint.latitude,
      startPoint.longitude,
      state = startMarkerState,
      title = "開始",
      snippet = "記録開始地点 - ${DateFormatters.TIME_FORMAT.format(track.startTime)}",
    ) {
      RouteBadgeMarker(bg = MarkerStartGreen) {
        Icon(
          painter = painterResource(R.drawable.ic_play_arrow),
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(20.dp),
        )
      }
    }

    if (showEndMarker) {
      val endPoint = displayPoints.last()
      val endMarkerState = androidx.compose.runtime.remember(endPoint) {
        MarkerState(position = LatLng(endPoint.latitude, endPoint.longitude))
      }
      // 到着は赤の ■。記録中は青＋白丸（現在地）。
      MarkerComposable(
        "end",
        endPoint.latitude,
        endPoint.longitude,
        track.isActive,
        state = endMarkerState,
        title = if (track.isActive) "現在地" else "終了",
        snippet = track.endTime?.let {
          "記録終了地点 - ${DateFormatters.TIME_FORMAT.format(it)}"
        } ?: "記録中の最新地点",
      ) {
        if (track.isActive) {
          RouteBadgeMarker(bg = MarkerActiveBlue) {
            Box(modifier = Modifier.size(12.dp).background(Color.White, CircleShape))
          }
        } else {
          RouteBadgeMarker(bg = MarkerEndRed) {
            Icon(
              painter = painterResource(R.drawable.ic_stop),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }

  // 立ち寄り場所（訪問順の番号つき紫バッジ）。番号でルートの順序が読める。
  stops.forEachIndexed { index, stop ->
    val stopMarkerState = androidx.compose.runtime.remember(stop.id, stop.place.latitude, stop.place.longitude) {
      MarkerState(position = LatLng(stop.place.latitude, stop.place.longitude))
    }
    MarkerComposable(
      "stop",
      stop.id,
      index,
      state = stopMarkerState,
      title = stop.place.name ?: stop.place.googleName ?: "立ち寄り",
      snippet = "${DateFormatters.SHORT_TIME_FORMAT.format(stop.arrivalTime)} ・ 滞在${stop.durationMinutes}分",
      onClick = {
        onStopClick(stop)
        false
      },
    ) {
      RouteBadgeMarker(bg = MarkerStopViolet) {
        Text(
          text = "${index + 1}",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
        )
      }
    }
  }

  // 立ち寄り中（ライブ）: ティールのバッジ＋滞在時間ラベルを常時表示（緯度経度はタップで）。
  currentStop?.let { stop ->
    val liveMarkerState = androidx.compose.runtime.remember(stop.place.latitude, stop.place.longitude) {
      MarkerState(position = LatLng(stop.place.latitude, stop.place.longitude))
    }
    MarkerComposable(
      "current-stop",
      stop.place.latitude,
      stop.place.longitude,
      stop.durationMinutes,
      state = liveMarkerState,
      title = stop.place.name ?: stop.place.googleName ?: "立ち寄り中",
      snippet = "%.5f, %.5f ・ 滞在%d分".format(stop.place.latitude, stop.place.longitude, stop.durationMinutes),
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RouteBadgeMarker(bg = MarkerCurrentStopTeal) {
          Box(modifier = Modifier.size(12.dp).background(Color.White, CircleShape))
        }
        Box(
          modifier = Modifier
            .padding(top = 2.dp)
            .background(MarkerCurrentStopTeal, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
          Text(
            text = "滞在${stop.durationMinutes}分",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
          )
        }
      }
    }
  }
}
