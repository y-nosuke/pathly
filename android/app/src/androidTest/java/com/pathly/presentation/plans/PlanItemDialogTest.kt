package com.pathly.presentation.plans

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanPlace
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 予定の行を足す・行を編集するダイアログの言葉（→ docs/specs/plans.md の「予定の行」）。 */
@RunWith(AndroidJUnit4::class)
class PlanItemDialogTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var saved: String? = null

  private fun setDialog(item: PlanItem?) {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        PlanItemDialog(item = item, onSave = { title, _, _, _ -> saved = title }, onDismiss = {})
      }
    }
  }

  private fun item(place: PlanPlace?, title: String?) = PlanItem(
    id = 1,
    planId = 1,
    dayIndex = 0,
    position = 0,
    place = place,
    title = title,
    note = null,
    plannedTime = null,
    stayMinutes = null,
  )

  @Test
  fun newScheduleRow_asksForItsContent() {
    setDialog(null)
    composeTestRule.onNodeWithText("予定を足す").assertExists()
    composeTestRule.onNodeWithText("内容（「昼食」「新幹線で移動」など）").assertExists()
    composeTestRule.onNodeWithTag("planItemSave").assertIsNotEnabled()

    composeTestRule.onNodeWithTag("planMemoTitleField").performTextInput("昼食")
    composeTestRule.onNodeWithTag("planItemSave").assertIsEnabled().performClick()
    assertEquals("昼食", saved)
  }

  @Test
  fun editingAScheduleRow_isCalledSchedule() {
    setDialog(item(place = null, title = "新幹線で移動"))
    composeTestRule.onNodeWithText("予定を編集").assertExists()
  }

  @Test
  fun placeRow_hasNoContentField() {
    setDialog(item(place = PlanPlace(placeId = 1, name = "鶴岡八幡宮", latitude = 35.3, longitude = 139.5), title = null))
    composeTestRule.onNodeWithText("鶴岡八幡宮").assertExists()
    composeTestRule.onNodeWithTag("planMemoTitleField").assertDoesNotExist()
  }
}
