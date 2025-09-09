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
  fun trackDetailScreen_showsTitle() {
    // Given
    val track = createSampleTrack()

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("外出記録詳細")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_backButton_triggersCallback() {
    // Given
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // When
    composeTestRule
      .onNodeWithContentDescription("戻る")
      .performClick()

    // Then
    verify { mockOnBackClick() }
  }

  @Test
  fun trackDetailScreen_backButton_hasClickAction() {
    // Given
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithContentDescription("戻る")
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun trackDetailScreen_displaysBasicInformation() {
    // Given
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640998800000L)   // 2022-01-01 01:00:00 UTC

    val track = GpsTrack(
      id = 123L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(5),
      createdAt = startTime,
      updatedAt = endTime
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("基本情報")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("日付")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("開始時刻")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("終了時刻")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("所要時間")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("総移動距離")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_displaysRecordingState() {
    // Given
    val track = createSampleTrack()

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("記録状態")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("状態")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("完了")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("トラックID")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("123")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_activeTrack_showsActiveState() {
    // Given
    val track = GpsTrack(
      id = 456L,
      startTime = Date(),
      endTime = null,
      isActive = true,
      points = createSamplePoints(3),
      createdAt = Date(),
      updatedAt = Date()
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("記録中")
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("この記録は現在進行中です")
      .assertIsDisplayed()

    // アクティブトラックでは終了時刻と所要時間は表示されない
    composeTestRule
      .onNodeWithText("終了時刻")
      .assertDoesNotExist()

    composeTestRule
      .onNodeWithText("所要時間")
      .assertDoesNotExist()
  }

  @Test
  fun trackDetailScreen_completedTrack_doesNotShowActiveMessage() {
    // Given
    val track = createSampleTrack(isActive = false)

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("この記録は現在進行中です")
      .assertDoesNotExist()

    composeTestRule
      .onNodeWithText("完了")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_displaysDistanceAndPointCount() {
    // Given
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = createSamplePoints(8),
      createdAt = Date(),
      updatedAt = Date()
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("(8点)", substring = true)
      .assertIsDisplayed()

    // 距離は実際の計算結果に依存するので、km表示があることだけ確認
    composeTestRule
      .onNodeWithText("km", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_zeroPointsTrack_showsZeroDistance() {
    // Given
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = emptyList(), // 0点
      createdAt = Date(),
      updatedAt = Date()
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("0.0km (0点)")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_onePointTrack_showsZeroDistance() {
    // Given
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = createSamplePoints(1), // 1点のみ
      createdAt = Date(),
      updatedAt = Date()
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("0.0km (1点)")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_duration_showsCorrectFormat() {
    // Given
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640995200000L + 3661000L) // 1時間1分後

    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(2),
      createdAt = startTime,
      updatedAt = endTime
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("1時間1分")
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_shortDuration_showsMinutesOnly() {
    // Given
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640995200000L + 1800000L) // 30分後

    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(2),
      createdAt = startTime,
      updatedAt = endTime
    )

    // When
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick
        )
      }
    }

    // Then
    composeTestRule
      .onNodeWithText("30分")
      .assertIsDisplayed()
  }

  private fun createSampleTrack(
    id: Long = 123L,
    isActive: Boolean = false
  ): GpsTrack {
    val startTime = Date(1640995200000L)
    return GpsTrack(
      id = id,
      startTime = startTime,
      endTime = if (isActive) null else Date(1640998800000L),
      isActive = isActive,
      points = createSamplePoints(5),
      createdAt = startTime,
      updatedAt = Date(1640998800000L)
    )
  }

  private fun createSamplePoints(count: Int): List<GpsPoint> {
    return (1..count).map { index ->
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
        createdAt = Date()
      )
    }
  }
}