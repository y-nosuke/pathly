package com.pathly.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.FeatureLayer
import com.google.android.gms.maps.model.FeatureLayerOptions
import com.google.android.gms.maps.model.FeatureStyle
import com.google.android.gms.maps.model.FeatureType
import com.google.android.gms.maps.model.PlaceFeature
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.pathly.BuildConfig
import com.pathly.ui.theme.PathlyMapColors
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

// 選んだ地域の境界（Maps SDK の境界のデータドリブン スタイル）。記録画面の「地域へ移動」と場所一覧の地図で使う。
// マップ ID（境界のレイヤーを有効にした明るいスタイル）を付けた地図にだけ描ける（→ ADR 0046）。

/** 境界を描く地図に付けるマップ ID。未設定なら null（マップ ID の無い地図になり、境界は描かない）。 */
private val boundaryMapId: String? = BuildConfig.GOOGLE_MAPS_MAP_ID.takeIf { it.isNotBlank() }

/** 境界を描く地図（記録画面・場所一覧・計画の詳細の地図）の作り方。マップ ID はこの地図にだけ付ける（ほかの地図は料金の区分が変わらないように）。 */
internal fun boundaryMapOptions(): GoogleMapOptions = GoogleMapOptions().apply { boundaryMapId?.let { mapId(it) } }

/**
 * 境界を描く地図の配色。境界は明るいスタイルにしか描けないので、描いている間（[showingBoundaries]）だけ明るくし、
 * ふだんは端末の明暗に合わせる。
 */
internal fun boundaryMapColorScheme(showingBoundaries: Boolean): ComposeMapColorScheme = if (showingBoundaries) ComposeMapColorScheme.LIGHT else ComposeMapColorScheme.FOLLOW_SYSTEM

/**
 * [placeIds] の地域の境界を、目印と同じ藍の実線と薄い塗りで描く（`GoogleMap` の中に置く）。
 * 使うレイヤーは国・行政区画レベル 1・レベル 2・地域区分（市区町村）。この地図で境界を描けるか（マップ ID があり、端末が
 * 対応し、スタイルでレイヤーが有効か）を [onAvailableChange] で知らせる（描けないときは何も描かず、呼び出し側は目印だけにする）。
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
internal fun RegionBoundaries(
  placeIds: Set<String>,
  onAvailableChange: (Boolean) -> Unit = {},
) {
  val strokeWidthPx = with(LocalDensity.current) { BoundaryStrokeWidth.toPx() }
  val currentOnAvailableChange by rememberUpdatedState(onAvailableChange)
  var layers by remember { mutableStateOf<List<FeatureLayer>>(emptyList()) }

  // 地図ができたら 1 回だけレイヤーを取る。端末の対応は後から変わることがある（レンダラーの準備が遅れる）ので見張り、
  // 地図が外れたら（画面を離れた・一覧と地図を行き来した）見張りを外す。
  MapEffect(Unit) { googleMap ->
    if (boundaryMapId == null) {
      currentOnAvailableChange(false)
      return@MapEffect
    }
    fun refresh() {
      val supported = googleMap.mapCapabilities.isDataDrivenStylingAvailable
      layers = if (supported) BoundaryFeatureTypes.map { googleMap.getFeatureLayer(FeatureLayerOptions.Builder().featureType(it).build()) } else emptyList()
      currentOnAvailableChange(layers.any { it.isAvailable })
    }
    val listener = GoogleMap.OnMapCapabilitiesChangedListener { refresh() }
    googleMap.addOnMapCapabilitiesChangedListener(listener)
    try {
      refresh()
      awaitCancellation()
    } finally {
      googleMap.removeOnMapCapabilitiesChangedListener(listener)
    }
  }

  // 選んだ地域が変わるたびに、塗る地物を選び直す（地域でない地物・選んでいない地域は塗らない）。
  // 塗りは小さい段ほど濃くする。入れ子（県と市など）を両方出すと、中の地域が重なってはっきり濃く見える。
  LaunchedEffect(layers, placeIds, strokeWidthPx) {
    fun apply() = layers.forEach { layer ->
      val style = FeatureStyle.Builder()
        .strokeColor(PathlyMapColors.Region.toArgb())
        .strokeWidth(strokeWidthPx)
        .fillColor(PathlyMapColors.Region.copy(alpha = boundaryFillAlpha(layer.featureType)).toArgb())
        .build()
      layer.setFeatureStyle { feature ->
        if (feature is PlaceFeature && feature.placeId in placeIds) style else null
      }
    }
    apply()
    // ダークモードでは、境界を出すと同時に地図を明るく切り替える（boundaryMapColorScheme）。切り替えで地図が描き直される
    // 途中にかけた塗り方は一部の区画にしか効かない（地図を動かすまで欠ける）ので、描き直しが落ち着いてからかけ直す。
    if (placeIds.isNotEmpty()) {
      delay(STYLE_REAPPLY_DELAY_MS)
      apply()
    }
  }
}

private val BoundaryFeatureTypes = listOf(
  FeatureType.COUNTRY,
  FeatureType.ADMINISTRATIVE_AREA_LEVEL_1,
  FeatureType.ADMINISTRATIVE_AREA_LEVEL_2,
  FeatureType.LOCALITY,
)

// 境界の線の太さ。
private val BoundaryStrokeWidth = 2.dp

/**
 * 境界の塗りの濃さ（藍の不透明度）。地図の文字が読めるよう薄くし、小さい段ほど濃くする
 * （県だけの所は 10%、県の中の市は重なって約 33%）。
 */
internal fun boundaryFillAlpha(featureType: String?): Float = when (featureType) {
  FeatureType.COUNTRY -> 0.06f
  FeatureType.ADMINISTRATIVE_AREA_LEVEL_1 -> 0.10f
  FeatureType.ADMINISTRATIVE_AREA_LEVEL_2 -> 0.18f
  else -> 0.26f
}

// 配色の切り替えで地図が描き直されるのを待つ時間（エミュレータでこの長さなら欠けなく描けた）。
private const val STYLE_REAPPLY_DELAY_MS = 1_500L
