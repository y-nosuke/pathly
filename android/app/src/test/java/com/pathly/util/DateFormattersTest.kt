package com.pathly.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateFormattersTest {

  private lateinit var savedZone: TimeZone
  private lateinit var savedLocale: Locale

  // 2026-11-08 12:00:00 JST（日曜）
  private val sunday = Date(1_794_106_800_000L)

  @Before
  fun setUp() {
    savedZone = TimeZone.getDefault()
    savedLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(savedZone)
    Locale.setDefault(savedLocale)
  }

  @Test
  fun `shortDate_曜日を添える`() {
    assertEquals("2026/11/08(日)", DateFormatters.shortDate(sunday))
  }

  @Test
  fun `date_曜日を添える`() {
    assertEquals("2026年11月08日(日)", DateFormatters.date(sunday))
  }

  @Test
  fun `日付_端末の言語が英語でも曜日は日本語`() {
    Locale.setDefault(Locale.US)
    assertEquals("2026/11/08(日)", DateFormatters.shortDate(sunday))
    assertEquals("2026年11月08日(日)", DateFormatters.date(sunday))
  }

  @Test
  fun `日付_端末のタイムゾーンで日付と曜日を決める`() {
    // 2026-11-08 00:30 JST は UTC ではまだ 11/07（土）。
    val earlyMorning = Date(1_794_065_400_000L)
    assertEquals("2026/11/08(日)", DateFormatters.shortDate(earlyMorning))
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    assertEquals("2026/11/07(土)", DateFormatters.shortDate(earlyMorning))
  }
}
