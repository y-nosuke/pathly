# Pathly Android

Pathly の Android アプリ（Kotlin + Jetpack Compose）。プロジェクト全体の概要は
[ルート README](../README.md)、要望・仕様・設計は [docs/](../docs) を参照してください。

## 🚀 セットアップ

### 必要な環境

- Android Studio（最新版）
- Android SDK 37
- JDK 25（Gradle が `gradle/gradle-daemon-jvm.properties` に従って選ぶ。無ければ自動で取得する）

### Google Maps API キー

`android/local.properties` に API キーを追加します。

```properties
GOOGLE_MAPS_API_KEY=your_google_maps_api_key_here
```

## 🔨 ビルドと実行

以下のコマンドは `android/` ディレクトリで実行します。

```bash
# デバッグビルド
./gradlew assembleDebug

# 実機/エミュレータへインストール（開発版 com.pathly.debug として入る）
./gradlew installDebug
```

開発版は普段使いのリリース版（`com.pathly`）とは別アプリで、データも別です。
版の付け方・リリースの手順は [docs/designs/release.md](../docs/designs/release.md) を参照。

## 🧪 テスト

```bash
# 単体テスト
./gradlew test

# インストルメンテーションテスト（実機/エミュレータが必要）
./gradlew connectedAndroidTest

# 静的解析
./gradlew lint

# コードフォーマット（Kotlin / spotless + ktlint）
./gradlew spotlessApply
```

## 📋 パッケージ構成

```text
app/src/main/java/com/pathly/
├── di/                # 依存性注入（Hilt modules）
├── data/              # データ層
│   ├── local/         # Room（database・DAO・entity・migration）
│   ├── repository/    # Repository の実装
│   ├── places/        # Google Places 連携（命名・テキスト検索）
│   ├── settings/      # SharedPreferences（記録間隔・位置の取り方・地図の表示）
│   ├── tracking/      # 記録サービスの制御と端末の状態（TrackingController）
│   ├── work/          # WorkManager のジョブ（名前解決のキャッチアップ）
│   └── backup/        # データの書き出し・読み込み
├── domain/            # ドメイン層
│   ├── model/         # ドメインモデル
│   ├── repository/    # Repository の interface
│   └── usecase/       # 複数画面で共有する手順（場所の登録・立ち寄りの手動追加など）
├── presentation/      # プレゼン層（画面ごとの ViewModel・State・Screen）
│   ├── tracking/      # 記録
│   ├── history/       # 履歴・経路詳細
│   ├── places/        # 場所・行きたい
│   ├── stops/         # 立ち寄りの追加・付け替え（画面をまたいで使う）
│   ├── common/        # 画面をまたぐ部品（フローティングシート・地図の描画・確認ダイアログ）
│   ├── settings/      # 設定
│   └── navigation/    # ボトムナビ・NavHost
├── service/           # 記録サービス（LocationTrackingService）
├── util/              # Logger・権限・日時フォーマット
└── ui/theme/          # Compose テーマ（PathlyAndroidTheme）
```

アーキテクチャの詳細は [docs/designs/architecture.md](../docs/designs/architecture.md) を参照。
