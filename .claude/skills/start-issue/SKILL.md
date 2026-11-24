---
name: start-issue
description: GitHub Issue を読んで着手し、実装・コード確認・コミット・スマホでの動作確認を経て PR で閉じるまでの手順。Claude Desktop では Issue ごとに子のセッションを立てて、そこで進める。「#12 をやって」「Issue 12 に着手」「Project の次をやって」「着手できるもののいちばん上をやって」と頼まれたときに使う。
---

# Issue に着手して PR で閉じる

運用ルールの正は `docs/development/issues-and-adr.md` と `docs/development/branch-and-pr.md`。ここはその手順書。
ほかの Issue と並行して（worktree で）進めるときの決まりは `docs/development/parallel.md`。手順の違いは各段に「並行のとき」と書く。
ユーザーへの返答は日本語。**commit・push・マージ・タグ・GitHub の設定変更は、ユーザーの確認を取ってから**
（自動マージは 9 の push の確認で一緒に取る）。

Claude Desktop では、**頼まれたセッション（親）は 1・2 だけをして、3 から先は子のセッションが進める**。

## 1. どの Issue をやるか決める

番号を言われたらそれ。「次をやって」なら Project の「着手できるもの」ビューの先頭（＝Status が Todo のものの並び順の先頭。`idea` は Status が Idea なので入らない）:

```bash
gh project item-list 3 --owner y-nosuke --format json --limit 100 \
  --jq '.items[] | select(.status=="Todo") | "\(.content.number) \(.labels // [] | join(",")) \(.title)"'
```

並び順は API の返す順で、先頭がいちばん上。念のため `idea` ラベルのものが混じっていたら飛ばす。

**並行のとき**: In Progress の Issue（`select(.status=="In Progress")`）と見比べ、次のものは飛ばしてユーザーに伝える
（`docs/development/parallel.md` の「並べ方の決まり」）。

- In Progress がもう 2 本ある。
- In Progress の Issue と同じ画面・層を触りそう。
- DB のスキーマを変えそうで、In Progress にも DB を変えるものがある。

## 2. 子のセッションを立てる（Claude Desktop）

Issue の作業は、頼まれたセッション（親）では始めない。**Issue ごとに子のセッションを立て、そこで 3 から先を進める**。
親のチャットは受付にして、Issue ごとの作業・確認・承認の流れを子に分けるため（並行のときに混ざらない）。

- `start_session` で立てる: `initiation: "user_asked"`・`use_worktree: true`・`context: "fresh"`。タイトルは `#<番号> <Issue の要約>`。
- 依頼文は短く: 「#<番号> をやって。`start-issue` スキルの 3 から進める。あなたは作業をする子のセッションなので、子のセッションは立てない。」
  親でユーザーと決めた前提（画面の形・やらないことなど）があれば、それも書く（子は親の会話を読めない）。
- 2 本以上頼まれたら、1 の「並行のとき」で並べられるかを見てから、並べられるものだけ立てる。並べられないものは、立てずにユーザーに伝える。
- 立てたら、子へのリンクを伝える。**コードの確認・コミット・push の OK は、ユーザーが子のチャットで直接出す**
  （親が取り次ぐと、ユーザーの言葉かどうかが子に分からない）。
- 子が止まった（確認待ちなど）・終わった知らせが来たら、親は一言で伝える。親が子の作業を引き取らない。
- **子のセッションでは 2 を飛ばす**（依頼文に「子のセッション」とある）。孫は立てない。
- ユーザーが「ここでやって」と言ったときと、Claude Desktop 以外（ターミナルの `claude` など）では、子を立てずにそのセッションで 3 から進める。

## 3. 読む

```bash
gh issue view <番号> --repo y-nosuke/pathly --comments
```

- 背景・やること・完了の条件・関連（仕様・設計・ADR）を読み、関連の文書も開く。
- `idea` ラベル、または「やること」に**未定**がある → いきなり実装しない。仕様の案を出してユーザーと決め、
  docs/specs（必要なら ADR）に落としてから実装する。決まったら `idea` ラベルを外す（`gh issue edit <番号> --remove-label idea`）。
- 完了の条件に**実機でしか確かめられないもの**があれば、ユーザーに頼む前提で進める。
- **画面が変わるなら、作る前に画面の形を見せて決める。** スマホで見てから並べ方を変えると、実装・build・テスト・
  スマホでの確認を丸ごとやり直すことになる（#203 では 2 回やり直した）。案と一緒に、`docs/specs/screens.md` と同じ
  文字の図（必要ならエミュレータの今の画面のスクリーンショット）で次を見せ、ユーザーと決めてから実装する。
  - どこに置くか（画面の型の区画 → [designs/ui-direction.md](../../../docs/designs/ui-direction.md)）・縦か横か
  - **あふれたとき**（数が多い・名前が長い・記録中のピルや案内の帯と並んだとき）にどうなるか。上限で止めるか、まとめるか、送るか
  - いつ出るか・いつ消えるか、まとめて操作するもの（すべて外す・すべて見る など）が要るか

## 4. Project で In Progress に移す

```bash
N=<番号>
ID=$(gh project item-list 3 --owner y-nosuke --format json --limit 100 --jq ".items[] | select(.content.number==$N) | .id")
gh project item-edit --project-id PVT_kwHOAqZ51M4BAMaO --id "$ID" \
  --field-id PVTSSF_lAHOAqZ51M4BAMaOzgzEies --single-select-option-id 47fc9ee4   # In Progress
```

Status の選択肢 id: Idea `533a6515` / Todo `f75ad846` / In Progress `47fc9ee4` / Done `98236657`（Issue を閉じれば自動で Done になる）。

## 5. ブランチを切る

種類は Issue のラベル（`feat` / `fix` / `test` / `docs` / `build` / `ci` / `chore` …）に合わせる。日付・番号は入れない。

**子のセッション（worktree の中）**: main は本体で checkout されているので `git checkout main` できない。

```bash
.claude/skills/start-issue/setup-worktree.sh   # android/local.properties（Maps のキー）を本体からコピーする
git fetch origin
git switch -c <種類>/<英語のケバブケース> origin/main   # worktree を作ったときのブランチ（claude/… など）にいるなら、
                                                     # 代わりに git branch -m <種類>/<英語のケバブケース>（origin/main から作られていれば）
```

worktree でないとき（Claude Desktop 以外・「ここでやって」）:

```bash
git checkout main && git pull --ff-only
git checkout -b <種類>/<英語のケバブケース>
```

## 6. 作る・確かめる

- CLAUDE.md の「守ること」に従う。DB を変えるなら version を上げてからコンパイル（schemas の上書きの罠）。
- 確かめ方は CLAUDE.md の「確かめ方」の表。**途中は変えたところだけ、全部は push の前に 1 回**にする
  （build と全部の instrumented test で 1 周 7 分ほどかかり、直すたびに回すと積み上がる）。
  - 直している途中: `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin` と、変えたパッケージのユニットテスト
    （`testDebugUnitTest --tests "com.pathly.xxx.*"`）、変えた画面の instrumented test を 1 クラスだけ
    （`run-instrumented.sh -Pandroid.testInstrumentationRunnerArguments.class=...`）。
  - 8 のコード確認の前: `run-build.sh`（spotless・lint・全部のユニットテスト。コミットに崩れを入れないため）。
  - 全部の instrumented test（`run-instrumented.sh`）は 9 の push の前に 1 回。スマホでの確認で直しが入っても、まとめて 1 回でよい
    （CI でも全部回る）。DB を変えたときは、途中でも全部回す（マイグレーションのテストが別のクラスにあるため）。
- UI を変えたら、スマホに入れる前に**エミュレータで自分で画面を触って見る**（`emulator-check` の「APK を入れて触る」）。
  あふれたとき（数を多く・名前を長く）・記録中・シートを畳んだときなど、3 で決めた形どおりかをスクリーンショットで確かめる。
  押せない・はみ出すといった見た目の問題は、ユーザーの確認の前に見つける。
- 仕様・設計が変わったら docs も同じ PR で直す。判断の理由が要るなら ADR を書く。
- `feat` の Issue で、もとの要望が `docs/requirements.md` に無ければ、同じ PR で該当セクションの末尾に足す
  （`（YYYY-MM-DD 追加・#Issue番号）`。日付は Issue を作った日）。既にある行と同じ要望ならその行に番号を添える。
  要望ではない（画面の作り・実現方法・制約）なら `no-requirement` ラベルを付ける（[issues-and-adr.md](../../../docs/development/issues-and-adr.md) の「要望書との突き合わせ」）。

**並行のとき**:

- `run-build.sh`・`run-instrumented.sh` は、別の worktree で走っていれば終わるまで待つ（「順番待ち: …」と出る）。
  待っている間に止めない。重いビルドは順番待ちのスクリプトで回し、素の `./gradlew build` は使わない。
- エミュレータで画面を触る間は `hold-check-lock.sh` でロックを持つ（`emulator-check` の「APK を入れて触る」）。
- エミュレータに別のブランチの開発版が入っていたら、アンインストールしてから入れる（`emulator-check` の「APK を入れて触る」）。

## 7. 自分の変更をレビューする

コードを変えたら、PR を出す前に組み込みの `code-review` スキル（`/code-review`）を変更にかける。
書いた本人とは別の目で、不具合・取りこぼし・無駄を探すため。

- 指摘は、直すもの・直さないもの（理由つき）に分けてユーザーに見せる。直したら `./gradlew build` をやり直す。
- ドキュメントだけの変更なら省いてよい。
- レビューで見つからないもの（実機の挙動・見た目）は、確かめ方の表のとおり別に確かめる。

## 8. コードを確認してもらい、コミットしてスマホで確かめてもらう

PR を出す前に、ユーザーにコードと実機の動きを見てもらう。PR を出してから直すと、CI とレビューをやり直すことになるため。

1. **変更の要点を示す**: 何を変えたか（画面・データ・仕様）、確かめたこと（build・テスト・エミュレータ・レビューの指摘と直したもの）、
   直さなかったこと（理由つき）。ユーザーはここでコードを見る（差分ペインなど）。
2. **OK が出たらコミット**: `<種類>(<範囲>): <日本語の要約>`。本文に「なぜ」と `Closes #<番号>`。末尾に `Co-Authored-By:` の行。
   直しが要ると言われたら直して 1 に戻る。
3. **スマホに開発版を入れて、動作確認の手順を示す**: `phone-check` スキル。ユーザーが確かめる。
   - アプリの動きが変わらない変更（ドキュメント・ビルド・CI・スキルだけ）は 3 を省く。
   - **気づいたこと（不具合も、足したい・変えたいことも）は、手順を最後まで試してからまとめて送ってもらう**よう頼む。
     1 つずつ直して入れ直すと、確認の往復が増える。
   - 問題があれば直してコミットし、3 をやり直す。直しが仕様の変更なら、作る前に 3（読む）と同じく形を見せて決める。
   - **並行のとき**: スマホには 1 つずつ入れる。別のブランチの開発版が入っていればアンインストールしてから入れ、
     リリース版の書き出しを読み込んでもらう（`phone-check` の 2）。別のセッションの確認がまだ終わっていなければ、終わるまで待つ。

## 9. push して PR・完了の条件のチェック・自動マージ（ユーザーの確認を取ってから）

確認は「push して PR を出し、完了の条件が全部満たせていれば自動マージにしてよいか」を 1 回で聞く。
この確認の OK が、自動マージを有効にする OK を兼ねる。

- PR: タイトルはコミットと同じ形（**リリースノートの 1 行になる**）。本文は `.github/pull_request_template.md` に沿って、
  概要・`Closes #<番号>`・変更内容・検証。確かめていないことは「未確認」と書く。スマホで確かめたなら「スマホ（開発版）で確かめた（ユーザー）」。
- **並行のとき（main が進んでいたら）**: push の前に `git fetch origin && git merge origin/main` で取り込み、
  `run-build.sh` と `run-instrumented.sh` をやり直す（取り込んだ変更でアプリの動きが変わるなら、スマホの確認もやり直す）。
  DB の版がぶつかったら、こちらの版を付け直してマイグレーションと schema を作り直す（`docs/development/parallel.md`）。
- `git push -u origin <ブランチ>` → `gh pr create --base main --head <ブランチ> --title ... --body-file ...`
- Claude Desktop では、出したら Auto-fix を有効にする（CI を自分でポーリングしない。CLAUDE.md の「確かめ方」）。
- **PR を出したら、Issue の完了の条件にチェックを付ける**（GitHub は自動では付けない）。確かめられた項目だけ `- [x]` にし、
  満たせなかった・マージ後にしか確かめられない項目は `- [ ]` のまま、理由と次の手をコメントに書く。

  ```bash
  gh issue view <番号> --json body --jq .body > <scratchpad>/body.md   # 確かめた項目を - [x] に書き換える
  gh issue edit <番号> --body-file <scratchpad>/body.md
  ```

- **チェックが全部付いたら、自動マージ（マージコミット）を有効にする**。必須チェック（`build` と `instrumented-test`）が
  通ると GitHub がマージする。Claude Desktop では `ccd_pr` の `set_auto_merge`（`merge_method: "merge"`）、
  それ以外では `gh pr merge <PR> --auto --merge`。
  - **1 つでも `- [ ]` が残るなら自動マージにしない**。残った項目と理由をユーザーに伝え、10 の手でのマージに回す。
  - CI がもう通っていてマージできる状態（clean）だと、GitHub は自動マージを受け付けない。そのときはユーザーに確かめてから
    `gh pr merge <PR> --merge` でマージする。
  - CI が落ちて直す push をしても、自動マージの予約は残る。
  - **並行のとき**: main の ruleset の必須チェックは「最新の main と合わせてから」を求めていない（strict でない）。PR の CI が通った後に
    別の PR がマージされると、合わせた姿は CI を通らないままマージされる。自動マージを有効にする前・手でマージする前に、
    PR の CI の後に main が進んでいないか見る（`gh pr view <PR> --json mergeStateStatus` が `BEHIND`、または
    `git fetch && git log HEAD..origin/main`）。進んでいれば `gh pr update-branch <PR>` で main を取り込み、CI を回し直す。

## 10. マージの後（自動マージにしなかったときは、マージもユーザーの確認を取ってから）

- 自動マージにしなかった PR: 必須チェック（`build` と `instrumented-test`）が通り、残った項目の扱いをユーザーと決めてから
  `gh pr merge <PR> --merge`（マージコミットのみ）。
- マージ後: `git checkout main && git pull --ff-only && git branch -d <ブランチ> && git fetch --prune`。
  worktree の中では main に移れないので `git fetch --prune` だけにし、worktree とブランチの片付けはセッションを閉じるとき
  （Claude Desktop のアーカイブ・`ExitWorktree`）に任せる。本体の main は本体で `git pull --ff-only` する。
- Issue が閉じて Project で Done になったことを確かめる。マージ後にしか確かめられない項目があれば、確かめてからチェックを付ける。
