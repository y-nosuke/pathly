# 場所（places）と立ち寄り（stops）の設計

立ち寄り場所を**永続化**し、**名前**を持たせ、**手動で編集**できるようにする設計。
将来の「行きたい場所リスト」も同じ「場所」を再利用できる構造にする。

- 関連要望（[requirements.md](../requirements.md)）
  - 立ち寄った場所を自動で検出してほしい（どこに何分いたか）
  - 場所名を自分たちに分かりやすい名前に手動で入力・変更したい
  - 行きたい場所をリストアップしたい／場所の詳細情報を登録したい（将来）
- 前提: 立ち寄りの**検出ロジック**は [gps-smoothing.md](./gps-smoothing.md) の補正後の点列に対して行う（`StopDetector`）。本書は検出結果を**どう保存・命名・編集するか**を扱う。
- データモデル（`places` / `stops` / `place_resolutions`）は [../specs/model.md](../specs/model.md) を参照。

---

## 用語・データの流れ

> 用語の日英対応（データ・処理）は [glossary.md](../specs/glossary.md) にまとめている。本節は流れの説明。

位置に関するデータは、段階を追って「点の集まり」から「意味のある場所」へと変換される。

1. **GPSの生データ（`gps_points`）** … 端末が記録したそのままの座標。ノイズを含む。
2. **補正後の位置情報（`smoothedPoints`）** … 生データを補正した**軌跡の点の集まり**（たくさんの点）。「**どこを通ったか**」。原データは変更せず読み込み時に計算する（[gps-smoothing.md](./gps-smoothing.md)）。
3. **`DetectedStop`** … 補正後の点列の中で「50m圏内に3分以上とどまった」箇所を1つにまとめたもの。「**どこに滞在したか**」。座標は滞在中の点の重心。検出の一時結果で永続化しない。
4. **`Place` / `Stop`** … `DetectedStop` を保存したもの。`Place`＝場所そのもの（座標・自分で付けた名前・メモ）、`Stop`＝その場所への訪問（place × track × 時刻）。本書が扱う範囲。

```text
gps_points（生データ・どんな座標を拾ったか）
   ↓ TrackSmoother.smooth（補正）
smoothedPoints（補正後の軌跡・どこを通ったか）
   ↓ StopDetector.detect（滞在の検出）
DetectedStop（どこに滞在したか・一時結果）
   ↓ findOrCreatePlace + 保存
Place / Stop（場所と訪問・永続化）
```

---

## 方針

### 場所と立ち寄りを分ける

- **places（場所）** … 場所そのもの。座標と、ユーザーが自分で付けた名前・メモを持つ。経路とは独立。
  立ち寄りで検出した場所・手動で追加した場所・行きたい場所、すべてここに入る。
- **stops（立ち寄り）** … 「どの経路で・どの場所に・いつからいつまで」いたかを表す**訪問**。
  places と gps_tracks を結ぶ。

```text
gps_tracks 1 ──< stops >── 1 places
                (訪問)        (場所そのもの)
                              └─ wishlist（行きたい）も places を参照
```

分けた理由・没案は [ADR-0013](../adr/0013-separate-place-stop-wishlist-tables.md)。

### 命名の由来（自動）

検出した座標の**最寄りの施設名**を Google **Places API (New) の Nearby Search** で取得する。
`local.properties` の `GOOGLE_MAPS_API_KEY`（地図と共用）を使う。

- 取得できたら **`google_places`**（施設名・住所・カテゴリ）に保存し、`place_resolutions` に解決記録（`resolvedAt`）を残す。
  **`places.name`＝ユーザーが自分で付けた名前には書かない**（[ADR-0001](../adr/0001-place-data-separation.md)）。
- **place 1件につき自動で叩くのは1回だけ**。「叩いたか」は `place_resolutions` の**行の有無**で判定する。
  POI が見つからなくても行を残す＝自動では二度と叩かない。**オフライン／通信エラーのときだけ行を作らず**、
  次の機会に持ち越す。課金の考え方と没案は [ADR-0014](../adr/0014-place-naming-cost-policy.md)。
- **キャッチアップ（WorkManager）**: `place_resolutions` を持たない場所を `data/work/PlaceNameCatchUpWorker` が
  まとめて解決する。起動時に `PathlyApplication` から予約するだけで、**実行の条件はネットワーク接続**
  （`NetworkType.CONNECTED`）。いま圏外でも通信が戻った時点で OS が走らせる。
- **座標の採用**: 解決できたら place の座標を**施設の座標（Places の LOCATION）へ置き換える**
  （暫定の GPS 座標より正確）。出どころの違いは [ADR-0011](../adr/0011-place-coordinate-source.md)。
- **手動再取得**: `googlePlaceId` が無い place（未取得・過去失敗・POI無し）を、詳細画面のボタンで
  ユーザー操作でだけ取り直せる。二度と命名できない状態にはしない導線。
- **手動で Google 施設に紐付け**: 場所の詳細の「Googleで情報を取得」／「Google施設を選び直す」で、
  近くの候補（またはキーワード検索）から選んだ施設を**この場所に**紐付ける（`linkPlaceToGoogle`）。
  `google_places`・`place_resolutions` を上書きし座標も採用する（`places.name` は変えない）。

### 名前の2列と表示名

名前を入れる列は 2 つあり、**出どころで使い分ける**（[ADR-0001](../adr/0001-place-data-separation.md)）。

| 列                   | 誰が入れるか                                          |
| -------------------- | ----------------------------------------------------- |
| `places.name`        | **ユーザーだけ**。Google が付けた名前は絶対に入れない |
| `google_places.name` | 自動命名・POI タップ・キーワード検索                  |

場所名を出す**すべての読み取り**を、次の優先で解決する:

```text
表示名 = places.name（自分の名前）
       ?: google_places.name（Google の名前）
       ?: google_places.address（住所）
       ?: 座標
```

- 一覧・詳細（`PlaceDao` の射影）に加え、**履歴の立ち寄り表示・関連経路一覧**（`StopDao` の射影）も
  `google_places` を LEFT JOIN して同じフォールバックを適用する。カテゴリ・住所も同じ JOIN で取る。
- フォールバックの計算は 1 か所（表示名ヘルパ）に集約し、各射影から使う。

### 名前欄の入力ルール

場所シートの名前欄は **`places.name`（自分で付けた名前）専用**。Google 由来の名前は初期値に入れない。
欄が埋まっているかどうかが、そのまま「自分で名前を付けたか、Google の名前で表示されているか」を表す。

- Google の名前は**シートの見出し**に出す（プレースホルダには入れない。名前に関する場所が縦に 2 つ
  並んで場所を食い、どちらが表示名なのか分かりにくくなるため）。
- 施設名を少し変えたいときのために、欄が空のときだけ名前欄の下に「この名前から書き換える」を出して
  見出しの名前を流し込む（打ち直しを強いない）。書き換えずに確定したら Google の名前と同じなので
  ユーザー名としては保存しない。
- **名前を変えても `googlePlaceId` は外さない**。列が分かれているので「スタバだけど自分は休憩と呼ぶ」が
  そのまま表現でき、カテゴリ・住所も残る。施設ごと取り違えたときは「Google施設を選び直す」で付け替える。
- 名前を空にして保存すると `places.name` は null に戻り、表示は Google 名へフォールバックする。

### Google マップで開く

詳細・シートから Intent で Google アプリ／Web に切り替え、写真・口コミ・営業時間・経路案内を委ねる
（API 呼び出しではない＝課金ゼロ）。`googlePlaceId` があればその施設のページ、無ければ `geo:` で座標を開く。
`googlePlaceId` が無い空タップの地点では出さない。

### 既知の限界

- **既存の解決済み場所は、自動ではカテゴリが埋まらない**（自動命名は place 1 件 1 回なので対象外）。
  手で埋めるなら「Google施設を選び直す」で同じ施設を選び直す。ただしそのとき**座標も施設の座標へ
  置き換わる**ので、地図上のピンが動いて見えることがある（[ADR-0011](../adr/0011-place-coordinate-source.md)）。
- `google_places.googlePlaceId` は NOT NULL（行がある＝マッチした）。「叩いたが該当なし」は
  `place_resolutions` に行だけ残る。
- 削除・取り消し（Undo）のスナップショットは `google_places` の行も含める。

---

## データモデル

**列の定義は [../specs/model.md](../specs/model.md)（概念モデル・ER 図）と Room エンティティを正とする。**
ここには設計上の意図だけ書く。

- **`stops` の外部キーは非対称**。`trackId → gps_tracks` は CASCADE（経路を消せば訪問も消える）だが、
  `placeId → places` は CASCADE にしない。**訪問が消えても場所は残す**（再利用・行きたい場所のため）。
- **`place_resolutions` は「行の有無」が意味を持つ**ログ。主役は `resolvedAt`（叩いた事実）で、
  結果そのものは `google_places` に入る（[ADR-0001](../adr/0001-place-data-separation.md)）。
- **メモ（`stops.note`）は訪問単位、名前（`places.name`）は場所単位**。同じ場所でもお出掛けごとに
  別のメモを持てるが、名前は place を共有する全訪問で同じ。空文字は保存せず null にそろえる。
- 命名対象を経路で絞るときは `stops` を JOIN して `trackId` で絞る（グローバルに全 place を叩かない）。

### 場所の重複排除（find-or-create）

同じ店の2回目の訪問を別々の場所にしないため、立ち寄りを保存するときは
**既存の場所が近く（既定 30m 以内）にあれば再利用**し、無ければ新規作成する。

### 場所の由来（source）と自動回収（v10）

`places.source`（`DETECTED` / `USER`）で場所の由来を持つ（決定の背景は [../adr/0005-place-source-and-lifecycle.md](../adr/0005-place-source-and-lifecycle.md)）。

- `DETECTED` … 記録中の自動検出が作った場所。参照（stop / wishlist）が無くなれば**自動回収**してよい（誤検知の後始末）。
- `USER` … ユーザーが明示的に作った場所（場所登録・手動立ち寄り追加・キーワード検索）。訪問も行きたいも無くても**自動では消さない**。

立ち寄り削除時の回収（`deleteStops`）は、参照ゼロ**かつ `DETECTED`** のときだけ place を消す。`USER` は保持する。
`findOrCreatePlace(lat, lon, source)` は新規作成時に由来を刻む。既存を再利用するとき、由来が `DETECTED` でも呼び出しが
`USER` なら `USER` に昇格する（ユーザーが触った場所を自動回収から守る。降格はしない）。

### 施設の同一性（googlePlaceId）での同定

ユーザー操作で **`googlePlaceId` が分かるとき（POI タップ・キーワード検索・手動追加の候補選択）は、施設の
同一性で同定**する（`findOrCreateByGooglePlaceId`）。同じ `googlePlaceId` の place を再利用し、無ければ
**新規**（座標同定はしない＝30m以内の隣接する別施設に相乗りしない）。place の座標は POI の Google 座標で保存。
POI タップで既に登録済みだったら「この場所は登録済みです」と軽く通知する。`googlePlaceId` が無いとき
（空タップ・手入力・名前なし）と自動検出は、従来どおり座標同定（30m）にフォールバックする。

### 誤検知の選び直し（この訪問だけ付け替え）

確定済みの立ち寄りが誤った場所（隣接店など）になっていたら、**この訪問だけ**を正しい場所へ付け替えられる
（`reassignStopPlace`）。記録画面は立ち寄りマーカーのタップ、履歴詳細は一覧の「選び直す」から `StopReassignDialog`
（近くの POI 候補から選ぶ／自分で入力）を開く。

- POI 候補を選ぶ → 施設の同一性で同定した place へ付け替え（隣接店を分離）。名前だけ手入力 → 元の座標で新しい
  USER 場所を作る（座標同定せず分離）。**他の経路・訪問は不変**。
- 付け替えで参照が無くなった元の場所が**検出由来（DETECTED）なら自動回収**する（[0005](../adr/0005-place-source-and-lifecycle.md)）。

---

## ドメインモデル

- `DetectedStop` … `StopDetector` の**検出結果（幾何のみ）**。緯度経度・到着/出発・点数。永続化しない。
- `Place` … 永続化された場所。座標＋自分で付けた名前・メモに加え、`google_places` から JOIN した
  施設名・住所・カテゴリを載せる（表示名のフォールバックは下記「表示名」）。
- `Stop` … 永続化された立ち寄り（id・`place: Place`・trackId・到着/出発・訪問メモ）。滞在時間は計算。

---

## 保存・命名のトリガー

**立ち寄りの検出は「記録中（自動）」が基本**で、取りこぼし・誤削除の救済として詳細画面の
**「再解析」（追加提案・非破壊）**を用意する。命名は記録中（オンライン時・自動）に加え、
未取得分を「場所を取得」で手動再実行できる。**詳細画面を開いても自動検出はしない**
（「stops が0件」では“本当に立ち寄りが無い”のか“取りこぼした”のか区別できないため）。

やり直し系の操作が**すべて非破壊**なのは意図的な設計 → [ADR-0012](../adr/0012-non-destructive-reanalysis.md)。

### 1. 記録中（自動・ライブ）

`LocationTrackingService` が新しい点を保存・補正する流れに続けて、立ち寄りも増分で処理する。
補正（[gps-smoothing.md](./gps-smoothing.md)）と同じ「確定プレフィックス」の考え方を使う。

**インクリメンタル検出（境界以降だけ見る）。** 毎ティック全点を検出し直すのではなく、
**「最後に確定した立ち寄りの `departureTime`（＝境界）」より後の補正点だけ**を `StopDetector` にかける
（`SmoothedPointDao.getByTrackAfter`）。`StopDetector` の貪欲スキャンは境界より手前の点に依存しない
（クラスタを確定したら次へ飛ぶだけ）ので、末尾のクラスタ分割は全点で検出した場合と**一致する**。
これで過去の立ち寄り区間を毎ティック舐め直さずに済み、検出コストは「直近の確定立ち寄り以降の点数」に比例する。

**境界は削除で下げない（誤復活の防止）。** 境界はトラック単位で**メモリに保持**し、記録セッション中は
**単調増加**させる（削除では下げない）。未設定のとき（プロセス再起動後の再開など）だけ、保存済み立ち寄りの
**最終 `departureTime`** で種をまき直す。こうすると、ユーザーが記録中に削除した立ち寄りの区間は境界より手前に
なって検出スライスに入らず、**GPS が同じでも再検出・再挿入されない**（＝削除が復活しない。理由は後述の [ADR-0002](../adr/0002-incremental-live-stop-detection.md)）。
既知の端: 記録中にプロセスが kill され、かつ**末尾の立ち寄りを削除した直後にクラッシュ**した場合のみ、
DB の最終 departure で種をまき直すため、その1件が再検出されうる（中間削除は影響なし）。

滞在は次のライフサイクルで扱う（**stop は離脱で確定＝案A**、**place は滞在中に先に確定**してライブ表示・命名に使う）:

1. **滞在候補**（3分未満）: 何もしない（滞在時間を数えるだけ）。
2. **立ち寄り中**（3分超・まだ離れていない）:
   - その時点の重心で `findOrCreatePlace`（30m重複排除）→ **place を確定**。
   - オンラインなら**名前解決**（`place_resolutions` に記録）。既存の解決済み place を再利用したときは叩かない。
   - UIに「● 立ち寄り中（滞在◯分）＋ place名（無ければ座標）」を**メモリ経由**（`StateFlow`）で流す。**stop はまだ保存しない**。
   - ただしこの**表示は、生の現在地（`GpsPointDao.getLatestPoint`）がその立ち寄りの中心の半径内にある間だけ**出す。
     補正後点は末尾が確定保留でラグがあり、また 50m を抜けきるまでクラスタが末尾を吸収し続けるため、
     生の現在地で「離脱」を先に判定して表示を即クリアする（**表示のみ**の判定で、保存する立ち寄りには影響しない）。
3. **確定**（離れた or 記録終了）:
   - **stop を保存**（place は既にあるので stop を作るだけ・名前解決は再実行しない）。
   - ライブ表示から消え、保存済み立ち寄り一覧に移る。

```text
新しい点が保存・補正される
  └─ updateStopsForTrack(trackId, isFinal)
       - 境界 = メモリの高水位 ?: 保存済み立ち寄りの最終 departure ?: なし（＝全点）
       - StopDetector.detect(境界より後の補正点だけ) で DetectedStop 群を得る
       - 末尾の「滞在中」クラスタ（末尾点を含む）:
           3分超なら findOrCreatePlace → (online) resolve、"立ち寄り中" を StateFlow で公開
           離れて確定 or isFinal なら stops に保存し、公開を解除
       - それ以外の確定クラスタ（すべて境界より後＝新規）:
           findOrCreatePlace → stops に保存、境界を確定分の最終 departure まで進める
           オンラインなら未解決 place を resolve（オフラインは行を作らず後でキャッチアップ）
```

- **place は滞在中（3分超）に先に保存**される。stop は離脱で確定（案A）。3分超の滞在は必ず後で stop になるため、宙に浮いた place は基本残らない（記録クラッシュ時のみ・無害で再利用可）。
- **境界（高水位）は削除で下げない**ので、記録中に手で消した立ち寄りは以降のスライスに入らず復活しない（[ADR-0002](../adr/0002-incremental-live-stop-detection.md)）。境界はメモリ保持で、記録終了（`isFinal`）時に破棄する。
- API回数は **place 1件1回**のまま。クラスタは50m以内なので重心のブレは小さく、place 確定後は `place_resolutions` があるので自動では叩き直さない。
- 記録終了時に `isFinal=true` で末尾を確定。オンラインなら未解決の place を一括命名。

### 2. 手動ボタン（詳細画面）

- **再解析（追加提案・非破壊）**: その track を検出し直し（保存済みの補正点列に `StopDetector`）、
  **既存の立ち寄りと時間帯が重ならない候補だけ**を「一覧に無い立ち寄り」として提示する。
  各候補には**判断用の場所名**を添える（近く30mに命名済み place があれば無料で再利用、無ければオンライン時のみ
  Places で1回。オフライン等は名前なし）。候補は**地図にオレンジのピン**で出し（既存は紫）、
  **地図の下の固定オーバーレイ**（地図は常に見えたまま・リストはその中で独立スクロール）で選ぶ。
  行タップで地図をその候補へ寄せ、**名前＋位置で確かめてから**チェックできる。
  ユーザーが**チェックした分だけ**を追加保存する（`findOrCreatePlace` で place を確保＝命名済みは再利用、
  検出時に引いた名前があればそれを焼き込んで **Places を二度叩かない**、付いていなければオンライン時に命名）。
  **既存の立ち寄りには一切触れない**。
  - 用途: 記録中に取りこぼした立ち寄りの追加、**誤って削除した立ち寄りの復旧**、しきい値変更後の追加反映。
  - **記録中も表示する**（誤って消した立ち寄りをその場で戻せるように）。ただし記録中は候補を
    **境界（＝最後に確定した立ち寄りの `departureTime`）以前＝「確定済みの過去」だけ**に絞る。
    滞在中・末尾はライブ検出が受け持つため候補から外し、**ライブ検出との二重登録を防ぐ**
    （`detectMissingStops` は `detectionHighWaterMillis` に境界エントリがあれば `departure <= 境界` で絞る。
    終了済みの経路は境界エントリが無いので全点が対象）。境界は削除で下がらないので、消した過去は候補に残る。
  - 実装: `detectMissingStops(trackId)` で候補（`StopCandidate`＝検出＋表示名）を列挙（永続化しない）
    → `addStops(trackId, 選択分)` で保存。
- **立ち寄りを追加（完全手動・非破壊）**: 検出に頼らず、地図の**通常タップ**で指した地点を立ち寄りとして足す。
  最寄り軌跡点から到着/出発を仮置きし、**レンジスライダー**で滞在区間を調整（区間は地図に青くハイライト）。
  検出しきい値に満たない短い滞在など、**再解析でも救えない立ち寄り**を救済する。詳細は「将来の拡張 → 立ち寄りの完全手動追加」。
  **記録中でも使える**（末尾が伸び続けるので、到着/出発の推定はその時点の軌跡で行う）。
  - 実装: `addManualStop(trackId, lat, lng, arrival, departure, name, googlePlaceId, googleName)`。
    近接確認の要否判断まで含めた手順は `domain/usecase/AddManualStopUseCase`（記録画面と共有）。
- **場所を取得**: 検出はやり直さず、その track の**未取得（`googlePlaceId` が無い）place だけ**を Places で取得する。オフラインで命名できなかった分の手動再実行。**非破壊**（既存の立ち寄りには触れない）。

> 追加専用なので、**しきい値を厳しくしても既存の stop は消えない**（手で削除する）。また
> **却下した候補は記憶しない**ので毎回再掲される（チェックしなければ無害）。
> この割り切りの理由は [ADR-0012](../adr/0012-non-destructive-reanalysis.md)。

### 命名の管理（place_resolutions・place 1件1回）

「一度叩いたら二度と自動で叩かない」を `place_resolutions`（解決ログ）で管理する。

- **自動命名の対象**: 対象 track の place のうち、`place_resolutions` に**行が無いもの**（＝まだ一度も叩いていない）。
- **結果を記録**: 応答が返ったら（見つかっても見つからなくても）`place_resolutions` に行を作る（`resolvedAt` に日時）。見つかれば `google_places` に施設名・住所・カテゴリと `googlePlaceId` を書く（`places.name` は触らない）。
- **オフライン／通信エラー**: 行を作らない → 次に命名が走ったとき再挑戦（キャッチアップ）。
- **手動「場所を取得」**: `googlePlaceId` が無い place（行が無い or null）を対象に、ユーザー操作でだけ再挑戦。

| 状況            | 記録        | 自動再取得   | 手動再取得 |
| --------------- | ----------- | ------------ | ---------- |
| POI発見         | 行・ID あり | しない       | しない     |
| POI無し         | 行・ID null | しない       | できる     |
| オフライン/失敗 | 行なし      | する(復帰時) | できる     |

判定に `name IS NULL` を使わない理由と課金の考え方は [ADR-0014](../adr/0014-place-naming-cost-policy.md)。

---

## Places 命名（Places SDK for Android）

Web API 直叩きではなく **Places SDK for Android（New）** を使う。
Android アプリ制限付きの API キー（地図と共用）を**そのまま安全に**使えるため。

- 初期化は **New API 面を有効にして行う**（`initializeWithNewPlacesApiEnabled`）。旧 `Places.initialize(...)` では
  `searchNearby` が使えない。Cloud 側は「Places API (New)」の有効化＋請求先リンクが前提。
- 呼び出しは **Nearby Search を半径 50m・最も近い 1 件**（立ち寄りの検出半径と揃える）。
  リクエストの組み立てとフィールド指定は `data/places/PlacesNameResolver` を正とする。
- **ラッパー 1 か所（`PlacesNameResolver`）に閉じ込める。** 呼び出し前に `ConnectivityManager` で
  オンライン判定し、オフラインなら叩かない。**0 件と例外を区別する**のが要点で、
  0 件＝「叩いたが POI 無し」（`resolvedAt` の行を残す）、例外＝「未実施」（行を作らない）。
- 最寄り 1 件は隣の別施設に化けやすいので、**ユーザーが選ぶ場面では候補を複数返す**
  （`searchNearbyCandidates`）。自動命名だけが 1 件で妥協している。

---

## UI

> 地図タップの意味（画面 × モード × 登録済み表示 ON/OFF × タップ対象）の一覧は [map-tap-behavior.md](./map-tap-behavior.md)、
> その決定背景は [ADR-0009](../adr/0009-map-tap-behavior.md)。

### 地図描画の共通化（記録画面・詳細画面）

記録画面（`TrackingScreen`）と詳細画面（`TrackDetailScreen`）は**別画面**（記録＝ライブ・片手／詳細＝事後の見直し・編集）
だが、地図の「経路の描き方」は共通コンポーネント `presentation/common/RouteMapContent` に一元化して見た目を揃える。
各画面は自前の `GoogleMap`（カメラ挙動が違う: 記録＝現在地追従／詳細＝経路にフィット）の中で
`RouteMapContent` を呼ぶ。描画: 確定立ち寄りの帯 → 立ち寄り中の帯 → 軌跡 → 開始/終了マーカー →
立ち寄りマーカー（番号）→ 立ち寄り中マーカー（滞在時間ラベル付き）。記録画面は端末の現在地ドットが
あるので終了/現在地マーカーは出さない（`showEndMarker=false`）。

### 記録画面（ライブ）

- 確定済みの立ち寄りを購読（`getStopsForTrack`）して**地図にマーカー表示**（詳細画面と同じ番号バッジ）。
- 「立ち寄り中」は**地図上のティールのマーカー＋滞在時間ラベル**で示す（緯度経度はマーカーのタップで）。
  記録中サービスが公開する `StateFlow`（メモリ・非永続）を購読し、離れて確定したら消える（表示は生の現在地で即クリア）。
- 停止ボタンは誤爆防止のため**確認ダイアログ**を挟む。
- **手動で立ち寄り追加**（検出に頼らず足す）: **「今ここ」ボタン**（現在地）と**地図の空きタップ**（任意地点）の2通り。
  到着/出発は指した地点の**近傍の軌跡点から自動導出**（詳細画面のような区間スライダーは載せない・簡素）。
  名前は確認ダイアログで**近くのPOI候補から選ぶ／自分で入力／名前なし**から決める。最寄り1件の自動命名は
  隣の別施設に化けやすい（`searchNearby` maxResultCount=1）ため、候補を**複数**出してユーザーに選ばせる
  （`PlacesNameResolver.searchNearbyCandidates` / `PlaceRepository.nearbyPois`）。
- **手動追加した立ち寄りは自動命名しない**: `addManualStop` は常に `place_resolutions` 行を残すので、記録中の
  ライブ検出（未解決 place を自動命名）が手動追加の名前（またはユーザーが選んだ「名前なし」）を上書きしない。
- **地図タップの役割**（記録画面）: **施設(POI)タップ**＝場所登録（記録有無に依らず）。
  **何もない地点タップ**＝「立ち寄りを追加」モードが ON なら立ち寄り追加、OFF ならその地点を場所として登録
  （立ち寄りは作らない・POI無しなので `googlePlaceId` なし）。UI はどちらも共通の場所シート
  （`PlaceActionSheet`）／立ち寄りシート（`ManualStopSheet`）で、対象に応じて中身を出し分ける
  （一覧は [map-tap-behavior.md](./map-tap-behavior.md)）。空きスポットの純粋な場所登録は
  場所タブの「地図で選ぶ」にもある（そちらの地図には**現在地ボタン**を用意）。

### 登録済みの場所の地図表示（トグル）

記録・履歴詳細・場所詳細の地図に、**登録済みの場所（全 place ＝ USER も DETECTED も）**を
**アンバーのピン**で重ねて表示できる。立ち寄り（紫の番号バッジ）とは色で区別し、重なりでは立ち寄りを前面にする。
描画は共通の `RegisteredPlaceMarkers`（`presentation/common/RouteMap.kt`）に切り出し、**トラックが無くても**
（記録開始前など）単独で描けるようにしている。手動追加で近くに既存の場所があると気づけるようにするのが狙い。
**状態が分かるよう色・グリフで描き分ける**（色: 訪問済み=グリーン／未訪問=グレー、グリフ: 行きたい=旗／それ以外=ピン。
判定は「場所」タブの `PlaceListItem` と揃える）。タップでスニペットに状態文言（例「行きたい・訪問済み」）も出す。

**マーカータップで既存 place に紐付け（手動追加時）**: 手動で立ち寄りを足すとき（記録中／履歴詳細の手動追加モード）、
登録済みマーカーをタップすると、その**既存 place にこの訪問を紐付ける**（`addManualStopForPlace`）。新規 place を作らず
重複を防ぐ。UI は共通の立ち寄りシート（`presentation/stops/ManualStopSheet`）を流用する
（名前欄の代わりに場所名を出し、確定を紐付けに差し替える）。
**滞在時間の調整**（スライダー＋到着/出発の ＋/− 微調整）は共通の `StopRangeEditor`
（`presentation/stops/ManualStopRange.kt`）に切り出し、記録中・履歴詳細の**両方の手動追加で使える**
（軌跡点が2点未満のときだけ推定にフォールバック）。

**近接確認（トグルOFF時のフォールバック）**: マーカーを出していない（トグルOFF）ときは②のマーカータップができないので、
**ID 無しの手動追加**（POI 未選択＝`googlePlaceId` 無し。空タップ／手入力／名前なし）を確定した時点で、
**検出半径（`StopDetector.RADIUS_METERS`＝50m）以内**の既存 place を探し（`findNearbyPlace`）、あれば
`NearbyPlaceConfirmDialog` で「**この場所に紐付け**／**新規で追加**」を確認する。「紐付け」は `addManualStopForPlace`、
「新規」は `addManualStop(forceNewPlace=true)` で**座標30m同定をバイパスして必ず新規 place を作る**（気づかない誤マージを防ぐ）。
近くに無ければ従来どおり追加する。トグルON時はマーカー＋②に任せ、この確認は出さない。

- **取得**: `PlaceRepository.observeRegisteredPlaces()`（`PlaceDao.observeRegisteredPlaces`）。全 place を
  最小情報（id・座標・表示名）でリアクティブに返す。表示名は `places.name → google_places.name → 住所` の
  フォールバック（`COALESCE`）。場所詳細ではその場所自身は主マーカーと重なるので呼び出し側で除外する。
- **ON/OFF の保存先は Room ではなく `SharedPreferences`**（`SettingsRepository` / prefs 名 `pathly_settings`）。
  トグルは**UI の表示設定**であって場所データではないため、DB（SQLite）には持たせない。GPS 記録間隔と同じ扱い。
  - **画面ごとに独立**して保持する。キーは `show_registered_places_{recording|history|place_detail}`（`Boolean`・既定 `false`）。
    画面を表す `MapSurface`(`RECORDING`/`HISTORY`/`PLACE_DETAIL`) で出し分ける。
  - 各画面の ViewModel が `settingsRepository.showRegisteredPlaces(surface)` を購読し、地図上のトグルボタンで
    `setShowRegisteredPlaces(surface, …)` を書く。プロセスをまたいで保持される。

### 詳細画面

- 地図: 立ち寄りピン（紫）。タイトルに場所名（未命名は「立ち寄り」）、スニペットに時刻・滞在分
- シート: 立ち寄り一覧。各行に**場所名（未命名は座標）／到着–出発・滞在分**、タップで**名前編集ダイアログ**
- 名前編集 → `updatePlaceName(placeId, name)` で places を更新。同じ場所の別訪問にも反映される
- **再解析**ボタン: 経路（2点以上）で表示。**記録中も出す**（記録中は候補を境界以前＝確定済みの過去だけに絞る。上記「手動ボタン」参照）→ 押すと「一覧に無い立ち寄り」候補を**地図（オレンジのピン）＋下部オーバーレイ**（地図は常に表示・名前つき）で提示し、行タップで地図を寄せて名前と位置を確認しながらチェックした分だけ追加する（非破壊）。候補ゼロなら「見つかりませんでした」。誤って削除した立ち寄りの復旧・取りこぼしの追加に使う
- **立ち寄りを追加**ボタン: 手動追加モードに入る（記録中も使える）
- **場所を取得**ボタン: `googlePlaceId` が無い place が残るとき表示 → その track の未取得 place を Places で取得（手動再取得）。ラベルは「未命名 N件」ではなく**未取得（`googlePlaceId` 無し）**を条件にする
- **立ち寄りの削除**: 各行の「削除」で1件、行を**長押し**すると複数選択して一括削除できる（記録中に拾った誤検知の掃除を想定。誤って消しても**再解析**で戻せる）。削除は単体・複数とも同じロジック（`deleteStops(stopIds)`）で、常に**訪問（stop）を消す**動作。消した結果**どこからも参照されなくなった place だけ**を自動で場所ごと回収する（孤立 GC）。回収条件は「**残る stop がゼロ かつ wishlist 登録もゼロ**」。逆に、他に stop が残る（＝他の履歴でも使われている）place や、wishlist に登録がある place は残す。**wishlist は place を消すと CASCADE で消える**ため、行きたい登録のある place は履歴画面からは巻き込まず必ず残す。place ごと明示的に消したいときは場所タブ（[wishlist.md](./wishlist.md)）から。
  - **確認ダイアログは出さず即時削除**し、**スナックバーの「取り消す」**（`undoLastDeletion`）で直近の削除を戻せる。削除前に stop・回収する place・解決ログの実体を1件分だけ控えておき、取り消し時に**元の id のまま再挿入**して復元する（画面を離れると確定）。

---

## 将来の拡張

### 検出精度の改善（誤検知対策）

現在の検出は「50m圏内に3分」という**位置と時間だけ**の単純ルールなので、誤検知が多い:

- 信号待ち・渋滞（車で止まっているだけ）
- GPSドリフト（静止中でも座標が揺れる／低速移動が滞在に見える）
- 屋内で電波が弱く座標が固まる

Google Maps / iOS の訪問検出が精度良いのは、**位置以外の信号で「本当に止まったか」を判定**するため。効く順:

1. **アクティビティ認識**（最優先）… Android の [Activity Recognition API](https://developers.google.com/location-context/activity-recognition)（`ACTIVITY_RECOGNITION` 権限＋`ActivityRecognitionClient`）で「静止／徒歩／自転車／車」を判定し、**車で停車中などは滞在にしない**。信号待ち系の誤検知が大きく減る。
2. **精度フィルタ／重み付け** … accuracy の悪い点を除外・軽く扱い、静止中のドリフトを抑える。
3. **到着／出発のヒステリシス** … 入る判定と出る判定で別しきい値にして境界のバタつきを防ぐ。
4. **Wi‑Fi / Bluetooth / セル基地局** … 同じ AP・セルに繋がり続けている＝静止、の補助（屋内で強い）。
5. **POIスナップ** … 既知施設の輪郭に吸着（立ち寄り版のマップマッチング）。
6. **機械学習** … ラベル付きデータで学習したモデル。

まずは 1（アクティビティ認識でゲート）だけでも体感がかなり変わる。実装時にあらためて仕様検討する。

---

## 実装マップ

| 要素                     | ファイル                                                                                                                                        |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| 検出（既存）             | `domain/model/StopDetector.kt`（返り値は `DetectedStop`）                                                                                       |
| ドメイン                 | `domain/model/Place.kt`, `Stop.kt`, `DetectedStop.kt`                                                                                           |
| Entity                   | `data/local/entity/PlaceEntity.kt`, `StopEntity.kt`（`note` 含む）, `StopWithPlace.kt`                                                          |
| Entity                   | `data/local/entity/PlaceResolutionEntity.kt`, `GooglePlaceEntity.kt`, `NamedPlaceRow.kt`（近傍1クエリ）                                         |
| DAO                      | `data/local/dao/PlaceDao.kt`, `StopDao.kt`, `PlaceResolutionDao.kt`, `GooglePlaceDao.kt`                                                        |
| マイグレーション         | `DatabaseMigrations.kt`（v4: place_resolutions / v6: stops.note / v10: places.source / v12: 座標の索引）                                        |
| Places 呼び出し          | `data/places/PlacesNameResolver.kt`（+ オンライン判定）                                                                                         |
| 記録中の検出・保存       | `service/LocationTrackingService.kt` → `PlaceRepository.updateStopsForTrack`（境界以降のみ検出＝`SmoothedPointDao.getByTrackAfter`）            |
| 名前解決のキャッチアップ | `data/work/PlaceNameCatchUpWorker.kt`（WorkManager・ネットワーク接続を制約）                                                                    |
| ライブ立ち寄り中         | 記録中サービスの `StateFlow`（非永続）→ `data/tracking/TrackingController` → `presentation/tracking/TrackingScreen.kt`                          |
| 再解析（追加提案）       | `detectMissingStops` / `addStops`（非破壊）＋ 詳細画面の候補オーバーレイ                                                                        |
| 完全手動追加             | `addManualStop`（非破壊）／`domain/usecase/AddManualStopUseCase.kt`（近接確認込み）＋ `presentation/stops/ManualStopSheet.kt`                   |
| 地図の共通描画           | `presentation/common/RouteMap.kt`（`RouteMapContent` / `RegisteredPlaceMarkers`）, `FloatingSheet.kt`                                           |
| リポジトリ               | `domain/repository/PlaceRepository.kt` / `data/repository/PlaceRepositoryImpl.kt`                                                               |
| 画面                     | `presentation/history/`（`TrackDetailScreen` / `TrackDetailMap` / `TrackDetailSheet` / `TrackDetailDialogs` / `TrackTuningPanel` ＋ ViewModel） |

---
