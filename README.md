# Pathly - お出掛け記録アプリ

<div align="center">

<img src="docs/assets/pathly-logo.svg" alt="Pathly Logo" width="120" height="120" />

**GPS軌跡を自動記録し、お出掛けの思い出を残す Android アプリ**

[![Platform](https://img.shields.io/badge/platform-Android-green)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-blue)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-34-orange)](https://developer.android.com/about/versions/14)
[![CI](https://github.com/y-nosuke/pathly/actions/workflows/android-build.yml/badge.svg)](https://github.com/y-nosuke/pathly/actions/workflows/android-build.yml)

</div>

## 📱 概要

**Pathly** は、お出掛けが好きな人向けの GPS 記録アプリです。外出中の移動経路を自動的に記録し、後から思い出を振り返ることができます。

## 🎯 主な機能

チェック済みが実装済み、未チェックが今後の予定です。

**リアルタイム記録**

- [x] 🛰️ GPS経路の自動記録・保存（バックグラウンド動作）
- [x] 📋 記録データの一覧表示・削除
- [x] 🗺️ 地図上での軌跡表示（Google Maps）
- [x] 🔴 リアルタイム経路表示・記録中ステータス
- [x] 💾 ローカルデータ保存（オフライン対応）

**事後振り返り・編集**

- [x] 🧹 GPSノイズ除去（位置補正）
- [x] 📍 立ち寄り場所の自動検出
- [ ] 📸 写真・動画記録
- [ ] ⭐ 場所の評価・コメント追加
- [ ] 🏷️ タグ付け機能

**場所・行きたい**

- [x] 📌 場所の登録・一覧（地図タップ・POIタップ・キーワード検索）
- [x] 🗒️ 行きたい場所リスト（優先度・メモ・訪問済み管理）
- [x] 🔗 場所から関連するお出掛け（経路）の一覧

**事前計画・詳細機能**

- [ ] 🧭 ルート計画・シミュレーション
- [ ] 🔄 データ共有・リアルタイム同期
- [ ] 📊 統計情報・分析機能

## 🏗️ 技術スタック

### フロントエンド

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture
- **Async:** Kotlin Coroutines + StateFlow

### バックエンド・データ

- **Database:** Room (SQLite)
- **BaaS:** Supabase（将来実装）
- **Location:** Google Play Services Location
- **Maps:** Google Maps SDK

### 開発

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
│   ├── designs/        # 設計（architecture / performance / security / logging / gps-smoothing / places-and-stops / wishlist / testing / cloud-database / glossary）
│   └── assets/         # 画像・ロゴ
├── .github/            # GitHub Actions（CI）
├── .githooks/          # Git フック（Markdown 整形）
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

## 📄 ライセンス

個人開発プロジェクトのため、**全権利を留保します（All Rights Reserved）**。著作権者の許可なく複製・改変・再配布・利用することを禁じます。詳細は [LICENSE](LICENSE) を参照してください。
