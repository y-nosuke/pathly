package com.pathly.presentation.tracking

import com.pathly.domain.model.PlacePrediction
import org.junit.Assert.assertEquals
import org.junit.Test

class MapSearchCandidatesTest {

  private val cafe = PlacePrediction("p1", "北九州カフェ", "福岡県北九州市")
  private val city = PlacePrediction("r1", "北九州市", "福岡県", isRegion = true)

  @Test
  fun placeMode_everyCandidateIsOneRegisterRow_inRelevanceOrder() {
    // 「場所」で探すと、地域の候補も「場所として登録」の 1 行だけ（地域へ移動は「地域」で探す）。
    val rows = listOf(city, cafe).toMapSearchCandidates(MapSearchMode.Place)

    assertEquals(
      listOf("r1" to MapSearchAction.Register, "p1" to MapSearchAction.Register),
      rows.map { it.prediction.placeId to it.action },
    )
  }

  @Test
  fun regionMode_everyCandidateIsOneMoveRow() {
    val rows = listOf(city).toMapSearchCandidates(MapSearchMode.Region)

    assertEquals(listOf("r1" to MapSearchAction.MoveToRegion), rows.map { it.prediction.placeId to it.action })
  }
}
