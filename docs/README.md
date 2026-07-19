# Pathly プロジェクト文書

## 📚 文書構成

このディレクトリには、Pathlyプロジェクトの要件定義・設計・実装に関する文書が格納されています。

### 📂 ディレクトリ構成

```text
docs/
├── README.md                           # この文書（文書構成の説明）
│
├── 01-requirements/                     # 要件定義
│   ├── requirements.md                  # 基本要件・技術スタック
│   ├── features-mapping.md              # 機能とユーザーストーリーのマッピング
│   │
│   ├── user-stories/                    # ユーザーストーリー
│   │   ├── overview.md                  # ユーザーストーリー一覧表
│   │   ├── mvp-stories.md               # MVP詳細ストーリー
│   │   ├── recording-stories.md         # 記録する（12個）
│   │   ├── location-correction-stories.md # 位置を補正する（6個）
│   │   ├── editing-stories.md           # 編集する（11個）
│   │   ├── planning-stories.md          # 計画する（8個）
│   │   ├── viewing-stories.md           # 確認する（8個）
│   │   ├── management-stories.md        # 管理する（8個）
│   │   ├── sharing-stories.md           # 共有する（7個）
│   │   ├── platform-stories.md          # 利用する（4個）
│   │   ├── operation-stories.md         # 操作する（6個）
│   │   └── settings-stories.md          # 設定する（6個）
│   │
│   └── use-cases/                       # 重要機能のユースケース（高優先度のみ）
│       ├── overview.md                  # ユースケース一覧
│       ├── UC001-gps-tracking.md        # GPS軌跡記録
│       ├── UC002-background-recording.md # バックグラウンド記録
│       ├── UC003-map-display.md         # 地図表示
│       ├── UC004-data-sharing.md        # データ共有
│       ├── UC005-offline-storage.md     # オフライン保存
│       └── UC006-track-editing.md       # 記録編集
│
├── 02-design/                          # 設計文書（MVP完了後に作成予定）
│   ├── system-architecture.md          # システムアーキテクチャ
│   ├── database-design.md              # データベース設計
│   ├── api-design.md                   # API設計
│   ├── ui-design.md                    # UI/UX設計
│   ├── security-design.md              # セキュリティ設計
│   └── offline-sync-design.md          # オフライン同期設計
│
├── 03-implementation/                  # 実装詳細
│   ├── android-project-structure.md    # Android プロジェクト構成
│   ├── gps-feature-analysis.md         # GPS機能分析
│   ├── development-methodology.md      # 開発進め方・文書化ルール
│   ├── coding-standards.md             # コーディング規約（作成予定）
│   └── development-workflow.md         # 開発ワークフロー（作成予定）
│
├── 04-deployment/                      # デプロイ・運用（将来作成予定）
│   ├── deployment-guide.md             # デプロイ手順
│   ├── environment-setup.md            # 環境構築
│   └── monitoring.md                   # 監視・運用
│
└── assets/                             # 設計図・画像（将来作成予定）
    ├── architecture-diagrams/          # アーキテクチャ図
    ├── ui-mockups/                     # UI モックアップ
    └── erd/                            # ER図
```

## 🎯 開発フェーズと文書

### **現在（Phase 1 MVP実装完了）**

- ✅ 要件定義（01-requirements）
- ✅ 基本データベース設計（02-design）
- ✅ 実装指針（03-implementation）
- ✅ Android実装: GPS記録 / 一覧 / 地図表示 / ローカル保存 / リアルタイム表示（Phase 1 MVP）
- ✅ ビルド環境最新化（AGP 9.2 / Gradle 9.6 / built-in Kotlin + KSP）

### **次のステップ（Phase 2）**

- 📝 外出の自動検知 / 立ち寄り自動検出 / 手動での場所記録
- 📝 GPSノイズ除去（位置補正）
- 📝 写真・メディア記録

### **将来（クラウド連携）**

- 📋 Supabase設計詳細化（02-design）
- 📋 API設計（02-design）
- 📋 デプロイ文書の作成（04-deployment）

## 📖 文書の読み方

### **新規参加者向け**

1. `01-requirements/requirements.md` - プロジェクト概要
2. `01-requirements/user-stories/overview.md` - 機能一覧
3. `03-implementation/development-methodology.md` - 開発ルール

### **実装者向け**

1. `01-requirements/user-stories/mvp-stories.md` - MVP詳細仕様
2. `02-design/android-database-design.md` - データベース設計
3. `03-implementation/android-project-structure.md` - Android構成
4. `03-implementation/gps-feature-analysis.md` - GPS機能詳細

### **設計者向け（将来）**

1. `02-design/database-design.md` - データベース全体設計
2. `02-design/system-architecture.md` - システム全体設計
3. `02-design/api-design.md` - API仕様

## 🔄 文書の更新方針

- **要件変更時**: user-stories を更新
- **実装完了時**: 実装詳細を追記
- **設計変更時**: 設計文書を更新
- **リリース時**: デプロイ文書を更新

## 📋 文書の品質基準

詳細は `03-implementation/development-methodology.md` を参照してください。
