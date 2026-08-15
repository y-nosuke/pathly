# 0007. 誤検知の立ち寄りは「この訪問だけ」正しい場所へ付け替える（Model B）

- Status: Accepted
- Date: 2026-08-08

関連: 現状の設計は [../designs/stops.md](../designs/stops.md) の「手で足す・直す」。[ADR-0005](0005-place-source-and-lifecycle.md) の「今後の方針 3」を実装したもの。

## Context（背景）

自動検出（`StopDetector`）は近くの別施設に化けることがある（最寄り 1 件命名の限界）。確定済みの立ち寄りを
**正しい場所に直したい**。`places` は経路から独立した**共有エンティティ**で、複数の訪問が同じ place を参照し得る。

## Decision（決定）

- **この訪問（stop）だけ** place を張り替える `reassignStopPlace(stopId, chosen, customName)` を用意する。
  - `chosen`（近くの POI 候補）があれば施設同定（[ADR-0006](0006-place-identity-by-googleplaceid.md)）で place を確保してそこへ。
  - `customName`（手入力）なら新しい USER place を作ってそこへ。
- 付け替えで**参照が無くなった元の place が `DETECTED`（誤検知の使い捨て）なら回収**する（[ADR-0005](0005-place-source-and-lifecycle.md) の安全な GC）。
- 記録画面・履歴詳細で**共通ダイアログ**（近くの候補から選ぶ／自分で入力）を使う。

## Alternatives（検討した没案）

- **Model A: 共有 place の名前を書き換える** … 同じ place を参照する**他の経路・訪問にも波及**してしまう
  （「この訪問だけ直したい」の意図に反する）→ 却下。
- **place の座標を動かして合わせる** … 他の参照先の地図位置まで動く。副作用が広い → 却下。
- **消して作り直す** … 立ち寄りの id・履歴が切れる。張り替えの方が安全 → 却下。

## Consequences（結果・トレードオフ）

- 訪問単位で安全に訂正でき、他の履歴を巻き込まない。
- 同じ実世界の場所を**複数の place が指す**ことは起こり得る（張り替えで分岐する）。これは安全側の割り切りで、
  重複の解消は登録済みマーカー・近接確認・「Google で情報を取得」で行う（[ADR-0008](0008-registered-places-on-map.md)）。
- 誤検知の使い捨て place はその場で回収され、場所タブが汚れにくい。
