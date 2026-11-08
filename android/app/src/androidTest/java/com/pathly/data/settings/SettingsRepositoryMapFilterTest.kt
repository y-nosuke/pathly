package com.pathly.data.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.PlaceCategoryFacet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 地図の絞り込みが設定に保存され、アプリを開き直しても（作り直しても）戻ることを確かめる。 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryMapFilterTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var saved: MapPlaceFilter

  @Before
  fun setUp() {
    saved = SettingsRepository(context).mapPlaceFilter.value
  }

  @After
  fun tearDown() {
    SettingsRepository(context).setMapPlaceFilter(saved)
  }

  @Test
  fun `絞り込みを保存して作り直しても戻る`() {
    val filter = MapPlaceFilter(
      wishlisted = true,
      visited = false,
      categories = setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.UNCATEGORIZED),
    )

    SettingsRepository(context).setMapPlaceFilter(filter)

    assertEquals(filter, SettingsRepository(context).mapPlaceFilter.value)
  }

  @Test
  fun `絞り込みなしも保存して戻る`() {
    SettingsRepository(context).setMapPlaceFilter(MapPlaceFilter(wishlisted = false))
    SettingsRepository(context).setMapPlaceFilter(MapPlaceFilter())

    assertEquals(MapPlaceFilter(), SettingsRepository(context).mapPlaceFilter.value)
  }
}
