package com.pathly.data.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 座標から最寄りの施設名を取得する（Places SDK for Android / New）。
 * 詳細は docs/designs/places-and-stops.md を参照。
 *
 * 結果は [Outcome] で 3 通りを区別する:
 *  - [Outcome.Found]        … POI が見つかった（places 更新＋解決ログに googlePlaceId）
 *  - [Outcome.NoMatch]      … オンラインで叩いたが POI 無し（解決ログに null 行＝自動では二度と叩かない）
 *  - [Outcome.NotAttempted] … 未実施（オフライン・未初期化・例外）。解決ログを残さず後でキャッチアップ
 */
@Singleton
class PlacesNameResolver @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val logger = Logger("PlacesNameResolver")

  sealed interface Outcome {
    data class Found(
      val name: String?,
      val address: String?,
      val category: String?,
      val googlePlaceId: String,
    ) : Outcome

    data object NoMatch : Outcome

    data object NotAttempted : Outcome
  }

  private val client: PlacesClient? by lazy {
    try {
      if (Places.isInitialized()) Places.createClient(context) else null
    } catch (e: Exception) {
      logger.w("Failed to create PlacesClient", e)
      null
    }
  }

  suspend fun resolve(latitude: Double, longitude: Double): Outcome = withContext(Dispatchers.IO) {
    if (!isOnline()) return@withContext Outcome.NotAttempted
    val placesClient = client ?: return@withContext Outcome.NotAttempted
    try {
      val circle = CircularBounds.newInstance(LatLng(latitude, longitude), SEARCH_RADIUS_METERS)
      val fields = listOf(
        Place.Field.ID,
        Place.Field.DISPLAY_NAME,
        Place.Field.FORMATTED_ADDRESS,
        Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
      )
      val request = SearchNearbyRequest.builder(circle, fields)
        .setMaxResultCount(1)
        .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
        .build()

      val response = Tasks.await(placesClient.searchNearby(request))
      val place = response.places.firstOrNull() ?: return@withContext Outcome.NoMatch
      val googlePlaceId = place.id?.takeIf { it.isNotBlank() } ?: return@withContext Outcome.NoMatch
      val name = place.displayName?.takeIf { it.isNotBlank() }
      val address = place.formattedAddress?.takeIf { it.isNotBlank() }
      val category = place.primaryTypeDisplayName?.takeIf { it.isNotBlank() }
      Outcome.Found(name, address, category, googlePlaceId)
    } catch (e: Exception) {
      logger.w("searchNearby failed for ($latitude, $longitude)", e)
      Outcome.NotAttempted
    }
  }

  /**
   * 座標の近くの POI 候補を**複数**返す（距離が近い順）。手動追加で「候補から選ぶ」ために使う。
   * 最寄り1件だけの [resolve] と違い、隣接する別の店（例: 富士そば/日高屋）を取り違えないよう
   * ユーザーに選ばせる。オフライン・失敗時は空リスト。
   */
  suspend fun searchNearbyCandidates(
    latitude: Double,
    longitude: Double,
    maxResults: Int = 6,
  ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
    if (!isOnline()) return@withContext emptyList()
    val placesClient = client ?: return@withContext emptyList()
    try {
      val circle = CircularBounds.newInstance(LatLng(latitude, longitude), CANDIDATE_RADIUS_METERS)
      val fields = listOf(
        Place.Field.ID,
        Place.Field.DISPLAY_NAME,
        Place.Field.FORMATTED_ADDRESS,
        Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
        Place.Field.LOCATION,
      )
      val request = SearchNearbyRequest.builder(circle, fields)
        .setMaxResultCount(maxResults.coerceIn(1, 20))
        .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
        .build()

      val response = Tasks.await(placesClient.searchNearby(request))
      response.places.mapNotNull { place ->
        val id = place.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val loc = place.location
        PlaceSearchResult(
          googlePlaceId = id,
          name = place.displayName?.takeIf { it.isNotBlank() },
          address = place.formattedAddress?.takeIf { it.isNotBlank() },
          category = place.primaryTypeDisplayName?.takeIf { it.isNotBlank() },
          latitude = loc?.latitude ?: latitude,
          longitude = loc?.longitude ?: longitude,
        )
      }
    } catch (e: Exception) {
      logger.w("searchNearby candidates failed for ($latitude, $longitude)", e)
      emptyList()
    }
  }

  /** インターネット接続が有効か。オフラインなら Places を叩かず [Outcome.NotAttempted] にする。 */
  private fun isOnline(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }

  companion object {
    private const val SEARCH_RADIUS_METERS = 50.0

    // 候補検索は取り違えを避けるため少し広めに取り、複数件から選ばせる。
    private const val CANDIDATE_RADIUS_METERS = 80.0
  }
}
