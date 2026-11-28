package com.pathly.presentation.common

import com.pathly.R
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class StopLabelGlyphTest {
  private fun place(code: String?) = Place(
    id = 1,
    name = null,
    latitude = 35.0,
    longitude = 139.0,
    note = null,
    googleName = "店",
    googleAddress = null,
    category = code?.let { PlaceCategory(it) },
    googlePlaceId = null,
    createdAt = Date(0),
    updatedAt = Date(0),
  )

  @Test
  fun `業種が分かれば業種のアイコン`() {
    assertEquals(R.drawable.ic_category_cafe, stopLabelGlyph(place("cafe")))
    assertEquals(R.drawable.ic_category_park, stopLabelGlyph(place("park")))
  }

  @Test
  fun `業種が無い・分からない場所はアイコンなし（場所ピンを出さない）`() {
    assertNull(stopLabelGlyph(place(null)))
    assertNull(stopLabelGlyph(place("unknown_type_xyz")))
  }
}
