package com.pathly.presentation.stops

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * 手動追加シートの「名前で検索」。地図に POI が出ていない場所を、名前で探して追加できることを見る。
 */
@RunWith(AndroidJUnit4::class)
class ManualStopSheetTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val facility = PlaceSearchResult(
    googlePlaceId = "gp-zoo",
    name = "テスト動物園",
    address = "東京都",
    category = null,
    latitude = 35.7,
    longitude = 139.7,
  )

  @Test
  fun mapPointOrigin_searchByName_picksFacilityWithItsCoordinates() {
    var confirmed: ManualStopInput? = null

    composeTestRule.setContent {
      PathlyAndroidTheme {
        ManualStopSheet(
          origin = ManualStopOrigin.MapPoint,
          latitude = 35.0,
          longitude = 139.0,
          points = samplePoints(),
          onFetchCandidates = { _, _ -> emptyList() },
          onConfirm = { confirmed = it },
          onCancel = {},
          onSearchPredictions = { query ->
            listOf(PlacePrediction(placeId = "gp-zoo", primaryText = "テスト動物園", secondaryText = query))
          },
          onFetchPrediction = { facility },
        )
      }
    }

    composeTestRule.onNodeWithText("店名・場所名で検索").performTextInput("どうぶつ")
    // 打鍵ごとには叩かない（デバウンス）ので、候補が出るまで待つ。
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("テスト動物園").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("テスト動物園").performClick()

    // 選ぶと見出しが施設名になり、そのまま追加できる。
    composeTestRule.onNodeWithText("追加").performClick()

    val input = requireNotNull(confirmed)
    assertEquals("gp-zoo", input.googlePlaceId)
    assertEquals("テスト動物園", input.googleName)
    // 座標は指した地点ではなく施設のものを使う（他の経路と揃えるため）。
    assertEquals(35.7, input.latitude, 0.0001)
    assertEquals(139.7, input.longitude, 0.0001)
    // 自分で入力していないので、ユーザー名は残さない。
    assertEquals(null, input.name)
  }

  @Test
  fun poiOrigin_doesNotShowSearchField() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        ManualStopSheet(
          origin = ManualStopOrigin.Poi(name = "テストカフェ", googlePlaceId = "gp-cafe"),
          latitude = 35.0,
          longitude = 139.0,
          points = samplePoints(),
          onFetchCandidates = { _, _ -> emptyList() },
          onConfirm = {},
          onCancel = {},
          onSearchPredictions = { emptyList() },
          onFetchPrediction = { null },
        )
      }
    }

    // POI をタップした時点で施設は確定しているので、探し直す欄は出さない。
    composeTestRule.onNodeWithText("テストカフェ").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("店名・場所名で検索").fetchSemanticsNodes().size)
  }

  private fun samplePoints(): List<GpsPoint> = (0..4).map { index ->
    GpsPoint(
      id = index.toLong(),
      trackId = 1L,
      latitude = 35.0 + index * 0.001,
      longitude = 139.0 + index * 0.001,
      altitude = null,
      accuracy = 10f,
      speed = null,
      bearing = null,
      timestamp = Date(1640995200000L + index * 60_000L),
      createdAt = Date(0L),
    )
  }
}
