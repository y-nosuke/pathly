# 行きたい場所（wishlist）の設計

これから行きたい場所を **登録・一覧・整理** できるようにする設計。
既存の場所（`places`）を再利用し、「まだ訪れていない場所」も「また行きたい場所」も同じ土台で扱う。

- 関連要望（[requirements.md](../requirements.md) の「計画」）
  - 行きたい場所をリストアップしたい
  - 場所の詳細情報（住所・営業時間など）を登録したい
  - 行きたい場所に優先度を設定したい
- 前提: 「場所（place）は経路と独立して永続する」という [places-and-stops.md](./places-and-stops.md) の設計に乗る。
  行きたい場所は **「stops を持たない place ＋ wishlist の1行」** として表現する。
- データモデル（`places` / `place_resolutions`）は [../specs/model.md](../specs/model.md) を参照。

> **マイルストーン注記**: 本来 [roadmap.md](../roadmap.md) の Phase 3（事前の計画）だが、先行して着手する。
> Phase 2（写真・評価・タグ等）とは独立に実装でき、既存 DB を壊さず追加できる。

---

## 方針

### なぜ places とは別に wishlist テーブルを持つのか

[places-and-stops.md](./places-and-stops.md) の設計では **places を静的に保ち、動的な状態は別テーブルに分離**している
（例: Google 解決の状態は `place_resolutions` に分離）。行きたい場所の「優先度・メモ・訪問済み状態」も
**場所そのものではなく、ユーザーの計画に属する動的な情報**なので、`places` に列を足さず `wishlist` テーブルに分離する。

```
places 1 ──< stops        （立ち寄り＝過去の訪問）
       1 ──o wishlist      （行きたい＝これからの計画）  ← 本書が追加
       1 ──o place_resolutions （Google 解決ログ）
```

- 1つの place は wishlist に **最大1件**（`placeId` に UNIQUE）。同じ場所を二重登録しない。
- 立ち寄りで検出済みの place を「また行きたい」に足す場合も、既存 place を再利用して wishlist 行を足すだけ。

### タグ（初回スコープ外・後回し）

要望（[requirements.md](../requirements.md)）にあるのは「**タグ付けで整理したい（複数・目的別）**」のみで、
「カテゴリ」という別概念は存在しない。用語は **「タグ」に統一**する。

初回スコープでは **タグを実装しない**。理由:

- 要望は複数タグ（「グルメ」「観光」など目的別に絞り込む）で、1件だけ選べる単一値では要望を満たせない。
- 複数タグは `tags` / `wishlist_tags`（多対多）を要し、記録側の `stop_tags`（[CLAUDE.md] の想定）とも
  共有すべき横断機能。行きたい場所の登録を先行させる今回の主旨からは切り離し、**後段でまとめて実装**する。

初回は優先度・メモ・訪問済み状態で計画用途は成立する。タグは段階リリースの後半（[段階リリース](#段階リリース)）で
`tags` を共有する形で追加する。

### 訪問済み状態

**その場所に立ち寄り（stops）が1件以上あれば「訪問済み」**とする（一覧クエリで stops を COUNT）。
加えて wishlist の `visitedAt`（NULL=未訪問 / 日時=訪問済み）で**手動**でも訪問済みにできる。
`isVisited = 立ち寄りあり OR visitedAt != null`。立ち寄り記録のある場所は自動で訪問済みになる。

---

## データモデル

実装は Long 主キー（`autoGenerate`）。既存の `places` / `place_resolutions` に合わせる。

### wishlist テーブル

| カラム    | 型      | 制約                            | 説明                               |
| --------- | ------- | ------------------------------- | ---------------------------------- |
| id        | INTEGER | PK AUTOINCREMENT                | 行きたい場所ID                     |
| placeId   | INTEGER | NOT NULL, UNIQUE, FK→places(id) | どの場所か（1 place につき1件）    |
| priority  | INTEGER | NOT NULL, DEFAULT 1             | 優先度（0=低 / 1=中 / 2=高）       |
| memo      | TEXT    | NULL                            | メモ・コメント（なぜ行きたいか等） |
| visitedAt | INTEGER | NULL                            | 訪問済み日時（NULL=未訪問）        |
| createdAt | INTEGER | NOT NULL                        | 作成日時                           |
| updatedAt | INTEGER | NOT NULL                        | 更新日時                           |

- インデックス: `wishlist(placeId)` は UNIQUE（重複登録の防止＋find-or-add に使う）。
- 外部キー: `wishlist.placeId → places.id ON DELETE CASCADE`（place を消したら wishlist 行も消える）。
  - ただし通常フローで place を消すことはない（places は再利用のため静的に保つ）。
- 名前・座標・住所は `places` 側に持つ（wishlist は持たない）。表示時に JOIN する。

### ER 図（[../specs/model.md](../specs/model.md) への追加分）

```mermaid
erDiagram
  places ||--o| wishlist : "行きたい"
  places ||--o{ stops : "立ち寄り(既存)"
  places ||--o| place_resolutions : "解決ログ(既存)"

  wishlist {
    Long id PK
    Long placeId FK
    Int priority
    String memo
    Date visitedAt
  }
```

---

## ドメインモデル

- `WishlistItem` … 行きたい場所1件。`place: Place`（座標・名前・住所）＋計画情報（優先度・メモ・訪問済み）。
  - 表示名は `place.name`（未命名は住所→座標の順でフォールバック）。
- `Priority` … `LOW / MEDIUM / HIGH` の enum（DB では 0/1/2）。

```
WishlistItem
 ├─ place: Place        （id・name?・lat・lng・address?）
 ├─ priority: Priority
 ├─ memo: String?
 └─ visitedAt: Date?    （null=未訪問）
```

---

## 登録の3経路

いずれも **place を find-or-create（30m 重複排除）で登録**するのが土台。「行きたい」は任意の属性で、
必要なときだけ wishlist 行を作る（既に登録済みの place なら重複させない）。
下記は登録の入口の一覧（実装済み: 1 キーワード検索・2 地図タップ・2b 記録画面POI。3 立ち寄りから は後段）。

### 1. キーワード検索で登録（オンライン・課金あり）

店名などで検索して選ぶ。Google から名前・住所・座標・`googlePlaceId` を取得できる。

1. 検索欄にキーワード入力 → **候補（Autocomplete）** を表示。
2. 候補を選択 → **`fetchPlace`** で `DISPLAY_NAME / FORMATTED_ADDRESS / LAT_LNG / ID` を取得。
3. 座標で `findOrCreatePlace`（30m）→ place に name/address を保存。
   `googlePlaceId` が取れているので **`place_resolutions` に解決済み行を記録**（Nearby を叩き直さない）。
4. wishlist 行を作成（優先度・メモを任意入力）。

- **課金最小化**（[places-and-stops.md](./places-and-stops.md) と同じ思想）:
  - **Autocomplete はセッショントークン**でまとめ、確定した1件だけ `fetchPlace` する。
  - オフライン時は検索経路を無効化（手動入力・地図タップに誘導）。
- 実装: 既存 `PlacesNameResolver`（Nearby 専用）とは別に、テキスト検索用の呼び出しを足す
  （`PlacesTextSearcher` など。`AutocompleteSessionToken` ＋ `FindAutocompletePredictionsRequest` ＋ `FetchPlaceRequest`）。

### 2. 地図をタップして登録（オフライン可）

地図上の任意地点を選んで座標で登録する。名前は後から。

1. 全画面マップで地点をタップ（またはロングプレス）→ ピンを置く。
2. `findOrCreatePlace`（30m）→ place を作成（name は null）。
3. wishlist 行を作成（優先度・メモを任意入力）。
4. 名前は後で **手動入力**、またはオンライン時に **「場所を取得」**（座標中心の Nearby ＝ 既存 `PlacesNameResolver.resolve` を再利用）で命名。

### 2b. 記録画面のマップから登録（POIタップ）

記録（トラッキング）画面の全画面マップでも、施設アイコン（POI）をタップして登録できる。

1. `onPOIClick` で POI（名前・座標）を取得 → 登録ダイアログ（名前・行きたいトグル）。
2. `registerPlace`（＝地図タップと同じ）。行きたい ON なら `addToWishlist`（優先度は既定 MEDIUM・メモは後で「場所」タブで編集）。
3. 記録中でも使える。空きタップは登録に使わない（地図操作と競合するため POI タップのみ）。

対応実装: `presentation/tracking/TrackingScreen.kt`（`onPOIClick` → ダイアログ）／`TrackingViewModel.registerPlace`。

### 3. 立ち寄り場所から登録（また行きたい）

> [requirements.md](../requirements.md) に「訪れた場所を『また行きたい』としてワンタップで追加したい」を追記済み（2026-07-26）。
> 振り返り（Phase 2）→計画の橋渡し。実装は段階リリースの後段（段階4）。

1. 詳細画面（[../specs/screens.md](../specs/screens.md) の立ち寄り一覧）の各行に **「また行きたい」** を追加。
2. その stop の place（既存）に対し wishlist 行を作成（無ければ）。既にあれば「登録済み」を示す。

---

## UI

ボトムナビに **「場所」タブを新設**（現状 3 タブ → 4 タブ）。**登録済みの全ての場所（places）を一覧**し、
各場所に「行きたい」を付け外しできる。行きたいは場所の属性（トグル）として扱う。
将来の「計画」タブ（Phase 3）の前身になる。

### ナビゲーション

| タブ     | 機能                                 | アイコン（新規）           |
| -------- | ------------------------------------ | -------------------------- |
| 記録     | 全画面マップ＋GPS記録                | `ic_location_on`           |
| 履歴     | 過去記録の一覧・統計                 | `ic_list`                  |
| **場所** | **全ての場所を一覧・登録・行きたい** | **`ic_place`（新規追加）** |
| 設定     | GPS記録間隔などの設定                | `ic_settings`              |

> アイコンは Material Icons 不使用のため `res/drawable` にベクター（`ic_place`=タブ / `ic_flag`=行きたいフラグ）を追加し `painterResource` で使う（[CLAUDE.md] 準拠）。

### 1. 場所一覧画面

```text
┌─────────────────────────────┐
│ 場所                    (＋) │ ← 右上: 追加ボタン
│ [すべて] [行きたい] [訪問済み]│ ← フィルタ
│ ┌─────────────────────────┐ │
│ │ ◯◯カフェ            [🚩] │ │ ← 名前・行きたいフラグ(タップでON/OFF)
│ │ 行きたい ★★★ / メモ抜粋… │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ △△公園  ✓訪問済み   [🚩] │ │
│ └─────────────────────────┘ │
├─────────────────────────────┤
│ [ 記録 ][ 履歴 ][ 場所 ][設定]│
└─────────────────────────────┘
```

- **一覧の範囲**: **全ての places**（立ち寄り検出で自動生成された場所も含む）。
- **並び替え**: 登録の新しい順（`createdAt DESC`・安定。行きたいトグルで並びが動かない）。
- **フィルタ**: すべて / 行きたい / 訪問済み（訪問済み＝立ち寄りあり or 手動）。
- **各行**: 場所名（未命名は住所→座標）・行きたいフラグ（タップで付け外し）・優先度（行きたい時）・メモ抜粋・訪問済みバッジ・削除アイコン。
- **追加（＋）**: 地図タップで登録（下記）。
- 空のときは「場所がありません」＋追加導線。

### 2. 追加フロー（地図タップ）

**全画面マップを土台にし、タップして初めて入力欄を出す**（マップを広く使う）。

1. 全画面マップ。未タップのうちは上部にヒントのみ（入力欄は出さない）。
2. **アイコン（POI）をタップ** → 名前を自動入力＋ピン。**何もない場所をタップ** → 名前は空欄＋ピン。
3. タップ後に下部フォームが出る: 名前（任意）・**「行きたい」トグル**（ON時のみ優先度・メモ）。
4. **保存＝まず場所を登録**（find-or-create 30m）。行きたいトグルが ON のときだけ wishlist にも入れる。
   「選び直す」でピンを解除して再タップできる。

> 名前・座標は place 側に持つ。POI 名は `onPOIClick` の `PointOfInterest.name` を使う（追加のオンライン取得はしない）。

### 3. 場所詳細画面（map-first）

```text
┌─────────────────────────────┐
│ (←)                         │
│        [全画面 Google Map]   │ ← place にピン
│ ┌─────────────────────────┐ │
│ │ ◯◯カフェ            [🚩] │ │ ← 名前・行きたいフラグ
│ │ 住所: 東京都…            │ │
│ │ 優先度 ★★★（行きたい時）  │ │
│ │ メモ …                   │ │
│ │ [ 未訪問 ⇄ 訪問済み ]     │ │ ← 行きたい時のみ
│ │ [ 保存 ]                 │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

- 左上に戻るボタン（`FilledTonalIconButton`・マップ上でも見えるよう背景付き）。
- 行きたいフラグの付け外し。ON のときだけ優先度・メモ・（立ち寄り記録が無ければ）訪問トグルを編集できる。立ち寄り記録がある場合は「訪問済み（立ち寄り記録 N 件）」と表示。
- **削除**: 一覧の各行と詳細から。**立ち寄り記録（stops）がある場所は削除不可（ボタン非活性）＝記録を残す**。行きたい・座標だけが紐づく場所のみ削除でき、place を消す（wishlist・place_resolutions は CASCADE、stops は消さない）。
- 未命名（座標のみ）の place の命名（Nearby 取得）は**後段**で追加予定。
- **将来**: 営業時間表示、地図アプリでナビ起動、計画（お出掛け）への割り当て。

---

## アーキテクチャ・実装マップ

Clean Architecture（[architecture.md](./architecture.md)）に沿う。既存の Place 系実装を最大限再利用する。

| 要素                   | ファイル                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------------ |
| Entity                 | `data/local/entity/WishlistEntity.kt` ／ `PlaceWithWishlist.kt`（place＋wishlist の JOIN） |
| DAO                    | `data/local/dao/WishlistDao.kt` ／ `PlaceDao.getPlacesWithWishlist()`（一覧）              |
| マイグレーション       | `DatabaseMigrations.kt` の v4→v5（`wishlist` 作成）                                        |
| ドメインモデル         | `domain/model/PlaceListItem.kt`（一覧行）, `Priority.kt`                                   |
| リポジトリ             | `domain/repository/WishlistRepository.kt` ／ `data/repository/WishlistRepositoryImpl.kt`   |
| 場所同定（再利用）     | 既存 `findOrCreatePlace`（`PlaceRepository`）を共用                                        |
| ViewModel / 画面       | `presentation/places/PlacesViewModel.kt` ／ `PlacesScreen.kt`（一覧・追加・詳細）          |
| ナビ                   | `MainActivity` の `selectedTab` に「場所」を追加、`ic_place` ベクター追加                  |
| DI                     | `di/` に `WishlistRepository` のバインド追加                                               |
| キーワード検索(後段)   | `data/places/PlacesTextSearcher.kt`（Autocomplete＋fetchPlace）                            |
| 名前解決(後段・再利用) | 既存 `PlacesNameResolver.resolve`（座標のみ place の命名）                                 |

### リポジトリ・インターフェース

```kotlin
interface WishlistRepository {
  /** 全ての場所を、行きたい登録（あれば）付きでリアクティブに取得。 */
  fun getPlaces(): Flow<List<PlaceListItem>>

  /** 座標から場所を登録（地図タップ）。find-or-create(30m)＋未命名なら命名。行きたいはしない。 */
  suspend fun registerPlace(latitude: Double, longitude: Double, name: String?): Long

  /** その場所を行きたいに登録。既に有れば既存 id を返す（重複させない）。 */
  suspend fun addToWishlist(placeId: Long, priority: Priority, memo: String?): Long

  suspend fun updateWishlist(id: Long, priority: Priority, memo: String?)
  suspend fun setVisited(id: Long, visited: Boolean)
  /** 行きたいから外す（wishlist 行のみ削除。place は残す）。 */
  suspend fun removeFromWishlist(id: Long)
}
```

---

## 段階リリース

1. **DB＋ドメイン＋一覧（オフラインで完結）✅ 実装済**: `wishlist` テーブル・DAO・マイグレーション、`PlaceListItem`、
   「場所」タブ（全 places 一覧）、**地図タップ登録**（POI 名自動入力・タップ後にフォーム）、行きたいトグル・優先度/メモ/訪問トグル。**Google 不要で成立**。
2. **命名（手動）✅ 実装済**: 詳細画面で名前を手動編集（要望「場所名を手動で入力・変更したい」）。空にすると未命名に戻る。`renamePlace`→`placeDao.updateName`。
   - 補足（後段）: 座標のみの place を「場所を取得」（Nearby）で自動命名するボタンは未実装（既存 `PlacesNameResolver` を再利用予定）。
3. **キーワード検索登録 ✅ 実装済**: `PlacesTextSearcher`（Autocomplete＋fetchPlace・セッショントークン・オンライン限定）。「追加」＞「検索して追加」で店名検索 → 候補選択 → 名前/住所/座標/googlePlaceId 取得 → `registerSearchedPlace`（place＋解決ログ）＋任意で行きたい。**Cloud で「Places API (New)」有効化＋課金が前提**。
4. **立ち寄りから「また行きたい」**: 詳細画面に導線を追加。
5. **タグ（複数）**: `tags` / `wishlist_tags` を追加し、一覧にタグ絞り込みを追加。記録側 `stop_tags` と共有する横断機能として設計。
6. **場所から関連経路の一覧**: 場所の詳細から、その場所に立ち寄った経路（tracks）の一覧を表示して辿れるようにする。`stops`（placeId → trackId）を JOIN すれば実現できる。
7. **将来**: 営業時間、計画（お出掛け）への割り当て、Phase 3 の「計画」タブへ発展。

いずれの段階でも、Google が使えなくても（地図タップで）アプリは成立する。

---

## 未確定・要検討

- **タグの設計**: 初回スコープ外。実装時に `tags`（固定候補＋自由入力か）・`wishlist_tags`・記録側 `stop_tags` との共有範囲を検討する。
- **優先度の表現**: 3段階（高/中/低）で確定。UI は ★3つ or 色。
- **一覧のノイズ**: 全 places を出すため、立ち寄り検出の未命名の点が多数並ぶ可能性。実機で量を見て、必要なら「名前あり優先」等の見せ方を検討（現状は許容と判断）。
- **削除の範囲**: 立ち寄り記録（stops）のある場所は削除不可（記録を残す）。削除できるのは行きたい・座標だけの場所で、place と CASCADE 対象（wishlist・解決ログ）のみを消す。
- **タブ増設の影響**: 4 タブでの下部ナビのレイアウト（ラベル/アイコンの収まり）を実機で確認。
