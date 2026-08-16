# 地図の上の UI の設計

**何が起きるかは [../specs/map.md](../specs/map.md) / [../specs/screens.md](../specs/screens.md) を正とする。**
ここには、画面をまたいで共有している部品と、その置き方だけを書く。

---

## 地図描画の共通化

記録画面と経路詳細は**別画面**（記録＝ライブ・片手／詳細＝事後の見直し・編集）だが、
経路の描き方は共通コンポーネント `presentation/common/RouteMapContent` に一元化して見た目を揃える。

各画面は**自前の `GoogleMap` の中で** `RouteMapContent` を呼ぶ。カメラの挙動が違うため
（記録＝現在地追従／詳細＝経路にフィット）、地図そのものは共有しない。

描画順は 確定立ち寄りの帯 → 立ち寄り中の帯 → 軌跡 → 開始/終了マーカー → 立ち寄りマーカー（番号）
→ 立ち寄り中マーカー（滞在時間ラベル）。記録画面は端末の現在地ドットがあるので終了マーカーを出さない
（`showEndMarker = false`）。

マーカーは**形で「確定＝丸／これから決める＝ピン」**を表す（→ [ADR-0018](../adr/0018-marker-shape-conveys-certainty.md)）。
ピンは `MapPinMarker` が Canvas で描く（Google 標準ピンは使わない）。ピンの**先端**と丸バッジの
**下端**が、そのままマーカーの基準点（既定で下端中央）に来るよう寸法を組んでいる。

マーカーは `MarkerComposable` を直に呼ばず、**`MapMarker`（`presentation/common/MapInfoWindow.kt`）で置く**。
タップで出る吹き出し（InfoWindow）は Compose ではなく地図が出しているのでシステムバックの対象にならず、
そのままだと地図の別の場所をタップするまで消えない。`MapMarker` は開いた吹き出しを
`MapInfoWindowState` に覚えさせ、**バックで閉じられるようにする**。状態は `rememberMapInfoWindowState()`
で **GoogleMap 1 つにつき 1 つ**作り、その地図のマーカー全部に渡す（吹き出しは地図全体で同時に 1 つしか
出ないので、覚えるのは 1 件でよい）。

マーカーのタップはシートと吹き出しを**同時に**出すので、**バック 1 回で両方畳む**。バックは最後に
登録されたハンドラ（＝シート）が受けるため、シート側で `CloseInfoWindowWithSheet` を呼び、
シートが composition から外れるときに吹き出しも閉じる。これが無いと 2 度押しになる。
シートの状態は画面が持ち、地図の composable には引数で渡す（`TrackingMapView` / `TrackMapView`）。

例外は**場所詳細で最初から出している吹き出し**（`markerState.showInfoWindow()`）。自分で開いたもの
ではなく画面の一部なので、`MapInfoWindowState` には覚えさせない＝**バックは画面を戻る**。

登録済みの場所のマーカーは `RegisteredPlaceMarkers` に切り出し、**トラックが無くても**
（記録開始前など）単独で描けるようにしている。**色＝訪問状態／グリフ＝業種／右上の旗＝行きたい**の
3 軸で描き分け、訪問・行きたいの判定は「場所」タブの `PlaceListItem` と揃える。立ち寄りと重なった
ときは立ち寄りを前面にする。

業種のグリフは `PlaceCategoryGroup`（Google の `primaryType` を束ねた分類）から引く。対応付けを
DB に持たないのは、`R.drawable` の ID を DB に入れられず結局コードで分岐するため
（→ [ADR-0017](../adr/0017-normalize-place-category.md)）。旗を右上へ逃がしているのは業種が
グリフを占めたからで、余白を左右対称に取って下端をバッジに揃え、**マーカーの基準点（下端中央）を
ずらさない**ようにしている。

## 非モーダルのシート

地図の上に出すシートは、標準の `ModalBottomSheet` ではなく**自前の `FloatingSheet`**
（`presentation/common/`）を使う。スクリムがタップを吸ってしまい、シートを出したまま地図を
操作できないため（→ [ADR-0010](../adr/0010-non-modal-map-sheets.md)）。

- 3 段階（`SheetDetent`: HIDDEN / PEEK / FULL）。畳みきったときに戻すボタンは `restoreLabel` を
  渡すとシート側が出す（各画面で書かない）。
- **地図に `contentPadding` としてシートの高さを渡す**。これでカメラの中心が見えている領域の中心に来て、
  タップした地点がシートの下に隠れない。高さは呼び出し側が `sheetState` から取れるよう、
  シートの状態を**外に持たせる**（`rememberFloatingSheetState` を画面側で持つ）。
- 用途ごとに 2 種類ある。**場所シート**（`presentation/places/PlaceActionSheet`）と
  **立ち寄りシート**（`presentation/stops/ManualStopSheet`）。滞在区間の調整は場所の登録に要らないので、
  同じシートにまとめない。
- 入力フォームは `PlaceFormBody` を共有する（追加・検索・編集・地図タップで同じ見た目）。
- **登録済みの場所を編集する部分は `PlaceEditorBody` 1 つに集約する。**場所タブの詳細画面と、
  地図のマーカーをタップして出るシートは、これを呼ぶ器が違うだけ（→ [ADR-0019](../adr/0019-single-place-editor.md)）。
  片方にだけ機能を足さないこと。

## 画面をまたぐ部品の置き場

| 置き場                 | 中身                                                                  |
| ---------------------- | --------------------------------------------------------------------- |
| `presentation/common/` | ドメインに依存しないもの（`FloatingSheet`・地図描画・確認ダイアログ） |
| `presentation/stops/`  | 立ち寄りの UI（シート・区間エディタ・付け替えダイアログ）             |
| `presentation/places/` | 場所の UI（一覧・詳細・場所シート）                                   |

滞在区間の調整（スライダー＋到着/出発の ＋/− 微調整）は `StopRangeEditor`
（`presentation/stops/ManualStopRange.kt`）に切り出し、記録中・経路詳細の**両方の手動追加で使う**
（軌跡点が 2 点未満のときだけ推定にフォールバック）。

## 表示トグルの保存先

「登録済みの場所を表示」の ON/OFF は **Room ではなく `SharedPreferences`** に持つ
（`SettingsRepository` / prefs 名 `pathly_settings`）。トグルは **UI の表示設定**であって
場所データではないため、DB には持たせない。GPS 記録間隔と同じ扱い。

- **画面ごとに独立**して保持する。画面は `MapSurface`（`RECORDING` / `HISTORY` / `PLACE_DETAIL`）で表す。
- 各画面の ViewModel が購読し、地図上のトグルで書く。プロセスをまたいで保持される。

## アイコンとテーマ

- **Material Icons は使わない**（非推奨のため）。`res/drawable` のベクター＋`painterResource` で足す。
- テーマは `PathlyAndroidTheme` に統一。Material3 のダイナミックカラー対応（Android 12+）で、
  非対応端末では暖色系のスキームにフォールバックする。色の定義は `ui/theme/`。
