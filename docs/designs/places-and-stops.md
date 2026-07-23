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

- 取得できたら place の `name`・`address` に保存（**一度取得したら再取得しない＝キャッシュ**）
- 取得できない／オフライン／APIエラー時は `name = null` のまま（座標表示にフォールバック）。あとで手動命名できる
- **課金**: Nearby Search は有料。自動命名は「**その経路の立ち寄りを初めて検出・保存したとき、1回だけ**」に限定する。開き直しでは自動命名しない（下記トリガー参照）
  - places に「試行済み」等の動的状態は持たせない（places は静的に保つ）
- **手動再取得**: 未命名（`name IS NULL`）の場所が残る経路では、詳細画面のボタンで**その場所だけ取り直せる**。オフラインで自動命名に失敗した分を、オンライン時にユーザー操作で再実行するための導線（開くたびの自動再課金は避けつつ、二度と命名できない状態にはしない）。

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

**詳細画面を開いたとき（遅延・冪等）**に確定する。

```
TrackDetail を開く
  └─ ensureStopsDetected(track)
       ├─ すでに stops があれば何もしない（冪等・再評価も再命名もしない）
       └─ 無ければ（初回のみ）:
            1. StopDetector.detect(track.smoothedPoints) で DetectedStop を得る
            2. 各 DetectedStop について findOrCreatePlace(lat,lng)（30m 重複排除）
            3. stops に (placeId, trackId, 到着, 出発) を保存
            4. resolveMissingNames(trackId): name が null の場所だけ Places で命名（1回だけ・ベストエフォート）
  └─ getStopsForTrack(trackId) を Flow で購読して表示
```

この方式の利点:

- **既存の記録**（終了済みトラック）も、開いた時点で一度だけ確定される（マイグレーション不要）
- Places 呼び出しは**初回検出時の未命名の場所だけ**＝課金を最小化。開き直しでは呼ばない
- 検出は補正後の点列に対して1度だけ。以後は保存済みを表示（軽量）

> 自動命名を初回検出時に閉じ込めるのは、(1) 開くたびの再課金を避けるため、(2) 名前が取れない場所（POIの無い立ち寄り）を毎回叩き直さないため。
> 初回がオフラインだと自動命名されないが、**詳細画面の「未命名の名前を取得」ボタンで後から取り直せる**（手動再取得）。記録終了時に即確定しないのは、その瞬間にネットワークが無いことが多いため。

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
  - `setPlaceFields(listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS))`
  - 返り値の先頭 `place.displayName` を name、`place.formattedAddress` を address に
- Cloud 側は **「Places API (New)」** を有効化＋請求先リンク済みが前提
- ラッパー `PlacesNameResolver` に閉じ込め、失敗（オフライン・0件・例外）は `null` を返してフォールバック

---

## UI（詳細画面）

- 地図: 立ち寄りピン（紫）。タイトルに場所名（未命名は「立ち寄り」）、スニペットに時刻・滞在分
- シート: 立ち寄り一覧。各行に**場所名（未命名は座標）／到着–出発・滞在分**、タップで**名前編集ダイアログ**
- 名前編集 → `updatePlaceName(placeId, name)` で places を更新。同じ場所の別訪問にも反映される
- 未命名が1件以上あるとき、**「未命名 N件の名前を取得」ボタン**を表示 → `resolveMissingNames(trackId)`（オフラインで失敗した分の手動再取得）

---

## 将来: 行きたい場所リスト

places は経路と独立しているので、行きたい場所は「stops を持たない place」として登録できる。
必要になったら `places` に `wantToVisit`（フラグ）や優先度・営業時間などの列を足す、
あるいは `wishlist` テーブルから places を参照する。本設計はどちらにも拡張できる。

---

## 実装マップ

| 要素             | ファイル                                                                          |
| ---------------- | --------------------------------------------------------------------------------- |
| 検出（既存）     | `domain/model/StopDetector.kt`（返り値を `DetectedStop` に）                      |
| ドメイン         | `domain/model/Place.kt`, `Stop.kt`, `DetectedStop.kt`                             |
| Entity           | `data/local/entity/PlaceEntity.kt`, `StopEntity.kt`, `StopWithPlace.kt`           |
| DAO              | `data/local/dao/PlaceDao.kt`, `StopDao.kt`                                        |
| マイグレーション | `data/local/migration/DatabaseMigrations.kt`（1→2 を実装）                        |
| Places 呼び出し  | `data/places/PlacesNameResolver.kt`                                               |
| リポジトリ       | `domain/repository/PlaceRepository.kt` / `data/repository/PlaceRepositoryImpl.kt` |
| 画面             | `presentation/history/TrackDetailScreen.kt`（+ 詳細用 ViewModel）                 |

---

## 段階リリース

1. DB（places/stops・マイグレーション・DAO）＋ドメイン＋検出結果の保存（**オフラインで完結**）
2. Places 命名（未命名の場所だけ解決・キャッシュ）
3. UI（一覧の名前表示・編集ダイアログ）

いずれの段階でも、Places が使えなくてもアプリは動く（座標表示＋手動命名で成立）。
