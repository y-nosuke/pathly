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

**「立ち寄り中」の表示だけは生の現在地（`GpsPointDao.getLatestPoint`）で離脱を判定する。**
補正後点は末尾が確定保留でラグがあり、さらに半径を抜けきるまでクラスタが末尾を吸収し続けるため、
補正後点で判定すると表示が消えない。**表示のみ**の判定で、保存する立ち寄りには影響しない。

```text
新しい点が保存・補正される
  └─ updateStopsForTrack(trackId, isFinal)
       - 境界 = メモリの高水位 ?: 保存済み立ち寄りの最終 departure ?: なし（＝全点）
       - StopDetector.detect(境界より後の補正点だけ) で DetectedStop 群を得る
       - 末尾の「滞在中」クラスタ（末尾点を含む）:
           しきい値超なら findOrCreatePlace → (online) resolve、"立ち寄り中" を StateFlow で公開
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
- **選び直し（この訪問だけ付け替え）**: `reassignStopPlace`。POI 候補を選べば施設の同一性で同定した place へ、
  名前だけ手入力なら元の座標で新しい USER 場所を作る。**他の経路・訪問は不変**
  （→ [ADR-0007](../adr/0007-reassign-stop-this-visit-only.md)）。

やり直し系がすべて非破壊なのは意図的 → [ADR-0012](../adr/0012-non-destructive-reanalysis.md)。

> **これから足す（→ [ADR-0024](../adr/0024-stop-duration-edit-and-manual-merge.md)・未実装）**
>
> - **滞在期間の編集** … `stops` の到着・出発を直接更新する。GPS 点・補正後の点は触らない。
> - **立ち寄りの統合** … 選んだ複数を 1 件にする（到着＝最も早い到着／出発＝最も遅い出発／メモは連結）。
>   間に挟まっていた別の場所の立ち寄りは消さない。
> - **期間の重なり（入れ子を含む）は禁止しない。** 広い敷地では同じ place への立ち寄りが 1 回の外出で
>   複数付くが、アプリは自動でまとめない。
> - スキーマ変更は不要（`stops` の更新・削除だけ）。取り消しは `undoLastDeletion` と同じ形で載る。
> - 手で編集した立ち寄りは、`detectMissingStops` の「既存と時間帯が重なる候補は除外」によって
>   再解析の候補から自然に外れる。「編集済み」フラグは持たせない。

---

## 削除と孤立回収

削除は単体・複数とも `deleteStops(stopIds)` に集約し、常に**訪問（stop）を消す**動作にする。
消した結果どこからも参照されなくなった place のうち、**検出由来（`DETECTED`）だけ**を回収する
（条件は「残る stop がゼロ かつ wishlist 登録もゼロ」）。由来の扱いは [places.md](places.md)。

取り消しのために、削除前に **stop・回収する place・解決ログの実体を 1 件分だけ控える**。
取り消し時は**元の id のまま再挿入**して復元する（`undoLastDeletion`）。

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
| 付け替え           | `reassignStopPlace` ＋ `presentation/stops/StopReassignDialog.kt`                                             |
| 画面               | `presentation/history/`（`TrackDetailScreen` / `TrackDetailMap` / `TrackDetailSheet` / `TrackDetailDialogs`） |
