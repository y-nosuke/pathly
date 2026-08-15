# ADR（アーキテクチャ決定記録）

設計上の**決定とその背景**を、1 決定 1 ファイルで残す。
「今どうなっているか」は `../designs/` の設計書に、「**なぜそう決めたか・どの案を捨てたか**」はここに置く。

## 使い方

- 設計上の分かれ道で 1 つ決めたら、[template.md](template.md) をコピーして `NNNN-短い題名.md` を作る。
- 番号は連番（`0001`, `0002`, …）。決定は**上書きしない**。あとで覆したら、新しい ADR を書いて
  古い ADR の Status を「Superseded by NNNN」にする（決定の変遷を消さず残す）。
- **軽く保つのがコツ**。迷ったら書かない。1 枚は短くてよい。
- 実装より**あとから書いてもよい**。設計書に経緯が溜まってきたら、ここへ移す（Date は決定した日、
  遡って書いたことを併記する）。

## 一覧

| #                                                   | 題名                                                                      | Status   |
| --------------------------------------------------- | ------------------------------------------------------------------------- | -------- |
| [0001](0001-place-data-separation.md)               | 場所データを Google 由来とユーザー入力に分離する（v7）                    | Accepted |
| [0002](0002-incremental-live-stop-detection.md)     | 記録中の立ち寄り検出を境界以降のインクリメンタルにする（削除の復活防止）  | Accepted |
| [0003](0003-track-name-and-favorite.md)             | 経路に名前・お気に入りを持たせ、立ち寄り件数は集計で出す（v8）            | Accepted |
| [0004](0004-capture-all-gps-fields-and-batch.md)    | GPS はバッチ全点を保存し、Location の付随情報も取りこぼさない（v9）       | Accepted |
| [0005](0005-place-source-and-lifecycle.md)          | 場所に由来（source）を持たせ、自動回収は検出由来のみにする（v10）         | Accepted |
| [0006](0006-place-identity-by-googleplaceid.md)     | ユーザー操作の場所同定を施設の同一性（googlePlaceId）優先にする           | Accepted |
| [0007](0007-reassign-stop-this-visit-only.md)       | 誤検知の立ち寄りは「この訪問だけ」正しい場所へ付け替える（Model B）       | Accepted |
| [0008](0008-registered-places-on-map.md)            | 登録済みの場所を地図に出し、紐付け・近接確認・Google 座標採用・起動時解決 | Accepted |
| [0009](0009-map-tap-behavior.md)                    | 地図タップの挙動を統一（表示 ON=そのまま新規／OFF=近接確認、半径 50m）    | Accepted |
| [0010](0010-non-modal-map-sheets.md)                | 地図の上に出すシートは非モーダル（自前）にする                            | Accepted |
| [0011](0011-place-coordinate-source.md)             | 場所の座標は Places の施設座標を使い、地図アイコンの位置には合わせない    | Accepted |
| [0012](0012-non-destructive-reanalysis.md)          | 再解析・再補正は破壊的にやり直さず、追加提案（非破壊・opt-in）にする      | Accepted |
| [0013](0013-separate-place-stop-wishlist-tables.md) | 場所・立ち寄り・行きたいを別テーブルに分け、places を静的に保つ           | Accepted |
| [0014](0014-place-naming-cost-policy.md)            | 自動命名は Places を使い、オンライン時・place 1 件 1 回に限る             | Accepted |
| [0015](0015-bake-track-distance.md)                 | 経路の総移動距離は確定時に焼き込み、一覧では点をロードしない（v11）       | Accepted |
| [0016](0016-allow-device-backup.md)                 | 端末の自動バックアップは既定のまま許容する                                | Accepted |
