package com.pathly.data.places

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.pathly.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 座標から最寄りの施設名を取得する（Places SDK for Android / New）。
 * 失敗（未初期化・オフライン・0件・例外）は null を返し、呼び出し側でフォールバックする。
 * 詳細は docs/designs/places-and-stops.md を参照。
 */
@Singleton
class PlacesNameResolver @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val logger = Logger("PlacesNameResolver")

  data class Result(val name: String?, val address: String?)

  private val client: PlacesClient? by lazy {
    try {
      if (Places.isInitialized()) Places.createClient(context) else null
    } catch (e: Exception) {
      logger.w("Failed to create PlacesClient", e)
      null
    }
  }

  suspend fun resolve(latitude: Double, longitude: Double): Result? = withContext(Dispatchers.IO) {
    val placesClient = client ?: return@withContext null
    try {
      val circle = CircularBounds.newInstance(LatLng(latitude, longitude), SEARCH_RADIUS_METERS)
      val fields = listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS)
      val request = SearchNearbyRequest.builder(circle, fields)
        .setMaxResultCount(1)
        .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
        .build()

      val response = Tasks.await(placesClient.searchNearby(request))
      val place = response.places.firstOrNull() ?: return@withContext null
      val name = place.displayName?.takeIf { it.isNotBlank() }
      val address = place.formattedAddress?.takeIf { it.isNotBlank() }
      if (name == null && address == null) null else Result(name, address)
    } catch (e: Exception) {
      logger.w("searchNearby failed for ($latitude, $longitude)", e)
      null
    }
  }

  companion object {
    private const val SEARCH_RADIUS_METERS = 50.0
  }
}
