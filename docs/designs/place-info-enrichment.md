# 場所情報の拡充とデータ分離（v7）

「場所」を名前だけでなく**カテゴリ・メモ・Googleマップ連携**で分かるようにし、
そのために **Google 由来の情報**と**ユーザー入力**を別テーブルに分ける v7 の構成。

- 決定の背景・没案は [ADR-0001](../adr/0001-place-data-separation.md)。
- 前提: 場所・立ち寄りの永続化と命名は [places-and-stops.md](./places-and-stops.md)、
  行きたい・「場所」タブは [wishlist.md](./wishlist.md)。
- データモデルは [../specs/model.md](../specs/model.md)。

---

## 足すもの

1. **カテゴリ（業種）** … 「ラーメン店」「公園」「美術館」を一目で。Google Places の
   `primaryTypeDisplayName` を取得（命名で既に取得しているフィールドと同じ Pro ティア＝追加課金ほぼ無し）。
2. **メモ** … 「行きたい」に登録しなくても場所に一言残せる。
3. **Google マップ連携** … 詳細から Google アプリ／Web を開き、写真・口コミ・営業時間・経路案内を委ねる。

---

## 目標データモデル（v7）

具体値（型・制約）の正は実コード（Room エンティティ）と `MIGRATION_6_7`。ここは構成を示す。

### places（ユーザー入力＋座標）

| カラム                                            | 説明                                                     | v7 での変更  |
| ------------------------------------------------- | -------------------------------------------------------- | ------------ |
| id / latitude / longitude / createdAt / updatedAt | 場所そのもの                                             | 据え置き     |
| `name?`                                           | **自分で付けた名前**（null=未命名）。Google 名は入れない | 意味を再解釈 |
| `note?`                                           | **場所のメモ**（「行きたい」と独立に常に持てる）         | 追加         |
| ~~address~~                                       | `google_places` へ移動                                   | 削除         |

### place_resolutions（問い合わせログ）

| カラム                   | 説明                                           | v7 での変更 |
| ------------------------ | ---------------------------------------------- | ----------- |
| placeId(PK) / resolvedAt | 叩いた事実と日時。**行がある＝問い合わせ済み** | 据え置き    |
| ~~googlePlaceId~~        | `google_places` へ移動                         | 削除        |

### google_places（Google 由来データ・新設）

| カラム                         | 説明                                                                  |
| ------------------------------ | --------------------------------------------------------------------- |
| placeId(PK, FK→places CASCADE) | 対象の場所（1 行/place）                                              |
| `googlePlaceId`(NOT NULL)      | **行がある＝マッチした**ので非 null。Place Details の参照キーにもなる |
| `name?`                        | Google の施設名                                                       |
| `address?`                     | Google の住所（formattedAddress）                                     |
| `category?`                    | Google のカテゴリ（primaryTypeDisplayName）                           |

### wishlist（計画フラグ）

| カラム                                                      | 説明                                | v7 での変更 |
| ----------------------------------------------------------- | ----------------------------------- | ----------- |
| id / placeId / priority / visitedAt / createdAt / updatedAt | 行きたいの計画情報                  | 据え置き    |
| ~~memo~~                                                    | 場所のメモは `places.note` に一本化 | 削除        |

問い合わせと結果を分けたことで、解決状態は次の 3 つで表せる:

| 状態                     | place_resolutions  | google_places |
| ------------------------ | ------------------ | ------------- |
| 未問い合わせ             | なし               | なし          |
| 問い合わせ済み・該当なし | あり（resolvedAt） | なし          |
| 問い合わせ済み・該当あり | あり               | あり          |

---

## 表示名のフォールバック

分離後、自動命名は `google_places.name` に入り `places.name` は null になりうる。
場所名を出す**すべての読み取り**を次の優先で解決する:

```
表示名 = places.name（自分の名前）
       ?: google_places.name（Google の名前）
       ?: google_places.address（住所）
       ?: 座標
```

- 一覧・詳細（`PlaceDao` の射影）に加え、**履歴の立ち寄り表示・関連経路一覧**（`StopDao` の射影）も
  `google_places` を LEFT JOIN して同じフォールバックを適用する。
- カテゴリ・住所も同じ JOIN で取り、一覧カード／詳細／場所シートに出す。
- フォールバックの計算は 1 か所（表示名ヘルパ）に集約し、各射影から使う。

---

## マイグレーション（v6 → v7）

minSdk 34（SQLite 3.35+）で `ALTER TABLE ... DROP COLUMN` が使えるため、`places` 再作成を避けて完結する。
**列を落とす前に中身を移送する**順序で行う:

1. `places` に `note` を追加し、`wishlist.memo` を `places.note` へ移送。
2. `google_places` を作り、**解決済み**（`place_resolutions.googlePlaceId` 非 null）を対象に
   その `googlePlaceId` と `places.address` を移送。
3. 不要列を落とす: `places.address` / `wishlist.memo` / `place_resolutions.googlePlaceId`。

- **既存 `places.name` はそのまま残す**（＝ユーザー名扱い）。`google_places.name`/`category` は null 始まりで
  以後の再解決で埋まるため、**移行直後の見た目は変わらない**（→ 方針の理由は [ADR-0001](../adr/0001-place-data-separation.md)）。
- DDL の実体は `DatabaseMigrations.MIGRATION_6_7`。`exportSchema` の `7.json` と `MigrationTest` の 6→7 で照合する。

---

## Google マップ連携

詳細画面に「Google マップで開く」を置き、Intent で Google アプリ／Web に切り替える（API 呼び出しではない＝課金ゼロ）。

- `google_places.googlePlaceId` あり → その施設のページを開く（写真・口コミ・営業時間つき）。
- なし（手動追加の座標だけ） → `geo:` フォールバック。

---

## 登録 UI の統一

地図タップ（記録・経路詳細・場所詳細）／地図で選ぶ／検索して追加は、いずれも
**全画面の地図＋下部の場所シート**（`PlaceActionSheet`）で出す。入力フォームは
`PlaceFormBody`（名前／行きたい／優先度／メモ）を共有する。

- シートは非モーダル（`FloatingSheet`）。畳めば地図を全画面で確認できる → [ADR-0010](../adr/0010-non-modal-map-sheets.md)
- UI: メモ欄＝常時表示（場所の説明）／優先度＝行きたい ON 時だけ（行きたい度）。

### 名前の入力

名前欄は **`places.name`（自分で付けた名前）専用**。Google 由来の名前は**初期値に入れない**ので、
欄が埋まっているかどうかが、そのまま「自分で名前を付けたか、Google の名前で表示されているか」を表す。

Google の名前は**シートの見出し**に出す（名前欄のプレースホルダには入れない）。プレースホルダだと
名前に関する場所が縦に 2 つ並んで場所を食ううえ、どちらが表示名なのか分かりにくかったため、
「いま何という名前で表示されるか」は見出し 1 か所に集約した。

- 施設名を少し変えたいときのために、欄が空のときだけ名前欄の下に「この名前から書き換える」を出して
  見出しの名前を流し込めるようにする（打ち直しを強いない）。
- 書き換えずに確定した場合は Google の名前と同じなので、ユーザー名としては保存しない。
- **名前を変えても `googlePlaceId` は外さない**。列が分かれているので「スタバだけど自分は休憩と
  呼ぶ」がそのまま表現でき、カテゴリ・住所も残る。施設ごと取り違えたときは
  「場所を選び直す」で付け替える。
- 名前を空にして保存すると `places.name` は null に戻り、表示は Google 名へフォールバックする。

---

## 座標の出どころ

場所の座標には出どころが 2 つあり、**同じ値とは限らない**。

| 呼び名             | 実体                                                 | 使う経路                                                                              |
| ------------------ | ---------------------------------------------------- | ------------------------------------------------------------------------------------- |
| **施設の座標**     | `PlaceSearchResult.latitude/longitude`（Places API） | 自動命名（`resolvePlace`）／Google施設を選び直す（`linkPlaceToGoogle`）／検索して追加 |
| **アイコンの座標** | `PointOfInterest.latLng`（地図SDK）                  | 地図の POI タップからの登録                                                           |

**正としているのは施設の座標**。敷地のある施設（神社・寺・公園など）ではアイコンの位置と数十 m
離れることがあり、地図上でピンがアイコンからずれて見えるが、これは仕様として扱う → [ADR-0011](../adr/0011-place-coordinate-source.md)。

「Google施設を選び直す」でアイコンの位置に合わせることはできない。あのダイアログの候補は Places API
から取るため、アイコンの座標が値として存在しない。

---

## 既知の限界

- **既存の解決済み場所は、自動ではカテゴリが埋まらない**（自動命名は place 1 件 1 回なので対象外）。
  手で埋めるなら詳細の「**Google施設を選び直す**」で同じ施設を選び直す（`linkPlaceToGoogle` が
  名前・住所・カテゴリを上書きする）。ただしそのとき**座標も施設の座標へ置き換わる**ので、
  地図上のピンが動いて見えることがある → [ADR-0011](../adr/0011-place-coordinate-source.md)。
- `google_places.googlePlaceId` は NOT NULL（行がある＝マッチ）。「叩いたが該当なし」は
  `place_resolutions` に行だけ残す。
- 削除・取り消し（Undo）の実体スナップショットは `google_places` の行も含める。

---

## 段階リリース

破壊的リファクタのため、**土台を 1 本通してから**機能を積む:

- **A. スキーマ／データ層（振る舞い不変）** … v7 マイグレーション、エンティティ、
  表示名フォールバック（`google_places` JOIN）、書き込み経路の付け替え、`MigrationTest`。
- **B. カテゴリ取得・表示** … 取得フィールドに `primaryTypeDisplayName` を追加し `google_places` に保存、
  一覧・詳細・POI ダイアログにカテゴリ表示。
- **C. メモ全場所化＋フォーム統一** … `places.note` の編集、共有フォーム抽出、wishlist からメモ経路を撤去。
- **D. Google マップ連携** … 詳細に起動ボタン（スキーマ非依存の純追加）。

いずれの段階でも、Google が使えなくてもアプリは動く（座標表示で成立）。

---

## 実装マップ

| 要素             | ファイル                                                                                                                |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Entity           | `data/local/entity/PlaceEntity.kt`, `WishlistEntity.kt`, `PlaceResolutionEntity.kt`, `GooglePlaceEntity.kt`（新）       |
| DAO              | `data/local/dao/PlaceDao.kt`, `StopDao.kt`, `PlaceResolutionDao.kt`, `GooglePlaceDao.kt`（新）                          |
| マイグレーション | `data/local/migration/DatabaseMigrations.kt`（`MIGRATION_6_7`）, `PathlyDatabase.kt`（version=7）                       |
| 読み取り射影     | `PlaceWithWishlist.kt`, `StopWithPlace.kt`, `PlaceVisitRow.kt`（`google_places` JOIN・表示名）                          |
| リポジトリ       | `data/repository/PlaceRepositoryImpl.kt`, `WishlistRepositoryImpl.kt`（Google データの書き込み先変更・memo→note）       |
| ドメイン         | `domain/model/Place.kt`, `PlaceListItem.kt`（address 除去・note・表示名）                                               |
| Places 呼び出し  | `data/places/PlacesNameResolver.kt`, `PlacesTextSearcher.kt`（category フィールド追加）                                 |
| UseCase          | `domain/usecase/PlaceEditUseCase.kt`（登録・近接確認・紐付け・編集の差分適用を3画面で共有）                             |
| 画面             | `presentation/places/PlacesScreen.kt`（`PlaceFormBody`）, `PlaceActionSheet.kt`, `presentation/common/FloatingSheet.kt` |

> 実装時に [../specs/model.md](../specs/model.md)（概念モデル・ER）も v7 の構成へ更新する。
