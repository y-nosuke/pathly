package com.pathly.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EncryptionHelper のインストルメンテーションテスト
 * データ暗号化機能の受け入れ基準テスト:
 * - データは暗号化して保存する
 * - Android Encrypted SharedPreferences使用
 */
@RunWith(AndroidJUnit4::class)
class EncryptionHelperTest {

  private lateinit var encryptionHelper: EncryptionHelper
  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    encryptionHelper = EncryptionHelper(context)
  }

  @Test
  fun testEncryptionEnabled_defaultIsTrue() {
    // When: デフォルト状態でチェック
    val isEnabled = encryptionHelper.isEncryptionEnabled()

    // Then: デフォルトで暗号化が有効
    assertTrue("Encryption should be enabled by default", isEnabled)
  }

  @Test
  fun testSetEncryptionEnabled() {
    // Given: 暗号化を無効に設定
    encryptionHelper.setEncryptionEnabled(false)

    // When: 設定を確認
    val isEnabled = encryptionHelper.isEncryptionEnabled()

    // Then: 暗号化が無効になっている
    assertFalse("Encryption should be disabled", isEnabled)

    // 元に戻す
    encryptionHelper.setEncryptionEnabled(true)
    assertTrue("Encryption should be re-enabled", encryptionHelper.isEncryptionEnabled())
  }

  @Test
  fun testSecureStringStorage() {
    // Given: テストデータ
    val testKey = "test_gps_coordinates"
    val testValue = "lat:35.6762,lng:139.6503,alt:10.5"

    // When: 暗号化して保存
    encryptionHelper.saveSecureString(testKey, testValue)

    // Then: 暗号化されたデータが取得できる
    val retrievedValue = encryptionHelper.getSecureString(testKey)
    assertEquals("Encrypted data should match original", testValue, retrievedValue)
  }

  @Test
  fun testSecureStringStorage_nonExistentKey() {
    // When: 存在しないキーを取得
    val retrievedValue = encryptionHelper.getSecureString("non_existent_key")

    // Then: nullが返される
    assertNull("Non-existent key should return null", retrievedValue)
  }

  @Test
  fun testSecureStringStorage_defaultValue() {
    // Given: デフォルト値
    val defaultValue = "default_location"

    // When: 存在しないキーをデフォルト値付きで取得
    val retrievedValue = encryptionHelper.getSecureString("non_existent_key", defaultValue)

    // Then: デフォルト値が返される
    assertEquals("Should return default value", defaultValue, retrievedValue)
  }

  @Test
  fun testRemoveSecureString() {
    // Given: 暗号化データを保存
    val testKey = "test_remove_key"
    val testValue = "test_remove_value"
    encryptionHelper.saveSecureString(testKey, testValue)

    // 保存されていることを確認
    assertNotNull("Data should be saved", encryptionHelper.getSecureString(testKey))

    // When: データを削除
    encryptionHelper.removeSecureString(testKey)

    // Then: データが削除されている
    assertNull("Data should be removed", encryptionHelper.getSecureString(testKey))
  }

  @Test
  fun testDatabasePassphraseGeneration() {
    // When: データベース用パスフレーズを生成/取得
    val passphrase1 = encryptionHelper.getOrCreateDatabasePassphrase()
    val passphrase2 = encryptionHelper.getOrCreateDatabasePassphrase()

    // Then: 同じパスフレーズが返される（一度生成されたら保持される）
    assertNotNull("Passphrase should not be null", passphrase1)
    assertEquals("Passphrase should be consistent", passphrase1, passphrase2)
    assertTrue("Passphrase should be long enough", passphrase1.length >= 32)
  }

  @Test
  fun testEncryptionIntegrityCheck() {
    // When: 暗号化システムの健全性チェック
    val isIntegrityValid = encryptionHelper.verifyEncryptionIntegrity()

    // Then: 暗号化システムが正常に動作
    assertTrue("Encryption integrity should be valid", isIntegrityValid)
  }

  @Test
  fun testClearAllSecureData() {
    // Given: 複数の暗号化データを保存
    encryptionHelper.saveSecureString("key1", "value1")
    encryptionHelper.saveSecureString("key2", "value2")
    encryptionHelper.saveSecureString("key3", "value3")

    // データが保存されていることを確認
    assertNotNull("Key1 should be saved", encryptionHelper.getSecureString("key1"))
    assertNotNull("Key2 should be saved", encryptionHelper.getSecureString("key2"))

    // When: 全ての暗号化データをクリア
    encryptionHelper.clearAllSecureData()

    // Then: 全てのデータが削除されている
    assertNull("Key1 should be cleared", encryptionHelper.getSecureString("key1"))
    assertNull("Key2 should be cleared", encryptionHelper.getSecureString("key2"))
    assertNull("Key3 should be cleared", encryptionHelper.getSecureString("key3"))
  }

  @Test
  fun testMultipleValuesStorage() {
    // Given: 複数のGPS関連データ
    val trackData = "track_id:123,start:1638360000000,end:1638363600000"
    val locationData = "lat:35.6762,lng:139.6503,accuracy:5.0"
    val settingsData = "tracking_interval:30,battery_optimization:true"

    // When: 複数のデータを暗号化保存
    encryptionHelper.saveSecureString("track_data", trackData)
    encryptionHelper.saveSecureString("location_data", locationData)
    encryptionHelper.saveSecureString("settings_data", settingsData)

    // Then: 全てのデータが正常に取得できる
    assertEquals(
      "Track data should match",
      trackData,
      encryptionHelper.getSecureString("track_data")
    )
    assertEquals(
      "Location data should match",
      locationData,
      encryptionHelper.getSecureString("location_data")
    )
    assertEquals(
      "Settings data should match",
      settingsData,
      encryptionHelper.getSecureString("settings_data")
    )
  }

  @Test
  fun testLongValueStorage() {
    // Given: 大きなGPSデータ（長い軌跡データをシミュレーション）
    val longGpsTrack = (1..1000).joinToString(",") { index ->
      "lat:${35.6762 + index * 0.0001},lng:${139.6503 + index * 0.0001},time:${System.currentTimeMillis() + index * 30000}"
    }

    // When: 長いデータを暗号化保存
    encryptionHelper.saveSecureString("long_gps_track", longGpsTrack)

    // Then: 長いデータも正常に保存・取得できる
    val retrievedData = encryptionHelper.getSecureString("long_gps_track")
    assertEquals("Long GPS track data should match", longGpsTrack, retrievedData)
    assertTrue("Retrieved data should be long", retrievedData?.length ?: 0 > 10000)
  }

  @Test
  fun testSpecialCharacterStorage() {
    // Given: 特殊文字を含むデータ
    val specialCharData =
      "location: 東京駅, description: \"GPS test with 特殊文字 & symbols!@#$%^&*()_+={[}]|\\:;\"<>?,./\""

    // When: 特殊文字を含むデータを暗号化保存
    encryptionHelper.saveSecureString("special_char_data", specialCharData)

    // Then: 特殊文字も正常に保存・取得できる
    val retrievedData = encryptionHelper.getSecureString("special_char_data")
    assertEquals("Special character data should match", specialCharData, retrievedData)
  }
}