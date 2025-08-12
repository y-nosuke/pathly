# US-001: GPS経路の自動記録・保存 - 実装タスク

## 🎯 ユーザーストーリー

**As a** お出掛けが好きな人  
**I want** 外出中にGPS軌跡を自動的に記録してもらいたい  
**So that** 手動操作なしで移動経路を残すことができる  

**関連ドキュメント**: [requirements/user-stories.md](../../requirements/user-stories.md#1-gps経路の自動記録保存)

---

## ✅ 実装タスク

### Phase 1: 権限管理・基盤準備

#### **T001: 位置情報権限管理システム**

- [ ] **LocationPermissionManager.kt** 実装
  - 位置情報権限の状態確認
  - 権限要求ダイアログ表示
  - バックグラウンド権限（ACCESS_BACKGROUND_LOCATION）対応
  - 権限拒否時の適切なエラーハンドリング
- [ ] **PermissionResult.kt** 実装
  - 権限結果の状態管理（GRANTED/DENIED/RATIONALE_REQUIRED）
- **工数**: 4時間  
- **期限**: 12/10  
- **依存**: なし  
- **重要度**: 🔴 High

#### **T002: Room データベース基盤**

- [ ] **Track.kt** Entity 実装
  - id, startTime, endTime, isActive, createdAt フィールド
- [ ] **GpsPoint.kt** Entity 実装  
  - id, trackId, latitude, longitude, accuracy, timestamp, speed フィールド
- [ ] **TrackDao.kt** 実装
  - insertTrack, getAllTracks, updateTrackEndTime メソッド
- [ ] **GpsPointDao.kt** 実装
  - insertGpsPoint, getPointsByTrackId メソッド
- [ ] **PathlyDatabase.kt** 実装
  - Room データベース設定、TypeConverter設定
- **工数**: 5時間  
- **期限**: 12/11  
- **依存**: なし  
- **重要度**: 🔴 High

---

### Phase 2: GPS機能実装

#### **T003: GPS位置情報取得システム**

- [ ] **LocationProvider.kt** 実装
  - Flow ベースの位置情報取得
  - 30秒間隔での座標取得設定
  - PRIORITY_BALANCED_POWER_ACCURACY 使用
  - GPS信号低下時のフォールバック処理
- [ ] **LocationUtils.kt** 実装
  - 距離計算ユーティリティ
  - 位置情報の精度チェック
- **工数**: 6時間  
- **期限**: 12/13  
- **依存**: T001 (権限管理)  
- **重要度**: 🔴 High

#### **T004: バックグラウンドサービス**

- [ ] **LocationTrackingService.kt** 実装
  - Foreground Service として実装
  - GPS座標の継続取得・保存
  - サービス開始・停止制御
  - コルーチンベースの非同期処理
- [ ] **TrackingNotificationManager.kt** 実装
  - 記録中通知の表示・更新
  - 記録時間・距離の通知表示
  - 通知からの停止アクション
- **工数**: 8時間  
- **期限**: 12/15  
- **依存**: T003 (LocationProvider), T002 (Database)  
- **重要度**: 🔴 High

---

### Phase 3: Repository・UseCase層

#### **T005: Repository実装**

- [ ] **LocationTrackingRepository** インターフェース実装
  - startNewTracking, stopTracking, isTrackingActive メソッド
  - getActiveTrackingState の Flow 実装
- [ ] **LocationTrackingRepositoryImpl.kt** 実装
  - データベースとサービス層の統合
  - GPS データの保存ロジック
  - 記録状態の管理
- **工数**: 4時間  
- **期限**: 12/16  
- **依存**: T004 (Service), T002 (Database)  
- **重要度**: 🟡 Medium

#### **T006: UseCase実装**

- [ ] **StartTrackingUseCase.kt** 実装
  - 権限チェック + GPS記録開始のビジネスロジック
  - エラーハンドリング（権限不足、GPS無効など）
- [ ] **StopTrackingUseCase.kt** 実装
  - 記録停止 + 統計計算のビジネスロジック
  - TrackingSummary の生成
- **工数**: 3時間  
- **期限**: 12/16  
- **依存**: T005 (Repository)  
- **重要度**: 🟡 Medium

---

### Phase 4: UI・ViewModel統合

#### **T007: ViewModel実装**

- [ ] **TrackingViewModel.kt** 実装
  - StateFlow による状態管理（TrackingUiState）
  - startTracking, stopTracking メソッド
  - エラー状態の管理
  - 記録時間・距離のリアルタイム更新
- [ ] **TrackingState.kt** ドメインモデル実装
  - STOPPED, STARTING, RECORDING, ERROR 状態管理
- **工数**: 5時間  
- **期限**: 12/17  
- **依存**: T006 (UseCase)  
- **重要度**: 🟡 Medium

#### **T008: UI実装**

- [ ] **TrackingScreen.kt** 実装
  - Jetpack Compose ベースのUI
  - 記録開始・停止ボタン
  - 記録状態の表示
- [ ] **TrackingButton.kt** コンポーネント実装
  - 記録開始・停止ボタンコンポーネント
  - ローディング状態の表示
- [ ] **TrackingStatusCard.kt** コンポーネント実装
  - 記録時間・距離・状態の表示
- **工数**: 6時間  
- **期限**: 12/18  
- **依存**: T007 (ViewModel)  
- **重要度**: 🟡 Medium

---

### Phase 5: テスト・統合

#### **T009: 単体テスト**

- [ ] **LocationPermissionManagerTest.kt**
- [ ] **LocationProviderTest.kt**
- [ ] **StartTrackingUseCaseTest.kt**
- [ ] **TrackingViewModelTest.kt**
- **工数**: 6時間  
- **期限**: 12/19  
- **重要度**: 🟢 Low

#### **T010: 統合テスト**

- [ ] GPS記録開始から保存まで一連のフロー
- [ ] バックグラウンド動作の確認
- [ ] 権限拒否時の動作確認
- **工数**: 4時間  
- **期限**: 12/20  
- **重要度**: 🟢 Low

---

## 📊 進捗管理

### 全体進捗

- **全体進捗**: ░░░░░░░░░░ 0% (0/10)
- **完了タスク**: 0/10
- **今週予定**: T001, T002, T003

### 今週のフォーカス（12/9-12/15）

1. **月曜日**: T001 LocationPermissionManager 実装開始
2. **火曜日**: T001 完成 + T002 Database Entity 実装
3. **水曜日**: T002 完成 + T003 LocationProvider 実装開始
4. **木曜日**: T003 完成
5. **金曜日**: T004 LocationTrackingService 実装開始

### ブロッカー・リスク

- 現在なし

---

## 🧪 受け入れテスト

各受け入れ基準の確認方法：

### AC-1: 位置情報許可要求

- [ ] **テスト**: アプリ初回起動時に位置情報許可ダイアログが表示される
- [ ] **確認**: 権限拒否時に適切なエラーメッセージが表示される

### AC-2: バックグラウンド記録継続

- [ ] **テスト**: アプリを最小化後、30秒間隔でGPS座標がデータベースに保存される
- [ ] **確認**: 通知バーに記録中ステータスが表示される

### AC-3: 30秒間隔GPS取得

- [ ] **テスト**: ログでGPS座標取得間隔を確認（30秒±5秒の範囲内）
- [ ] **確認**: バッテリー消費が適切なレベル（PRIORITY_BALANCED_POWER_ACCURACY）

### AC-4: バッテリー最適化

- [ ] **テスト**: PRIORITY_BALANCED_POWER_ACCURACY が使用されている
- [ ] **確認**: 連続1時間記録時のバッテリー消費が10%以下

### AC-5: ローカルデータベース保存

- [ ] **テスト**: GPS座標がRoom データベースのgps_pointsテーブルに保存される
- [ ] **確認**: アプリ再起動後もデータが保持される

### AC-6: 手動制御

- [ ] **テスト**: 記録開始ボタンで記録が開始される
- [ ] **テスト**: 記録停止ボタンで記録が停止される
- [ ] **確認**: UI状態が適切に更新される

---

## 🔗 関連ドキュメント

- **要件定義**: [user-stories.md](../../requirements/user-stories.md#1-gps経路の自動記録保存)
- **詳細分析**: [gps-feature-analysis.md](../../research/gps-feature-analysis.md)
- **技術設計**: [android-architecture.md](../../design/android-architecture.md)
- **全体進捗**: [progress-tracking.md](../progress-tracking.md)

---

## 💡 実装メモ・気づき

### 技術的考慮事項

- **コルーチン活用**: GPS取得とDB保存を非同期処理
- **Flow使用**: リアクティブなデータフロー設計
- **Hilt DI**: 依存性注入によるテスタブルな設計
- **エラーハンドリング**: 権限、GPS信号、ストレージの各エラーパターン

### パフォーマンス最適化

- GPS取得間隔の動的調整（バッテリー残量に応じて）
- メモリリークの防止（Service, ViewModel のライフサイクル管理）
- データベースのバッチInsert検討

### 今後の拡張点

- GPS精度の向上（複数センサーの統合）
- 自動開始機能（外出検知）
- クラウド同期（Phase2）
