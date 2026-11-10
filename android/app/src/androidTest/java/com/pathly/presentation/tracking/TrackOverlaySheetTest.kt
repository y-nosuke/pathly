package com.pathly.presentation.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/** 記録画面の「この範囲の経路」の一覧（選ぶ・上限・範囲の外の経路）。 */
@RunWith(AndroidJUnit4::class)
class TrackOverlaySheetTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun track(id: Long, name: String) = GpsTrack(
    id = id,
    startTime = Date(1_700_000_000_000L + id * 86_400_000L),
    endTime = Date(1_700_000_000_000L + id * 86_400_000L + 3_600_000L),
    isActive = false,
    name = name,
    storedDistanceMeters = 1234.0,
    createdAt = Date(),
    updatedAt = Date(),
  )

  private fun show(initial: TrackOverlayState, onToggle: (GpsTrack) -> Unit = {}) {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          TrackOverlaySheet(
            state = initial,
            sheetState = rememberFloatingSheetState(),
            onToggle = onToggle,
            onClearAll = {},
            onClose = {},
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }

  @Test
  fun listsTracksInRange_andTogglesOnTap() {
    val a = track(1, "海沿いの散歩")
    var toggled: GpsTrack? = null
    show(TrackOverlayState(isSheetOpen = true, inRange = listOf(a)), onToggle = { toggled = it })

    composeTestRule.onNodeWithText("この範囲の経路 1 件").assertIsDisplayed()
    composeTestRule.onNodeWithText("海沿いの散歩").assertIsDisplayed().performClick()
    assertEquals(a, toggled)
  }

  @Test
  fun emptyRange_showsMessage() {
    show(TrackOverlayState(isSheetOpen = true))

    composeTestRule.onNodeWithText("この範囲を通った経路はありません").assertIsDisplayed()
  }

  @Test
  fun atLimit_unselectedRowsAreDisabled_butSelectedCanBeRemoved() {
    val tracks = (1L..9L).map { track(it, "経路$it") }
    val selected = tracks.take(TRACK_OVERLAY_MAX).mapIndexed { i, t -> OverlayTrack(t, i) }
    show(TrackOverlayState(isSheetOpen = true, inRange = tracks, selected = selected))

    composeTestRule.onNodeWithText("地図に重ねる経路を選ぶ（8 / 8 本）").assertIsDisplayed()
    composeTestRule.onNodeWithTag("trackOverlayRow-1").assertIsEnabled()
    composeTestRule.onNodeWithTag("trackOverlayList").performScrollToNode(hasTestTag("trackOverlayRow-9"))
    composeTestRule.onNodeWithTag("trackOverlayRow-9").assertIsNotEnabled()
  }

  @Test
  fun selectedOutsideRange_isListedSeparately() {
    val inside = track(1, "範囲の中の経路")
    val outside = track(2, "前に重ねた経路")
    show(
      TrackOverlayState(
        isSheetOpen = true,
        inRange = listOf(inside),
        selected = listOf(OverlayTrack(outside, 0)),
      ),
    )

    composeTestRule.onNodeWithText("範囲の外（重ねている経路）").assertIsDisplayed()
    composeTestRule.onNodeWithText("前に重ねた経路").assertIsDisplayed()
    composeTestRule.onNodeWithText("すべて外す").assertIsDisplayed()
  }
}
