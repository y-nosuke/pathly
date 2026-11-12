package com.pathly.presentation.common

import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.RegionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 効いている条件のチップの文言（業種・地域）と、3 状態の軸の選択肢の文言。 */
class FilterChipLabelTest {

  @Test
  fun label_isNullWhenNothingSelected() {
    // 効いていない軸はチップを出さない。
    assertNull(categoryChipLabel(emptySet()))
    assertNull(regionChipLabel(emptySet()))
  }

  @Test
  fun label_showsTheCategoryWhenOneSelected() {
    assertEquals("カフェ", categoryChipLabel(setOf(PlaceCategoryFacet.CAFE)))
  }

  // 選んだ順ではなく選択肢の順で先頭を決める（同じ選択なら同じ文言）。
  @Test
  fun label_showsFirstInOptionOrderAndTheRestAsCount() {
    assertEquals("飲食 他2", categoryChipLabel(setOf(PlaceCategoryFacet.UNCATEGORIZED, PlaceCategoryFacet.PARK, PlaceCategoryFacet.FOOD)))
  }

  @Test
  fun regionLabel_addsTheTopLevelToCitiesAndCountsTheRest() {
    assertEquals("東京都 府中市", regionChipLabel(setOf(RegionKey("JP", "東京都", "府中市"))))
    assertEquals("東京都 他1", regionChipLabel(setOf(RegionKey("US"), RegionKey("JP", "東京都"))))
  }

  // 効いている条件のチップを押すと、効いている 2 つの間で入れ替える（指定なしには戻さない。外すのは ×）。
  @Test
  fun flippedChoice_swapsBetweenTheTwoActiveChoices() {
    assertEquals(Axis.OFF, flippedChoice(Axis.ON, Axis.ON, Axis.OFF))
    assertEquals(Axis.ON, flippedChoice(Axis.OFF, Axis.ON, Axis.OFF))
  }

  @Test
  fun choiceLabel_showsUnspecifiedForAny() {
    assertEquals("指定なし", choiceLabel(Axis.ANY, Axis.ANY) { it.name })
    assertEquals("ON", choiceLabel(Axis.ON, Axis.ANY) { it.name })
  }

  private enum class Axis { ANY, ON, OFF }

  @Test
  fun toggled_addsAndRemoves() {
    assertEquals(setOf(1, 2), setOf(1).toggled(2))
    assertEquals(setOf(1), setOf(1, 2).toggled(2))
  }
}
