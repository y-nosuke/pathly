# 0006. ユーザー操作の場所同定を施設の同一性（googlePlaceId）優先にする

- Status: Accepted
- Date: 2026-08-08

関連: 現状の設計は [../designs/places.md](../designs/places.md) の「同定」。[ADR-0005](0005-place-source-and-lifecycle.md) の「今後の方針 1」を実装したもの。

## Context（背景）

ユーザー操作（POI タップの場所登録・キーワード検索・手動立ち寄り追加）で place を確保するとき、
`findOrCreatePlace` は**座標 30m 同定**だけだった。これだと:

- **隣接する別の施設**（例: 日高屋のとなりの富士そば）が 30m 以内にあると**同じ place に相乗り**してしまう。
- 逆に、同じ POI を別の日に登録すると座標が少しずれて**別 place が増殖**する。

施設には安定した `googlePlaceId` があるので、座標より施設の同一性で同定した方が正確に分離・統合できる。

## Decision（決定）

- `PlaceRepository.findOrCreateByGooglePlaceId(googlePlaceId, lat, lon, source)` を用意し、
  **`googlePlaceId` が分かるユーザー操作は ID で同定**する（`google_places` の `googlePlaceId → placeId` を引く）。
  同じ ID があればその place を再利用（＝**登録済み**）、無ければ **Google の座標で**新規作成する。
- `googlePlaceId` が無い操作（空タップ・手入力・名前なし・オフライン等）は**座標同定にフォールバック**する。
- POI 由来なら `google_places`（名前・住所・カテゴリ・ID）を控える。既存に当たったら「**この場所は登録済みです**」と軽く通知する。

## Alternatives（検討した没案）

- **座標同定のみ（従来）** … 隣接店の相乗り・同一 POI の増殖が起きる → 却下。
- **名前の一致で同定** … 表記ゆれ・同名店で誤判定。ID の方が確実 → 却下。
- **常に新規（同定しない）** … 同じ POI の再登録が重複だらけになる → 却下。

## Consequences（結果・トレードオフ）

- 隣接する別施設は別 place に分離され、同一 POI の再訪・再登録は同じ place にまとまる。
- **`googlePlaceId` を持たない place**（手動登録・オフライン未解決）は依然として座標同定に頼るため、
  ID 無しどうしの近接・重複は別の仕組みで補う（登録済みマーカー表示・近接確認：[ADR-0008](0008-registered-places-on-map.md) /
  タップ挙動統一：[ADR-0009](0009-map-tap-behavior.md)）。
- Google 座標を採用するため、暫定 GPS 座標より地図表示・重複判定が正確になる。
