# Pathly ドキュメント

お出掛け記録アプリ Pathly のドキュメント。**要望 → 仕様 → 設計** を分離して管理します。

- **要望（Why / なにをしたいか）**: ユーザーの「こうしたい」だけを記録。実現方法は書かない
- **仕様（What）**: 要望をどう満たすか（ロジック・画面・データ）
- **設計（How）**: どう作るか（構成・技術）

> 肝は [requirements.md](requirements.md)。要望と仕様・設計を混ぜないことで、「本当は何をしたかったか」を見失わず、仕様・設計を後から自由に組み替えられます。

---

## ドキュメント一覧

### 要望・計画

| ファイル                           | 内容                        |
| ---------------------------------- | --------------------------- |
| [requirements.md](requirements.md) | 要望書（実現したいこと）★肝 |
| [roadmap.md](roadmap.md)           | フェーズ・優先度・進捗      |

### 仕様（What）

| ファイル                               | 内容                              |
| -------------------------------------- | --------------------------------- |
| [specs/features.md](specs/features.md) | 機能仕様（GPS記録ロジック等）     |
| [specs/screens.md](specs/screens.md)   | 画面仕様（UI・ナビゲーション）    |
| [specs/model.md](specs/model.md)       | データモデル（Room / ローカルDB） |

### 設計（How）

| ファイル                                               | 内容                              |
| ------------------------------------------------------ | --------------------------------- |
| [designs/glossary.md](designs/glossary.md)             | 用語集（データ・処理の日英対応）  |
| [designs/architecture.md](designs/architecture.md)     | アーキテクチャ・プロジェクト構成  |
| [designs/security.md](designs/security.md)             | セキュリティ設計                  |
| [designs/logging.md](designs/logging.md)               | ログ方針・実装                    |
| [designs/gps-smoothing.md](designs/gps-smoothing.md)   | GPS軌跡の補正（スムージング）設計 |
| [designs/places-and-stops.md](designs/places-and-stops.md) | 場所・立ち寄りの永続化と命名設計 |
| [designs/testing.md](designs/testing.md)               | テスト戦略                        |
| [designs/cloud-database.md](designs/cloud-database.md) | クラウドDB・同期設計（将来）      |

---

## 運用ルール

- **新しい要望が出たら** → まず `requirements.md` に「〜したい」を追記（日付付き）
- **作り方を決めたら** → `specs/`（何を）と `designs/`（どう）に落とす
- **進捗** → `roadmap.md` で管理（要望書には進捗を書かない）
