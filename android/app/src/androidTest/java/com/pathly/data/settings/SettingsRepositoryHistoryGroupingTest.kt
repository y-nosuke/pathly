package com.pathly.data.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pathly.domain.model.GroupOpenState
import com.pathly.domain.model.TrackGrouping
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 履歴一覧の分け方と開閉が設定へ保存され、アプリを開き直しても（作り直しても）戻ることを確かめる。 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryHistoryGroupingTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var savedGrouping: TrackGrouping
  private lateinit var savedOpen: GroupOpenState

  @Before
  fun setUp() {
    val repository = SettingsRepositoryImpl(context)
    savedGrouping = repository.historyGrouping.value
    savedOpen = repository.historyGroupOpenState.value
  }

  @After
  fun tearDown() {
    val repository = SettingsRepositoryImpl(context)
    repository.setHistoryGrouping(savedGrouping)
    repository.setHistoryGroupOpenState(savedOpen)
  }

  @Test
  fun groupingAndToggledGroups_surviveRecreation() {
    val open = GroupOpenState(opened = setOf("year:2025", "month:2025-08"), closed = setOf("month:2026-11"))

    SettingsRepositoryImpl(context).apply {
      setHistoryGrouping(TrackGrouping.MONTH)
      setHistoryGroupOpenState(open)
    }

    val reopened = SettingsRepositoryImpl(context)
    assertEquals(TrackGrouping.MONTH, reopened.historyGrouping.value)
    assertEquals(open, reopened.historyGroupOpenState.value)
  }

  @Test
  fun emptyOpenState_survivesRecreation() {
    SettingsRepositoryImpl(context).setHistoryGroupOpenState(GroupOpenState(opened = setOf("year:2024")))
    SettingsRepositoryImpl(context).setHistoryGroupOpenState(GroupOpenState())

    assertEquals(GroupOpenState(), SettingsRepositoryImpl(context).historyGroupOpenState.value)
  }
}
