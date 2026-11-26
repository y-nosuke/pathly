package com.pathly.data.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.domain.model.PlaceGrouping
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 場所一覧の分け方と開いたまとまりが設定へ保存され、アプリを開き直しても（作り直しても）戻ることを確かめる。 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryPlaceGroupingTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var savedGrouping: PlaceGrouping
  private lateinit var savedOpen: Map<PlaceGrouping, Set<String>>

  @Before
  fun setUp() {
    val repository = SettingsRepositoryImpl(context)
    savedGrouping = repository.placeGrouping.value
    savedOpen = PlaceGrouping.entries.associateWith { repository.openPlaceGroups(it).value }
  }

  @After
  fun tearDown() {
    val repository = SettingsRepositoryImpl(context)
    repository.setPlaceGrouping(savedGrouping)
    savedOpen.forEach { (grouping, keys) -> repository.setOpenPlaceGroups(grouping, keys) }
  }

  @Test
  fun `分け方を保存して作り直しても戻る`() {
    SettingsRepositoryImpl(context).setPlaceGrouping(PlaceGrouping.CATEGORY)

    assertEquals(PlaceGrouping.CATEGORY, SettingsRepositoryImpl(context).placeGrouping.value)
  }

  @Test
  fun `開いたまとまりを分け方ごとに保存して作り直しても戻る`() {
    // 地域の名前にはカンマなどが入り得るので、そのまま戻ることも確かめる。
    val region = setOf("region:JP|東京都|", "region:JP|東京都|府中市", "region:US||Washington, D.C.")
    val category = setOf("category:CAFE")
    val repository = SettingsRepositoryImpl(context)
    repository.setOpenPlaceGroups(PlaceGrouping.REGION, region)
    repository.setOpenPlaceGroups(PlaceGrouping.CATEGORY, category)

    val reopened = SettingsRepositoryImpl(context)
    assertEquals(region, reopened.openPlaceGroups(PlaceGrouping.REGION).value)
    assertEquals(category, reopened.openPlaceGroups(PlaceGrouping.CATEGORY).value)
  }

  @Test
  fun `すべて閉じたことも保存して戻る`() {
    SettingsRepositoryImpl(context).setOpenPlaceGroups(PlaceGrouping.REGION, setOf("region:JP|北海道|"))
    SettingsRepositoryImpl(context).setOpenPlaceGroups(PlaceGrouping.REGION, emptySet())

    assertEquals(emptySet<String>(), SettingsRepositoryImpl(context).openPlaceGroups(PlaceGrouping.REGION).value)
  }
}
