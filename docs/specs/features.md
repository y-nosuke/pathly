# Week1 GPS記録機能 - 詳細分析

## 📋 ユースケース分析

### 主要ユースケース: GPS軌跡の自動記録

**アクター**: お出掛けユーザー  
**目標**: 外出中の移動経路を自動的に記録する  
**前提条件**:

- アプリがインストール済み
- 位置情報権限が未設定または設定済み
- デバイスのGPS機能が有効

**成功シナリオ**:

1. ユーザーがアプリを起動
2. 位置情報権限の確認・取得
3. GPS記録サービスの開始
4. バックグラウンドでの継続記録
5. 記録の停止と保存

**失敗シナリオ**:

- 位置情報権限拒否
- GPS機能無効
- バッテリー不足
- ストレージ容量不足

---

## 🎯 詳細ユースケースシナリオ

### シナリオ 1: 初回起動時の権限取得

**状況**: ユーザーが初めてアプリを起動する

**正常フロー**:

1. **起動**: ユーザーがPathlyアプリをタップして起動
2. **権限チェック**: アプリが位置情報権限の状態を確認
3. **権限要求**: 位置情報権限が未許可の場合、権限要求ダイアログを表示
4. **権限許可**: ユーザーが「許可」をタップ
5. **バックグラウンド権限**: 「常に許可」の選択を促す
6. **記録準備**: GPS記録サービスが利用可能になる
7. **UI更新**: 記録開始ボタンがアクティブになる

**代替フロー**:

- **3a. 権限拒否**: ユーザーが「拒否」をタップ
  - 3a1. アプリ機能説明を表示
  - 3a2. 設定画面への導線を提供
  - 3a3. 権限なしでは記録不可の旨を通知

**技術実装ポイント**:

```kotlin
// 権限チェックと要求 (Kotlin Coroutines)
class LocationPermissionManager {
    suspend fun checkAndRequestPermissions(activity: ComponentActivity): PermissionResult {
        return withContext(Dispatchers.Main) {
            when {
                hasLocationPermission() -> PermissionResult.GRANTED
                shouldShowRationale() -> {
                    showRationaleDialog()
                    requestPermission()
                }
                else -> requestPermission()
            }
        }
    }
}
```

---

### シナリオ 2: GPS記録の開始

**状況**: ユーザーが外出前に記録を開始する

**正常フロー**:

1. **記録開始**: ユーザーが「記録開始」ボタンをタップ
2. **権限確認**: 位置情報権限の再確認
3. **GPS準備**: LocationManagerの初期化
4. **サービス開始**: ForegroundServiceとしてGPS記録サービス開始
5. **通知表示**: 記録中を示す通知をステータスバーに表示
6. **初回位置取得**: 最初のGPS座標を取得
7. **記録開始確認**: UIに記録中ステータスを表示

**代替フロー**:

- **2a. 権限失効**: 権限が取り消されている場合
  - 2a1. 権限再取得フローへ
- **6a. GPS取得失敗**: 初回GPS取得に失敗
  - 6a1. 再試行ロジック実行
  - 6a2. 最大3回リトライ後エラー表示

**技術実装ポイント**:

```kotlin
// GPS記録サービス (Foreground Service + Coroutines)
class LocationTrackingService : Service() {
    private val locationFlow = channelFlow {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            30_000L, // 30秒間隔
            0f,
            locationListener
        )
    }.flowOn(Dispatchers.IO)

    private fun startTracking() {
        serviceScope.launch {
            locationFlow
                .catch { e -> handleLocationError(e) }
                .collect { location ->
                    saveLocationToDatabase(location)
                }
        }
    }
}
```

---

### シナリオ 3: バックグラウンドでの継続記録

**状況**: ユーザーがアプリを閉じても記録が継続される

**正常フロー**:

1. **アプリ最小化**: ユーザーがホームボタンを押してアプリを閉じる
2. **サービス継続**: ForegroundServiceが記録を継続
3. **定期取得**: 30秒間隔でGPS座標を取得
4. **データ保存**: 取得した座標をローカルDBに保存
5. **通知更新**: 記録時間・距離を通知に反映
6. **バッテリー最適化**: 不要な処理を停止してバッテリー消費を抑制

**代替フロー**:

- **3a. GPS信号低下**: 屋内や地下でGPS精度が低下
  - 3a1. 最後の既知位置を使用
  - 3a2. ネットワーク位置情報で補完
- **6a. バッテリー低下**: バッテリーが20%以下になる
  - 6a1. 取得間隔を60秒に変更
  - 6a2. ユーザーに省電力モードを通知

**技術実装ポイント**:

```kotlin
// バッテリー最適化 + コルーチン
class LocationTrackingService {
    private fun adjustTrackingInterval() {
        serviceScope.launch {
            batteryLevelFlow
                .distinctUntilChanged()
                .collect { batteryLevel ->
                    val interval = when {
                        batteryLevel < 20 -> 60_000L // 1分間隔
                        batteryLevel < 50 -> 45_000L // 45秒間隔
                        else -> 30_000L // 30秒間隔
                    }
                    updateLocationRequestInterval(interval)
                }
        }
    }
}
```

---

### シナリオ 4: 記録の停止と保存

**状況**: ユーザーが帰宅後に記録を停止する

**正常フロー**:

1. **停止操作**: ユーザーが「記録停止」ボタンをタップ
2. **最終位置取得**: 最後のGPS座標を取得
3. **サービス停止**: LocationTrackingServiceを停止
4. **通知削除**: ステータスバーから記録中通知を削除
5. **データ整理**: 記録データの整合性チェック
6. **統計計算**: 総移動距離・時間を計算
7. **保存完了**: 記録完了を通知

**技術実装ポイント**:

```kotlin
// 記録停止とデータ処理
class TrackingRepository {
    suspend fun stopTracking(trackId: String): TrackingSummary {
        return withContext(Dispatchers.IO) {
            val track = trackDao.getTrackById(trackId)
            val gpsPoints = gpsPointDao.getPointsByTrackId(trackId)

            val summary = calculateTrackingSummary(gpsPoints)
            trackDao.updateTrackEndTime(trackId, System.currentTimeMillis())

            summary
        }
    }

    private fun calculateTrackingSummary(points: List<GpsPoint>): TrackingSummary {
        val totalDistance = points.zipWithNext { a, b ->
            calculateDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        }.sum()

        return TrackingSummary(
            totalDistance = totalDistance,
            duration = points.last().timestamp - points.first().timestamp,
            pointCount = points.size
        )
    }
}
```

---

## ⚙️ Android Kotlin + コルーチン実装の技術ポイント

### 1. アーキテクチャ設計

```kotlin
// MVVM + Clean Architecture
class LocationTrackingViewModel(
    private val trackingRepository: TrackingRepository,
    private val locationService: LocationTrackingService
) : ViewModel() {

    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    fun startTracking() {
        viewModelScope.launch {
            try {
                _trackingState.value = TrackingState.STARTING
                val trackId = trackingRepository.createNewTrack()
                locationService.startTracking(trackId)
                _trackingState.value = TrackingState.RECORDING
            } catch (e: Exception) {
                _trackingState.value = TrackingState.ERROR(e.message)
            }
        }
    }
}
```

### 2. コルーチンベースのGPS取得

```kotlin
class LocationProvider {
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val locationListener = LocationListener { location ->
            trySend(location)
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            30_000L,
            0f,
            locationListener
        )

        awaitClose {
            locationManager.removeUpdates(locationListener)
        }
    }.flowOn(Dispatchers.IO)
}
```

### 3. Room データベース設計

```kotlin
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long? = null,
    val isActive: Boolean = true
)

@Entity(tableName = "gps_points")
data class GpsPoint(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float? = null
)

@Dao
interface GpsPointDao {
    @Insert
    suspend fun insertGpsPoint(point: GpsPoint)

    @Query("SELECT * FROM gps_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun getPointsByTrackId(trackId: String): List<GpsPoint>
}
```

### 4. エラーハンドリング

```kotlin
class LocationTrackingService {
    private fun handleLocationError(error: Throwable) {
        when (error) {
            is SecurityException -> notifyPermissionError()
            is LocationException -> retryLocationRequest()
            else -> {
                Timber.e(error, "Unexpected location error")
                stopSelf()
            }
        }
    }

    private suspend fun retryLocationRequest() {
        repeat(3) { attempt ->
            delay(5000 * (attempt + 1)) // 指数バックオフ
            try {
                requestLocationUpdates()
                return
            } catch (e: Exception) {
                if (attempt == 2) throw e
            }
        }
    }
}
```

---

## 📊 パフォーマンス考慮事項

### バッテリー最適化

- GPS取得間隔の動的調整
- 不要な処理の停止
- バックグラウンド制限への対応

### メモリ管理

- GPSポイントのバッチ処理
- 古いデータの定期削除
- Flow による反応性とメモリ効率

### データベース最適化

- インデックス設定
- バッチInsert
- トランザクション管理

---

## 🧪 テスト戦略

### 単体テスト

```kotlin
class LocationTrackingRepositoryTest {
    @Test
    fun `GPS記録開始時に新しいTrackが作成される`() = runTest {
        // Given
        val repository = LocationTrackingRepository(mockDao)

        // When
        val trackId = repository.startNewTracking()

        // Then
        verify(mockDao).insertTrack(any())
        assertThat(trackId).isNotEmpty()
    }
}
```

### 統合テスト

```kotlin
@Test
fun `GPS記録からデータベース保存まで一連の流れ`() = runTest {
    // GPS取得 → データ変換 → DB保存の流れをテスト
}
```

### UI テスト

```kotlin
@Test
fun `記録開始ボタンタップで記録が開始される`() {
    // Jetpack Compose UIテスト
}
```

この詳細分析により、Week1のGPS記録機能の実装に必要な全ての要素が明確になりました。
