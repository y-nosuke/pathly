---
name: start-issue
description: GitHub Issue を読んで着手し、PR で閉じるまでの手順。「#12 をやって」「Issue 12 に着手」「Project の次をやって」「着手できるもののいちばん上をやって」と頼まれたときに使う。
---

# Issue に着手して PR で閉じる

運用ルールの正は `docs/development/issues-and-adr.md` と `docs/development/branch-and-pr.md`。ここはその手順書。
ユーザーへの返答は日本語。**commit・push・マージ・タグ・GitHub の設定変更は、ユーザーの確認を取ってから**。

## 1. どの Issue をやるか決める

番号を言われたらそれ。「次をやって」なら Project の「着手できるもの」ビューの先頭（＝並び順の先頭で、`idea` でないもの）:

```bash
gh project item-list 3 --owner y-nosuke --format json --limit 100 \
  --jq '.items[] | select(.status=="Todo") | "\(.content.number) \(.labels // [] | join(",")) \(.title)"'
```

並び順は API の返す順で、先頭がいちばん上。`idea` ラベルのものは飛ばす。

## 2. 読む

```bash
gh issue view <番号> --repo y-nosuke/pathly --comments
```

- 背景・やること・完了の条件・関連（仕様・設計・ADR）を読み、関連の文書も開く。
- `idea` ラベル、または「やること」に**未定**がある → いきなり実装しない。仕様の案を出してユーザーと決め、
  docs/specs（必要なら ADR）に落としてから実装する。
- 完了の条件に**実機でしか確かめられないもの**があれば、ユーザーに頼む前提で進める。

## 3. Project で In Progress に移す

```bash
N=<番号>
ID=$(gh project item-list 3 --owner y-nosuke --format json --limit 100 --jq ".items[] | select(.content.number==$N) | .id")
gh project item-edit --project-id PVT_kwHOAqZ51M4BAMaO --id "$ID" \
  --field-id PVTSSF_lAHOAqZ51M4BAMaOzgzEies --single-select-option-id 47fc9ee4   # In Progress
```

Status の選択肢 id: Todo `f75ad846` / In Progress `47fc9ee4` / Done `98236657`（Issue を閉じれば自動で Done になる）。

## 4. ブランチを切る

```bash
git checkout main && git pull --ff-only
git checkout -b <種類>/<英語のケバブケース>
```

種類は Issue のラベル（`feat` / `fix` / `test` / `docs` / `build` / `ci` / `chore` …）に合わせる。日付・番号は入れない。

## 5. 作る・確かめる

- CLAUDE.md の「守ること」に従う。DB を変えるなら version を上げてからコンパイル（schemas の上書きの罠）。
- 確かめ方は CLAUDE.md の「確かめ方」の表。最低限 `./gradlew build`（`android/` で）。
  UI・DB を変えたら `emulator-check` スキルで instrumented test と画面の確認。
- 仕様・設計が変わったら docs も同じ PR で直す。判断の理由が要るなら ADR を書く。

## 6. 自分の変更をレビューする

コードを変えたら、PR を出す前に組み込みの `code-review` スキル（`/code-review`）を変更にかける。
書いた本人とは別の目で、不具合・取りこぼし・無駄を探すため。

- 指摘は、直すもの・直さないもの（理由つき）に分けてユーザーに見せる。直したら `./gradlew build` をやり直す。
- ドキュメントだけの変更なら省いてよい。
- レビューで見つからないもの（実機の挙動・見た目）は、確かめ方の表のとおり別に確かめる。

## 7. コミット・PR（ユーザーの確認を取ってから）

- コミット: `<種類>(<範囲>): <日本語の要約>`。本文に「なぜ」。末尾に `Co-Authored-By:` の行。
- PR: タイトルはコミットと同じ形（**リリースノートの 1 行になる**）。本文は `.github/pull_request_template.md` に沿って、
  概要・`Closes #<番号>`・変更内容・検証。確かめていないことは「未確認」と書く。
- `gh pr create --base main --head <ブランチ> --title ... --body-file ...`

## 8. マージ（ユーザーの確認を取ってから）

- **マージの前に、Issue の完了の条件にチェックを付ける**（GitHub は自動では付けない）。確かめられた項目だけ `- [x]` にし、
  満たせなかった・マージ後にしか確かめられない項目は `- [ ]` のまま、理由と次の手をコメントに書く。

  ```bash
  gh issue view <番号> --json body --jq .body > <scratchpad>/body.md   # 確かめた項目を - [x] に書き換える
  gh issue edit <番号> --body-file <scratchpad>/body.md
  ```

- 必須チェック（`build` と `instrumented-test`）が通ってから。`gh pr merge <PR> --merge`（マージコミットのみ）。
- マージ後: `git checkout main && git pull --ff-only && git branch -d <ブランチ> && git fetch --prune`。
- Issue が閉じて Project で Done になったことを確かめる。マージ後にしか確かめられない項目があれば、確かめてからチェックを付ける。
