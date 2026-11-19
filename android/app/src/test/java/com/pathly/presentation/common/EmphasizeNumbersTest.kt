package com.pathly.presentation.common

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class EmphasizeNumbersTest {

  private val style = SpanStyle(fontWeight = FontWeight.Bold)

  // 統計の帯は数字だけを目立たせる（単位・区切りはそのまま）。3 桁区切り・小数も 1 つの数字として扱う。
  @Test
  fun emphasizesOnlyNumberRuns() {
    val text = "12 / 38 回 ・ 3,815 km ・ 4.3 時間"
    val ranges = emphasizeNumbers(text, style).spanStyles.map { text.substring(it.start, it.end) }
    assertEquals(listOf("12", "38", "3,815", "4.3"), ranges)
  }
}
