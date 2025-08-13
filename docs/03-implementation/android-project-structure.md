# Pathly Android プロジェクト初期構造 - MVVM + Repository パターン

## 📂 推奨ディレクトリ構造

```text
pathly-android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/pathly/
│   │   │   │   ├── PathlyApplication.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   │
│   │   │   │   ├── data/                           # データ層
│   │   │   │   │   ├── local/                      # ローカルデータソース
│   │   │   │   │   │   ├── database/
│   │   │   │   │   │   │   ├── PathlyDatabase.kt
│   │   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   │   ├── Track.kt
│   │   │   │   │   │   │   │   └── GpsPoint.kt
│   │   │   │   │   │   │   └── dao/
│   │   │   │   │   │   │       ├── TrackDao.kt
│   │   │   │   │   │   │       └── GpsPointDao.kt
│   │   │   │   │   │   └── preferences/
│   │   │   │   │   │       └── PathlyPreferences.kt
│   │   │   │   │   │
│   │   │   │   │   └── repository/                 # Repository実装
│   │   │   │   │       ├── LocationTrackingRepositoryImpl.kt
│   │   │   │   │       └── TrackingDataRepositoryImpl.kt
│   │   │   │   │
│   │   │   │   ├── domain/                         # ドメイン層
│   │   │   │   │   ├── model/                      # ドメインモデル
│   │   │   │   │   │   ├── TrackingSummary.kt
│   │   │   │   │   │   ├── LocationPoint.kt
│   │   │   │   │   │   └── TrackingState.kt
│   │   │   │   │   │
│   │   │   │   │   ├── repository/                 # Repository インターフェース
│   │   │   │   │   │   ├── LocationTrackingRepository.kt
│   │   │   │   │   │   └── TrackingDataRepository.kt
│   │   │   │   │   │
│   │   │   │   │   └── usecase/                    # ユースケース
│   │   │   │   │       ├── StartTrackingUseCase.kt
│   │   │   │   │       ├── StopTrackingUseCase.kt
│   │   │   │   │       └── GetTrackingHistoryUseCase.kt
│   │   │   │   │
│   │   │   │   ├── presentation/                   # プレゼンテーション層
│   │   │   │   │   ├── ui/
│   │   │   │   │   │   ├── theme/                  # Jetpack Composeテーマ
│   │   │   │   │   │   │   ├── Color.kt
│   │   │   │   │   │   │   ├── Theme.kt
│   │   │   │   │   │   │   └── Type.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── tracking/               # GPS記録画面
│   │   │   │   │   │   │   ├── TrackingScreen.kt
│   │   │   │   │   │   │   ├── TrackingViewModel.kt
│   │   │   │   │   │   │   └── components/
│   │   │   │   │   │   │       ├── TrackingButton.kt
│   │   │   │   │   │   │       └── TrackingStatusCard.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── history/                # 履歴画面
│   │   │   │   │   │   │   ├── HistoryScreen.kt
│   │   │   │   │   │   │   ├── HistoryViewModel.kt
│   │   │   │   │   │   │   └── components/
│   │   │   │   │   │   │       └── TrackingHistoryItem.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── map/                    # 地図画面 (Phase 2)
│   │   │   │   │   │   │   ├── MapScreen.kt
│   │   │   │   │   │   │   └── MapViewModel.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   └── common/                 # 共通UIコンポーネント
│   │   │   │   │   │       ├── LoadingIndicator.kt
│   │   │   │   │   │       └── ErrorMessage.kt
│   │   │   │   │   │
│   │   │   │   │   └── navigation/                 # ナビゲーション
│   │   │   │   │       └── PathlyNavigation.kt
│   │   │   │   │
│   │   │   │   ├── service/                        # バックグラウンドサービス
│   │   │   │   │   ├── LocationTrackingService.kt
│   │   │   │   │   └── TrackingNotificationManager.kt
│   │   │   │   │
│   │   │   │   ├── permission/                     # 権限管理
│   │   │   │   │   ├── LocationPermissionManager.kt
│   │   │   │   │   └── PermissionResult.kt
│   │   │   │   │
│   │   │   │   ├── location/                       # 位置情報関連
│   │   │   │   │   ├── LocationProvider.kt
│   │   │   │   │   ├── LocationUtils.kt
│   │   │   │   │   └── BatteryOptimizer.kt
│   │   │   │   │
│   │   │   │   └── di/                            # 依存性注入
│   │   │   │       ├── DatabaseModule.kt
│   │   │   │       ├── RepositoryModule.kt
│   │   │   │       ├── UseCaseModule.kt
│   │   │   │       └── LocationModule.kt
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   ├── mipmap/
│   │   │   │   ├── values/
│   │   │   │   └── xml/
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── test/                                   # 単体テスト
│   │   │   └── java/com/pathly/
│   │   │       ├── data/
│   │   │       │   └── repository/
│   │   │       │       └── LocationTrackingRepositoryTest.kt
│   │   │       │
│   │   │       ├── domain/
│   │   │       │   └── usecase/
│   │   │       │       └── StartTrackingUseCaseTest.kt
│   │   │       │
│   │   │       └── presentation/
│   │   │           └── tracking/
│   │   │               └── TrackingViewModelTest.kt
│   │   │
│   │   └── androidTest/                           # UI・統合テスト
│   │       └── java/com/pathly/
│   │           ├── database/
│   │           │   └── PathlyDatabaseTest.kt
│   │           │
│   │           └── ui/
│   │               └── tracking/
│   │                   └── TrackingScreenTest.kt
│   │
│   ├── build.gradle.kts                           # アプリレベルbuild.gradle
│   └── proguard-rules.pro
│
├── build.gradle.kts                               # プロジェクトレベルbuild.gradle
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

---

## 🏗️ 主要ファイルの詳細構成

### 1. データ層 (Data Layer)

#### **PathlyDatabase.kt**

```kotlin
@Database(
    entities = [Track::class, GpsPoint::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class PathlyDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun gpsPointDao(): GpsPointDao
}
```

#### **Track.kt (Entity)**

```kotlin
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

#### **TrackDao.kt**

```kotlin
@Dao
interface TrackDao {
    @Insert
    suspend fun insertTrack(track: Track)

    @Query("SELECT * FROM tracks ORDER BY startTime DESC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("UPDATE tracks SET endTime = :endTime, isActive = false WHERE id = :trackId")
    suspend fun updateTrackEndTime(trackId: String, endTime: Long)
}
```

---

### 2. ドメイン層 (Domain Layer)

#### **LocationTrackingRepository.kt (Interface)**

```kotlin
interface LocationTrackingRepository {
    suspend fun startNewTracking(): String
    suspend fun stopTracking(trackId: String): TrackingSummary
    suspend fun isTrackingActive(): Boolean
    fun getActiveTrackingState(): Flow<TrackingState>
}
```

#### **StartTrackingUseCase.kt**

```kotlin
class StartTrackingUseCase(
    private val repository: LocationTrackingRepository,
    private val permissionManager: LocationPermissionManager
) {
    suspend operator fun invoke(): Result<String> {
        return try {
            when (permissionManager.checkPermission()) {
                PermissionResult.GRANTED -> {
                    val trackId = repository.startNewTracking()
                    Result.success(trackId)
                }
                else -> Result.failure(PermissionDeniedException())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

### 3. プレゼンテーション層 (Presentation Layer)

#### **TrackingViewModel.kt**

```kotlin
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val startTrackingUseCase: StartTrackingUseCase,
    private val stopTrackingUseCase: StopTrackingUseCase,
    private val repository: LocationTrackingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun startTracking() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            startTrackingUseCase()
                .onSuccess { trackId ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isTracking = true,
                        activeTrackId = trackId
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }
}
```

#### **TrackingScreen.kt**

```kotlin
@Composable
fun TrackingScreen(
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TrackingStatusCard(
            isTracking = uiState.isTracking,
            duration = uiState.duration,
            distance = uiState.distance
        )

        Spacer(modifier = Modifier.height(32.dp))

        TrackingButton(
            isTracking = uiState.isTracking,
            isLoading = uiState.isLoading,
            onStartClick = { viewModel.startTracking() },
            onStopClick = { viewModel.stopTracking() }
        )
    }
}
```

---

### 4. サービス層 (Service Layer)

#### **LocationTrackingService.kt**

```kotlin
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var repository: LocationTrackingRepository
    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var notificationManager: TrackingNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        startForeground(NOTIFICATION_ID, notificationManager.createNotification())

        serviceScope.launch {
            locationProvider.getLocationUpdates()
                .catch { e -> handleLocationError(e) }
                .collect { location ->
                    repository.saveGpsPoint(location)
                    notificationManager.updateNotification(location)
                }
        }
    }
}
```

---

## 📦 依存関係 (build.gradle.kts)

### **app/build.gradle.kts**

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    kapt("com.google.dagger:hilt-compiler:2.48")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0") // Phase 2

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}
```

---

## 🔧 重要な設定ファイル

### **AndroidManifest.xml**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 権限 -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".PathlyApplication"
        android:allowBackup="true"
        android:theme="@style/Theme.Pathly">

        <!-- メインアクティビティ -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Pathly">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- バックグラウンドサービス -->
        <service
            android:name=".service.LocationTrackingService"
            android:foregroundServiceType="location"
            android:exported="false" />

    </application>
</manifest>
```

### **PathlyApplication.kt**

```kotlin
@HiltAndroidApp
class PathlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber初期化 (ログ出力)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

---

## 🏛️ アーキテクチャの特徴

### **MVVM + Repository パターン**

1. **Presentation Layer**: UI (Compose) + ViewModel
2. **Domain Layer**: UseCase + Repository Interface + Model
3. **Data Layer**: Repository Implementation + Database + API

### **コルーチン活用**

- **viewModelScope**: UI状態管理
- **Flow**: リアクティブなデータ流れ
- **withContext**: スレッド切り替え
- **CoroutineScope**: Service内での非同期処理

### **依存性注入 (Hilt)**

- モジュール化された依存関係
- テスト時のモック注入対応
- ライフサイクル対応のスコープ管理

---

## 🚀 開発手順の提案

### Phase 1: 基盤構築 (Week 1-2)

1. **プロジェクト初期化**
   - Android Studio新規プロジェクト作成
   - 依存関係の追加
   - 基本的なディレクトリ構造作成

2. **データベース構築**
   - Room Entity作成 (Track, GpsPoint)
   - DAO インターフェース実装
   - Database クラス作成

3. **権限管理**
   - LocationPermissionManager実装
   - 権限要求フロー実装

### Phase 2: コア機能実装 (Week 3-4)

1. **Location Provider**
   - GPS取得サービス実装
   - Flow ベースの位置情報配信

2. **Repository 実装**
   - LocationTrackingRepository実装
   - UseCase クラス実装

3. **ViewModel & UI**
   - TrackingViewModel実装
   - Jetpack Compose UI実装

### Phase 3: サービス統合 (Week 5-6)

1. **Foreground Service**
   - LocationTrackingService実装
   - 通知管理機能

2. **統合テスト**
   - エンドツーエンドテスト
   - パフォーマンス検証

この構造により、Week1のGPS記録機能から段階的に機能拡張できる堅牢なベースが構築できます！
