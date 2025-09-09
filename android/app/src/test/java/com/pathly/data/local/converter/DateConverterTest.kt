package com.pathly.data.local.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class DateConverterTest {

  private val converter = DateConverter()

  @Test
  fun `fromTimestamp_正常な値_正しいDateオブジェクトを返す`() {
    // Given
    val timestamp = 1640995200000L // 2022-01-01 00:00:00 UTC

    // When
    val result = converter.fromTimestamp(timestamp)

    // Then
    assertNotNull("Dateオブジェクトが作成される", result)
    assertEquals("タイムスタンプが正しい", timestamp, result!!.time)
  }

  @Test
  fun `fromTimestamp_null値_nullを返す`() {
    // Given
    val timestamp: Long? = null

    // When
    val result = converter.fromTimestamp(timestamp)

    // Then
    assertNull("nullが返される", result)
  }

  @Test
  fun `fromTimestamp_ゼロ値_エポック時刻のDateを返す`() {
    // Given
    val timestamp = 0L

    // When
    val result = converter.fromTimestamp(timestamp)

    // Then
    assertNotNull("Dateオブジェクトが作成される", result)
    assertEquals("エポック時刻が正しい", 0L, result!!.time)
  }

  @Test
  fun `dateToTimestamp_正常なDate_正しいタイムスタンプを返す`() {
    // Given
    val timestamp = 1640995200000L
    val date = Date(timestamp)

    // When
    val result = converter.dateToTimestamp(date)

    // Then
    assertNotNull("タイムスタンプが取得される", result)
    assertEquals("タイムスタンプが正しい", timestamp, result)
  }

  @Test
  fun `dateToTimestamp_null値_nullを返す`() {
    // Given
    val date: Date? = null

    // When
    val result = converter.dateToTimestamp(date)

    // Then
    assertNull("nullが返される", result)
  }

  @Test
  fun `dateToTimestamp_現在時刻_現在のタイムスタンプを返す`() {
    // Given
    val now = Date()

    // When
    val result = converter.dateToTimestamp(now)

    // Then
    assertNotNull("タイムスタンプが取得される", result)
    assertEquals("現在時刻のタイムスタンプが正しい", now.time, result)
  }

  @Test
  fun `双方向変換_Date→Timestamp→Date_元の値と一致する`() {
    // Given
    val originalDate = Date(1640995200000L)

    // When
    val timestamp = converter.dateToTimestamp(originalDate)
    val convertedDate = converter.fromTimestamp(timestamp)

    // Then
    assertNotNull("変換後のDateが存在する", convertedDate)
    assertEquals("元のDateと変換後のDateが一致", originalDate.time, convertedDate!!.time)
  }

  @Test
  fun `双方向変換_Timestamp→Date→Timestamp_元の値と一致する`() {
    // Given
    val originalTimestamp = 1640995200000L

    // When
    val date = converter.fromTimestamp(originalTimestamp)
    val convertedTimestamp = converter.dateToTimestamp(date)

    // Then
    assertNotNull("変換後のタイムスタンプが存在する", convertedTimestamp)
    assertEquals(
      "元のタイムスタンプと変換後のタイムスタンプが一致",
      originalTimestamp,
      convertedTimestamp,
    )
  }

  @Test
  fun `双方向変換_null値_両方向でnullが保持される`() {
    // Given & When & Then
    val timestampToDate = converter.fromTimestamp(null)
    val dateToTimestamp = converter.dateToTimestamp(null)

    assertNull("timestamp→dateでnullが保持される", timestampToDate)
    assertNull("date→timestampでnullが保持される", dateToTimestamp)
  }

  @Test
  fun `極端な値_未来の日付_正しく変換される`() {
    // Given
    val futureTimestamp = 4102444800000L // 2100-01-01 00:00:00 UTC

    // When
    val futureDate = converter.fromTimestamp(futureTimestamp)
    val convertedBack = converter.dateToTimestamp(futureDate)

    // Then
    assertNotNull("未来の日付が変換される", futureDate)
    assertEquals("未来のタイムスタンプが正しい", futureTimestamp, convertedBack)
  }

  @Test
  fun `極端な値_過去の日付_正しく変換される`() {
    // Given
    val pastTimestamp = -2208988800000L // 1900-01-01 00:00:00 UTC

    // When
    val pastDate = converter.fromTimestamp(pastTimestamp)
    val convertedBack = converter.dateToTimestamp(pastDate)

    // Then
    assertNotNull("過去の日付が変換される", pastDate)
    assertEquals("過去のタイムスタンプが正しい", pastTimestamp, convertedBack)
  }
}
