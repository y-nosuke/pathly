# Pathly Android

Pathly の Android アプリ（Kotlin + Jetpack Compose）。プロジェクト全体の概要は
[ルート README](../README.md)、要望・仕様・設計は [docs/](../docs) を参照してください。

## 🚀 セットアップ

### 必要な環境

- Android Studio（最新版）
- Android SDK 37
- JDK 17

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

# 実機/エミュレータへインストール
./gradlew installDebug
```

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
├── di/            # 依存性注入（Hilt modules）
├── data/          # データ層（local: Room DAO・entity / repository: 実装）
├── domain/        # ドメイン層（model・Repository interface）
├── presentation/  # プレゼン層（画面別 ViewModel/State/Screen）
├── service/       # バックグラウンドGPSサービス
└── ui/theme/      # Compose テーマ
```

アーキテクチャの詳細は [docs/designs/architecture.md](../docs/designs/architecture.md) を参照。
