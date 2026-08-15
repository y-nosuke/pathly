package com.pathly.presentation.common

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.draw.shadow
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
import com.pathly.domain.model.PlaceCategoryGroup
import com.pathly.domain.model.RegisteredPlace
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

// 「登録済みの場所」マーカー（トグルON時）。色で訪問状態（訪問済み=グリーン／未訪問=グレー）を示し、
// グリフで業種（カフェ・公園・駅…）を示す。立ち寄り（紫の番号）とは色・グリフで見分ける。
internal val MarkerVisitedGreen = Color(0xFF2E7D32)
internal val MarkerUnvisitedGray = Color(0xFF6B6B6B)

// 「行きたい」を示す小さな旗のバッジ。訪問状態（緑/グレー）とは別の軸なので、色も別に取る。
internal val MarkerWishlistAmber = Color(0xFFE08A00)

/**
 * 業種のグリフ。何の場所かを一目で分かるようにする（→ adr/0017）。
 *
 * 対応付けを DB ではなくここに置いているのは、`R.drawable` の ID は DB に入れられず、結局
 * コードで分岐することになるため。テーブルにすると変えるたびにマイグレーションが要るだけになる。
 */
@DrawableRes
internal fun categoryGlyph(group: PlaceCategoryGroup): Int = when (group) {
  PlaceCategoryGroup.FOOD -> R.drawable.ic_category_food
  PlaceCategoryGroup.CAFE -> R.drawable.ic_category_cafe
  PlaceCategoryGroup.SHOPPING -> R.drawable.ic_category_shopping
  PlaceCategoryGroup.PARK -> R.drawable.ic_category_park
  PlaceCategoryGroup.CULTURE -> R.drawable.ic_category_culture
  PlaceCategoryGroup.ENTERTAINMENT -> R.drawable.ic_category_entertainment
  PlaceCategoryGroup.TRANSIT -> R.drawable.ic_category_transit
  PlaceCategoryGroup.LODGING -> R.drawable.ic_category_lodging
  PlaceCategoryGroup.SERVICE -> R.drawable.ic_category_service
  // 業種が分からない場所は、これまでどおりの場所ピン。
  PlaceCategoryGroup.OTHER -> R.drawable.ic_place
}

// 確定した立ち寄りの滞在区間ハイライト（濃い青の帯）。軌跡（オレンジ）の下に太く敷く。
internal val StopSegmentColor = Color(0xF00D47A1)

// 立ち寄り中（ライブ）の滞在区間ハイライト。確定分（青）と見分けるティール。
internal val CurrentStopSegmentColor = Color(0xF000BFA5)

/** 立ち寄りの到着〜出発に対応する軌跡点（滞在区間）。時刻で範囲を切り出す（両端含む）。 */
internal fun stopSegmentPoints(points: List<GpsPoint>, arrival: Date, departure: Date): List<GpsPoint> = points.filter { !it.timestamp.before(arrival) && !it.timestamp.after(departure) }

/**
 * 「登録済みの場所」マーカー群（アンバーのピン）。トラックの有無に依らず単独で描画できるよう
 * 切り出し、記録画面（トラック未確定でも表示）・場所詳細でも使い回す。GoogleMap の content 内で呼ぶ。
 */
@Composable
@GoogleMapComposable
@OptIn(MapsComposeExperimentalApi::class)
internal fun RegisteredPlaceMarkers(
  registeredPlaces: List<RegisteredPlace>,
  onRegisteredPlaceClick: (RegisteredPlace) -> Unit = {},
) {
  registeredPlaces.forEach { place ->
    val placeMarkerState = androidx.compose.runtime.remember(place.placeId, place.latitude, place.longitude) {
      MarkerState(position = LatLng(place.latitude, place.longitude))
    }
    // 色＝訪問状態、グリフ＝業種、右上の旗＝行きたい。状態が変わったら描き直す。
    val badgeColor = if (place.isVisited) MarkerVisitedGreen else MarkerUnvisitedGray
    val group = place.categoryGroup
    MarkerComposable(
      "registered",
      place.placeId,
      place.isWishlisted,
      place.isVisited,
      group,
      state = placeMarkerState,
      title = place.displayName,
      snippet = place.statusLabel,
      onClick = {
        onRegisteredPlaceClick(place)
        false
      },
    ) {
      RegisteredPlaceMarker(bg = badgeColor, glyph = categoryGlyph(group), wishlisted = place.isWishlisted)
    }
  }
}

/**
 * 登録済みの場所のマーカー。丸バッジ（色＝訪問状態／グリフ＝業種）に、行きたい登録があれば
 * 右上へ小さな旗を重ねる。
 *
 * 業種をグリフに使うようになって、旗をグリフで示せなくなったため別の場所へ逃がした。
 * 余白を左右対称に取り、下端をバッジの下端に揃えているのは、**マーカーの基準点（下端中央）を
 * ずらさない**ため。ここを崩すとピンが指す座標が実際の場所からずれる。
 */
@Composable
internal fun RegisteredPlaceMarker(bg: Color, @DrawableRes glyph: Int, wishlisted: Boolean) {
  if (!wishlisted) {
    RouteBadgeMarker(bg = bg) { MarkerGlyph(glyph) }
    return
  }
  Box(contentAlignment = Alignment.TopEnd) {
    Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
      RouteBadgeMarker(bg = bg) { MarkerGlyph(glyph) }
    }
    Box(
      modifier = Modifier
        .padding(end = 2.dp)
        .size(16.dp)
        .background(MarkerWishlistAmber, CircleShape)
        .border(1.5.dp, Color.White, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_flag_filled),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(9.dp),
      )
    }
  }
}

/** バッジの中央に置くグリフ（白・18dp）。 */
@Composable
private fun MarkerGlyph(@DrawableRes glyph: Int) {
  Icon(
    painter = painterResource(glyph),
    contentDescription = null,
    tint = Color.White,
    modifier = Modifier.size(18.dp),
  )
}

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
  // 「登録済みの場所」マーカー（トグルON時）。タップは②の紐付け用（既定は無反応）。
  registeredPlaces: List<RegisteredPlace> = emptyList(),
  onRegisteredPlaceClick: (RegisteredPlace) -> Unit = {},
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
      snippet = "記録開始地点 - ${DateFormatters.time(track.startTime)}",
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
          "記録終了地点 - ${DateFormatters.time(it)}"
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

  // 登録済みの場所（トグルON時）。立ち寄りマーカーの下に敷き、重なりでは立ち寄りを前面にする。
  RegisteredPlaceMarkers(registeredPlaces, onRegisteredPlaceClick)

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
      snippet = "${DateFormatters.shortTime(stop.arrivalTime)} ・ 滞在${stop.durationMinutes}分",
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
      // ラベルはバッジの「上」に出す。マーカーは既定で点が下端（バッジ）に来るので、
      // 端末の現在地ドット（青）や現在地マーカーと重ならず読める。白フチ＋影でコントラストも確保する。
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .shadow(3.dp, RoundedCornerShape(8.dp))
            .background(MarkerCurrentStopTeal, RoundedCornerShape(8.dp))
            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text(
            text = "滞在${stop.durationMinutes}分",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
          )
        }
        Box(modifier = Modifier.padding(top = 2.dp)) {
          RouteBadgeMarker(bg = MarkerCurrentStopTeal) {
            // 「今ここに滞在中」が一目で分かるよう、白の場所ピンのグリフにする（ただの白丸より識別しやすい）。
            Icon(
              painter = painterResource(R.drawable.ic_location_on),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}
