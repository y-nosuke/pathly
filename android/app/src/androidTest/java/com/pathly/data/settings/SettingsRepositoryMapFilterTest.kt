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

/** 地図の絞り込みが地図ごとに設定へ保存され、アプリを開き直しても（作り直しても）戻ることを確かめる。 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryMapFilterTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var saved: Map<MapSurface, MapPlaceFilter>

  @Before
  fun setUp() {
    val repository = SettingsRepository(context)
    saved = MapSurface.entries.associateWith { repository.mapPlaceFilter(it).value }
  }

  @After
  fun tearDown() {
    val repository = SettingsRepository(context)
    saved.forEach { (surface, filter) -> repository.setMapPlaceFilter(surface, filter) }
  }

  @Test
  fun `絞り込みを保存して作り直しても戻る`() {
    val filter = MapPlaceFilter(
      wishlisted = true,
      visited = false,
      categories = setOf(PlaceCategoryFacet.CAFE, PlaceCategoryFacet.UNCATEGORIZED),
    )

    SettingsRepository(context).setMapPlaceFilter(MapSurface.RECORDING, filter)

    assertEquals(filter, SettingsRepository(context).mapPlaceFilter(MapSurface.RECORDING).value)
  }

  @Test
  fun `絞り込みなしも保存して戻る`() {
    SettingsRepository(context).setMapPlaceFilter(MapSurface.HISTORY, MapPlaceFilter(wishlisted = false))
    SettingsRepository(context).setMapPlaceFilter(MapSurface.HISTORY, MapPlaceFilter())

    assertEquals(MapPlaceFilter(), SettingsRepository(context).mapPlaceFilter(MapSurface.HISTORY).value)
  }

  @Test
  fun `地図ごとに独立して持つ`() {
    val repository = SettingsRepository(context)
    repository.setMapPlaceFilter(MapSurface.RECORDING, MapPlaceFilter(wishlisted = true))
    repository.setMapPlaceFilter(MapSurface.HISTORY, MapPlaceFilter())
    repository.setMapPlaceFilter(MapSurface.PLACE_DETAIL, MapPlaceFilter(categories = setOf(PlaceCategoryFacet.PARK)))

    val reopened = SettingsRepository(context)
    assertEquals(MapPlaceFilter(wishlisted = true), reopened.mapPlaceFilter(MapSurface.RECORDING).value)
    assertEquals(MapPlaceFilter(), reopened.mapPlaceFilter(MapSurface.HISTORY).value)
    assertEquals(MapPlaceFilter(categories = setOf(PlaceCategoryFacet.PARK)), reopened.mapPlaceFilter(MapSurface.PLACE_DETAIL).value)
  }
}
