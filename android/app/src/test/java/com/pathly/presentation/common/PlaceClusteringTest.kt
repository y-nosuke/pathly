package com.pathly.presentation.common

import com.pathly.domain.model.RegisteredPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceClusteringTest {

  // ズーム 15 の東京あたりでは、経度 0.001° ≒ 23dp（まとめる距離 44dp より近い）、0.01° ≒ 233dp。
  private fun place(id: Long, lngOffset: Double, latOffset: Double = 0.0, wishlisted: Boolean = false) = RegisteredPlace(
    placeId = id,
    name = "場所$id",
    latitude = 35.68 + latOffset,
    longitude = 139.76 + lngOffset,
    isWishlisted = wishlisted,
  )

  @Test
  fun nearPlaces_areClustered() {
    val entries = clusterPlaces(listOf(place(1, 0.0), place(2, 0.001)), zoom = 15f)

    val cluster = entries.single() as PlaceMapEntry.Cluster
    assertEquals(listOf(1L, 2L), cluster.places.map { it.placeId })
    assertEquals(139.7605, cluster.longitude, 1e-9)
  }

  // 縦（緯度）も画面上の距離で測る。北緯 35.68° のズーム 15 では、緯度 0.001° ≒ 28dp、0.003° ≒ 85dp。
  @Test
  fun latitudeDistance_isMeasuredOnScreen() {
    assertEquals(1, clusterPlaces(listOf(place(1, 0.0), place(2, 0.0, latOffset = 0.001)), zoom = 15f).size)
    assertEquals(2, clusterPlaces(listOf(place(1, 0.0), place(2, 0.0, latOffset = 0.003)), zoom = 15f).size)
  }

  @Test
  fun farPlaces_stayApart() {
    val entries = clusterPlaces(listOf(place(1, 0.0), place(2, 0.01)), zoom = 15f)

    assertEquals(2, entries.size)
    assertTrue(entries.all { it is PlaceMapEntry.Single })
  }

  // 同じ 2 件でも、寄れば離れる（経度 0.001° はズーム 17 で ≒ 93dp）。
  @Test
  fun zoomingIn_splitsTheCluster() {
    val places = listOf(place(1, 0.0), place(2, 0.001))

    assertEquals(1, clusterPlaces(places, zoom = 15f).size)
    assertEquals(2, clusterPlaces(places, zoom = 17f).size)
  }

  // 同じ座標の場所はどこまで寄っても離れないので、上限より寄ったらまとめない。
  @Test
  fun atMaxZoom_nothingIsClustered() {
    val entries = clusterPlaces(listOf(place(1, 0.0), place(2, 0.0)), zoom = MAX_CLUSTER_ZOOM)

    assertEquals(2, entries.size)
    assertTrue(entries.all { it is PlaceMapEntry.Single })
  }

  // 並びが変わっても結果は同じ（地図を動かしたり一覧を並べ替えたりしただけでまとまりが変わらない）。
  @Test
  fun result_doesNotDependOnInputOrder() {
    val places = listOf(place(1, 0.0), place(2, 0.001), place(3, 0.002), place(4, 0.01))

    assertEquals(clusterPlaces(places, zoom = 15f), clusterPlaces(places.reversed(), zoom = 15f))
  }

  @Test
  fun cluster_flagsWishlistedWhenAnyMemberIs() {
    val withWish = clusterPlaces(listOf(place(1, 0.0), place(2, 0.001, wishlisted = true)), zoom = 15f)
    val without = clusterPlaces(listOf(place(1, 0.0), place(2, 0.001)), zoom = 15f)

    assertTrue((withWish.single() as PlaceMapEntry.Cluster).hasWishlisted)
    assertFalse((without.single() as PlaceMapEntry.Cluster).hasWishlisted)
  }

  // 同じ座標の場所は上限のズームでも離れない。少し離れていれば上限の手前で分かれる
  // （経度 0.001° はズーム 17.5 で ≒ 131dp）。
  @Test
  fun cluster_isTooCloseToSplitOnlyWhenItStaysTogetherNearMaxZoom() {
    val samePoint = clusterPlaces(listOf(place(1, 0.0), place(2, 0.0)), zoom = 15f).single() as PlaceMapEntry.Cluster
    val apart = clusterPlaces(listOf(place(1, 0.0), place(2, 0.001)), zoom = 15f).single() as PlaceMapEntry.Cluster

    assertTrue(samePoint.isTooCloseToSplit)
    assertFalse(apart.isTooCloseToSplit)
  }

  @Test
  fun zoomLevel_isFlooredToHalfSteps() {
    assertEquals(15f, clusterZoomLevel(15.49f))
    assertEquals(15.5f, clusterZoomLevel(15.5f))
    assertEquals(15.5f, clusterZoomLevel(15.99f))
  }
}
