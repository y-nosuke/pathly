# 0001. 場所データを Google 由来とユーザー入力に分離する（v7）

- Status: Accepted
- Date: 2026-08-05

関連: 設計は [../designs/place-info-enrichment.md](../designs/place-info-enrichment.md)、
既存の前提は [../designs/places-and-stops.md](../designs/places-and-stops.md) / [../designs/wishlist.md](../designs/wishlist.md)。

## Context（背景）

マップ上の施設をタップしても名前しか出ず、行きたいか判断できない。場所一覧も名前だけで、
後で見返してもどんな場所か思い出せない。根本原因は、`places` が名前・住所しか持たず、
地図タップの `PointOfInterest` も名前・座標・placeId しか返さないこと。

情報を足すにあたり、次の 2 つを見据える必要があった:

- 名前は**基本 Google の施設名を使い、ときどき自分で付けた名前で上書き**したい。
- カテゴリは、いずれ**自分でカテゴリ（タグ）を付ける**機能も足したい。

つまり「Google が返したもの」と「ユーザーが決めたもの」が混ざると、上書きの区別や将来の
ユーザータグとの整理で破綻する。

## Decision（決定）

`places` は「その点＋ユーザーが書いたもの」だけを持ち、Google が返したものは専用テーブルへ寄せる。
v7 で以下に再編する（テーブルの詳細は設計書）:

- **名前を分離** … `places.name` は**ユーザーが付けた名前だけ**。Google の施設名は `google_places.name`。
  表示は `places.name ?: google_places.name ?: google_places.address ?: 座標` のフォールバック。
- **問い合わせログとデータを分離** … `place_resolutions` は**叩いた事実のログに専念**（`placeId` / `resolvedAt`）、
  Google が返したデータ（`googlePlaceId` / `name` / `address` / `category`）は新設の `google_places` に。
- **カテゴリを保存** … `primaryTypeDisplayName` を `google_places.category` に。命名で既に取得している
  フィールドと**同じ Pro ティア**なので追加課金はほぼ無い（新 API を足すのではなく取得フィールドを増やす）。
- **メモを一本化** … `wishlist.memo` を廃止し `places.note` に。「行きたい」登録の有無と無関係にメモを持てる。
- **移行方針 A** … 既存 `places.name` はそのまま（＝ユーザー名扱い）残し、`google_places.name`/`category` は
  null 始まりで以後の再解決で埋める。移行直後の見た目は変えない。

## Alternatives（検討した没案）

- **カテゴリ/名前を `places` に列追加** … 手っ取り早いが、Google 由来とユーザー入力が混ざり、
  将来のユーザータグ分離時にこそ本物のリファクタが要る → 却下。**今分けるほうが将来の手戻りを防ぐ**。
- **`place_resolutions` にデータ列を足すだけ（分割しない）** … テーブルの役割が「ログ」から「データの器」に
  広がり、「叩いたが該当なし」のログ性が薄れる。データ側を独立させれば `google_places` と明快に名乗れて
  ログのニュアンスも残る → **分割を採用**。
- **メモを 2 段持ち**（`places.note`＝場所の説明／`wishlist.memo`＝行きたい理由） … 理屈は通るが、
  一人用アプリでメモ欄 2 つはどちらに書くか迷うだけ → 一本化。
- **移行方針 B**（解決済みは `places.name` を `google_places.name` へ寄せる） … 意味に忠実だが、
  ユーザーが改名した Google 場所を誤って Google 名にし、再解決で上書きする risk → データ僅少の今は安全側の A。
- **写真をアプリ内に持つ（Place Photo）** … 取得課金・キャッシュ設計が重く、写真機能自体が将来フェーズ。
  代わりに詳細から **Google マップアプリを開く**導線（Intent・課金ゼロ）で写真・口コミ・営業時間を委ねる → 却下。

## Consequences（結果・トレードオフ）

- **表示名フォールバックの波及**: 場所名を出すすべての読み取り射影（一覧・詳細だけでなく履歴の立ち寄り表示も）が
  `google_places` を JOIN してフォールバックを計算する必要がある。これが本リファクタの波及の本体。
- **既存の解決済み場所はカテゴリが埋まらない**（自動命名は place 1 件 1 回で対象外）。必要なら
  「解決済みも category だけ取り直す」導線を別途足す。
- テーブルが 1 つ増え、解決成功時に 2 行書く（ログ＋データ）。一方で読み書きの経路はきれいに分かれる。
- 将来のユーザータグ（多対多）を、Google 由来（1:1）と衝突せずに足せる土台になる。
- 破壊的リファクタのため、振る舞い不変の土台（スキーマ＋データ層）を先に 1 本通してから機能を積む。
