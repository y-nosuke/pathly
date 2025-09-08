# Androidテスト戦略

## Androidテストの基礎知識

### Androidテストの種類

Androidアプリケーションには主に**2種類のテスト**があります：

#### 1. ユニットテスト（Unit Tests）

- **実行環境**: 開発用PC上のJVM
- **場所**: `app/src/test/`
- **特徴**:
  - 非常に高速（数秒で数百テスト実行可能）
  - Android端末やエミュレーターが不要
  - Androidフレームワークに依存しない純粋なJavaKotlinコード
- **テスト対象**: ビジネスロジック、計算処理、データ変換など
- **例**: 距離計算、日付フォーマット、バリデーション処理

#### 2. インストルメンテーションテスト（Instrumentation Tests）

- **実行環境**: Android端末またはエミュレーター
- **場所**: `app/src/androidTest/`
- **特徴**:
  - 実行に時間がかかる（数分）
  - 実際のAndroidシステム上で動作
  - Androidフレームワーク、データベース、UI部品にアクセス可能
- **テスト対象**: UI操作、データベース、センサー、権限システムなど
- **例**: 画面タップ、データベース保存、GPS機能、カメラ機能

### テストピラミッド（推奨構成）

```text
        /\
       /UI\     ← 少数の重要なUIテスト
      /____\      （インストルメンテーションテスト）
     /      \
    /統合テスト\   ← 各レイヤー間の連携テスト
   /________\      （一部インストルメンテーション）
  /          \
 /ユニットテスト\  ← 大多数のテスト
/__________\       （JVMで高速実行）
```

**理想的な割合**:

- ユニットテスト: 70%（高速、安定、保守しやすい）
- 統合テスト: 20%（レイヤー間の動作確認）
- UIテスト: 10%（ユーザー体験の重要部分のみ）

### どちらを選ぶべきか？

| テスト対象       | テストの種類             | 理由                         |
| ---------------- | ------------------------ | ---------------------------- |
| 計算・ロジック   | ユニットテスト           | Androidに依存しない、高速    |
| ViewModel        | ユニットテスト           | MockでRepository等を代替可能 |
| データベース操作 | インストルメンテーション | 実際のSQLiteが必要           |
| UI操作・表示     | インストルメンテーション | 実際の画面描画が必要         |
| センサー・権限   | インストルメンテーション | Androidシステムが必要        |

## テスト方針概要

Pathlyプロジェクトでは、Clean Architecture構成に基づき、層ごとに適切なテストアプローチを採用します。テストの安定性と実行速度を両立し、継続的なリファクタリングをサポートする包括的なテスト戦略を実装しています。

## テスト構成

### テスト分類と実行環境

```text
android/app/src/
├── test/                   # ユニットテスト（JVMで実行）
│   └── java/com/pathly/    # 高速、ビジネスロジック中心
└── androidTest/            # インストルメンテーションテスト（Android端末で実行）
    └── java/com/pathly/    # UI、データベース、統合テスト
```

### 各レイヤーのテスト戦略

#### 1. ドメイン層テスト（ユニットテスト）

- **場所**: `app/src/test/java/com/pathly/domain/`
- **目的**: ビジネスロジックの検証
- **特徴**: 依存なし、高速実行

```kotlin
// 例: GpsTrackTest.kt
@Test
fun calculateDistance_twoPoints_returnsCorrectDistance() {
    // ドメインモデルの距離計算ロジックをテスト
}
```

#### 2. データ層テスト

##### ユニットテスト

- **場所**: `app/src/test/java/com/pathly/data/`
- **対象**: Repository実装、コンバーター、エンティティ変換
- **特徴**: DAOをモック化、高速実行

##### インストルメンテーションテスト  

- **場所**: `app/src/androidTest/java/com/pathly/data/`
- **対象**: DAO、データベース統合、Repository統合テスト
- **特徴**: 実際のSQLite使用、Roomの動作検証

```kotlin
// 例: GpsTrackDaoTest.kt（Android Test）
@Before
fun setup() {
    database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PathlyDatabase::class.java
    ).allowMainThreadQueries().build()
}
```

#### 3. プレゼンテーション層テスト

##### ViewModelテスト（ユニットテスト）

- **場所**: `app/src/test/java/com/pathly/presentation/`
- **対象**: ViewModel、状態管理ロジック
- **特徴**: Repository、Contextをモック化

```kotlin
// 例: TrackingViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository = mockk<GpsTrackRepository>(relaxed = true)
}
```

##### UIテスト（インストルメンテーションテスト）

- **場所**: `app/src/androidTest/java/com/pathly/presentation/`  
- **対象**: Compose UI、ユーザーインタラクション
- **特徴**: 実際のコンポーネント描画とインタラクション

```kotlin
// 例: HistoryScreenTest.kt
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun historyScreen_initialState_showsTitle() {
    composeTestRule.setContent {
        PathlyAndroidTheme {
            HistoryScreen(viewModel = mockViewModel)
        }
    }
    composeTestRule.onNodeWithText("外出履歴").assertIsDisplayed()
}
```

## テクニカルガイドライン

### 1. 依存関係とツール

#### テストライブラリ構成

```kotlin
// ユニットテスト
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("androidx.arch.core:core-testing:2.2.0")
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("app.cash.turbine:turbine:1.0.0")

// インストルメンテーションテスト
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
androidTestImplementation("androidx.room:room-testing:2.6.1")
androidTestImplementation("io.mockk:mockk-android:1.13.8")
```

#### Gradle設定（重要）

```kotlin
// build.gradle.kts - ライセンスファイル重複エラーの回避
packaging {
    resources {
        excludes += "META-INF/LICENSE.md"
        excludes += "META-INF/LICENSE-notice.md"
    }
}
```

### 2. コルーチンテストパターン

#### StandardTestDispatcher使用

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun someAsyncFunction() = runTest {
        // コルーチンを使ったテスト
    }
}
```

### 3. モッキング戦略

#### MockKを使用したモック

```kotlin
// Repository のモック
private val mockRepository = mockk<GpsTrackRepository>(relaxed = true)

// 戻り値を設定
coEvery { mockRepository.getAllTracks() } returns flowOf(tracks)

// 関数呼び出しの検証
coVerify { mockRepository.saveTrack(any()) }
```

#### Androidコンポーネントのモック

```kotlin
// Context、Applicationのモック
private val mockApplication = mockk<Application>(relaxed = true)

// 権限チェックのモック
mockkStatic("androidx.core.content.ContextCompat")
every {
    androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
} returns PackageManager.PERMISSION_GRANTED
```

### 4. データベーステストパターン

#### In-Memoryデータベース使用

```kotlin
@Before
fun setup() {
    database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PathlyDatabase::class.java
    )
        .allowMainThreadQueries()  // テスト用の設定
        .build()
}

@After
fun tearDown() {
    database.close()
}
```

### 5. Compose UIテストパターン

#### 基本的なテスト構造

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun screenTest() {
    // Given - テストデータ準備
    val testData = createTestData()

    // When - UIをセットアップ
    composeTestRule.setContent {
        PathlyAndroidTheme {
            TestScreen(data = testData)
        }
    }

    // Then - UI要素を検証
    composeTestRule
        .onNodeWithText("期待されるテキスト")
        .assertIsDisplayed()
}
```

#### 複数要素の検証

```kotlin
// 同じContentDescriptionが複数ある場合
composeTestRule
    .onAllNodesWithContentDescription("削除")
    .assertCountEquals(3)  // 3つの削除ボタンが存在することを確認

// 最初の要素のみテスト
composeTestRule
    .onAllNodesWithContentDescription("削除")[0]
    .performClick()
```

#### State変更のテスト

```kotlin
@Test
fun stateChangeTest() {
    // UIの状態変更をテスト
    composeTestRule.runOnUiThread {
        uiStateFlow.value = newState
    }

    // 変更後のUIを検証
    composeTestRule
        .onNodeWithText("新しい状態")
        .assertIsDisplayed()
}
```

## テスト実行コマンド

### Gradleコマンド

```bash
# すべてのユニットテスト実行
./gradlew test

# すべてのインストルメンテーションテスト実行  
./gradlew connectedAndroidTest

# 特定のテストクラス実行
./gradlew test --tests "com.pathly.presentation.tracking.TrackingViewModelTest"

# テストレポート生成
./gradlew testDebugUnitTest
# レポート: app/build/reports/tests/testDebugUnitTest/index.html
```

### Android Studioでの実行

- 個別テスト: テストメソッド左の緑矢印をクリック
- クラス単位: テストクラス名を右クリック → "Run"
- パッケージ単位: テストパッケージを右クリック → "Run tests"

## 品質保証とベストプラクティス

### 1. テストの命名規則

```kotlin
// パターン: [MethodName]_[Scenario]_[ExpectedResult]
fun startTracking_withValidPermissions_updatesStateToActive()
fun calculateDistance_twoPointsSameLocation_returnsZero()
fun historyScreen_emptyTracks_showsEmptyMessage()
```

### 2. Given-When-Thenパターンの徹底

```kotlin
@Test
fun someTest() {
    // Given - テストの前提条件
    val inputData = createTestData()
    val mockBehavior = setupMockBehavior()

    // When - テスト対象の実行
    val result = targetFunction(inputData)

    // Then - 結果の検証
    assertEquals(expectedResult, result)
    verify { mockObject.expectedCall() }
}
```

### 3. テストデータの管理

```kotlin
// テストヘルパー関数を作成
private fun createSampleTrack(
    id: Long = 1L,
    pointsCount: Int = 2
): GpsTrack {
    return GpsTrack(
        id = id,
        startTime = Date(),
        // ... 他のプロパティ
        points = createSamplePoints(pointsCount)
    )
}
```

### 4. 非同期処理のテスト

```kotlin
@Test
fun asyncTest() = runTest {
    // StateFlowのテスト
    val stateValues = mutableListOf<State>()
    val job = launch {
        viewModel.uiState.collect { stateValues.add(it) }
    }

    // 処理実行
    viewModel.performAction()

    // 状態変更を検証
    assertEquals(expectedState, stateValues.last())
    job.cancel()
}
```

## エラー対応とトラブルシューティング

### よくあるエラーと解決策

#### 1. ライセンスファイル重複エラー

```bash
6 files found with path 'META-INF/LICENSE.md'
```

**解決**: `build.gradle.kts`に`packaging`ブロックを追加

#### 2. Compose UI複数要素エラー  

```bash
Expected at most 1 node but found 3 nodes
```

**解決**: `onNodeWithX` → `onAllNodesWithX().assertCountEquals(n)`に変更

#### 3. コルーチンテストのタイムアウト

**解決**: `StandardTestDispatcher`使用、適切な`runTest`適用

#### 4. データベースのリークエラー

**解決**: `@After`で必ず`database.close()`実行

### テスト実行環境

#### Android Emulator設定（推奨）

- **API Level**: 36 (Android 16+)
- **Device**: Medium Phone API 36
- **特徴**: Compose UI、Room、権限システムの完全サポート

#### 実機テスト

- **最小要件**: API 34 (Android 14) 以上
- **権限**: 位置情報権限が必要（LocationTrackingService用）

## 継続的インテグレーション

### GitHub Actions対応（将来実装予定）

```yaml
# .github/workflows/android-tests.yml
- name: Run Unit Tests
  run: ./gradlew test

- name: Run Instrumentation Tests  
  run: ./gradlew connectedAndroidTest
```

### テストカバレッジ目標

- **ユニットテスト**: 80%以上（ビジネスロジック）
- **統合テスト**: 主要フロー100%カバー
- **UIテスト**: クリティカルパス100%カバー

## まとめ

このテスト戦略により、Pathlyアプリケーションの品質と安定性を確保し、リファクタリング時の安全性を提供します。Clean Architectureの各層に適したテストアプローチを採用することで、効率的で保守可能なテストスイートを構築しています。
