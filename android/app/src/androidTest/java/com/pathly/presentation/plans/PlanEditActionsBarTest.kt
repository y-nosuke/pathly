package com.pathly.presentation.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.newPlanDetail
import com.pathly.domain.model.withTitleAndNote
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/** 計画のシートの下端の「下書き」「保存」の行（→ docs/specs/plans.md の「保存と下書き」）。 */
@RunWith(AndroidJUnit4::class)
class PlanEditActionsBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val base = newPlanDetail("鎌倉散歩", Date(0))
  private val changed = base.withTitleAndNote("鎌倉と江の島", null)
  private var edit by mutableStateOf(PlanEditState(base, saved = null, persisted = base))
  private val pressed = mutableListOf<String>()

  private fun setBar() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        Column {
          PlanEditActionsBar(edit = edit, onSaveDraft = { pressed += "draft" }, onSave = { pressed += "save" })
        }
      }
    }
  }

  @Test
  fun appearsOnlyAfterAChange() {
    setBar()
    composeTestRule.onNodeWithTag("planSave").assertDoesNotExist()

    edit = PlanEditState(changed, saved = null, persisted = base)
    composeTestRule.onNodeWithTag("planSave").assertIsDisplayed().assertIsEnabled().performClick()
    composeTestRule.onNodeWithTag("planSaveDraft").assertIsDisplayed().assertIsEnabled().performClick()
    assertEquals(listOf("save", "draft"), pressed)
  }

  @Test
  fun savedPlan_hasNoDraftButton() {
    edit = PlanEditState(changed, saved = base, persisted = base)
    setBar()
    composeTestRule.onNodeWithTag("planSave").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("planSaveDraft").assertDoesNotExist()
  }

  @Test
  fun newPlanOpenedFromItsDraft_canBeSaved_butNotDraftedAgain() {
    edit = PlanEditState(changed, saved = null, persisted = changed, draftId = 1)
    setBar()
    composeTestRule.onNodeWithTag("planSave").assertIsEnabled()
    composeTestRule.onNodeWithTag("planSaveDraft").assertIsNotEnabled()
  }
}
