package com.pathly.presentation.tracking

import com.pathly.domain.model.PlacePrediction
import org.junit.Assert.assertEquals
import org.junit.Test

class MapSearchCandidatesTest {

  private val cafe = PlacePrediction("p1", "北九州カフェ", "福岡県北九州市")
  private val city = PlacePrediction("r1", "北九州市", "福岡県", isRegion = true)

  @Test
  fun facilityIsRegisterOnly_regionIsMoveThenRegister_inRelevanceOrder() {
    val rows = listOf(city, cafe).toMapSearchCandidates(registerEnabled = true)

    assertEquals(
      listOf(
        "r1" to MapSearchAction.MoveToRegion,
        "r1" to MapSearchAction.Register,
        "p1" to MapSearchAction.Register,
      ),
      rows.map { it.prediction.placeId to it.action },
    )
    // 同じ候補の 2 行でもキーは重ならない（一覧のキーに使う）。
    assertEquals(rows.size, rows.map { it.key }.toSet().size)
  }

  @Test
  fun registerDisabled_leavesOnlyMoveRows() {
    val rows = listOf(city, cafe).toMapSearchCandidates(registerEnabled = false)

    assertEquals(listOf("r1" to MapSearchAction.MoveToRegion), rows.map { it.prediction.placeId to it.action })
  }
}
