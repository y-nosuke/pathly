package com.pathly.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollToTopButtonTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var listState: LazyListState

  private fun setList() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
          LazyColumn(state = listState, modifier = Modifier.testTag("list")) {
            items(100) { Text("行 $it") }
          }
          ScrollToTopButton(listState = listState, modifier = Modifier.align(Alignment.BottomEnd))
        }
      }
    }
  }

  @Test
  fun `先頭にいるときはボタンを出さない`() {
    setList()
    composeTestRule.onNodeWithContentDescription("一覧の先頭へ戻る").assertDoesNotExist()
  }

  @Test
  fun `スクロールするとボタンが出て、押すと先頭に戻り、ボタンが消える`() {
    setList()
    composeTestRule.onNodeWithTag("list").performScrollToIndex(60)
    composeTestRule.onNodeWithContentDescription("一覧の先頭へ戻る").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals(0, listState.firstVisibleItemIndex)
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodes(hasContentDescription("一覧の先頭へ戻る")).fetchSemanticsNodes().isEmpty()
    }
  }
}
