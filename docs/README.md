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

| ファイル                               | 内容                             |
| -------------------------------------- | -------------------------------- |
| [specs/features.md](specs/features.md) | GPS記録機能のユースケース仕様    |
| [specs/screens.md](specs/screens.md)   | 画面仕様（UI・ナビゲーション）   |
| [specs/model.md](specs/model.md)       | データモデル（概念モデル・ER図） |
| [specs/glossary.md](specs/glossary.md) | 用語集（データ・処理の日英対応） |

### 設計（How）

| ファイル                                                   | 内容                                   |
| ---------------------------------------------------------- | -------------------------------------- |
| [designs/architecture.md](designs/architecture.md)         | アーキテクチャ・プロジェクト構成       |
| [designs/security.md](designs/security.md)                 | セキュリティ設計                       |
| [designs/logging.md](designs/logging.md)                   | ログ方針・実装                         |
| [designs/performance.md](designs/performance.md)           | パフォーマンス設計（電力・メモリ・DB） |
| [designs/gps-smoothing.md](designs/gps-smoothing.md)       | GPS軌跡の補正（スムージング）設計      |
| [designs/places-and-stops.md](designs/places-and-stops.md) | 場所・立ち寄りの永続化と命名設計       |
| [designs/wishlist.md](designs/wishlist.md)                 | 行きたい場所・「場所」タブの設計       |
| [designs/testing.md](designs/testing.md)                   | テスト戦略                             |
| [designs/cloud-database.md](designs/cloud-database.md)     | クラウドDB・同期設計（将来）           |

---

## 運用ルール

- **新しい要望が出たら** → まず `requirements.md` に「〜したい」を追記（日付付き）
- **作り方を決めたら** → `specs/`（何を）と `designs/`（どう）に落とす
- **進捗** → `roadmap.md` で管理（要望書には進捗を書かない）

### 書くときの原則

- **依存は一方向（specs は designs に依存しない）** → 参照リンクは `designs → specs`（要望・仕様）に向ける。`specs → designs` のリンクは張らない（仕様は実現方法を知らずに成立させる）。
- **コードを見れば分かることは書かない** → 現行コードの写し（クラス本体・ディレクトリツリー・依存バージョンのピン留めなど）は、いずれ実装とズレて誤情報になるため docs に置かない。docs に書くのは**コードからは読み取れないもの**＝「なぜそうしたか（設計判断）」「何をしたいか（仕様・要望）」「未実装の設計案・ポリシー」。具体値（バージョン・シグネチャ等）は実コード／Gradle 定義を正とし、docs からは参照に留める。
