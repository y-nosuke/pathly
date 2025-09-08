# ログ管理ガイドライン

## 概要

Pathlyアプリのログ出力に関する方針とガイドラインを定めています。
コードの可読性を保ちながら、効果的なデバッグとトラブルシューティングを実現することを目的としています。

## ログレベルの分類

### 1. エラー (Log.e)

**常に本番環境でも出力**

- アプリケーションの動作に重大な影響を与える問題
- ユーザーに影響するエラー

```kotlin
Log.e("LocationService", "Location permission not granted")
Log.e("LocationService", "Location services are disabled")
Log.e("TrackingViewModel", "Failed to save track to database", exception)
```

### 2. 警告 (Log.w)

**常に本番環境でも出力**

- 潜在的な問題や予期しない状況
- アプリは動作するが注意が必要な状況

```kotlin
Log.w("LocationService", "No location received after 30 seconds")
Log.w("LocationService", "GPS accuracy is very low: ${accuracy}m")
Log.w("TrackingViewModel", "Location service is null in observeLocationUpdates")
```

### 3. 情報 (Log.i)

**常に出力**

- 重要な状態変更やアプリの動作状況
- ユーザーアクションの記録

```kotlin
Log.i("LocationService", "Location tracking started")
Log.i("TrackingViewModel", "Track completed with ${pointCount} points")
```

### 4. デバッグ (Log.d)

**DEBUGビルドでのみ出力**

- 開発・デバッグ時に必要な情報
- 重要な処理の開始・終了

```kotlin
Log.d("LocationService", "startLocationTracking() called")
Log.d("LocationService", "Location updates requested successfully")
Log.d("TrackingViewModel", "Service connected")
```

## カスタムLoggerの使用

### Logger.ktの活用

プロジェクトでは `com.pathly.util.Logger` を提供しています：

```kotlin
import com.pathly.util.Logger

// 自動的にBuildConfig.DEBUGを考慮
Logger.d("LocationService", "Debug information")     // DEBUGビルドのみ
Logger.i("LocationService", "Important information") // 常に出力
Logger.w("LocationService", "Warning message")       // 常に出力
Logger.e("LocationService", "Error message")         // 常に出力

// 非常に詳細なデバッグ情報
Logger.verbose("LocationService", "Very detailed debug info") // DEBUGビルドのみ
```

### タグの命名規則

- **サービス**: `"LocationService"`, `"DatabaseService"`
- **ViewModel**: `"TrackingViewModel"`, `"HistoryViewModel"`
- **Repository**: `"GpsTrackRepository"`, `"UserRepository"`
- **UI**: `"TrackingScreen"`, `"MapScreen"`

## ログ出力の指針

### ✅ 推奨されるログ

1. **ユーザーアクションの開始/終了**

   ```kotlin
   Logger.d("TrackingViewModel", "User started GPS tracking")
   Logger.i("LocationService", "GPS tracking completed")
   ```

2. **重要な状態変更**

   ```kotlin
   Logger.i("LocationService", "Foreground service started")
   Logger.d("TrackingViewModel", "Service connected successfully")
   ```

3. **エラーと例外**

   ```kotlin
   Logger.e("LocationService", "Failed to request location updates", exception)
   Logger.w("TrackingViewModel", "Location permission denied")
   ```

4. **パフォーマンス関連**

   ```kotlin
   Logger.i("DatabaseService", "Saved ${pointCount} GPS points in ${duration}ms")
   ```

### ❌ 避けるべきログ

1. **冗長な詳細情報**

   ```kotlin
   // ❌ 避ける
   Logger.d("LocationService", "Permission check - Fine: true, Coarse: true")
   Logger.d("LocationService", "Location: lat=35.123456, lon=139.654321")

   // ✅ 必要な場合のみ
   Logger.verbose("LocationService", "Location: lat=${lat}, lon=${lon}, accuracy=${accuracy}")
   ```

2. **頻繁に呼ばれる処理内のログ**

   ```kotlin
   // ❌ 避ける（30秒間隔で大量ログが出力される）
   onLocationResult { location ->
       Logger.d("LocationService", "Location received: ${location}")
       // 処理...
   }
   ```

3. **機密情報を含むログ**

   ```kotlin
   // ❌ 絶対避ける
   Logger.d("AuthService", "User token: ${token}")
   Logger.d("UserService", "User email: ${email}")
   ```

## 開発時のログ戦略

### 1. 通常開発時

- 必要最小限のログを残す
- エラー・警告・重要な状態変更のみ

### 2. デバッグ時

- 問題箇所に一時的に `Logger.verbose()` を追加
- 問題解決後は削除またはコメントアウト

### 3. 新機能開発時

- 開発中は詳細ログを追加
- 完成後は必要なもののみ残して削除

## 本番環境での考慮事項

### リリースビルドでの自動除去

```kotlin
// DEBUGビルドでのみ実行される
if (BuildConfig.DEBUG) {
    Logger.d("LocationService", "Detailed debug info")
}

// Logger.d()は内部でBuildConfig.DEBUGをチェック済み
Logger.d("LocationService", "Auto-filtered debug info")
```

### パフォーマンス考慮

- 文字列フォーマットは必要な場合のみ実行
- ログレベルチェック後に重い処理を実行

```kotlin
// ✅ 推奨
if (BuildConfig.DEBUG) {
    Logger.d("LocationService", "Heavy computation result: ${heavyComputation()}")
}

// ❌ 避ける（リリースビルドでもheavyComputation()が実行される）
Logger.d("LocationService", "Heavy computation result: ${heavyComputation()}")
```

## トラブルシューティング

### 1. 位置情報関連の問題

```bash
# 関連ログを確認
adb logcat -s Pathly-LocationService Pathly-TrackingViewModel
```

### 2. データベース関連の問題

```bash
# データベース関連ログを確認
adb logcat -s Pathly-GpsTrackRepository Pathly-DatabaseService
```

### 3. 権限関連の問題

```bash
# 権限とサービス関連ログを確認
adb logcat -s Pathly-LocationService | grep -E "(permission|Permission)"
```

## まとめ

- **可読性重視**: 不要なログは削除し、コードをクリーンに保つ
- **目的明確**: エラー、警告、重要な状態変更に焦点を当てる
- **環境考慮**: DEBUGビルドと本番ビルドでログレベルを適切に分ける
- **継続改善**: 新しい問題が発生したら、必要に応じてログ戦略を見直す

このガイドラインに従うことで、効率的なデバッグとメンテナンスしやすいコードベースを維持できます。
