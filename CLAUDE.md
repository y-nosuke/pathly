# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

**アプリ名：** Pathly（お出掛け記録アプリ）  
**目的：** お出掛けの記録・計画・振り返りを行う  
**主要ユーザー：** 出掛けるのが好きな人（Android + iPhone環境）  
**開発方針：** 段階的開発（リアルタイム記録 → 事後振り返り → 事前計画）  
**開発体制：** 一人開発、アジャイル手法、Claude Code活用

## 技術スタック

### メインプラットフォーム

- **Android：** Kotlin + コルーチン + Jetpack Compose
- **状態管理：** ViewModel + StateFlow（コルーチン対応）
- **アーキテクチャ：** MVVM + Clean Architecture
- **依存性注入：** Hilt (Dagger)
- **データベース：** Room (SQLite)
- **非同期処理：** Kotlin Coroutines + StateFlow

### バックエンド・インフラ

- **BaaS：** Supabase（PostgreSQL + Edge Functions）
- **Web：** Next.js + Vercel
- **地図：** Google Maps SDK
- **アーキテクチャ：** サーバーレス構成（常駐サーバーなし）

### データ管理

- **データベース：** PostgreSQL（Supabase）
- **ローカル保存：** Room (SQLite)。端末内のみで完結し、暗号化はしていない（クラウド同期は Phase 3）
- **同期：** リアルタイム同期（Supabase Realtime）

## 現在の開発フェーズ

### Phase 1: リアルタイム記録（MVP）- 完了

**確定機能：**

1. ✅ GPS経路の自動記録・保存
2. ✅ 記録したデータの基本的な一覧表示
3. ✅ 地図上での軌跡表示
4. ✅ ローカルデータ保存
5. ✅ リアルタイム経路表示・記録中ステータス表示（US010/US011）

**Phase 2 で実装済みの機能（先行着手含む）：**

- ✅ GPSノイズ除去（位置補正・スムージング）
- ✅ 立ち寄り場所の自動検出（50m圏内+3分滞在）・永続化・自動命名（Places）
- ✅ 場所・行きたい（wishlist）管理（登録・優先度・メモ・訪問済み・キーワード検索・関連経路一覧）
- ✅ 手動での場所記録（記録画面の「今ここ」・地図タップからの場所登録／立ち寄り追加）
- ✅ 立ち寄りの手動追加・誤検知の付け替え・訪問メモ・再解析（追加提案）
- ✅ 経路の名前・お気に入り・絞り込み／並べ替え
- ✅ 登録済みの場所を地図に表示・近接確認

**Phase 2以降に先送りされた機能：**

- 外出の自動検知→記録開始
- 写真撮影機能
- 記録の詳細編集（評価・コメント・費用・タグ）
- クラウド同期（複数人での共有）

## データベース構造（PostgreSQL）

### 主要テーブル

- **users** - ユーザー情報
- **outings** - お出掛け情報（計画・実行済み）
- **outing_participants** - お出掛け参加者（複数人）
- **tracks** - GPS軌跡データ
- **gps_points** - GPS座標点（原データ・補正後）
- **stops** - 立ち寄り場所
- **media** - 写真・動画・音声メモ
- **stop_media** - 場所とメディアの関連
- **tags** - タグ情報
- **stop_tags** - 場所とタグの関連

### 重要な設計思想

- GPS座標は原データと補正後データの両方を保存
- メディアファイルは位置情報付きで管理
- タグシステムによる柔軟な分類

## アーキテクチャ構造

### Clean Architecture レイヤー構成

```bash
app/src/main/java/com/pathly/
├── di/                     # 依存性注入（Hilt modules）
├── data/                   # データ層
│   ├── local/             # Room database, DAOs, entities, migrations
│   ├── repository/        # Repository実装
│   ├── places/            # Google Places 連携（命名・テキスト検索）
│   ├── settings/          # SharedPreferences（GPS間隔・地図の表示設定）
│   ├── tracking/          # 記録サービスの制御・端末状態（TrackingController）
│   └── work/              # WorkManager ジョブ（名前解決のキャッチアップ）
├── domain/                # ドメイン層
│   ├── model/             # ドメインモデル
│   ├── repository/        # Repository interface
│   └── usecase/           # 複数画面で共有する手順（場所の登録・立ち寄りの手動追加）
├── presentation/          # プレゼンテーション層（画面別のViewModel, State, Screen）
│   ├── tracking/          # 記録画面
│   ├── history/           # 履歴・経路詳細
│   ├── places/            # 場所・行きたい（wishlist）・場所シート
│   ├── stops/             # 立ち寄りの追加・付け替えUI（画面横断）
│   ├── common/            # 画面横断の部品（FloatingSheet・地図描画・確認ダイアログ）
│   ├── settings/          # 設定
│   └── navigation/        # ボトムナビ・NavHost（Navigation-Compose）
├── service/               # Androidサービス（GPS追跡など）
├── util/                  # Logger・権限判定・日時フォーマット
└── ui/theme/             # Compose UIテーマ
```

### データフロー

1. **UI (Compose Screen)** ← StateFlow ← **ViewModel**
2. **ViewModel** → Repository Interface（複数画面で重複する手順は UseCase 経由）→ **Repository Implementation**
3. **Repository** → Room DAO → **Local Database**
4. **Service** → Repository / DAO → GPS位置データの永続化。サービスの起動・バインドと端末状態（権限・位置情報ON/OFF）は `data/tracking/TrackingController` が持ち、ViewModel は Service を直接触らない

### 重要な実装パターン

- **依存性注入：** `@AndroidEntryPoint`, `@HiltViewModel`使用
- **状態管理：** StateFlow + collectAsState()でリアクティブUI
- **データ変換：** Entity ↔ Domain Model の変換層
- **権限管理：** Compose + ActivityResultLauncher統合

## 主要機能概要

### Phase 1（MVP）機能

1. **GPS軌跡記録** - バックグラウンド動作、10秒間隔取得
2. **軌跡一覧表示** - 日付別の記録一覧
3. **地図表示** - Google Maps上での軌跡表示
4. **ローカル保存** - オフライン対応

### 実装済みの主な機能（Phase 2・先行着手）

- **立ち寄り判定** - 50m圏内+3分滞在の自動検出・永続化・自動命名（Places）
- **立ち寄りの手動操作** - 手動追加（「立ち寄りを追加」）・誤検知の付け替え・再解析（追加提案）・訪問メモ
- **場所・行きたいリスト** - 登録（地図/POI/キーワード検索）・優先度・メモ・訪問済み・関連経路一覧
- **場所名の手動編集** - 未命名⇄命名。名前欄は「自分で付けた名前」専用で、Google の名前は別列（v7）
- **経路一覧** - 名前・お気に入り・絞り込み／並べ替え
- **地図の上のUI** - 全画面の地図＋非モーダルのフローティングシートに統一（ADR-0010）

### 将来実装予定機能

- **写真・動画記録** - 位置情報付きメディア管理
- **事後編集** - 評価・コメント・費用・タグの追加
- **事前計画** - ルート計画・予算
- **データ共有** - 複数人でのリアルタイム同期

## 開発環境・コマンド

### Android開発コマンド

```bash
# プロジェクトビルド
./gradlew build

# デバッグAPKビルド
./gradlew assembleDebug

# リリースAPKビルド
./gradlew assembleRelease

# アプリインストール（デバッグ）
./gradlew installDebug

# テスト実行
./gradlew test                    # ユニットテスト
./gradlew connectedAndroidTest    # インストルメンテーションテスト

# リント・静的解析
./gradlew lint
./gradlew lintDebug

# プロジェクトクリーン
./gradlew clean
```

### 将来追加予定のセットアップ

```bash
# TODO: Supabaseクライアント設定
# TODO: Google Maps SDK設定
# TODO: Next.jsプロジェクト初期化（Web管理画面）
# TODO: Vercelデプロイ設定
```

## セキュリティ・パフォーマンス考慮事項

### セキュリティ

- **認証：** Supabase Auth（ID+パスワード）※ Phase 3・未着手
- **データ暗号化：** Supabase自動暗号化 + 機密データはアプリレベル暗号化 ※ Phase 3・未着手
- **通信：** HTTPS/TLS必須
- **ローカル：** 暗号化なし。Room の DB は平文で、設定は SharedPreferences。将来 DB を暗号化するなら SQLCipher 等の導入が必要（Jetpack Security は Deprecated）
- 何を持ち何を持たないかの約束は `docs/specs/security.md`、その実現方法は `docs/designs/security.md`（`allowBackup` が既定のままで DB が Auto Backup に乗る点も記載）

### パフォーマンス

- **GPS取得：** 設定で変更可（既定10秒、5/10/30/60秒）＋バッチ許容、PRIORITY_BALANCED_POWER_ACCURACY
- **データ同期：** 差分同期、バッチ処理
- **画像：** 最大2MB圧縮
- **オフライン：** ローカル保存→後で同期

### エラー対応

- **GPS失敗：** 権限要求、設定案内、最後の既知位置使用
- **ネットワーク：** 自動リトライ（指数バックオフ）、オフライン対応
- **クラッシュ：** Supabase Error Tracking、graceful degradation

## UI設計方針

### ナビゲーション構造

- **タブ型：** [記録] [履歴] [場所] [設定]（地図は各画面に全画面で内包。計画タブは Phase 3 で追加予定）
- **記録開始：** ホーム画面の大きなボタン + 通知バーのクイックアクセス

### お出掛け中操作（Phase 2以降）

- ワンタップ操作重視
- 大きなボタン設計
- ステータス表示
- リアルタイム情報更新

## 料金・コスト管理

### 控えめ利用（月額$0-5）

- Supabase: $0（無料枠内）
- Vercel: $0（Hobbyプラン）
- Google Maps: $0-5（無料枠内）

### 中程度利用（月額$35-45）

- Supabase: $25（Proプラン）
- Vercel: $0（Hobbyプラン）
- Google Maps: $10-20

## 開発時の重要事項

### プロジェクト固有の考慮事項

- **位置権限：** ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION必須
- **バックグラウンド実行：** LocationTrackingService使用
- **データベースバージョン：** Room v16（v2: places/stops、v3: smoothed_points、v4: place_resolutions、v5: wishlist、v6: stops.note＝立ち寄りメモ、v7: 場所データをGoogle由来[google_places]とユーザー入力[places]に分離・メモをplaces.noteへ一本化、v8: gps_tracksにname/isFavorite＝経路の名前・お気に入り、v9: gps_pointsにLocation付随情報＝provider/各種精度/MSL高度/elapsedRealtimeNanos/isMock/extrasJson、v10: placesにsource＝場所の由来[DETECTED/USER]・自動回収はDETECTEDのみ、v11: gps_tracksにtotalDistanceMeters＝確定時に焼き込む総移動距離[一覧が全点をロードして再平滑化しないため]・既存分は起動時にバックフィル、v12: placesの座標に索引＝近傍検索[同一場所30m判定・近接確認50m]が全表走査にならないように、v13: 場所の業種を google_place_categories に正規化＝機械可読な code[Googleのprimary type]を正とし表示名は別列・google_places.category は categoryId の外部キーに置換[ADR-0017]、v14: 手動の訪問済みを wishlist から visited_places に分離＝行の存在が訪問済み・列名は markedAt[印を付けた日時であって訪問日ではない]・行きたいと独立に付け外しできる[ADR-0020]、v15: Google の座標を google_places に分離＝places の座標は同定用のアンカーで作成時に確定し以後動かさない・表示は COALESCE(google_places, places) で解決[同じ場所の大量重複の原因だった・ADR-0023]・既にできた重複は同じ施設なら統合する、v16: google_places.googlePlaceId に UNIQUE＝ひとつの Google 施設を持てる場所を1つに限る[同じ施設を2つの place が持つと「この施設の場所はどれか」の答えがブレる]・寄せられないときは施設情報を付けずに場所を残す・地図で指して作った場所[USER]は同じ施設でも自動でまとめない[ADR-0025]）。破壊的フォールバックは無効。スキーマ変更時は `DatabaseMigrations` に正式なマイグレーションを追加すること
- **最小SDK：** API 34（Android 14）以上 / compileSdk 37・targetSdk 37（Android 17）
- **ビルド環境：** AGP 9.3 / Gradle 9.7 / Kotlin 2.3（AGP内蔵Kotlin）。KotlinはAGPバンドル版に連動するため独立に最新化しないこと。正確な値は `gradle/libs.versions.toml` と `gradle-wrapper.properties` を見ること
- **アノテーション処理：** KSP使用（Room/Hilt/WorkManager）。kaptは廃止
- **バックグラウンドジョブ：** WorkManager（Hilt でワーカーを組み立てるため自動初期化はマニフェストで停止し、`PathlyApplication` が `Configuration.Provider` として担う）
- **整形：** spotless（ktlint）。`./gradlew build` に含まれるので、push 前は build を通すこと。崩れは `./gradlew spotlessApply`
- **アイコン：** Material Iconsは非推奨のため不使用。`res/drawable`のベクター + `painterResource`で追加する
- **コルーチン：** すべての非同期処理でKotlin Coroutines使用

### コーディング規約

- **パッケージ構造：** Clean Architecture厳守
- **DIパターン：** Hilt使用、手動DIは避ける
- **State管理：** StateFlow使用、LiveData非推奨
- **Composeテーマ：** PathlyAndroidTheme統一使用
- **ログ管理：** `com.pathly.util.Logger`使用、詳細は`docs/designs/logging.md`参照

### テストアプローチ

- **ユニットテスト：** JUnit4 (app/src/test/)
- **UIテスト：** Compose Testing + Espresso (app/src/androidTest/)
- **ViewModel：** Repository をモック化してテスト

## その他重要な開発指針

- **通知機能：** なし（自動動作を優先）
- **学習目標：** Kotlinコルーチンの習得
- **コスト重視：** 無料枠最大活用、段階的スケールアップ
- **実装方針：** 詳細設計は実装時に決定（アジャイル）
- **プライバシー：** 位置情報削除は個人に委ね、自動削除なし
