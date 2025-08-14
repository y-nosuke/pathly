# Pathly セキュリティ設計

## 概要

Pathlyは位置情報を扱うアプリとして、ユーザーの個人情報保護とプライバシー確保を最優先とする。
本文書では、MVP実装で必要最小限のセキュリティ対策を定義する。

## 🔒 セキュリティ要件

### 法的要件

- **個人情報保護法**：位置情報は個人情報として適切に管理
- **Androidプライバシーポリシー**：Google Playストア要件準拠
- **データ最小化原則**：必要最小限の情報のみ取得・保存

### セキュリティ目標

1. **機密性**：位置情報の暗号化保存
2. **完全性**：データ改ざんの防止
3. **可用性**：ユーザーによるデータ制御
4. **透明性**：データ利用目的の明確化

## 📱 ローカルデータ保護

### 1. データベース暗号化

#### Room データベース暗号化

```kotlin
// SQLCipher使用によるRoom暗号化
implementation("net.zetetic:android-database-sqlcipher:4.5.4")

// 暗号化データベース初期化
val passphrase = generateSecurePassphrase()
val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))

Room.databaseBuilder(context, PathlyDatabase::class.java, "pathly_database")
    .openHelperFactory(factory)
    .build()
```

#### 暗号化対象データ

- **GPS座標**：緯度・経度の暗号化
- **タイムスタンプ**：記録日時の保護
- **移動距離**：計算結果の保護

### 2. 暗号化キー管理

#### Android Keystore使用

```kotlin
// セキュアなキー生成・保存
class SecureKeyManager {
    private val keyAlias = "pathly_db_key"
    
    fun generateOrGetKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(false)
        .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
```

#### キー保護方針

- **Android Keystore**：暗号化キーのハードウェア保護
- **キーローテーション**：定期的なキー更新（将来実装）
- **バックアップ除外**：キーのクラウドバックアップ防止

### 3. 機密データ削除

#### セキュア削除実装

```kotlin
class SecureDataDeletion {
    // 機密データの完全削除
    fun securelyDeleteTrack(trackId: String) {
        // 1. データベースからの削除
        trackDao.deleteTrack(trackId)
        
        // 2. メモリからのクリア
        Runtime.getRuntime().gc()
        
        // 3. 一時ファイルの削除
        clearTempFiles()
    }
    
    // ユーザーによる全データ削除
    fun deleteAllUserData() {
        // データベース完全削除
        context.deleteDatabase("pathly_database")
        
        // 設定データ削除
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .apply()
    }
}
```

## 🛡️ 権限管理

### 1. 位置情報権限

#### 段階的権限要求

```kotlin
class LocationPermissionManager {
    // 1. 基本的な位置情報権限
    private val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
    
    // 2. バックグラウンド位置情報権限（API 29+）
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    
    suspend fun requestLocationPermissions(activity: ComponentActivity): PermissionResult {
        // Step 1: 基本権限要求
        val basicResult = requestBasicLocationPermission(activity)
        if (basicResult != PermissionResult.GRANTED) {
            return basicResult
        }
        
        // Step 2: バックグラウンド権限要求（必要時のみ）
        return requestBackgroundLocationPermission(activity)
    }
}
```

#### 権限要求タイミング

- **アプリ起動時**：基本的な位置情報権限
- **記録開始時**：バックグラウンド位置情報権限
- **権限説明**：なぜ必要かを明確に説明

### 2. 最小権限原則

#### 必要最小限の権限

```xml
<!-- 必要な権限のみ宣言 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 不要な権限は宣言しない -->
<!-- <uses-permission android:name="android.permission.CAMERA" /> -->
<!-- <uses-permission android:name="android.permission.READ_CONTACTS" /> -->
```

#### 精度レベル管理

```kotlin
class LocationAccuracyManager {
    fun getLocationRequest(accuracyLevel: AccuracyLevel): LocationRequest {
        return when (accuracyLevel) {
            AccuracyLevel.HIGH -> LocationRequest.Builder(30_000) // 30秒間隔
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
                
            AccuracyLevel.BALANCED -> LocationRequest.Builder(60_000) // 60秒間隔
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
                
            AccuracyLevel.LOW_POWER -> LocationRequest.Builder(300_000) // 5分間隔
                .setPriority(Priority.PRIORITY_LOW_POWER)
                .build()
        }
    }
}
```

## 🔐 プライバシー保護

### 1. データ最小化

#### 収集データの制限

```kotlin
data class GpsPoint(
    val id: String,
    val trackId: String,
    
    // 必要最小限の位置情報
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val timestamp: Long,
    
    // 収集しない情報
    // - 詳細な住所情報
    // - デバイス固有ID
    // - 他のアプリ情報
    // - 連絡先情報
)
```

#### 精度制限

```kotlin
class LocationPrivacyFilter {
    // 精度を制限して保存
    fun filterLocation(location: Location): Location {
        return Location(location).apply {
            // 精度を100m以内に制限
            if (accuracy > 100f) {
                accuracy = 100f
            }
            
            // 不要な詳細情報を削除
            extras = null
            provider = null
        }
    }
}
```

### 2. データ保持期間

#### 自動削除機能

```kotlin
class DataRetentionManager {
    // デフォルト保持期間：1年
    private val defaultRetentionDays = 365L
    
    suspend fun cleanupOldData() {
        val cutoffTime = System.currentTimeMillis() - (defaultRetentionDays * 24 * 60 * 60 * 1000)
        
        // 古いトラッキングデータを削除
        trackDao.deleteTracksOlderThan(cutoffTime)
        gpsPointDao.deletePointsOlderThan(cutoffTime)
    }
    
    // ユーザー設定による保持期間変更
    fun setRetentionPeriod(days: Long) {
        preferences.edit()
            .putLong("data_retention_days", days)
            .apply()
    }
}
```

### 3. ユーザー制御

#### データ削除機能

```kotlin
class UserDataControl {
    // 個別記録の削除
    suspend fun deleteTrack(trackId: String) {
        secureDataDeletion.securelyDeleteTrack(trackId)
    }
    
    // 期間指定削除
    suspend fun deleteDataByDateRange(startDate: Long, endDate: Long) {
        trackDao.deleteTracksByDateRange(startDate, endDate)
    }
    
    // 全データ削除
    suspend fun deleteAllData() {
        secureDataDeletion.deleteAllUserData()
    }
    
    // データエクスポート（ユーザーの権利）
    suspend fun exportUserData(): String {
        val tracks = trackDao.getAllTracks()
        val gpsPoints = gpsPointDao.getAllPoints()
        return JsonExporter.exportToJson(tracks, gpsPoints)
    }
}
```

## 🚨 セキュリティ監視

### 1. 異常検知

#### 異常アクセス監視

```kotlin
class SecurityMonitor {
    // 異常な位置情報アクセス検知
    fun monitorLocationAccess() {
        val accessCount = getLocationAccessCount()
        val timeWindow = 3600_000L // 1時間
        
        if (accessCount > 1000) { // 1時間に1000回以上のアクセス
            logSecurityEvent("Abnormal location access detected")
            // 必要に応じてアクセス制限
        }
    }
    
    // 権限変更監視
    fun monitorPermissionChanges() {
        if (!hasLocationPermission()) {
            stopLocationTracking()
            notifyUserPermissionRequired()
        }
    }
}
```

### 2. セキュリティログ

#### ログ管理

```kotlin
class SecurityLogger {
    // セキュリティイベントのログ記録
    fun logSecurityEvent(event: String, details: Map<String, String> = emptyMap()) {
        val logEntry = SecurityLogEntry(
            timestamp = System.currentTimeMillis(),
            event = event,
            details = details.filterKeys { !isSensitiveKey(it) } // 機密情報除外
        )
        
        // ローカルログに記録（個人情報は含めない）
        localLogger.log(logEntry)
    }
    
    private fun isSensitiveKey(key: String): Boolean {
        val sensitiveKeys = setOf("latitude", "longitude", "address", "user_id")
        return sensitiveKeys.contains(key.lowercase())
    }
}
```

## ⚙️ セキュリティ設定

### 1. ユーザー設定項目

#### セキュリティ設定UI

```kotlin
// 設定画面でのセキュリティオプション
data class SecuritySettings(
    val locationAccuracy: AccuracyLevel = AccuracyLevel.BALANCED,
    val dataRetentionDays: Long = 365L,
    val autoDeleteEnabled: Boolean = true,
    val encryptionEnabled: Boolean = true, // 常にtrue（変更不可）
    val backgroundLocationEnabled: Boolean = true
)
```

#### 設定項目説明

- **位置精度**：バッテリー消費と精度のバランス選択
- **データ保持期間**：自動削除までの期間設定
- **自動削除**：古いデータの自動削除有効/無効
- **バックグラウンド記録**：バックグラウンド位置情報の使用許可

### 2. プライバシーポリシー

#### 必須記載事項

```markdown
## プライバシーポリシー必須項目

### 収集する情報
- GPS位置情報（緯度・経度・精度・タイムスタンプ）
- アプリ使用統計（クラッシュレポート等）

### 利用目的
- お出掛け記録の作成・表示
- 移動軌跡の地図表示
- アプリ機能の改善

### 第三者提供
- 原則として第三者に提供しない
- Google Maps APIでの地図表示のみ

### データ保持期間
- デフォルト：1年間
- ユーザー設定により変更可能
- ユーザーによる削除可能

### 問い合わせ先
- データ削除・修正の要求受付
```

## 🔧 実装チェックリスト

### MVP実装必須項目

#### データ保護

- [ ] Room データベースの暗号化実装
- [ ] Android Keystoreによるキー管理
- [ ] 機密データのセキュア削除機能

#### 権限管理

- [ ] 段階的な位置情報権限要求
- [ ] 権限拒否時の適切な処理
- [ ] 最小権限原則の実装

#### プライバシー

- [ ] データ最小化の実装
- [ ] ユーザーによるデータ削除機能
- [ ] プライバシーポリシーの作成

#### 監視・設定

- [ ] 基本的なセキュリティ監視
- [ ] ユーザーセキュリティ設定項目
- [ ] 異常時の適切な処理

### 将来実装項目

- [ ] キーローテーション機能
- [ ] より詳細な異常検知
- [ ] セキュリティ監査ログ
- [ ] データ匿名化機能

## 🚀 実装優先順位

### Phase 1: 基本セキュリティ（MVP必須）

1. Room データベース暗号化
2. 位置情報権限の適切な管理
3. ユーザーデータ削除機能

### Phase 2: 強化セキュリティ

4. セキュリティ監視機能
5. データ保持期間管理
6. 詳細プライバシー設定

### Phase 3: 高度セキュリティ（将来）

7. キーローテーション
8. データ匿名化
9. セキュリティ監査

---

このセキュリティ設計により、位置情報を安全に管理し、ユーザーのプライバシーを適切に保護できます。
MVP実装では Phase 1 の基本セキュリティ項目を優先して実装してください。
