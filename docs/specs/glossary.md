# 用語集（Glossary）

位置情報の処理で使う用語を、**日本語（議論用）**と**英語（コードの識別子）**で対応させる。
変数名・関数名・テーブル名をブレさせないための共通辞書。

関連設計: [gps-smoothing.md](../designs/gps-smoothing.md)（補正）／[places-and-stops.md](../designs/places-and-stops.md)（場所・立ち寄り）

---

## データ（名詞）

位置に関するデータは、段階を追って「点の集まり」から「意味のある場所」へ変換される。

| 日本語           | 英語（コード）      | 意味                                                           | 実体                                                             |
| ---------------- | ------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------- |
| 軌跡（経路）     | track               | 1回の記録（GPSの点列＋期間）                                   | `GpsTrack` / `gps_tracks`                                        |
| 生データ（生点） | raw point           | 端末が記録したそのままの座標。ノイズを含む                     | `GpsPoint` / `gps_points`                                        |
| 補正後の点列     | smoothed point(s)   | 生データを補正した軌跡の点。「**どこを通ったか**」             | `SmoothedPoint` / `smoothed_points`（`GpsTrack.smoothedPoints`） |
| 検出された滞在   | detected stop       | 補正後点列で「50m圏内に3分以上」の箇所。**一時結果・非永続**   | `DetectedStop`                                                   |
| 場所             | place               | 場所そのもの（座標＋**自分で付けた名前**・メモ）。経路から独立 | `Place` / `places`                                               |
| 施設（Google）   | google place        | Google 由来の情報（施設名・住所・カテゴリ）。place に 1:0..1   | `google_places`（`Place.googleName` など）                       |
| 立ち寄り（訪問） | stop                | どの経路でどの場所にいつ居たか（place × track）                | `Stop` / `stops`                                                 |
| 解決記録         | resolution (record) | 場所名を Google に問い合わせた記録（叩いたか・結果）           | `PlaceResolution` / `place_resolutions`                          |

データの流れ:

```
生データ（gps_points・どんな座標を拾ったか）
   ↓ 補正（smoothing）
補正後の点列（smoothed_points・どこを通ったか）
   ↓ 立ち寄り検出（detection）
検出された滞在（DetectedStop・どこに滞在したか／一時結果）
   ↓ 確定（commit）＋場所の同定（find-or-create）
場所・立ち寄り（Place / Stop・永続化）
   ↓ 名前解決（resolve）
施設名（google_places・叩いた記録は place_resolutions）
```

### 名前の2列（混ぜないこと）

| 呼び名               | 実体                 | 誰が入れるか                                    |
| -------------------- | -------------------- | ----------------------------------------------- |
| **自分で付けた名前** | `places.name`        | ユーザーだけ。Google の名前は**絶対に入れない** |
| **Google の名前**    | `google_places.name` | 自動命名・POI タップ・キーワード検索            |

表示名は `places.name` → `google_places.name` → 住所 → 座標 の順にフォールバックする
（[../designs/places-and-stops.md](../designs/places-and-stops.md)）。

### 座標の2つの出どころ（混ぜないこと）

| 呼び名             | 実体                                                 | 使う経路                                     |
| ------------------ | ---------------------------------------------------- | -------------------------------------------- |
| **施設の座標**     | `PlaceSearchResult.latitude/longitude`（Places API） | 自動命名・Google施設を選び直す・検索して追加 |
| **アイコンの座標** | `PointOfInterest.latLng`（地図 SDK）                 | 地図の POI タップからの登録                  |

同じ施設でも**値が違うことがある**（敷地のある神社・公園などで数十 m）。正としているのは施設の座標
→ [ADR-0011](../adr/0011-place-coordinate-source.md)。この 2 つを「POI の座標」などと呼び分けずに
議論すると必ず混乱するので、**この 2 語だけを使う**。

---

## 処理（動詞）

| 日本語                       | 英語（コード）              | 意味                                                                                                       | 実体                                             |
| ---------------------------- | --------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| **解析**                     | analyze / analysis          | GPSデータから構造を導く**ローカル計算全体**（補正＋立ち寄り検出）。外部通信なし                            | 記録中に逐次で実施                               |
| 　└ **補正**（スムージング） | smooth / smoothing          | 生データ → 補正後の点列                                                                                    | `TrackSmoother.smooth`                           |
| 　　└ ジャンプ除外           | remove jumps                | 速度が非現実的な外れ値を除外                                                                               | `removeJumps`                                    |
| 　　└ 平滑化                 | accuracy-weighted smoothing | 精度重み付き移動平均でならす                                                                               | `accuracyWeightedSmooth`                         |
| 　└ **立ち寄り検出**         | detect / detection          | 補正後点列 → DetectedStop                                                                                  | `StopDetector.detect`                            |
| **確定**                     | finalize / commit           | 暫定でなくすこと（下記補足）。立ち寄りは DetectedStop を Place+Stop として保存                             | `updateSmoothedForTrack` / `updateStopsForTrack` |
| 　└ 場所の同定（重複排除）   | find-or-create              | 30m以内の既存 place を再利用、無ければ新規作成                                                             | `findOrCreatePlace`                              |
| **名前解決**                 | resolve / resolution        | Google から場所名を**取得**（外部・オンライン・課金）。**自動**                                            | `PlacesNameResolver.resolve`                     |
| **命名**（手動）             | rename / edit name          | ユーザーが名前を付け替える                                                                                 | `updatePlaceName`                                |
| **メモ**（訪問メモ）         | note / stop note            | ユーザーがその**訪問**にメモを付ける（stop 単位。空で null）                                               | `updateStopNote`                                 |
| **再解析**（追加提案）       | reanalyze                   | 検出し直して「一覧に無い候補」を挙げ、選択分だけ**追加**する（非破壊・opt-in）                             | `detectMissingStops` / `addStops`                |
| **手動追加**（完全手動）     | manual add                  | 検出に頼らず地図で指した地点を立ち寄りとして**追加**する（非破壊）。UI のボタンは「立ち寄りを追加」        | `addManualStop` / `AddManualStopUseCase`         |
| **紐付け**（Google 施設）    | link                        | 既存の場所を、選んだ Google 施設に結びつける（施設情報・座標を採用）                                       | `linkPlaceToGoogle`                              |
| **付け替え**（この訪問だけ） | reassign                    | 誤検知の立ち寄りを、この訪問だけ別の場所へ移す（[ADR-0007](../adr/0007-reassign-stop-this-visit-only.md)） | `reassignStopPlace`                              |

---

## 補足（紛らわしい語の使い分け）

- **命名 vs 名前解決**  
  自動（Google Places）は「**名前解決 resolve**」、手動（ユーザー）は「**命名 rename**」。
  名前解決は既にある施設名を*引いてくる*、命名はユーザーが*付ける*。`place_resolutions`・`resolve` と一貫。

- **メモ（note）vs 命名（name）**  
  **メモは stop（訪問）単位**、**名前は place（場所）単位**。同じ場所でもお出掛けごとに別メモを持てるが、
  名前は place を共有する全訪問で同じ。メモ編集は履歴詳細の「メモ」ボタン、場所詳細の訪問一覧では表示のみ。

- **確定（finalize / commit）**  
  「暫定 → 確定」を指す共通語。
  - 補正: 末尾 `half` 点が確定する（未来の点が来るまで暫定）。
  - 場所（place）: 滞在が3分を超えたら先に確定（ライブ表示・名前解決に使う）。
  - 立ち寄り（stop）: 滞在中の最後のクラスタが離れて確定し、保存される（案A）。

- **解析と名前解決の関係**  
  解析は**ローカル計算のみ**（補正＋検出）。名前解決は**外部通信**なので解析に含めない。
  解析は記録中に走り、名前解決だけを後から「場所を取得」で再実行できる。

- **再解析 vs 手動追加**  
  どちらも非破壊で立ち寄りを**足す**が、**再解析**は検出器が見つけた候補から選ぶ（＝検出できる立ち寄りの救済）。
  **手動追加**は検出に頼らず地図で指した地点を足す（＝しきい値に満たない等、検出に出ない立ち寄りの救済）。
  どちらも既存の stop を消さない（[ADR-0012](../adr/0012-non-destructive-reanalysis.md)）。

---

## フェーズ用語

段階的開発（[roadmap.md](../roadmap.md)）の各フェーズを指す言葉。

| 日本語       | 英語（コード） | 意味                                                                                                                |
| ------------ | -------------- | ------------------------------------------------------------------------------------------------------------------- |
| 記録         | record         | お出掛け中に GPS 軌跡をリアルタイムで残すこと（Phase 1）                                                            |
| **振り返る** | review         | **過去のお出掛けの経路（track）を見ること**。履歴→詳細で軌跡・立ち寄りを確認する行為（Phase 2「事後の振り返り」）。 |
| 計画         | plan           | これからのお出掛けを事前に準備すること（行きたい場所・ルート等・Phase 3）                                           |
