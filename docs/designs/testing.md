# テスト戦略

**Pathly で何をどう守るか**を書く。JUnit・MockK・Compose Testing の一般的な書き方は
公式ドキュメントに譲り、ここには**このプロジェクト固有の判断と、実際に踏んだ落とし穴**だけ残す。

## 置き場所と使い分け

| 種類                     | 場所                   | 実行                | 対象                                                  |
| ------------------------ | ---------------------- | ------------------- | ----------------------------------------------------- |
| ユニットテスト           | `app/src/test/`        | JVM（数秒）         | ドメイン・UseCase・Repository・ViewModel              |
| インストルメンテーション | `app/src/androidTest/` | 実機/エミュ（数分） | DAO・マイグレーション・取り消しのある変更・Compose UI |

**判断基準は「実際の Android が要るか」だけ**。Room の SQL・マイグレーション・外部キーの挙動は実際の SQLite が要るので
androidTest、それ以外はモックで JVM に寄せる。

## 層ごとの方針

### ドメイン・UseCase（ここを厚く守る）

`domain/model/`（`Geo` / `TrackSmoother` / `StopDetector` / `GpsTrack`）は依存が無いので素直に書ける。

**`domain/usecase/` は特に厚く守る。** `PlaceEditUseCase` / `AddManualStopUseCase` は記録画面・経路詳細・
場所タブの 3 画面で共有していて、切り出す前は**近接確認の分岐が Composable 側にあってテストできなかった**。
Repository をモックすれば 3 画面ぶんの挙動をまとめて検証できるので、**画面から UseCase へ移したロジックには
必ずテストを付ける**。

### Repository

DAO をモックして JVM で回す。守りたいのは SQL ではなく**書き込み先の判断**で、たとえば
「Google の名前を `places.name` に書いていないか」「取得済みの施設情報があるとき Places を叩き直さないか」
といった、間違えても動いてしまう種類のバグを狙う。

場所と立ち寄りの 3 つの窓口（`LiveStopRepositoryImpl`・`StopRepositoryImpl`・`PlaceRepositoryImpl`）は、テストも窓口ごとに分け、
組み立ては `PlaceStopRepositoryTestBase` で共有する。本番と同じく 3 つが 1 つのロックと状態を共有するので、
窓口をまたぐ操作（記録中の立ち寄りの場所の名前を変える など）もそのまま確かめられる（→ [ADR-0049](../adr/0049-split-place-repository-by-responsibility.md)）。

**例外は「取り消しのある変更」**（立ち寄りの統合・削除・付け替え、場所の削除）。変更前の行を控えて消し、
子は CASCADE に任せ、取り消しでは外部キーの順に元の ID で入れ直す、という作りなので、
モックでは外部キー・CASCADE・再挿入の挙動が見えない。これらは Repository をインメモリの実 DB で組み立てて
androidTest で通す（`StopAndPlaceChangeIntegrationTest`）。同じ仕組みの操作を足したら、ここにもテストを足す。

### ViewModel

Repository をモックし、`StateFlow` の遷移を見る。**Android framework に触る部分は ViewModel に置かない**
（`data/tracking/TrackingController` に寄せてある）。そうしないとテストが書けないので、
書きにくいと感じたら設計側を疑う。

### DAO・マイグレーション（androidTest）

- DAO はインメモリ DB（`Room.inMemoryDatabaseBuilder`）。`@After` で必ず `close()`。
- **マイグレーションは `MigrationTest` で全バージョン連鎖を検証する**（`room-testing` の `MigrationTestHelper`）。
  破壊的フォールバックを無効にしているので、これが落ちるとユーザーの手元でアプリが起動しなくなる。

### Compose UI（androidTest）

画面が壊れていないかの薄い確認に留める。地図は差し替え可能なスロットにしてあり、テストでは実地図を出さない
（実機の Google Play services に依存させないため）。

## CI と push 前のゲート

`.github/workflows/android-build.yml` が main への push / PR で動き、**`./gradlew build` 一発**で
ユニットテスト・lint・spotless（ktlint）・assemble(debug/release) をまとめて実行する。
debug APK（開発版 `com.pathly.debug`）と lint/test レポートをアーティファクトに残す（同一ブランチの新 push で進行中の実行はキャンセル）。
版を git から決めるため全履歴を取得する。リリース（タグの push）は別のワークフローで、[release.md](release.md) を参照。

> **push 前は手元で `spotlessCheck testDebugUnitTest compileDebugAndroidTestKotlin` を通すこと**
> （見張り付きの `.claude/skills/emulator-check/run-build.sh` の既定）。整形・コンパイル（本体・ユニットテスト・androidTest）・
> ユニットテストを見る。`test` だけでは整形（`spotlessKotlinCheck`）を、本体のコンパイルだけではテストのコンパイルを見逃して
> CI で落ちる（どちらも実際に落としたことがある）。整形の崩れは `./gradlew spotlessApply` で直る。
>
> **lint・release 版（コンパイル・`lintVitalRelease`・R8）・APK の組み立ては手元で回さず、CI の `build` に任せる**（#280）。
> 手元（Windows のノート）で `./gradlew build` を回すと 30 分近くかかり、手元の確認は 1〜2 分で済む。CI の `build` は 9〜13 分で、
> マージの必須チェックなので、手元で外したものも main に入る前に必ず見られる。lint の指摘は PR の CI で初めて分かる
> （直して push し直す）。debug APK はスマホに入れる（`installDebug`）ときに組み立てる。
> 重いのは lint（4 本）と R8 で、手元では CPU を取り合う。Gradle のヒープを上げても速くならない（測った結果は #280）。

インストルメンテーションテストは、同じワークフローの別ジョブ（`instrumented-test`）が **CI のエミュレータ（API 36）で回す**。
`build` と並行で走るので待ち時間は延びない。レポートはアーティファクト（`instrumented-test-reports`）に残る。
main の ruleset で **`build` と `instrumented-test` の両方が必須**（どちらかが落ちている・終わっていないとマージできない →
[branch-and-pr.md](../development/branch-and-pr.md) の「main の守り」）。
手元で回すときは、Wi-Fi でつないだスマホを巻き込まないよう `ANDROID_SERIAL=emulator-5554` を付けて
`./gradlew connectedDebugAndroidTest` を実行する。手元では変えた画面のクラス（DB を変えたら `com.pathly.data` のパッケージ）だけを回し、
全部は CI に任せる（[start-issue](../../.claude/skills/start-issue/SKILL.md) の 6）。

## 実機でしか確かめられないもの

エミュレータのグリーンでは足りない項目。リリース前に手で確認する。

- **DB マイグレーションの実データ移行**（`MigrationTest` は空に近いデータでしか回らない）
- **位置情報サービスが OFF の状態での記録開始**（開始されず、「位置情報の設定を開く」が出る）
- **サービスの異常終了からの復帰**（START_STICKY での再開）。プロセスを落とすと数秒で
  `restoreTrackingIfNeeded()` が同じトラックに続けて記録を再開する
- バックグラウンドでの長時間記録・電池の最適化の影響

## 踏んだ落とし穴

- **ライセンスファイルの重複**（`6 files found with path 'META-INF/LICENSE.md'`）
  → `build.gradle.kts` の `packaging { resources { excludes += ... } }` で回避済み。
- **依存の版ずれ**（kotlinx-serialization の BOM 不整合）で androidTest だけが落ちた。
  ユニットテストが通っても androidTest が通るとは限らない。
- **腐ったテスト**は落ちるまで気づけない。UI を作り替えたら、そのテストも同時に直す
  （`TrackDetailScreen` の分割時に実際に取り残した）。
- **Room のスキーマの上書き**: エンティティを変えたあと **version を上げる前にコンパイルすると**、KSP が
  「今の version」の `schemas/<N>.json` を新しい内容で上書きする。実行時の Room は最新のスキーマとしか照らし合わせないので
  アプリは動き、ユニットテストでも `./gradlew build` でも気づけない（`MigrationTest` でだけ落ちる）。
  → 順番は「エンティティを変える → version を上げる → コンパイル」。先にコンパイルしたら
  `git checkout <前のコミット> -- app/schemas/.../<前の版>.json` で戻す。コミット前に **過去の版の `schemas/*.json` が変わっていないか** `git status` で見る。
- **整形の検査が増分ビルドで飛ばされる**: 手元の確認が通っても、spotless が up-to-date で飛ばされ、
  CI（毎回まっさらに動く）で初めて落ちることがある。Kotlin を触ったら `./gradlew spotlessCheck --rerun-tasks` で強制的に検査する。
  部分タスク（compile だけ・test だけ）もそれぞれ別のゲートを見逃す（コンストラクタを変えてテストのコンパイルが落ちた例あり）。
- **地図を描く UI テストはクラッシュする**（Play 開発者サービスの地図が、テスト環境で必要なクラスを欠く）。地図は
  `mapContent` のスロットにしてあり、テストでは空を渡す。本番の見た目は変わらない。
- **CI のエミュレータの既定は画面が小さい**: 下のほうの要素が画面外になり、UI テストが「表示されていない」「タップできない」で落ちた。
  CI は `profile: pixel_7` にしている。

## エミュレータの準備（Windows）

- SDK は `C:\Users\yoichi\AppData\Local\Android\Sdk`。手元の AVD は `Pixel_10_API36`（android-36・google_apis_playstore・x86_64）。
- `avdmanager` / `sdkmanager` は JDK 25 だと「Java 17 以上が必要」と誤って止まる。`JAVA_HOME` を Android Studio の JBR
  （`C:\Program Files\Android\Android Studio\jbr`）に向けると動く。Gradle は自前の JDK で動くので影響しない。
- 起動・テスト・APK を入れて触る手順は、Claude のスキル `.claude/skills/emulator-check/` にまとめてある。

## テスト実行

```bash
./gradlew spotlessCheck testDebugUnitTest compileDebugAndroidTestKotlin   # push 前の確認
./gradlew build                                                           # CI と同じ全部入り（手元だと 30 分ほど）
```

```bash
./gradlew connectedAndroidTest
```

```bash
./gradlew test --tests "com.pathly.domain.usecase.PlaceEditUseCaseTest"
```

レポート: `app/build/reports/tests/testDebugUnitTest/index.html`
