package com.pathly.presentation.history

import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsTitleTest {

  @Test
  fun notFiltered_showsPlainTitle() {
    assertEquals("📊 お出掛け統計", statisticsTitle(shownCount = 4, totalCount = 4, isFiltered = false))
  }

  // 絞り込み中は、絞った数字だと分かるよう全体の件数を添える（一致していても絞り込み中であることは出す）。
  @Test
  fun filtered_showsShownAndTotalCounts() {
    assertEquals("📊 お出掛け統計（絞り込み中 2 / 4 回）", statisticsTitle(shownCount = 2, totalCount = 4, isFiltered = true))
    assertEquals("📊 お出掛け統計（絞り込み中 4 / 4 回）", statisticsTitle(shownCount = 4, totalCount = 4, isFiltered = true))
  }
}
