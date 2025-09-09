package com.pathly.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * GPS軌跡データの暗号化機能を提供するヘルパークラス
 * Android Encrypted SharedPreferencesを使用してローカルデータを安全に保存
 */
class EncryptionHelper(private val context: Context) {

  companion object {
    private const val PREFS_FILENAME = "pathly_encrypted_prefs"
    private const val KEY_ENCRYPTION_ENABLED = "encryption_enabled"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
  }

  private val logger = Logger("EncryptionHelper")

  private val masterKey: MasterKey by lazy {
    try {
      MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    } catch (e: Exception) {
      logger.e("Failed to create master key", e)
      throw SecurityException("Encryption initialization failed", e)
    }
  }

  private val encryptedSharedPreferences: SharedPreferences by lazy {
    try {
      EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )
    } catch (e: Exception) {
      logger.e("Failed to create encrypted preferences", e)
      throw SecurityException("Encrypted storage initialization failed", e)
    }
  }

  /**
   * 暗号化機能が有効かどうかを確認
   */
  fun isEncryptionEnabled(): Boolean {
    return try {
      encryptedSharedPreferences.getBoolean(KEY_ENCRYPTION_ENABLED, true)
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
      true // デフォルトで暗号化を有効にする
    }
  }

  /**
   * 暗号化機能の有効/無効を設定
   */
  fun setEncryptionEnabled(enabled: Boolean) {
    try {
      encryptedSharedPreferences.edit()
        .putBoolean(KEY_ENCRYPTION_ENABLED, enabled)
        .apply()
      logger.i("Encryption ${if (enabled) "enabled" else "disabled"}")
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
    }
  }

  /**
   * データベース用のパスフレーズを生成・保存
   */
  fun getOrCreateDatabasePassphrase(): String {
    return try {
      val existingPassphrase = encryptedSharedPreferences.getString(KEY_DB_PASSPHRASE, null)
      if (existingPassphrase != null) {
        logger.d("Using existing database passphrase")
        existingPassphrase
      } else {
        val newPassphrase = generateSecurePassphrase()
        encryptedSharedPreferences.edit()
          .putString(KEY_DB_PASSPHRASE, newPassphrase)
          .apply()
        logger.i("Created new database passphrase")
        newPassphrase
      }
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
      throw SecurityException("Passphrase generation failed", e)
    }
  }

  /**
   * 機密データを暗号化して保存
   */
  fun saveSecureString(key: String, value: String) {
    try {
      encryptedSharedPreferences.edit()
        .putString(key, value)
        .apply()
      logger.d("Saved secure string for key: $key")
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
    }
  }

  /**
   * 暗号化されたデータを取得
   */
  fun getSecureString(key: String, defaultValue: String? = null): String? {
    return try {
      val value = encryptedSharedPreferences.getString(key, defaultValue)
      if (value != null) {
        logger.d("Retrieved secure string for key: $key")
      }
      value
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
      defaultValue
    }
  }

  /**
   * 暗号化されたデータを削除
   */
  fun removeSecureString(key: String) {
    try {
      encryptedSharedPreferences.edit()
        .remove(key)
        .apply()
      logger.d("Removed secure string for key: $key")
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
    }
  }

  /**
   * すべての暗号化データをクリア
   */
  fun clearAllSecureData() {
    try {
      encryptedSharedPreferences.edit()
        .clear()
        .apply()
      logger.w("Cleared all encrypted data")
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
    }
  }

  /**
   * セキュアなパスフレーズを生成
   */
  private fun generateSecurePassphrase(): String {
    val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
    val length = 32
    return (1..length)
      .map { charset.random() }
      .joinToString("")
  }

  /**
   * 暗号化システムの健全性をチェック
   */
  fun verifyEncryptionIntegrity(): Boolean {
    return try {
      val testKey = "integrity_test"
      val testValue = "test_encryption_${System.currentTimeMillis()}"

      // 書き込みテスト
      saveSecureString(testKey, testValue)

      // 読み取りテスト
      val retrievedValue = getSecureString(testKey)

      // クリーンアップ
      removeSecureString(testKey)

      val isValid = testValue == retrievedValue
      logger.i("Encryption integrity check: ${if (isValid) "PASSED" else "FAILED"}")
      isValid
    } catch (e: Exception) {
      logger.e("Encryption operation failed", e)
      false
    }
  }
}