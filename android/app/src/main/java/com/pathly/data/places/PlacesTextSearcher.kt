package com.pathly.data.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
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

  /** キーワードの候補（Autocomplete）を返す。オフライン・未初期化・失敗時は空リスト。 */
  suspend fun predict(query: String): List<PlacePrediction> = withContext(Dispatchers.IO) {
    if (query.isBlank() || !isOnline()) return@withContext emptyList()
    val placesClient = client ?: return@withContext emptyList()
    val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
    try {
      val request = FindAutocompletePredictionsRequest.builder()
        .setQuery(query)
        .setSessionToken(token)
        .build()
      val response = Tasks.await(placesClient.findAutocompletePredictions(request))
      response.autocompletePredictions.map {
        PlacePrediction(
          placeId = it.placeId,
          primaryText = it.getPrimaryText(null).toString(),
          secondaryText = it.getSecondaryText(null).toString(),
        )
      }
    } catch (e: Exception) {
      logger.w("findAutocompletePredictions failed for \"$query\"", e)
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
      val fields = listOf(
        Place.Field.ID,
        Place.Field.DISPLAY_NAME,
        Place.Field.FORMATTED_ADDRESS,
        Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
        Place.Field.LOCATION,
      )
      val builder = FetchPlaceRequest.builder(placeId, fields)
      if (token != null) builder.setSessionToken(token)
      val response = Tasks.await(placesClient.fetchPlace(builder.build()))
      val place = response.place
      val latLng = place.location ?: return@withContext null
      PlaceSearchResult(
        googlePlaceId = place.id?.takeIf { it.isNotBlank() } ?: placeId,
        name = place.displayName?.takeIf { it.isNotBlank() },
        address = place.formattedAddress?.takeIf { it.isNotBlank() },
        category = place.primaryTypeDisplayName?.takeIf { it.isNotBlank() },
        latitude = latLng.latitude,
        longitude = latLng.longitude,
      ).also { lastFetch = placeId to it }
    } catch (e: Exception) {
      logger.w("fetchPlace failed for $placeId", e)
      null
    } finally {
      // セッション終了（次回は新しいトークン）。
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
