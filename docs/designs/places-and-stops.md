# 場所（places）と立ち寄り（stops）の設計

立ち寄り場所を**永続化**し、**名前**を持たせ、**手動で編集**できるようにする設計。
将来の「行きたい場所リスト」も同じ「場所」を再利用できる構造にする。

- 関連要望（[requirements.md](../requirements.md)）
  - 立ち寄った場所を自動で検出してほしい（どこに何分いたか）
  - 場所名を自分たちに分かりやすい名前に手動で入力・変更したい
  - 行きたい場所をリストアップしたい／場所の詳細情報を登録したい（将来）
- 前提: 立ち寄りの**検出ロジック**は [gps-smoothing.md](./gps-smoothing.md) の補正後の点列に対して行う（`StopDetector`）。本書は検出結果を**どう保存・命名・編集するか**を扱う。

---

## 用語・データの流れ

> 用語の日英対応（データ・処理）は [glossary.md](./glossary.md) にまとめている。本節は流れの説明。

位置に関するデータは、段階を追って「点の集まり」から「意味のある場所」へと変換される。

1. **GPSの生データ（`gps_points`）** … 端末が記録したそのままの座標。ノイズを含む。
2. **補正後の位置情報（`smoothedPoints`）** … 生データを補正した**軌跡の点の集まり**（たくさんの点）。「**どこを通ったか**」。原データは変更せず読み込み時に計算する（[gps-smoothing.md](./gps-smoothing.md)）。
3. **`DetectedStop`** … 補正後の点列の中で「50m圏内に3分以上とどまった」箇所を1つにまとめたもの。「**どこに滞在したか**」。座標は滞在中の点の重心。検出の一時結果で永続化しない。
4. **`Place` / `Stop`** … `DetectedStop` を保存したもの。`Place`＝場所そのもの（名前・座標・住所）、`Stop`＝その場所への訪問（place × track × 時刻）。本書が扱う範囲。

```
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

### なぜ「場所」と「立ち寄り」を分けるのか

検出できるのは「ある経路の途中で、ここに◯分いた」という**訪問イベント**。
一方で「行きたい場所リスト」や「同じカフェに2回行った」を表現するには、
**場所そのもの**を訪問から独立して持てる必要がある。

そこで:

- **places（場所）** … 場所そのもの。緯度経度・名前・住所を持つ。経路とは独立。
  立ち寄りで検出した場所・手動で追加した場所・将来の行きたい場所、すべてここに入る。
- **stops（立ち寄り）** … 「どの経路で・どの場所に・いつからいつまで」いたかを表す**関連（訪問）**。
  places と gps_tracks を結ぶ中間テーブル。

```
gps_tracks 1 ──< stops >── 1 places
                (訪問)        (場所そのもの)
                              └─ 将来: 行きたい場所リストも places を参照
```

### 命名の由来（自動）

検出した座標の**最寄りの施設名**を Google **Places API (New) の Nearby Search** で取得する。
`local.properties` の `GOOGLE_MAPS_API_KEY`（地図と共用）を使う。

- 取得できたら place の `name`・`address` に保存し、`place_resolutions` に解決記録（`resolvedAt`・`googlePlaceId`）を残す。
- **place 1件につき自動で叩くのは1回だけ**。「叩いたか」は `place_resolutions` の**行の有無**で判定する（`name IS NULL` では判定しない＝POIの無い場所を毎回叩かないため）。
- POIが見つからないときも `resolvedAt` の行を残す（`googlePlaceId` は null）＝自動では二度と叩かない。**オフライン／通信エラーのときは行を作らず**、オンライン復帰後に自動でキャッチアップする。
- **課金**: Nearby Search は有料。**オンラインのときだけ・place 1件1回**に限定して最小化する。
  - places に「試行済み」等の動的状態は持たせない（places は静的に保つ）。解決状態は `place_resolutions` に**分離**する。
- **手動再取得**: `googlePlaceId` が無い place（未取得・過去失敗・POI無し）を、詳細画面のボタンでユーザー操作でだけ取り直せる。二度と命名できない状態にはしない導線。

> Geocoder（無料・住所のみ）ではなく Places（有料・施設名）を選んだ理由:
> 「スターバックス」「◯◯公園」のような**人が分かる名前**が欲しいため。住所だけでは振り返りに使いにくい。

---

## データモデル

実装は Long 主キー（`autoGenerate`）。既存の `gps_tracks` / `gps_points` に合わせる。

### places テーブル

| カラム    | 型      | 制約             | 説明                                          |
| --------- | ------- | ---------------- | --------------------------------------------- |
| id        | INTEGER | PK AUTOINCREMENT | 場所ID                                        |
| name      | TEXT    | NULL             | 表示名（null=未命名。Places解決 or 手動入力） |
| latitude  | REAL    | NOT NULL         | 緯度                                          |
| longitude | REAL    | NOT NULL         | 経度                                          |
| address   | TEXT    | NULL             | 住所（Places の formattedAddress）            |
| createdAt | INTEGER | NOT NULL         | 作成日時                                      |
| updatedAt | INTEGER | NOT NULL         | 更新日時                                      |

### stops テーブル（立ち寄り＝訪問）

| カラム        | 型      | 制約                        | 説明               |
| ------------- | ------- | --------------------------- | ------------------ |
| id            | INTEGER | PK AUTOINCREMENT            | 立ち寄りID         |
| placeId       | INTEGER | NOT NULL, FK→places(id)     | どの場所か         |
| trackId       | INTEGER | NOT NULL, FK→gps_tracks(id) | どの経路での訪問か |
| arrivalTime   | INTEGER | NOT NULL                    | 到着時刻           |
| departureTime | INTEGER | NOT NULL                    | 出発時刻           |
| createdAt     | INTEGER | NOT NULL                    | 作成日時           |

- インデックス: `stops(placeId)`, `stops(trackId)`
- 外部キー:
  - `stops.trackId → gps_tracks.id ON DELETE CASCADE`（経路を消したら立ち寄りも消える）
  - `stops.placeId → places.id`（**場所は消さない**。訪問が消えても場所は残す＝再利用・行きたい場所のため）

> `stops` は「経路と場所の関連（訪問）」テーブルでもある。命名対象を track で絞るときは
> `stops` を JOIN して `trackId` で絞る（グローバルに全 place を叩かない）。

### place_resolutions テーブル（Google解決ログ）

Google Places を「叩いたか」を place 単位で記録する。places を静的に保つため、動的な解決状態はここに分離する。

| カラム        | 型      | 制約                     | 説明                                             |
| ------------- | ------- | ------------------------ | ------------------------------------------------ |
| placeId       | INTEGER | PK, FK→places(id)        | 対象の場所（1行/place）                          |
| resolvedAt    | INTEGER | NOT NULL                 | Googleに問い合わせた日時（＝「叩いた」印）        |
| googlePlaceId | TEXT    | NULL                     | 見つかった Google の place ID（POI無しは null）   |

- **行がある＝問い合わせ済み**（結果の有無を問わず）。無ければ未実施。
- 主役は `resolvedAt`（叩いた事実）。`googlePlaceId` は結果で、POIが無ければ null。
- 外部キー: `placeId → places.id ON DELETE CASCADE`（place は通常消さないが整合のため）。
- 将来 Place Details（写真・営業時間等）を足すときの参照キーにもなる。

### 場所の重複排除（find-or-create）

同じ店の2回目の訪問を別々の場所にしないため、立ち寄りを保存するときは
**既存の場所が近く（既定 30m 以内）にあれば再利用**し、無ければ新規作成する。

---

## ドメインモデル

- `DetectedStop` … `StopDetector` の**検出結果（幾何のみ）**。緯度経度・到着/出発・点数。永続化しない。
  （旧 `Stop` をリネーム。永続化する `Stop` と区別するため）
- `Place` … 永続化された場所（id・name?・緯度経度・address?）。
- `Stop` … 永続化された立ち寄り（id・`place: Place`・trackId・到着/出発）。滞在時間は計算。

---

## 保存・命名のトリガー

検出・保存・命名は次の2経路でのみ行う。**詳細画面を開いても自動検出はしない**
（「stops が0件」では“本当に立ち寄りが無い”のか“取りこぼした”のか区別できず、勝手に叩くのは筋が悪いため）。
旧 `ensureStopsDetected`（開いたら冪等検出）は廃止する。

### 1. 記録中（自動・ライブ）

`LocationTrackingService` が新しい点を保存・補正する流れに続けて、立ち寄りも増分で処理する。
補正（[gps-smoothing.md](./gps-smoothing.md)）と同じ「確定プレフィックス」の考え方を使う。

滞在は次のライフサイクルで扱う（**stop は離脱で確定＝案A**、**place は滞在中に先に確定**してライブ表示・命名に使う）:

1. **滞在候補**（3分未満）: 何もしない（滞在時間を数えるだけ）。
2. **立ち寄り中**（3分超・まだ離れていない）:
   - その時点の重心で `findOrCreatePlace`（30m重複排除）→ **place を確定**。
   - オンラインなら**名前解決**（`place_resolutions` に記録）。既存の解決済み place を再利用したときは叩かない。
   - UIに「● 立ち寄り中（滞在◯分）＋ place名（無ければ座標）」を**メモリ経由**（`StateFlow`）で流す。**stop はまだ保存しない**。
3. **確定**（離れた or 記録終了）:
   - **stop を保存**（place は既にあるので stop を作るだけ・名前解決は再実行しない）。
   - ライブ表示から消え、保存済み立ち寄り一覧に移る。

```
新しい点が保存・補正される
  └─ updateStopsForTrack(trackId, isFinal)
       - StopDetector.detect(保存済みの補正点列) で DetectedStop 群を得る
       - 末尾の「滞在中」クラスタ（末尾点を含む）:
           3分超なら findOrCreatePlace → (online) resolve、"立ち寄り中" を StateFlow で公開
           離れて確定 or isFinal なら stops に保存し、公開を解除
       - 過去の確定クラスタ:
           未保存ぶんだけ findOrCreatePlace → stops に保存
           オンラインなら未解決 place を resolve（オフラインは行を作らず後でキャッチアップ）
```

- **place は滞在中（3分超）に先に保存**される。stop は離脱で確定（案A）。3分超の滞在は必ず後で stop になるため、宙に浮いた place は基本残らない（記録クラッシュ時のみ・無害で再利用可）。
- API回数は **place 1件1回**のまま。クラスタは50m以内なので重心のブレは小さく、place 確定後は `place_resolutions` があるので自動では叩き直さない。
- 記録終了時に `isFinal=true` で末尾を確定。オンラインなら未解決の place を一括命名。

### 2. 手動ボタン（詳細画面）

- **再解析**: その track を**再補正 → 立ち寄り再検出 → 命名**する。アルゴリズム／しきい値を直したあとの反映、記録中に取りこぼしたときの作り直し。`stops` を消して検出し直すが、**place は消えない**ので30m重複排除で**命名済み place が再利用され、名前は保持**される（[gps-smoothing.md](./gps-smoothing.md) の再補正を包含）。
- **場所を取得**: 検出はやり直さず、その track の**未取得（`googlePlaceId` が無い）place だけ**を Places で取得する。オフラインで命名できなかった分の手動再実行。

### 命名の管理（place_resolutions・place 1件1回）

「一度叩いたら二度と自動で叩かない」を `place_resolutions`（解決ログ）で管理する。

- **自動命名の対象**: 対象 track の place のうち、`place_resolutions` に**行が無いもの**（＝まだ一度も叩いていない）。
- **結果を記録**: 応答が返ったら（見つかっても見つからなくても）行を作る。`resolvedAt` に日時、見つかれば `googlePlaceId` を入れ、`places.name`/`address` を更新。
- **オフライン／通信エラー**: 行を作らない → 次に命名が走ったとき再挑戦（キャッチアップ）。
- **手動「場所を取得」**: `googlePlaceId` が無い place（行が無い or null）を対象に、ユーザー操作でだけ再挑戦。

| 状況              | 記録         | 自動再取得 | 手動再取得 |
| ----------------- | ------------ | ---------- | ---------- |
| POI発見           | 行・ID あり  | しない     | しない     |
| POI無し           | 行・ID null  | しない     | できる     |
| オフライン/失敗   | 行なし       | する(復帰時)| できる     |

> 命名を「オンライン時・place 1件1回」に閉じ込めるのは、(1) 課金を最小化するため、(2) POIの無い場所を毎回叩き直さないため。
> `name IS NULL` を条件にしないのは、POI無しの場所が永遠に未命名のまま毎回叩かれてしまうため。判定は `place_resolutions` の行の有無で行う。

---

## Places 命名（Places SDK for Android）

Web API 直叩きではなく **Places SDK for Android（New）** を使う。
Android アプリ制限付きの API キー（地図と共用）を**そのまま安全に**使えるため。

- 依存: `com.google.android.libraries.places:places:5.1.1`（minSdk 24。本アプリは 34 でOK）
- 初期化: `PathlyApplication.onCreate` で **`Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.GOOGLE_MAPS_API_KEY)`**
  - New API 面（`searchNearby`）を使うため、旧 `Places.initialize(...)` ではなくこちらを呼ぶ
- 呼び出し: `PlacesClient.searchNearby(SearchNearbyRequest)`
  - `CircularBounds.newInstance(LatLng(lat, lng), 50.0)` で立ち寄り座標中心・半径 50m
  - `setMaxResultCount(1)`, `setRankPreference(RankPreference.DISTANCE)`
  - `setPlaceFields(listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS))`
  - 返り値の先頭 `place.id` を `googlePlaceId`、`place.displayName` を name、`place.formattedAddress` を address に
- Cloud 側は **「Places API (New)」** を有効化＋請求先リンク済みが前提
- ラッパー `PlacesNameResolver` に閉じ込め、**呼び出し前にオンライン判定**（`ConnectivityManager`）し、オフラインなら叩かない。0件は「叩いたがPOI無し」（`resolvedAt` の行を残す）、例外は「未実施」（行を作らない）として区別する

---

## UI

### 記録画面（ライブ）

- 記録中に「立ち寄り中」の place があれば、**「● 立ち寄り中（滞在◯分）＋ place名（無ければ座標）」**を表示。
- 記録中サービスが公開する `StateFlow`（メモリ・非永続）を購読する。離れて確定したら消える。

### 詳細画面

- 地図: 立ち寄りピン（紫）。タイトルに場所名（未命名は「立ち寄り」）、スニペットに時刻・滞在分
- シート: 立ち寄り一覧。各行に**場所名（未命名は座標）／到着–出発・滞在分**、タップで**名前編集ダイアログ**
- 名前編集 → `updatePlaceName(placeId, name)` で places を更新。同じ場所の別訪問にも反映される
- **場所を取得**ボタン: `googlePlaceId` が無い place が残るとき表示 → その track の未取得 place を Places で取得（手動再取得）。ラベルは「未命名 N件」ではなく**未取得（`googlePlaceId` 無し）**を条件にする
- **再解析**ボタン: 軌跡を再補正し、立ち寄りを検出し直して命名する（[gps-smoothing.md](./gps-smoothing.md) の「再補正」を包含）

---

## 将来の拡張

### 行きたい場所リスト

places は経路と独立しているので、行きたい場所は「stops を持たない place」として登録できる。
必要になったら `places` に `wantToVisit`（フラグ）や優先度・営業時間などの列を足す、
あるいは `wishlist` テーブルから places を参照する。本設計はどちらにも拡張できる。

### 場所一覧からの直接検索（Google）

将来「場所一覧」画面を作る場合、**経路と関係なく** place を Google で検索・命名できると良い。
places が track から独立し、解決状態も `place_resolutions` に分離されているため、
「一覧上の未取得 place を検索」「キーワードから place を新規登録」も同じ土台に乗る
（`stops` を介した track 絞りをしないだけ）。`googlePlaceId` は Place Details 取得の参照にも使える。

---

## 実装マップ

| 要素                  | ファイル                                                                          |
| --------------------- | --------------------------------------------------------------------------------- |
| 検出（既存）          | `domain/model/StopDetector.kt`（返り値は `DetectedStop`）                         |
| ドメイン              | `domain/model/Place.kt`, `Stop.kt`, `DetectedStop.kt`                             |
| Entity（既存）        | `data/local/entity/PlaceEntity.kt`, `StopEntity.kt`, `StopWithPlace.kt`           |
| Entity（予定）        | `data/local/entity/PlaceResolutionEntity.kt`                                      |
| DAO                   | `data/local/dao/PlaceDao.kt`, `StopDao.kt`, `PlaceResolutionDao.kt`（予定）       |
| マイグレーション      | `DatabaseMigrations.kt`（1→2 済 / 立ち寄り記録中対応は次バージョンで追加）        |
| Places 呼び出し       | `data/places/PlacesNameResolver.kt`（+ オンライン判定）                           |
| 記録中の検出・保存(予定)| `service/LocationTrackingService.kt` → `PlaceRepository.updateStopsForTrack`      |
| ライブ立ち寄り中(予定) | 記録中サービスの `StateFlow`（非永続）→ `presentation/tracking/TrackingScreen.kt` |
| 再解析(予定)          | 詳細画面 → 補正の再計算 + 立ち寄り再検出 + 命名                                    |
| リポジトリ            | `domain/repository/PlaceRepository.kt` / `data/repository/PlaceRepositoryImpl.kt` |
| 画面                  | `presentation/history/TrackDetailScreen.kt`（+ 詳細用 ViewModel）                 |

---

## 段階リリース

済:

1. DB（places/stops・マイグレーション・DAO）＋ドメイン＋検出結果の保存（**オフラインで完結**）
2. Places 命名（解決・キャッシュ）
3. UI（一覧の名前表示・編集ダイアログ）

本設計（記録中の検出・命名）で追加する分:

4. `place_resolutions` テーブル＋DAO（解決ログ・マイグレーション）
5. 記録中の増分検出・保存（`updateStopsForTrack`、stop は確定＝案A）
6. 命名を `place_resolutions` 基準に変更（place 1件1回・オンライン判定・キャッチアップ）
7. `ensureStopsDetected`（開いたら検出）を廃止し、詳細画面に**再解析**／**場所を取得**ボタンを用意
8. ライブ「立ち寄り中」（3分超で place 先行確定＋名前解決、`StateFlow` で記録画面に表示）

いずれの段階でも、Places が使えなくてもアプリは動く（座標表示＋手動取得で成立）。
