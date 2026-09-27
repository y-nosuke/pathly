# CLAUDE.md

Claude Code がこのリポジトリで作業するときの前提と約束。**詳細はリンク先が正**で、ここには守ること・落とし穴・確かめ方だけを書く。

## プロジェクト

**Pathly** — お出掛けの記録・振り返り・計画をする Android アプリ。一人開発で、Claude Code に開発を進めてもらっている。

- 何をしたいか: [docs/requirements.md](docs/requirements.md)（要望）
- どの順でやるか: [docs/roadmap.md](docs/roadmap.md) と [Project](https://github.com/users/y-nosuke/projects/3)（やる順番）・[Issues](https://github.com/y-nosuke/pathly/issues)（個々の作業）
- 何が起きるか・どう作るか: [docs/README.md](docs/README.md) の索引（specs / designs / adr / development）
- 実装済みの機能の一覧: [README.md](README.md)

## いまの構成（実在するもの）

- **Android アプリだけ**（`android/`）。Kotlin・Jetpack Compose・MVVM + Clean Architecture・Hilt・Room・Coroutines + StateFlow・Navigation-Compose・WorkManager・Google Maps / Places。
- **データは端末の中だけ**（Room の SQLite。暗号化なし）。設定は SharedPreferences。持ち出しはアプリの書き出し・読み込み（[specs/backup.md](docs/specs/backup.md)）。
- **まだ無いもの**: クラウド（Supabase）・認証・同期・Web（Next.js）・iPhone 版。Phase 3 の構想で、[roadmap](docs/roadmap.md) の「温めている案」にある。**あるものとして扱わない**。

## コードの構成

パッケージ構成の図は [android/README.md](android/README.md)、レイヤーと「なぜこの構成か」は [designs/architecture.md](docs/designs/architecture.md)。ここには図から読めない約束だけを書く。

- 依存の向き: Screen ← StateFlow ← ViewModel → Repository interface（重複する手順は UseCase）→ 実装 → Room。詳細は [designs/architecture.md](docs/designs/architecture.md)。
- **ViewModel は Service を直接触らない。** 記録サービスの起動・バインドと端末状態（権限・位置情報 ON/OFF）は `data/tracking/TrackingController`。

## 守ること

### コード

- DI は Hilt（`@HiltViewModel` / `@AndroidEntryPoint`）。状態は StateFlow（LiveData は使わない）。非同期は Coroutines。
- テーマは `PathlyAndroidTheme`。**Material Icons は使わない**（`res/drawable` のベクター + `painterResource`）。
- ログは `com.pathly.util.Logger`。**座標・住所・施設名はログに出さない**（リリース版でも `i`/`w`/`e` は残る。[designs/logging.md](docs/designs/logging.md)）。
- **DB のスキーマを変えたら `DatabaseMigrations` に正式なマイグレーションを足す**（破壊的フォールバックは無効）。版は `PathlyDatabase.VERSION`。変更の経緯は各 ADR と `schemas/*.json`。
  - **version を上げる前にコンパイルしない**。前の版の `schemas/N.json` が上書きされて壊れる（build では気づけない）。
- SharedPreferences のファイルを増やしたら、書き出しの対象 `DataBackupManager.PREFS_NAMES` にも足す。
- WorkManager は Hilt でワーカーを組み立てるため自動初期化を止めてあり、`PathlyApplication`（`Configuration.Provider`）が担う。

### ビルド・依存

- AGP・Gradle・Kotlin（AGP 内蔵）・KSP。版は `android/gradle/libs.versions.toml` と `gradle-wrapper.properties` が正。
- Kotlin の版は `libs.versions.toml` の `kotlin`（`kotlin.plugin.compose` が引く KGP）で決まる。上げるときは KSP・Hilt の追随を `./gradlew build` と instrumented test で確かめる。
- minSdk 34 / compileSdk・targetSdk 37。ビルド用の JDK は 25（`gradle-daemon-jvm.properties`）、アプリのバイトコードは 17。
- 依存の更新は Dependabot が月 1 回 PR を作る。中身を見てからマージする（[development/branch-and-pr.md](docs/development/branch-and-pr.md)）。

### 進め方

- 作業は **Issue** から。「#12 をやって」と言われたら `gh issue view 12` で読み、[Project](https://github.com/users/y-nosuke/projects/3) で In Progress に移してから着手する。やる順番は Project の並び順。未決の案は Claude のメモリに置かず Issue にする（[development/issues-and-adr.md](docs/development/issues-and-adr.md)）。
- ブランチ名は `<種類>/<英語のケバブケース>`、コミットと PR タイトルは `<種類>(<範囲>): <日本語の要約>`。PR 本文に `Closes #12`。PR タイトルはリリースノートになる（[development/branch-and-pr.md](docs/development/branch-and-pr.md)）。
- 設計の判断（なぜ・没案）は ADR、今の姿は specs / designs に書く。終わった作業の記録は設計書に残さない（[docs/README.md](docs/README.md) の運用ルール）。
- 繰り返す手順はスキル（`.claude/skills/`）にある: `start-issue`（Issue の着手〜PR）・`emulator-check`（エミュレータでのテスト・確認）・`release`（リリースとスマホの更新）。
- **commit・push・タグ・マージ・GitHub の設定変更は、ユーザーの確認を取ってから**行う。
- ユーザーへの返答は**日本語**で書く（途中経過の一言も）。

## 確かめ方

| いつ                     | 何をする                                                                                                                                       |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| push 前                  | `./gradlew build`（spotless・lint・ユニットテスト・assemble をまとめて見る。部分タスクだけでは見逃す）                                         |
| UI・DB を変えたとき      | `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`（Wi-Fi でつないだスマホを巻き込まない。AVD は `Pixel_10_API36`）            |
| マージ前                 | CI の `build` と `instrumented-test`（どちらも必須）。マージはマージコミットのみ                                                               |
| リリース前               | R8 はリリース版にしかかからない。`./gradlew assembleRelease` をエミュレータに入れて一通り触る（[designs/release.md](docs/designs/release.md)） |
| 実機でしか見られないもの | GPS の長時間記録など。ユーザーに頼む（Issue の完了の条件に書く）                                                                               |

## 手元の環境（Windows + Git Bash）の落とし穴

- adb に端末側のパス（`/data/local/tmp/...`）を渡すときは `MSYS_NO_PATHCONV=1` を付ける。付けないと Git Bash が Windows のパスに書き換える。
- `adb` が PATH に無いシェルでは `C:/Users/yoichi/AppData/Local/Android/Sdk/platform-tools/adb` を使う。Wi-Fi の接続が切れたら `adb mdns services` で見つけ直す。
- `sed -i` は CRLF を LF に変えて spotless を落とすことがある。使ったら `./gradlew spotlessApply`。
- エミュレータの UI を adb で操作するときは、1 操作ごとに画面を待つ（メニューが開き切る前に次を押すと空振りする）。

## よく使うコマンド（`android/` で実行）

```bash
./gradlew build                      # push 前の確認（全部入り）
./gradlew installDebug               # 開発版 com.pathly.debug を入れる（リリース版 com.pathly とは別アプリ・別データ）
./gradlew spotlessApply              # 整形の崩れを直す
./gradlew assembleRelease            # リリース版（リリース鍵は `$GRADLE_USER_HOME/gradle.properties` から。無ければ未署名）
```

## 方針

- 通知で知らせる機能は作らない（自動で動くことを優先。記録中の常駐通知は Android の要件なので例外）。
- 位置情報の削除は本人に委ね、自動削除はしない。
- 無料枠を最大限使い、段階的に広げる。詳細な仕様・設計は着手時に決める。
