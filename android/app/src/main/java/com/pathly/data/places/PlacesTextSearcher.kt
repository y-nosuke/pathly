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
import com.pathly.domain.model.PlaceCategory
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
      logger.w("fetchPlace failed for $placeId", e)
      null
    } finally {
      // セッション終了（次回は新しいトークン）。
      sessionToken = null
    }
  }

  /**
   * TODO(v13-backfill): DB v13 の移行用。流し終えたら [com.pathly.data.work.PlaceCategoryBackfillWorker]
   * ごと削除する。
   *
   * 既知の [googlePlaceId] の**業種だけ**を引く。v13 より前に解決した場所は業種を持っていないが、
   * 自動の再取得は「place 1 件 1 回」（adr/0014）で止まるため、移行時に一度だけここから埋める。
   * 検索セッションの一部ではないのでトークンは付けず、[lastFetch] の控えも触らない。
   *
   * 「業種を持たない施設だった」（成功して null）と「引けなかった」（失敗）は呼び出し側で
   * 区別する必要があるため [Result] で返す。前者を失敗と扱うと再試行が永久に止まらない。
   */
  suspend fun fetchCategoryOnly(googlePlaceId: String): Result<PlaceCategory?> = withContext(Dispatchers.IO) {
    if (!isOnline()) return@withContext Result.failure(IllegalStateException("offline"))
    val placesClient = client ?: return@withContext Result.failure(IllegalStateException("Places not initialized"))
    try {
      val fields = listOf(Place.Field.ID, Place.Field.PRIMARY_TYPE, Place.Field.PRIMARY_TYPE_DISPLAY_NAME)
      val response = Tasks.await(placesClient.fetchPlace(FetchPlaceRequest.builder(googlePlaceId, fields).build()))
      Result.success(response.place.toPlaceCategory())
    } catch (e: Exception) {
      logger.w("fetchPlace (category only) failed for $googlePlaceId", e)
      Result.failure(e)
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
