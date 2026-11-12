package com.pathly.data.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceTypes
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * キーワードから場所を検索する（Places SDK for Android / New）。
 * Autocomplete（候補）→ fetchPlace（確定）をセッショントークンでまとめて課金を最小化する。
 * 詳細は docs/designs/wishlist.md を参照。
 *
 * 課金最小化: 1回の検索セッション（[startSession]〜[fetch]）で1トークンを共有し、
 * 候補入力は何度打っても、確定した1件だけ [fetch] する。オフライン時は叩かない。
 */
@Singleton
class PlacesTextSearcher @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val logger = Logger("PlacesTextSearcher")

  private val client: PlacesClient? by lazy {
    try {
      if (Places.isInitialized()) Places.createClient(context) else null
    } catch (e: Exception) {
      logger.w("Failed to create PlacesClient", e)
      null
    }
  }

  @Volatile
  private var sessionToken: AutocompleteSessionToken? = null

  // 直近の fetch 結果を1件だけ控える。同じ placeId を続けて引くとき（場所シートのプレビュー表示 →
  // 同じ場所の登録）に Places を二度叩かないための最小キャッシュ。
  @Volatile
  private var lastFetch: Pair<String, PlaceSearchResult>? = null

  /** 検索画面を開いたときに呼ぶ。新しいセッショントークンを用意する。 */
  fun startSession() {
    sessionToken = AutocompleteSessionToken.newInstance()
  }

  /**
   * キーワードの候補（Autocomplete）を返す。オフライン・未初期化・失敗時は空リスト。
   * [locationBias] を渡すと、その範囲の中の候補を優先する（範囲の外も出る。料金は変わらない）。
   * [regionsOnly] なら地域（国・都道府県・市区町村・郵便番号など）だけに絞る（記録画面の「地域へ移動」）。
   */
  suspend fun predict(
    query: String,
    locationBias: GeoBounds? = null,
    regionsOnly: Boolean = false,
  ): List<PlacePrediction> = withContext(Dispatchers.IO) {
    if (query.isBlank() || !isOnline()) return@withContext emptyList()
    val placesClient = client ?: return@withContext emptyList()
    val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
    try {
      val builder = FindAutocompletePredictionsRequest.builder()
        .setQuery(query)
        .setSessionToken(token)
      // 東経 180 度をまたぐ範囲は南西・北東の 2 点で表せないので、寄せずに探す。
      locationBias?.takeUnless { it.crossesAntimeridian }?.let {
        builder.setLocationBias(RectangularBounds.newInstance(LatLng(it.south, it.west), LatLng(it.north, it.east)))
      }
      if (regionsOnly) builder.setTypesFilter(listOf(PlaceTypes.REGIONS))
      val request = builder.build()
      val response = Tasks.await(placesClient.findAutocompletePredictions(request))
      response.autocompletePredictions.map {
        PlacePrediction(
          placeId = it.placeId,
          primaryText = it.getPrimaryText(null).toString(),
          secondaryText = it.getSecondaryText(null).toString(),
        )
      }
    } catch (e: Exception) {
      // 検索語は施設名そのものになりやすいので出さない（→ designs/logging.md）。
      logger.w("findAutocompletePredictions failed (query length=${query.length})", e)
      emptyList()
    }
  }

  /** 候補を確定して座標つきの結果を得る。セッションはここで終了する（トークンを破棄）。 */
  suspend fun fetch(placeId: String): PlaceSearchResult? = withContext(Dispatchers.IO) {
    // 直近と同じ placeId なら控えを返す（Places を二度叩かない）。
    lastFetch?.let { if (it.first == placeId) return@withContext it.second }
    if (!isOnline()) return@withContext null
    val placesClient = client ?: return@withContext null
    val token = sessionToken
    try {
      val builder = FetchPlaceRequest.builder(placeId, PLACE_DETAIL_FIELDS)
      if (token != null) builder.setSessionToken(token)
      val response = Tasks.await(placesClient.fetchPlace(builder.build()))
      val place = response.place
      val latLng = place.location ?: return@withContext null
      PlaceSearchResult(
        googlePlaceId = place.id?.takeIf { it.isNotBlank() } ?: placeId,
        name = place.displayName?.takeIf { it.isNotBlank() },
        address = place.formattedAddress?.takeIf { it.isNotBlank() },
        category = place.toPlaceCategory(),
        latitude = latLng.latitude,
        longitude = latLng.longitude,
      ).also { lastFetch = placeId to it }
    } catch (e: Exception) {
      logger.w("fetchPlace failed", e)
      null
    } finally {
      // セッション終了（次回は新しいトークン）。
      sessionToken = null
    }
  }

  /**
   * 地域の候補を確定して、その地域の範囲（viewport）を得る。地図を移すだけなので、引くのは範囲と位置だけ
   * （施設の名前・種類は引かない＝Place Details の安い区分に収まる）。セッションはここで終了する。
   * 範囲が返らないときは、位置のまわりの決まった広さにする。オフライン・失敗時は null。
   */
  suspend fun fetchRegion(placeId: String): GeoBounds? = withContext(Dispatchers.IO) {
    if (!isOnline()) return@withContext null
    val placesClient = client ?: return@withContext null
    val token = sessionToken
    try {
      val builder = FetchPlaceRequest.builder(placeId, REGION_FIELDS)
      if (token != null) builder.setSessionToken(token)
      val place = Tasks.await(placesClient.fetchPlace(builder.build())).place
      place.viewport?.let { v ->
        GeoBounds(v.southwest.latitude, v.southwest.longitude, v.northeast.latitude, v.northeast.longitude)
      } ?: place.location?.let {
        GeoBounds(
          it.latitude - REGION_FALLBACK_HALF_SPAN_DEG,
          it.longitude - REGION_FALLBACK_HALF_SPAN_DEG,
          it.latitude + REGION_FALLBACK_HALF_SPAN_DEG,
          it.longitude + REGION_FALLBACK_HALF_SPAN_DEG,
        )
      }
    } catch (e: Exception) {
      logger.w("fetchPlace (region) failed", e)
      null
    } finally {
      sessionToken = null
    }
  }

  private fun isOnline(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }
}

private val REGION_FIELDS = listOf(Place.Field.ID, Place.Field.LOCATION, Place.Field.VIEWPORT)

// 範囲が返らない地域で、位置のまわりに取る広さ（緯度・経度の半幅。約 2km 四方）。
private const val REGION_FALLBACK_HALF_SPAN_DEG = 0.01
