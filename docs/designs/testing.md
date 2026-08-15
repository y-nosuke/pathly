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
debug APK と lint/test レポートをアーティファクトに残す（同一ブランチの新 push で進行中の実行はキャンセル）。

> **push 前は `./gradlew build` を通すこと。** `test` だけ／`lint` だけを回すと、整形
> （`spotlessKotlinCheck`）や別のゲートを見逃して CI で落ちる。実際に両方で落としたことがある。
> 整形の崩れは `./gradlew spotlessApply` で直る。

インストルメンテーションテストはエミュレータが要るため **CI では回さない**。実機／ローカルのエミュレータで確認する。

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
