# 0048. 画面は設定と場所の検索を domain 越しに使い、端末の機能だけ data を直接使う

- Status: Accepted
- Date: 2026-11-26

## Context（背景）

[designs/architecture.md](../designs/architecture.md) は「依存はすべて domain に向く」としていたが、presentation が data の具体クラスを直接使っていた。

- 設定（`SettingsRepository` と `MapSurface`・`StatsList`・`ThemeMode`・`LocationAccuracy`）… 5 つの ViewModel と 2 つの画面
- 場所のキーワード検索（`PlacesTextSearcher`）… 3 つの ViewModel と `PlaceEditActions`
- 書き出し・読み込み（`DataBackupManager`）と異常終了の記録（`CrashReportStore`）… 設定画面だけ
- 記録サービスの制御（`TrackingController`）… architecture.md に書いてあり、意図どおり

文書と実装がずれたままだと、次に手を入れるときにどちらに合わせるかで迷う（#262）。

## Decision（決定）

**何画面からも使う部品は domain のインターフェース越しに使い、設定画面だけで使う端末の機能は data を直接使ってよい**ことにする。

- 設定: domain に `SettingsRepository`（インターフェース）を置き、実装は data の `SettingsRepositoryImpl`（SharedPreferences）。
  設定の値の型（`MapSurface`・`StatsList`・`ThemeMode`・`LocationAccuracy`）は `domain/model` に置く。
- 場所の検索: domain に `PlaceSearchRepository` を置き、実装は data の `PlacesTextSearcher`（Google Places）。
- どちらも `RepositoryModule` の `@Binds` で実装を差す。presentation・service・ほかの Repository の実装はインターフェースを見る。
- 例外として presentation から data を直接使ってよいのは次の 3 つ。
  - `data/tracking/TrackingController`（記録サービスの起動・バインドと権限・位置情報の ON/OFF）
  - `data/backup`（書き出し・読み込み）
  - `data/crash`（異常終了の記録）

例外にしたものは、どれも Android の都合（サービス・`Uri`・ファイル・`ApplicationExitInfo`）にじかに結びついていて、
差し替える先が考えにくい。書き出しと異常終了の記録は設定画面からしか使わない。

## Alternatives（検討した没案）

- **4 つとも domain のインターフェース越しにする** … 文書の「依存はすべて domain に向く」がそのまま成り立つ → **却下**。
  書き出しは `android.net.Uri` を受け取るので、そのままでは domain が Android に依存しはじめる。避けるには `Uri` を包む型が要り、
  1 画面だけで使うものに仕組みが増える。
- **コードは変えず、文書を今の姿に合わせる** … 変更がいちばん小さい → **却下**。設定と検索は記録・履歴・場所の画面の中心で、
  ユニットテストでも差し替えている。data に直結したままだと、保存先（将来のクラウド同期など）や検索の提供元を変えるたびに
  画面側に波及する。

## Consequences（結果・トレードオフ）

- 設定と検索は、実装を変えても presentation に波及しない。テストはインターフェースをモックする。
- 設定に項目を足すときは、インターフェース（domain）と実装（data）の 2 か所を直す。
- presentation から `com.pathly.data.` を import してよいのは、上の 3 つだけ。ほかのものが要るようになったら、
  まず domain にインターフェースを置けないかを考える。
- 書き出しに含める SharedPreferences の名前（`SettingsRepositoryImpl.PREFS_NAME`）は data の中の話なので、実装の側に残す。
