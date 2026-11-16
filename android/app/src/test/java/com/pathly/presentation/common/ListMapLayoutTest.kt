package com.pathly.presentation.common

import com.pathly.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListMapLayoutTest {

  // 絞り込んでいなければ、全件の数だけを出す。
  @Test
  fun listMapSummary_noFilter_showsTotal() {
    assertEquals("場所 12 件", listMapSummary("場所", activeFilterCount = 0, visibleCount = 12, totalCount = 12))
  }

  // 絞り込んでいれば、条件の数と「残った数 / 全件」を出す（絞り込みを忘れて足りないと思わないように）。
  @Test
  fun listMapSummary_filtered_showsCountAndRatio() {
    assertEquals("絞り込み 2・5 / 12 件", listMapSummary("経路", activeFilterCount = 2, visibleCount = 5, totalCount = 12))
  }

  // 寄せる範囲が 1 つも無ければ寄せない（null）。
  @Test
  fun toLatLngBounds_empty_isNull() {
    assertNull(emptyList<GeoBounds>().toLatLngBounds())
  }
}
