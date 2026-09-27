package com.pathly.presentation.common

import com.pathly.domain.model.PlaceCategoryFacet
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFilterChipTest {

  @Test
  fun label_showsAxisNameWhenNothingSelected() {
    assertEquals("業種", categoryChipLabel(emptySet()))
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
  fun toggled_addsAndRemoves() {
    assertEquals(setOf(1, 2), setOf(1).toggled(2))
    assertEquals(setOf(1), setOf(1, 2).toggled(2))
  }
}
