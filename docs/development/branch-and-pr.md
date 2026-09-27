# ブランチ・コミット・PR の運用

main から切ったブランチで作業し、PR を経て main にマージする。種類の語彙は
[Conventional Commits](https://www.conventionalcommits.org/ja/) に揃え、**ブランチ名・コミット・PR タイトルで同じ語を使う**。

```
main から切る → コミット → ./gradlew build → push → PR → CI → マージ（マージコミット）→ ブランチは自動で消える
```

---

## 種類（type）

| 種類       | 使うとき                                                            |
| ---------- | ------------------------------------------------------------------- |
| `feat`     | 機能の追加・変更（ユーザーから見える）                              |
| `fix`      | バグ修正                                                            |
| `perf`     | 性能の改善（見える挙動は変えない）                                  |
| `refactor` | 挙動を変えない作り替え                                              |
| `test`     | テストだけの追加・修正                                              |
| `docs`     | ドキュメントだけ（`docs/`・README・CLAUDE.md）                      |
| `build`    | ビルドの仕組み・**依存ライブラリの更新**（`build(deps)`）・署名・版 |
| `ci`       | GitHub Actions のワークフロー                                       |
| `chore`    | 上のどれにも当たらない雑務（IDE 設定・`.gitignore` など）           |

**ひとつの PR が複数にまたがるときは、主な目的で 1 つ選ぶ。** 迷ったら、ユーザーに見える変更を優先する
（`feat` > `fix` > `perf` > `refactor` > その他）。目的が 2 つあるなら PR を分ける。
PR の中のコミットは、それぞれの中身に合った種類で分けてよい（例: `feat` の PR に `docs` のコミット）。

---

## ブランチ名

`<種類>/<英語のケバブケース>`。何をするブランチか一目で分かる短い名前にする。

- 例: `feat/manual-add-stop`・`fix/stop-merge-crash`・`build/update-deps`・`docs/branch-and-pr-rules`
- `feature/` は使わない（`feat/` に揃える。2026-09 以前は混在していた）。
- 日付・連番・PR 番号は付けない。
- Claude Desktop が自動で付ける `claude/…` の名前は、PR を作る前に付け直す（`git branch -m`）。
- **小さな修正のたびにブランチを切らない。** 同じ種類の小さな変更（開発環境・Claude の設定やスキル・ドキュメントの手直しなど）は、
  開いている同じ種類のブランチ・PR にまとめる（PR のタイトルは中身に合わせて直す）。機能や修正とは混ぜない。

---

## コミットメッセージ

```
<種類>(<範囲>): <日本語の要約>

<本文: なぜ変えたか・何を変えたか（箇条書き可）>
```

- **要約は日本語で、何が変わるかを書く**（「〜する」「〜にする」）。末尾に句点は付けない。
- 範囲（scope）は省略してよい。よく使うもの:

  | 範囲                    | 対象                              |
  | ----------------------- | --------------------------------- |
  | `tracking`              | 記録画面・記録サービス            |
  | `history`               | 履歴・経路詳細                    |
  | `stops`                 | 立ち寄り                          |
  | `places`                | 場所・行きたい・Places 連携       |
  | `map`                   | 地図の描画・タップ                |
  | `settings`              | 設定                              |
  | `db`                    | Room のスキーマ・マイグレーション |
  | `deps`                  | 依存ライブラリ（`build(deps)`）   |
  | `lint` / `test` / `log` | 静的解析・テスト基盤・ログ        |

- 本文には**コードを読んでも分からない「なぜ」**を書く。
- Claude と作ったコミットは末尾に `Co-Authored-By:` を付ける。

---

## PR

- **タイトルはコミットと同じ形**（`<種類>(<範囲>): <日本語の要約>`）。
  **PR タイトルはそのままリリースノートの 1 行になる**（[release.md](../designs/release.md)）ので、
  後から読んで何が変わったか分かる書き方にする。
- 本文は [PR テンプレート](../../.github/pull_request_template.md) に沿って、概要・変更内容・検証を書く。
  対応する Issue があれば `Closes #番号` で結ぶ（→ [issues-and-adr.md](issues-and-adr.md)）。
- **push 前に `./gradlew build` を通す**（[testing.md](../designs/testing.md#ci-と-push-前のゲート)）。
- 1 つの PR は 1 つの目的に絞る。

---

## マージ

- **マージコミットでマージする。squash / rebase マージはリポジトリの設定で無効にしている。**
  versionCode は main のコミット数から決まるため、squash するとブランチ上でビルドした版より
  マージ後の main の版が小さくなりうる（[release.md](../designs/release.md)）。
- マージしたブランチは GitHub 上で自動で消える。手元は `git branch -d <ブランチ>` と `git fetch --prune` で消す。

---

## 依存の更新（Dependabot）

`.github/dependabot.yml` により、**月に 1 回**、新しい版が出た依存の更新 PR が自動で作られる。

| 対象                         | PR タイトル   | まとめ方             |
| ---------------------------- | ------------- | -------------------- |
| Android の依存（`android/`） | `build(deps)` | 1 回につき 1 つの PR |
| GitHub Actions のアクション  | `ci(deps)`    | 1 回につき 1 つの PR |

- ブランチ名は Dependabot が決める（`dependabot/…`）。ブランチ名の規則の例外として扱う。
- **自動マージはしない。** CI が通っても、中身を見てからマージする。とくに次は連動して確かめる:
  - Kotlin（`kotlin`）を上げる → KSP・Hilt が追随しているか、`./gradlew build` と instrumented test で確かめる
  - `compileSdk` / `targetSdk` が上がる更新 → behavior changes を読み、実機で記録を確かめる
- 見送る更新は、PR に理由を書いて閉じる（Dependabot はその版をもう提案しない）。
