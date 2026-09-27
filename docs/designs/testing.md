# テスト戦略

**Pathly で何をどう守るか**を書く。JUnit・MockK・Compose Testing の一般的な書き方は
公式ドキュメントに譲り、ここには**このプロジェクト固有の判断と、実際に踏んだ落とし穴**だけ残す。

## 置き場所と使い分け

| 種類                     | 場所                   | 実行                | 対象                                     |
| ------------------------ | ---------------------- | ------------------- | ---------------------------------------- |
| ユニットテスト           | `app/src/test/`        | JVM（数秒）         | ドメイン・UseCase・Repository・ViewModel |
| インストルメンテーション | `app/src/androidTest/` | 実機/エミュ（数分） | DAO・マイグレーション・Compose UI        |

**判断基準は「実際の Android が要るか」だけ**。Room の SQL とマイグレーションは実際の SQLite が要るので
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

> **push 前は `./gradlew build` を通すこと。** `test` だけ／`lint` だけを回すと、整形
> （`spotlessKotlinCheck`）や別のゲートを見逃して CI で落ちる。実際に両方で落としたことがある。
> 整形の崩れは `./gradlew spotlessApply` で直る。

インストルメンテーションテストは、同じワークフローの別ジョブ（`instrumented-test`）が **CI のエミュレータ（API 36）で回す**。
`build` と並行で走るので待ち時間は延びない。レポートはアーティファクト（`instrumented-test-reports`）に残る。
main のブランチ保護で **`build` と `instrumented-test` の両方が必須**（どちらかが落ちているとマージできない）。
手元で回すときは、Wi-Fi でつないだスマホを巻き込まないよう `ANDROID_SERIAL=emulator-5554` を付けて
`./gradlew connectedDebugAndroidTest` を実行する。

## 実機でしか確かめられないもの

エミュレータのグリーンでは足りない項目。リリース前に手で確認する。

- **DB マイグレーションの実データ移行**（`MigrationTest` は空に近いデータでしか回らない）
- **位置情報サービスが OFF の状態での記録開始**
- **サービスの異常終了からの復帰**（START_STICKY での再開）
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
- **整形の検査が増分ビルドで飛ばされる**: 手元の `./gradlew build` が通っても、spotless が up-to-date で飛ばされ、
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
./gradlew build
```

```bash
./gradlew connectedAndroidTest
```

```bash
./gradlew test --tests "com.pathly.domain.usecase.PlaceEditUseCaseTest"
```

レポート: `app/build/reports/tests/testDebugUnitTest/index.html`
