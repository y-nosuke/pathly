# 場所の設計

**何が起きるかは [../specs/places.md](../specs/places.md) を正とする。**ここには作り方だけ書く。

場所は経路から独立した共有エンティティで、立ち寄り（`stops`）・行きたい（`wishlist`）・訪問済みの印（`visited_places`）が参照する。
テーブルを分けた判断は [ADR-0013](../adr/0013-separate-place-stop-wishlist-tables.md)、
Google 由来データを別テーブルにした判断は [ADR-0001](../adr/0001-place-data-separation.md)。

---

## データモデルの意図

**列の定義は [../specs/model.md](../specs/model.md) と Room エンティティを正とする。**意図だけ書く。

- **`stops` の外部キーは非対称**。`trackId → gps_tracks` は CASCADE（経路を消せば訪問も消える）だが、
  `placeId → places` は CASCADE にしない。**訪問が消えても場所は残す**。
- **`place_resolutions` は「行の有無」が意味を持つ**ログ。主役は `resolvedAt`（叩いた事実）で、
  結果そのものは `google_places` に入る。
- `wishlist(placeId)` は **UNIQUE**（重複登録の防止と find-or-add に使う）。`places` に従属し CASCADE。
- **`visited_places` も「行の有無」が意味を持つ**（行の存在＝手動で訪問済みにした）。付ける＝挿入・外す＝削除だけで
  行を書き換えない。`markedAt` は**印を付けた日時**で、訪れた日時ではない（→ [ADR-0020](../adr/0020-visited-independent-from-wishlist.md)）。
  **行きたいとは独立**なので、行きたいを外しても印は残る。二度付けても最初の日時を残す（挿入は IGNORE）。
- **メモ（`stops.note`）は訪問単位、名前（`places.name`）は場所単位。** 空文字は保存せず null にそろえる。
- **業種は `google_place_categories` に正規化**し、`google_places.categoryId` から参照する
  （→ [ADR-0017](../adr/0017-normalize-place-category.md)）。正は機械可読な `code`（Places の
  `primaryType`＝`cafe`）で、`displayName`（「カフェ」）は表示専用。**判定に表示名を使わない**
  （ロケールで変わる）。行は事前にシードせず、応答で出会った業種だけ都度 upsert して育てる。
- 近傍検索が全表走査にならないよう `places(latitude, longitude)` に索引を張る。記録中は位置のバッチごとに
  引かれるため、場所が増えるほど効く。

## ドメインモデル

- `Place` … 座標＋自分で付けた名前・メモに加え、`google_places` から JOIN した施設名・住所・カテゴリ。
- `PlaceCategory` … 業種。`code`（判定に使う）と `displayName`（表示に使う）の対。業種が取れなければ
  `Place.category` ごと null で、**表示名だけ持つ状態は作らない**。
- `PlaceListItem` … 一覧の 1 行。`Place` ＋ 行きたい登録（あれば）＋ 訪問済みの印（あれば）＋ 立ち寄り件数。
  **行きたい登録が無い場所も同じ型で並ぶ**（`wishlistId` が null）。訪問済みは
  `visitCount > 0 || markedVisitedAt != null` で、行きたいの有無を見ない。

---

## 同定（重複させない）

| 入力                     | 使う手                                                                                                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `googlePlaceId` が分かる | `findOrCreateByGooglePlaceId` … `google_places` の `googlePlaceId → placeId` を引く。無ければ **Google の座標で**新規（→ [ADR-0006](../adr/0006-place-identity-by-googleplaceid.md)） |
| 座標しか無い             | `findOrCreatePlace` … 同一場所とみなす距離の中の既存を再利用                                                                                                                          |
| 近接確認で「新規」       | `forceNewPlace = true` で座標同定をバイパス                                                                                                                                           |

`findOrCreateByGooglePlaceId` は**既存が見つかったら座標も含め何も書き換えずに返す**。
POI タップで登録済みだったときに座標が動かないのはこのため（→ [ADR-0011](../adr/0011-place-coordinate-source.md)）。

> **既知の不具合（→ [ADR-0023](../adr/0023-place-identity-and-coordinate-anchor.md)・未実装）**
>
> 座標同定は `places` の座標を鍵にしているのに、自動命名がその座標を施設の座標へ**上書きする**
> （下記「命名」）。施設の代表点が重心から 30m を超えて離れると、その place は自分を作った重心から
> **二度と見つからなくなり**、確保のたびに新しい place ができる。記録中は位置バッチごとに
> `findOrCreatePlace` を呼ぶため、条件がそろうと 1 回の滞在で数百件に増える。
>
> 直す方向は ADR-0023 のとおり: `places` の座標を**アンカー（作成時に確定・以後不変）**にし、
> Google の座標は `google_places` に持たせる。同定はアンカー →（解決後に）`googlePlaceId` の
> 2 段にし、滞在中の place 確保は 1 回にする。

## 由来（source）と自動回収

`places.source`（`DETECTED` / `USER`）を持つ（→ [ADR-0005](../adr/0005-place-source-and-lifecycle.md)）。

- `findOrCreatePlace(lat, lon, source)` は新規作成時に由来を刻む。
- 既存を再利用するとき、由来が `DETECTED` でも呼び出しが `USER` なら **`USER` に昇格**する
  （ユーザーが触った場所を自動回収から守る。**降格はしない**）。
- 自動回収の対象は `DETECTED` だけ（[stops.md](stops.md)）。

---

## 命名（Places API）

Web API の直叩きではなく **Places SDK for Android（New）** を使う。Android アプリ制限付きの
API キー（地図と共用）を**そのまま安全に**使えるため。

- 初期化は **New API 面を有効にして行う**（`initializeWithNewPlacesApiEnabled`）。旧 `Places.initialize(...)`
  では `searchNearby` が使えない。Cloud 側は「Places API (New)」の有効化＋請求先リンクが前提。
- 呼び出しは **Nearby Search で最も近い 1 件**。半径は立ち寄りの検出半径と揃える。
  リクエストの組み立てとフィールド指定は `data/places/PlacesNameResolver` を正とする。
- **ラッパー 1 か所に閉じ込める。** 呼び出し前に `ConnectivityManager` でオンライン判定し、
  オフラインなら叩かない。**0 件と例外を区別する**のが要点で、0 件＝「叩いたが POI 無し」
  （`resolvedAt` の行を残す）、例外＝「未実施」（行を作らない）。
- 最寄り 1 件は隣の別施設に化けやすいので、**ユーザーが選ぶ場面では候補を複数返す**
  （`searchNearbyCandidates` / `nearbyPois`）。自動命名だけが 1 件で妥協している。
- キーワード検索は別系統（`data/places/PlacesTextSearcher`）。Autocomplete は**セッショントークン**で
  まとめ、確定した 1 件だけ `fetchPlace` する。取得済みの情報は `knownDetails` として登録時に渡し、
  **Google を引き直さない**。

### 「一度だけ」の管理

自動命名の対象は `place_resolutions` に**行が無い** place（→ [ADR-0014](../adr/0014-place-naming-cost-policy.md)）。

| 状況            | 記録        | 自動再取得   | 手動再取得 |
| --------------- | ----------- | ------------ | ---------- |
| POI発見         | 行・ID あり | しない       | しない     |
| POI無し         | 行・ID null | しない       | できる     |
| オフライン/失敗 | 行なし      | する(復帰時) | できる     |

未解決のまま残った place は `data/work/PlaceNameCatchUpWorker`（WorkManager・**ネットワーク接続を制約**）が
まとめて拾う。起動時に予約するだけなので、いま圏外でも通信が戻った時点で OS が走らせる。

解決できたら place の座標を**施設の座標へ置き換える**。手動の紐付け（`linkPlaceToGoogle`）も同様に
`google_places`・`place_resolutions` を上書きし座標を採用する（`places.name` は触らない）。

> **この上書きが重複登録の原因**（→ [ADR-0023](../adr/0023-place-identity-and-coordinate-anchor.md)・未実装）。
> 名前は v7 で「ユーザーの名前は `places`・Google の名前は `google_places`」に分離したのに
> （→ [ADR-0001](../adr/0001-place-data-separation.md)）、**座標だけがその分離から取り残されている**。
> 実装後は `places` の座標を書き換えず `google_places` 側に座標を持たせ、表示は
> `COALESCE(google_places.latitude, places.latitude)` で解決する。表示が施設の座標になる
> （→ [ADR-0011](../adr/0011-place-coordinate-source.md)）点は変わらない。

## 表示名の解決

`places.name → google_places.name → 住所 → 座標` のフォールバックを、**すべての読み取りで揃える**。

- 一覧・詳細（`PlaceDao` の射影）に加え、履歴の立ち寄り表示・関連経路一覧（`StopDao` の射影）も
  `google_places` を LEFT JOIN する。住所も同じ JOIN で取る。カテゴリはさらに
  `google_place_categories` を LEFT JOIN して `code` / `displayName` を引く
  （`@Relation` 側は `GooglePlaceWithCategory` が担う）。
- 計算は 1 か所（表示名ヘルパ）に集約し、各射影から使う。
- 地図のマーカー用には最小情報だけ返す射影を別に持つ（`observeRegisteredPlaces`・`NamedPlaceRow`）。
  以前は「全 place を読む → 近いものを探す → place ごとに `google_places` を引く」で N+1 になっていた。

## Google マップで開く

Intent で Google アプリ／Web に切り替える（API 呼び出しではない＝課金ゼロ）。
`googlePlaceId` があればその施設のページ、無ければ `geo:` で座標を開く。

---

## 実装マップ

| 要素            | ファイル                                                                                                    |
| --------------- | ----------------------------------------------------------------------------------------------------------- |
| ドメイン        | `domain/model/Place.kt`, `PlaceListItem.kt`, `Priority.kt`, `PlaceRegistration.kt`, `PlaceSearchResult.kt`  |
| Entity          | `data/local/entity/PlaceEntity.kt`, `GooglePlaceEntity.kt`, `PlaceResolutionEntity.kt`, `WishlistEntity.kt` |
| 射影            | `PlaceWithWishlist.kt`, `NamedPlaceRow.kt`, `PlaceVisitRow.kt`                                              |
| DAO             | `data/local/dao/PlaceDao.kt`, `GooglePlaceDao.kt`, `PlaceResolutionDao.kt`, `WishlistDao.kt`                |
| Places 呼び出し | `data/places/PlacesNameResolver.kt`（Nearby）, `PlacesTextSearcher.kt`（Autocomplete＋fetchPlace）          |
| キャッチアップ  | `data/work/PlaceNameCatchUpWorker.kt`                                                                       |
| UseCase         | `domain/usecase/PlaceEditUseCase.kt`（登録・近接確認・紐付け・編集の差分適用を 3 画面で共有）               |
| リポジトリ      | `domain/repository/PlaceRepository.kt` / `WishlistRepository.kt` と各実装                                   |
| 画面            | `presentation/places/`（`PlacesScreen` / `PlaceActionSheet` / `PlacesViewModel` / `PlacesState`）           |
