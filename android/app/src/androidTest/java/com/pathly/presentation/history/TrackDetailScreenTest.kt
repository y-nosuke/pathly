package com.pathly.presentation.history

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Place
import com.pathly.domain.model.Stop
import com.pathly.ui.theme.PathlyAndroidTheme
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class TrackDetailScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var mockOnBackClick: () -> Unit

  @Before
  fun setup() {
    mockOnBackClick = mockk(relaxed = true)
  }

  @Test
  fun trackDetailScreen_backButton_triggersCallback() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("戻る")
      .performClick()

    verify { mockOnBackClick() }
  }

  @Test
  fun trackDetailScreen_backButton_hasClickAction() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("戻る")
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun trackDetailScreen_bottomSheet_showsStatLabels() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithText("移動距離")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("地点数")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_bottomSheet_showsPointCountAndDistance() {
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = createSamplePoints(8),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    // 地点数タイルの値
    composeTestRule
      .onNodeWithText("8")
      .assertIsDisplayed()

    // 距離タイルは実際の計算結果に依存するので km 表示があることだけ確認
    composeTestRule
      .onNodeWithText("km", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_activeTrack_showsRecordingBadge() {
    val track = GpsTrack(
      id = 456L,
      startTime = Date(),
      endTime = null,
      isActive = true,
      points = createSamplePoints(3),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithText("記録中", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_completedTrack_doesNotShowRecordingBadge() {
    val track = createSampleTrack(isActive = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithText("記録中", substring = true)
      .assertDoesNotExist()
  }

  @Test
  fun trackDetailScreen_emptyTrack_showsNoDataMessage() {
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = emptyList(),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithText("GPSデータがありません")
      .assertIsDisplayed()

    // 地点数タイルは 0
    composeTestRule
      .onNodeWithText("0.0km")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_duration_showsHoursAndMinutes() {
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640995200000L + 3661000L) // 1時間1分後

    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(2),
      createdAt = startTime,
      updatedAt = endTime,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    // 所要時間は差分のためタイムゾーン非依存。サブタイトル内に含まれる
    composeTestRule
      .onNodeWithText("1時間1分", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_shortDuration_showsMinutesOnly() {
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640995200000L + 1800000L) // 30分後

    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(2),
      createdAt = startTime,
      updatedAt = endTime,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
        )
      }
    }

    composeTestRule
      .onNodeWithText("30分", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_showsStopWithPlaceName() {
    val track = createSampleTrack()
    val stops = listOf(sampleStop(name = "テストカフェ"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          stops = stops,
        )
      }
    }

    composeTestRule.onNodeWithText("立ち寄り 1件").assertIsDisplayed()
    composeTestRule.onNodeWithText("テストカフェ").assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_tapStop_opensDialogAndSaveTriggersCallback() {
    val onEdit = mockk<(Long, String) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    val stops = listOf(sampleStop(placeId = 9L, name = "旧名"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          stops = stops,
          onEditPlaceName = onEdit,
        )
      }
    }

    composeTestRule.onNodeWithText("旧名").performClick()
    composeTestRule.onNodeWithText("場所の名前").assertIsDisplayed()
    composeTestRule.onNodeWithText("保存").performClick()

    verify { onEdit(9L, "旧名") }
  }

  private fun sampleStop(
    placeId: Long = 1L,
    name: String? = null,
  ): Stop {
    val place = Place(
      id = placeId,
      name = name,
      latitude = 35.0,
      longitude = 139.0,
      address = null,
      createdAt = Date(0L),
      updatedAt = Date(0L),
    )
    return Stop(
      id = 1L,
      place = place,
      trackId = 123L,
      arrivalTime = Date(1640995200000L),
      departureTime = Date(1640995200000L + 300000L),
    )
  }

  private fun createSampleTrack(
    id: Long = 123L,
    isActive: Boolean = false,
  ): GpsTrack {
    val startTime = Date(1640995200000L)
    return GpsTrack(
      id = id,
      startTime = startTime,
      endTime = if (isActive) null else Date(1640998800000L),
      isActive = isActive,
      points = createSamplePoints(5),
      createdAt = startTime,
      updatedAt = Date(1640998800000L),
    )
  }

  private fun createSamplePoints(count: Int): List<GpsPoint> = (1..count).map { index ->
    GpsPoint(
      id = index.toLong(),
      trackId = 1L,
      latitude = 35.6762 + (index * 0.001),
      longitude = 139.6503 + (index * 0.001),
      altitude = null,
      accuracy = 10f,
      speed = null,
      bearing = null,
      timestamp = Date(1640995200000L + index * 60000), // 1分間隔
      createdAt = Date(),
    )
  }
}
