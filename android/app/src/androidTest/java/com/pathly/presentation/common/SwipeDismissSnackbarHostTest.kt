package com.pathly.presentation.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.ui.theme.PathlyAndroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** スナックバーは横にスワイプすると消える（押すまで残る取り消しのスナックバーも）。 */
@RunWith(AndroidJUnit4::class)
class SwipeDismissSnackbarHostTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val hostState = SnackbarHostState()
  private lateinit var scope: CoroutineScope

  private fun setHost() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        scope = rememberCoroutineScope()
        SwipeDismissSnackbarHost(hostState)
      }
    }
  }

  private fun showUndo(): MutableList<SnackbarResult> {
    val results = mutableListOf<SnackbarResult>()
    composeTestRule.runOnIdle {
      scope.launch {
        results += hostState.showSnackbar("「鎌倉」を外しました", actionLabel = "取り消す", duration = SnackbarDuration.Indefinite)
      }
    }
    composeTestRule.onNodeWithText("「鎌倉」を外しました").assertExists()
    return results
  }

  @Test
  fun swipeLeft_dismissesWithoutUndo() {
    setHost()
    val results = showUndo()

    composeTestRule.onNodeWithTag("swipeDismissSnackbar").performTouchInput { swipeLeft() }

    composeTestRule.onNodeWithText("「鎌倉」を外しました").assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(listOf(SnackbarResult.Dismissed), results) }
  }

  @Test
  fun swipeRight_alsoDismisses() {
    setHost()
    val results = showUndo()

    composeTestRule.onNodeWithTag("swipeDismissSnackbar").performTouchInput { swipeRight() }

    composeTestRule.onNodeWithText("「鎌倉」を外しました").assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(listOf(SnackbarResult.Dismissed), results) }
  }

  @Test
  fun nextSnackbar_showsUnswiped() {
    setHost()
    showUndo()
    composeTestRule.onNodeWithTag("swipeDismissSnackbar").performTouchInput { swipeLeft() }

    composeTestRule.runOnIdle { scope.launch { hostState.showSnackbar("保存しました") } }

    composeTestRule.onNodeWithText("保存しました").assertExists()
  }
}
