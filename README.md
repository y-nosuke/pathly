# Pathly - お出掛け記録アプリ

<div align="center">

![Pathly Logo](https://via.placeholder.com/200x80/4285f4/ffffff?text=Pathly)

**GPS軌跡を自動記録し、お出掛けの思い出を残すAndroidアプリ**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-blue.svg)](https://kotlinlang.org)

</div>

## 📱 概要

**Pathly**は、お出掛けが好きな人やカップル向けのGPS記録アプリです。外出中の移動経路を自動的に記録し、後から思い出を振り返ることができます。

### 🎯 主な機能

**Phase 1: リアルタイム記録（MVP）** - *現在開発中*

- ✅ GPS経路の自動記録・保存
- ✅ 記録したデータの基本的な一覧表示
- ✅ 地図上での軌跡表示
- ✅ ローカルデータ保存

**Phase 2: 事後振り返り・編集** - *計画中*

- 📍 立ち寄り場所の自動検出
- 📸 写真・動画記録
- ⭐ 場所の評価・コメント追加
- 🏷️ タグ付け機能

**Phase 3: 事前計画・詳細機能** - *計画中*  

- 📋 行きたい場所リスト
- 🗺️ ルート計画・シミュレーション
- 👫 カップル間でのデータ共有
- 📊 統計情報・分析機能

## 🏗️ 技術スタック

### Frontend

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture
- **Async:** Kotlin Coroutines + Flow
- **Navigation:** Navigation Compose

### Backend & Data

- **Database:** Room (SQLite)
- **BaaS:** Supabase (将来実装)
- **Location:** Google Play Services Location
- **Maps:** Google Maps SDK (Phase 2)

### Development

- **DI:** Hilt (Dagger)
- **Testing:** JUnit 5, MockK, Compose Testing
- **Build:** Gradle Kotlin DSL
- **CI/CD:** GitHub Actions (予定)

## 🚀 セットアップ

### 必要な環境

- Android Studio Hedgehog (2023.1.1) 以上
- Android SDK 34
- Kotlin 1.9.0 以上
- JDK 17

### ローカル開発環境構築

```bash
# 1. リポジトリをクローン
git clone https://github.com/[YOUR_USERNAME]/pathly.git
cd pathly

# 2. Android Studio でプロジェクトを開く
# File > Open > pathlyフォルダを選択

# 3. 必要な SDK とツールをインストール
# Android Studio が自動的に不足コンポーネントを検出・インストール

# 4. Google Maps API キーを設定 (Phase 2で必要)
# local.properties に追加:
# MAPS_API_KEY=your_google_maps_api_key_here
```

### ビルドと実行

```bash
# デバッグビルド
./gradlew assembleDebug

# テスト実行
./gradlew test

# インストルメンテーションテスト
./gradlew connectedAndroidTest
```

## 📋 プロジェクト構造

```text
app/src/main/java/com/pathly/
├── data/                      # データ層
│   ├── local/database/        # Room データベース
│   └── repository/            # Repository 実装
├── domain/                    # ドメイン層
│   ├── model/                 # ドメインモデル
│   ├── repository/            # Repository インターフェース
│   └── usecase/               # ユースケース
├── presentation/              # プレゼンテーション層
│   ├── ui/tracking/           # GPS記録画面
│   ├── ui/history/            # 履歴画面
│   └── navigation/            # ナビゲーション
├── service/                   # バックグラウンドサービス
├── permission/                # 権限管理
└── di/                        # 依存性注入
```

## 🧪 テスト

```bash
# 単体テスト
./gradlew test

# UI テスト
./gradlew connectedAndroidTest

# テストカバレッジ
./gradlew jacocoTestReport
```

## 📖 ドキュメント

- [要件定義書](docs/requirements.md) - プロジェクトの全体要件
- [ユーザーストーリー](docs/user-stories.md) - MVP機能の詳細
- [GPS機能分析](docs/gps-feature-analysis.md) - GPS記録機能の詳細分析  
- [プロジェクト構造](docs/android-project-structure.md) - Android プロジェクトの構造設計

## 🎯 開発計画

### Week 1-2: 基盤構築

- ✅ プロジェクト初期化
- 🏗️ Room データベース設計・実装
- 🏗️ 位置情報権限管理

### Week 3-4: コア機能実装  

- 📍 GPS位置取得サービス
- 💾 Repository & UseCase 実装
- 🎨 Jetpack Compose UI

### Week 5-6: サービス統合

- 🔄 Foreground Service実装
- 🧪 統合テスト
- 🚀 MVP リリース準備

## 🤝 コントリビューション

現在は個人開発プロジェクトですが、将来的にコントリビューションを受け付ける予定です。

### 開発フロー

1. Issue を作成して機能や修正を提案
2. Feature ブランチで開発
3. Pull Request でレビュー
4. main ブランチにマージ

## 📄 ライセンス

このプロジェクトは [MIT License](LICENSE) の下で公開されています。

## 📞 お問い合わせ

プロジェクトに関する質問や提案がありましたら、Issues でお知らせください。

---

<div align="center">

**Made with ❤️ for GPS tracking enthusiasts**

[🐛 Bug Report](https://github.com/[YOUR_USERNAME]/pathly/issues/new?template=bug_report.md) ·
[✨ Feature Request](https://github.com/[YOUR_USERNAME]/pathly/issues/new?template=feature_request.md) ·
[📚 Documentation](docs/)

</div>
