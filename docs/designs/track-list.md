# 経路一覧（履歴）の名前・お気に入り・絞り込み・並べ替え

対象画面: `presentation/history/HistoryScreen.kt`（外出履歴タブ）。
経路（お出掛け＝`gps_tracks`）に**名前**と**お気に入り**を持たせ、一覧を「場所」タブと同じ流儀で
**絞り込み・並べ替え**できるようにする。各カードには**立ち寄り件数**を表示する。
要望は [../requirements.md](../requirements.md)（2026-08-06 追加分）。決定の背景は [../adr/0003-track-name-and-favorite.md](../adr/0003-track-name-and-favorite.md)。

## データモデル

`gps_tracks` に 2 列を追加（DB v8・[../adr/0003-track-name-and-favorite.md](../adr/0003-track-name-and-favorite.md)）。

| 列           | 型                         | 意味                                            |
| ------------ | -------------------------- | ----------------------------------------------- |
| `name`       | TEXT (nullable)            | ユーザーが付けた経路名。null/空白のみ＝未命名。 |
| `isFavorite` | INTEGER NOT NULL DEFAULT 0 | お気に入り登録フラグ。                          |

立ち寄り件数は**保存しない**。`stops` を経路ごとに数えた集計を都度使う（記録の追加・削除に自動追従するため）。

- `StopDao.observeStopCountsByTrack()` … `SELECT trackId, COUNT(*) FROM stops GROUP BY trackId` を
  `Flow<List<TrackStopCount>>` で流す。立ち寄り0件の経路は行に出ない（=0件として扱う）。
- リポジトリ `GpsTrackRepositoryImpl.getAllTracks()` は一覧用の行 Flow と件数 Flow を `combine` し、
  `GpsTrack.stopCount` に載せて返す。

### 距離は確定時に焼き込む（v11）

`gps_tracks.totalDistanceMeters`（REAL・nullable）に、記録の**確定時**に補正後点列で計算した総移動距離を
書き込む。一覧はこの値を読むだけで、**GPS 点を一切ロードしない**。

以前は一覧を描くたびに全経路の全点を読み、その場で平滑化して距離を出していた。経路が増えるほど重くなり、
しかも UI スレッドで走っていたため、記録が溜まると履歴タブが目に見えて詰まった。

- 一覧の 1 行は `TrackListRow`（経路 ＋ 生点の件数だけを集計した射影）で取る。点数表示はこの集計で賄う。
- v11 より前に記録した経路は `null` なので、起動時に `GpsTrackRepository.backfillMissingDistances()` が
  一度だけ埋める（対象が無ければ即終了）。
- 距離順の並べ替えもこの列を見る。補正パラメータを変えても**再計算はしない**（焼き込み済みの値が残る）。

## ドメイン

`GpsTrack` に `name: String?`・`isFavorite: Boolean`・`stopCount: Int` を追加。
`hasName`（`name` が空白でない）を持つ。名前・件数・お気に入りはリポジトリが詰めて渡す。

リポジトリ操作（`GpsTrackRepository`）:

- `renameTrack(trackId, name)` … 前後空白を除き、空なら未命名（null）に正規化して保存。
- `setFavorite(trackId, favorite)` … お気に入りの ON/OFF。

## 一覧の絞り込み・並べ替え（`HistoryState`）

「場所」タブ（`PlacesState`）と同じ設計。**絞り込みは 3 軸独立**（各 3 状態）、**並べ替えは軸＋昇降**。
フィルタ・ソートは `HistoryState.visibleTracks`（純関数）で適用する（ViewModel は状態を持つだけ）。

- 絞り込み軸（すべて独立・AND）:
  - お気に入り: 指定なし / お気に入り / お気に入り以外
  - 命名: 指定なし / 名前あり / 未命名
  - 立ち寄り: 指定なし / あり / なし
- 「すべて」チップで 3 軸まとめて解除。何も絞っていなければ「すべて」を選択表示。
- 並べ替え軸（既定の向き）: 日付順（降順・既定）/ 立ち寄り数順（降順）/ 距離順（降順）/ 時間順（降順）/ 名前順（昇順）。
  - 各軸は昇順の比較器で定義し、降順ならまとめて反転（安定ソート）。元の並びは開始が新しい順。
  - 名前順の未命名は `null` 相当で、昇順で先頭・降順で末尾。

一覧に載せるのは**完了済み**（記録中でなく終了時刻あり）だけ。記録中トラックは従来どおり一覧先頭に別枠表示。

## UI（`HistoryScreen`）

- タイトル下に**絞り込みチップ**（横スクロール可）＋**並べ替え**（軸ドロップダウン＋昇降トグル）バー。
  記録が 1 件も無いときはバーを隠す。絞り込みで 0 件のときは「条件に合う記録がありません」。
- カード:
  - 見出しは**名前があれば名前**（無ければ日付）。名前ありのときは日付を副題に添える。
  - 開始/終了時刻、移動距離、**立ち寄り件数**（`立ち寄り: N件`）を表示（従来の「(N点)」デバッグ表示は廃止）。
  - 右に**お気に入りハート**（トグル）と**オーバーフローメニュー**（`名前を付ける/編集`・`削除`）。
  - 名前編集は `AlertDialog`。空で保存すると未命名に戻る。
- アイコンは `res/drawable` のベクター（`ic_favorite`／`ic_favorite_filled`／`ic_more_vert`）＋`painterResource`。

## 実装マップ

| 何を                         | どこで                                                                                       |
| ---------------------------- | -------------------------------------------------------------------------------------------- |
| 列追加（name/isFavorite）    | `GpsTrackEntity` / `DatabaseMigrations.MIGRATION_7_8`（DB v8）                               |
| 距離の焼き込み（v11）        | `GpsTrackEntity.totalDistanceMeters` / `MIGRATION_10_11` / `GpsTrackDao.updateTotalDistance` |
| 一覧用の射影                 | `data/local/entity/TrackListRow.kt`（経路＋点数の集計・点はロードしない）                    |
| 既存分の埋め戻し             | `GpsTrackRepository.backfillMissingDistances`（`PathlyApplication` から一度だけ）            |
| 名前・お気に入り更新         | `GpsTrackDao.updateName` / `updateFavorite`                                                  |
| 立ち寄り件数の集計           | `StopDao.observeStopCountsByTrack`（`TrackStopCount`）                                       |
| 件数の合流                   | `GpsTrackRepositoryImpl.getAllTracks`（`combine`）                                           |
| 絞り込み・並べ替え（純関数） | `HistoryState.visibleTracks`                                                                 |
| 操作                         | `HistoryViewModel`（setter 群・`toggleFavorite`・`renameTrack`）                             |
| 画面                         | `HistoryScreen`（フィルタ/ソートバー・カード・名前編集ダイアログ）                           |
