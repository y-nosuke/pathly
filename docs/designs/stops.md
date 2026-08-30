# 立ち寄りの設計

**何が起きるかは [../specs/stops.md](../specs/stops.md) を正とする。**ここには作り方だけ書く。
検出そのもの（`StopDetector`）は補正後の点列に対して走る（[smoothing.md](smoothing.md)）。

---

## ドメインモデル

- `DetectedStop` … `StopDetector` の**検出結果（幾何のみ）**。緯度経度・到着/出発・点数。永続化しない。
- `Stop` … 永続化された立ち寄り（id・`place: Place`・trackId・到着/出発・訪問メモ）。滞在時間は計算で出す。

---

## 記録中の検出（増分・境界以降だけ見る）

`LocationTrackingService` が点を保存・補正する流れに続けて、立ち寄りも増分で処理する。
補正と同じ「確定プレフィックス」の考え方を使う。

**毎ティック全点を検出し直さない。**「最後に確定した立ち寄りの `departureTime`（＝**境界**）」より
後の補正点だけを `StopDetector` にかける（`SmoothedPointDao.getByTrackAfter`）。
`StopDetector` の貪欲スキャンは**境界より手前の点に依存しない**（クラスタを確定したら次へ飛ぶだけ）ので、
末尾のクラスタ分割は全点で検出した場合と**一致する**。検出コストは「直近の確定立ち寄り以降の点数」に比例する。

**境界は削除で下げない。** 境界はトラック単位で**メモリに保持**し、記録セッション中は**単調増加**させる。
未設定のとき（プロセス再起動後の再開など）だけ、保存済み立ち寄りの最終 `departureTime` で種をまき直す。
こうすると、記録中に削除した立ち寄りの区間は境界より手前になって検出スライスに入らず、
**GPS が同じでも再挿入されない**（→ [ADR-0002](../adr/0002-incremental-live-stop-detection.md)）。

> 既知の端: 記録中にプロセスが kill され、かつ**末尾の立ち寄りを削除した直後にクラッシュ**した場合のみ、
> DB の最終 departure で種をまき直すため、その 1 件が再検出されうる（中間削除は影響なし）。

**place は滞在中に先に確定し、stop は離脱で確定する（案A）。** 滞在がしきい値を超えた時点で
重心から `findOrCreatePlace` して place を作り、オンラインなら名前解決まで済ませる。
これで「立ち寄り中」を場所名つきで出せる。stop はまだ保存せず、離脱または `isFinal` で保存する。

**確保はひとつの滞在につき 1 回**（→ [ADR-0023](../adr/0023-place-identity-and-coordinate-anchor.md)）。
確保した placeId をトラック単位でメモリに覚え、その滞在の間は使い回す。**ティックごとに探し直さない。**
位置バッチは既定 10 秒ごとに届くので、毎回確保しにいくと同じ滞在で place と Places 呼び出しが積み上がる。

**「立ち寄り中」の表示だけは生の現在地（`GpsPointDao.getLatestPoint`）で離脱を判定する。**
補正後点は末尾が確定保留でラグがあり、さらに半径を抜けきるまでクラスタが末尾を吸収し続けるため、
補正後点で判定すると表示が消えない。**表示のみ**の判定で、保存する立ち寄りには影響しない。

```text
新しい点が保存・補正される
  └─ updateStopsForTrack(trackId, isFinal)
       - 境界 = メモリの高水位 ?: 保存済み立ち寄りの最終 departure ?: なし（＝全点）
       - StopDetector.detect(境界より後の補正点だけ) で DetectedStop 群を得る
       - 末尾の「滞在中」クラスタ（末尾点を含む）:
           しきい値超なら（その滞在で未確保のときだけ）findOrCreatePlace → (online) resolve、
           確保済みなら覚えた placeId を使う。"立ち寄り中" を StateFlow で公開
           離れて確定 or isFinal なら stops に保存し、公開を解除
       - それ以外の確定クラスタ（すべて境界より後＝新規）:
           findOrCreatePlace → stops に保存、境界を確定分の最終 departure まで進める
           オンラインなら未解決 place を resolve（オフラインは行を作らず後でキャッチアップ）
```

境界はメモリ保持で、記録終了（`isFinal`）時に破棄する。

---

## 手で足す・直す

- **再解析**: `detectMissingStops(trackId)` が保存済みの補正点列を `StopDetector` にかけ、
  **既存の立ち寄りと時間帯が重ならない候補**（`StopCandidate` ＝検出＋表示名）を返す。永続化しない。
  選ばれた分を `addStops(trackId, 選択分)` で保存する。
  - 候補の名前は、近くに命名済み place があれば**再利用（無料）**、無ければオンライン時のみ Places を 1 回。
    追加時はその名前を焼き込むので **Places を二度叩かない**。
  - **記録中は候補を境界以前に絞る**。`detectMissingStops` は `detectionHighWaterMillis` に境界エントリが
    あれば `departure <= 境界` で絞り、ライブ検出が受け持つ末尾・滞在中を外して二重登録を防ぐ。
    終了済みの経路は境界エントリが無いので全点が対象。境界は削除で下がらないため、消した過去は候補に残る。
- **立ち寄りを追加**: `addManualStop(trackId, lat, lng, arrival, departure, name, googlePlaceId, googleName)`。
  到着/出発は指した地点の最寄り軌跡点から導出する。近接確認の要否判断まで含めた手順は
  `domain/usecase/AddManualStopUseCase`（記録画面と共有）。
  - **手動追加した立ち寄りは自動命名しない。** `addManualStop` は常に `place_resolutions` 行を残すので、
    ライブ検出の自動命名がユーザーの入力（または「名前なし」の選択）を上書きしない。
- **既存の場所へ紐付け**: `addManualStopForPlace`。新規 place を作らずに訪問だけ足す。
- **名前で探して足す**: 施設がまだ決まっていない起点（地図の空き地点・「今ここ」）では、
  `presentation/common/PlaceNameSearchField`（キーワード検索の共通部品）で施設を選べる。座標だけを
  頼りにすると、地図に POI が出ていない場所・代表点が敷地の中心にある広い施設は選べないため。
  選ぶと `googlePlaceId` と**施設の座標**が確定し、以降は POI をタップしたときと同じ扱いになる。
- **選び直し（この訪問だけ付け替え）**: `reassignStopPlace`。POI 候補を選べば施設の同一性で同定した place へ、
  名前だけ手入力なら元の座標で新しい USER 場所を作る。**他の経路・訪問は不変**
  （→ [ADR-0007](../adr/0007-reassign-stop-this-visit-only.md)）。
  - ダイアログの中身は場所詳細の「Googleで情報を取得」と**同じ** `NearbyCandidatePickerDialog`。
    近くの候補に出ない施設のために**名前での検索**も出す（両画面とも同じ `PlaceNameSearchField`）。
    テキスト欄が2つ並ぶが役目は別で、上は**自分で付ける名前**、下は**施設の検索**。
- **滞在期間の編集**: `updateStopDuration(stopId, arrival, departure)` が `stops` の到着・出発だけを
  更新する。**GPS 点・補正後の点は触らない**（観測した事実は変えない）。到着が出発以降になる指定は
  書かずに捨てる。画面は手動追加と同じ `StopRangeEditor`（軌跡点のインデックスで調整）を使うので、
  保存される時刻は必ず**実在する点の時刻**になる。
- **統合**: `mergeStops(stopIds)` が選んだ立ち寄りを 1 件にする。残すのは**到着が最も早い 1 件**
  （id が変わらないので地図・一覧の対応が飛ばない）。期間は最も早い到着〜最も遅い出発、メモは
  到着順に連結（空白だけのメモは落とす）。**間に挟まっていた別の場所の立ち寄りは消さない**
  （その期間の内側に残る）。
  - まとめられるのは **同じ経路の・同じ場所への**訪問が 2 件以上のときだけ。混ざっていれば
    何もせず `null` を返す（どれが正か決められないため）。UI 側も選択が 1 place に揃うまで
    「まとめる」を押せない。
  - 全員が同じ place を指しているので、**place の回収は起きない**（残る 1 件が参照し続ける）。

やり直し系がすべて非破壊なのは意図的 → [ADR-0012](../adr/0012-non-destructive-reanalysis.md)。

### 期間の重なりは禁止しない

広い敷地では、1 回の外出で同じ場所に複数の立ち寄りが付く（入口で 5 分 → 300m 歩く → ゾウ舎で 20 分）。
**アプリは自動でまとめない。**まとめるかどうかはその日の解釈なので、ユーザーが上の 2 つで決める
（→ [ADR-0024](../adr/0024-stop-duration-edit-and-manual-merge.md)）。

その結果として**期間の重なり（完全に含む「入れ子」を含む）が生じるが、これを禁止しない**。
モールの中のカフェ、園内の施設という入れ子は現実に起きるので、禁止すると表せなくなる。

- スキーマは変えない（`stops` の更新・削除だけ）。取り消しは削除と**同じ 1 スロット**に載せる
  （`undoLastStopChange`）。
- 手で編集した立ち寄りは、`detectMissingStops` の「既存と時間帯が重なる候補は除外」によって
  再解析の候補から自然に外れる。**「編集済み」フラグは持たせない。**

---

## 削除と孤立回収

削除は単体・複数とも `deleteStops(stopIds)` に集約し、常に**訪問（stop）を消す**動作にする。
消した結果どこからも参照されなくなった place のうち、**検出由来（`DETECTED`）だけ**を回収する
（条件は「残る stop がゼロ かつ wishlist 登録もゼロ」）。由来の扱いは [places.md](places.md)。

取り消しのために、削除前に **stop・回収する place・解決ログの実体を 1 件分だけ控える**。
取り消し時は**元の id のまま再挿入**して復元する（`undoLastStopChange`）。

この控えは**削除と統合で共用する**（スナックバーは常に最新の 1 件しか出さないので単一スロットで足りる）。
統合は「消した行」に加えて「書き換えた行（残した 1 件の変更前）」を控え、取り消しでは再挿入と
上書きの両方を行う。

---

## 実装マップ

| 要素               | ファイル                                                                                                      |
| ------------------ | ------------------------------------------------------------------------------------------------------------- |
| 検出               | `domain/model/StopDetector.kt`（返り値は `DetectedStop`・しきい値の定数もここ）                               |
| ドメイン           | `domain/model/Stop.kt`, `DetectedStop.kt`                                                                     |
| Entity / DAO       | `data/local/entity/StopEntity.kt`, `StopWithPlace.kt` / `data/local/dao/StopDao.kt`                           |
| 記録中の検出・保存 | `service/LocationTrackingService.kt` → `PlaceRepository.updateStopsForTrack`                                  |
| ライブ立ち寄り中   | 記録中サービスの `StateFlow`（非永続）→ `data/tracking/TrackingController` → `presentation/tracking/`         |
| 再解析             | `detectMissingStops` / `addStops`                                                                             |
| 手動追加           | `addManualStop` / `domain/usecase/AddManualStopUseCase.kt` ＋ `presentation/stops/ManualStopSheet.kt`         |
| 施設の名前検索     | `presentation/common/PlaceNameSearchField.kt`（追加シート・選び直し・場所詳細の紐付けで共用）                 |
| 付け替え           | `reassignStopPlace` ＋ `presentation/stops/StopReassignDialog.kt`                                             |
| 期間の編集         | `updateStopDuration` ＋ `presentation/stops/StopDurationSheet.kt`（区間UIは `ManualStopRange.kt` と共用）     |
| 統合               | `mergeStops` ＋ `presentation/history/TrackDetailSheet.kt` の選択バー（「まとめる」）                         |
| 画面               | `presentation/history/`（`TrackDetailScreen` / `TrackDetailMap` / `TrackDetailSheet` / `TrackDetailDialogs`） |
