package com.pathly.presentation.common

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.PlaceCategoryFacet
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedListOptionsTest {

  @Test
  fun enum_roundTrips() {
    val handle = SavedStateHandle()
    handle.putEnum("k", PlaceCategoryFacet.PARK)
    assertEquals(PlaceCategoryFacet.PARK, handle.getEnum("k", PlaceCategoryFacet.OTHER))
  }

  // 版が変わって名前が無くなっても落ちずに既定値へ戻す。
  @Test
  fun enum_unknownOrMissingNameFallsBackToDefault() {
    val handle = SavedStateHandle(mapOf("k" to "GONE"))
    assertEquals(PlaceCategoryFacet.OTHER, handle.getEnum("k", PlaceCategoryFacet.OTHER))
    assertEquals(PlaceCategoryFacet.OTHER, handle.getEnum("missing", PlaceCategoryFacet.OTHER))
  }

  @Test
  fun enumSet_roundTripsAndSkipsUnknownNames() {
    val handle = SavedStateHandle()
    handle.putEnumSet("s", setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.UNCATEGORIZED))
    assertEquals(setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.UNCATEGORIZED), handle.getEnumSet<PlaceCategoryFacet>("s"))

    val withUnknown = SavedStateHandle(mapOf("s" to arrayListOf("CAFE", "GONE")))
    assertEquals(setOf(PlaceCategoryFacet.CAFE), withUnknown.getEnumSet<PlaceCategoryFacet>("s"))
    assertEquals(emptySet<PlaceCategoryFacet>(), SavedStateHandle().getEnumSet<PlaceCategoryFacet>("missing"))
  }
}
