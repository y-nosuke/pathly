package com.pathly.presentation.plans

import com.pathly.domain.model.newPlanDetail
import com.pathly.domain.model.withTitleAndNote
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** シートの下端の操作の行（下書き・保存）を出すか（→ docs/specs/plans.md の「保存と下書き」）。 */
class PlanEditStateTest {
  private val base = newPlanDetail("鎌倉散歩", Date(0))
  private val changed = base.withTitleAndNote("鎌倉と江の島", null)

  @Test
  fun newPlan_withoutChanges_hidesTheActions() {
    assertFalse(PlanEditState(base, saved = null, persisted = base).showsEditActions)
  }

  @Test
  fun newPlan_withChanges_showsTheActions() {
    assertTrue(PlanEditState(changed, saved = null, persisted = base).showsEditActions)
  }

  @Test
  fun newPlan_openedFromItsDraft_showsTheActions_soItCanBeSaved() {
    assertTrue(PlanEditState(changed, saved = null, persisted = changed, draftId = 1).showsEditActions)
  }

  @Test
  fun savedPlan_withoutChanges_hidesTheActions() {
    assertFalse(PlanEditState(base, saved = base, persisted = base).showsEditActions)
  }

  @Test
  fun savedPlan_withChanges_showsTheActions() {
    assertTrue(PlanEditState(changed, saved = base, persisted = base).showsEditActions)
  }

  @Test
  fun savedPlan_openedFromADraft_showsTheActions() {
    assertTrue(PlanEditState(changed, saved = base, persisted = changed, draftId = 1).showsEditActions)
  }

  @Test
  fun savedPlan_withTracksToUnlink_showsTheActions() {
    assertTrue(PlanEditState(base, saved = base, persisted = base, unlinkTrackIds = setOf(1L)).showsEditActions)
  }
}
