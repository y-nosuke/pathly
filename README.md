# Pathly - お出掛け記録アプリ

<div align="center">

![Pathly Logo](https://via.placeholder.com/200x80/4285f4/ffffff?text=Pathly)

**GPS軌跡を自動記録し、お出掛けの思い出を残す Android アプリ**

[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-blue.svg)](https://kotlinlang.org)

</div>

## 📱 概要

**Pathly** は、お出掛けが好きな人向けの GPS 記録アプリです。外出中の移動経路を自動的に記録し、後から思い出を振り返ることができます。

### 🎯 主な機能

**Phase 1: リアルタイム記録（MVP）** - _完了_

- ✅ GPS経路の自動記録・保存（バックグラウンド動作）
- ✅ 記録したデータの一覧表示・削除
- ✅ 地図上での軌跡表示（Google Maps）
- ✅ リアルタイム経路表示・記録中ステータス
- ✅ ローカルデータ保存（オフライン対応）

**Phase 2: 事後振り返り・編集** - _計画中_

- 🔧 GPSノイズ除去（位置補正）
- 📍 立ち寄り場所の自動検出
- 📸 写真・動画記録
- ⭐ 場所の評価・コメント追加
- 🏷️ タグ付け機能

**Phase 3: 事前計画・詳細機能** - _計画中_

- 📋 行きたい場所リスト
- 🗺️ ルート計画・シミュレーション
- 🔄 データ共有・リアルタイム同期
- 📊 統計情報・分析機能

## 🏗️ 技術スタック

### Frontend

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture
- **Async:** Kotlin Coroutines + StateFlow

### Backend & Data

- **Database:** Room (SQLite)
- **BaaS:** Supabase（将来実装）
- **Location:** Google Play Services Location
- **Maps:** Google Maps SDK

### Development

- **DI:** Hilt (Dagger)
- **Annotation Processing:** KSP
- **Testing:** JUnit4, MockK, Turbine, Compose Testing
- **Build:** AGP 9.2 / Gradle 9.6 / Kotlin 2.3（AGP内蔵）
- **CI/CD:** GitHub Actions

## 📋 プロジェクト構造

```text
pathly/
├── android/            # Android アプリ（Kotlin + Jetpack Compose）→ android/README.md
│   ├── app/            # アプリモジュール（ソース・ビルド設定）
│   └── gradle/         # Gradle wrapper・バージョンカタログ
├── docs/               # ドキュメント（要望 → 仕様 → 設計）
│   ├── requirements.md # 要望書（★肝）
│   ├── roadmap.md      # ロードマップ
│   ├── specs/          # 仕様（features / screens / model）
│   └── designs/        # 設計（architecture / security / logging / testing / cloud-database）
├── .github/            # GitHub Actions（CI）
├── .vscode/            # エディタ設定（Prettier など）
├── CLAUDE.md           # 開発ガイド
└── README.md
```

## 🚀 開発

- **Android アプリ**: セットアップ・ビルド・テストは [android/README.md](android/README.md) を参照
- **Web 管理画面（Next.js）**: 将来追加予定

## 📖 ドキュメント

詳細は [`docs/`](docs/) に集約しています。

- [要望書](docs/requirements.md) — 実現したいこと（★肝）
- [ロードマップ](docs/roadmap.md) — フェーズ・優先度・進捗
- [ドキュメント索引](docs/README.md) — 仕様（specs/）・設計（designs/）
- [CLAUDE.md](CLAUDE.md) — 開発ガイド（規約・詳細コマンド）
