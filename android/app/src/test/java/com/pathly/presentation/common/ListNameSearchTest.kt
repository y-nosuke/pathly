package com.pathly.presentation.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListNameSearchTest {

  @Test
  fun `検索語が空なら絞らない`() {
    assertTrue(matchesNameQuery("鎌倉さんぽ", ""))
    assertTrue(matchesNameQuery("鎌倉さんぽ", "  "))
  }

  @Test
  fun `部分一致で探す`() {
    assertTrue(matchesNameQuery("秋の鎌倉さんぽ", "鎌倉"))
    assertFalse(matchesNameQuery("秋の鎌倉さんぽ", "江ノ島"))
  }

  @Test
  fun `前後の空白は無視する`() {
    assertTrue(matchesNameQuery("鎌倉さんぽ", " 鎌倉 "))
  }

  @Test
  fun `英字の大文字と小文字を区別しない`() {
    assertTrue(matchesNameQuery("Good Coffee Farms", "coffee"))
    assertTrue(matchesNameQuery("good coffee", "COFFEE"))
  }

  @Test
  fun `全角と半角を区別しない`() {
    assertTrue(matchesNameQuery("ＣＡＦＥ　１２３", "cafe 123"))
    assertTrue(matchesNameQuery("cafe 123", "ＣＡＦＥ"))
    assertTrue(matchesNameQuery("ｶﾌｪ", "カフェ"))
  }

  @Test
  fun `ひらがなとカタカナは同じとみなさない`() {
    assertFalse(matchesNameQuery("カフェ", "かふぇ"))
  }

  @Test
  fun `日付の文字列でも探せる`() {
    assertTrue(matchesNameQuery("2026/11/08(日)", "11/08"))
    assertTrue(matchesNameQuery("2026/11/08(日)", "(日)"))
  }
}
