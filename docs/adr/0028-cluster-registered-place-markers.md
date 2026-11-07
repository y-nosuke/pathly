# 0028. 登録済みの場所のマーカーは自前の計算でまとめ、地図ライブラリの Clustering は使わない

- Status: Accepted
- Date: 2026-11-07

関連: 登録済みの場所を地図に出す判断は [0008](0008-registered-places-on-map.md)、
マーカーの形は [0018](0018-marker-shape-conveys-certainty.md)。

## Context（背景）

登録済みの場所が増えると、引いた地図でマーカーが重なって読めない。マーカーに名前を添える案（#119）
を入れると、さらに混み合う。近いマーカーを 1 つにまとめ、寄ると広がるようにしたい（#120）。

まとめるときに崩したくないものが 2 つあった。

- **吹き出しをバックで閉じる仕組み。** マーカーは `MapMarker` で置き、開いた吹き出しを
  `MapInfoWindowState` が `MarkerState` で覚えている（[designs/ui.md](../designs/ui.md)）。
- **タップの意味がモードで変わること。** 通常モード＝編集、立ち寄りの追加モード＝紐付け
  （[specs/map.md](../specs/map.md)）。

## Decision（決定）

**まとめる計算を自前で持つ**（`presentation/common/PlaceClustering.kt` の `clusterPlaces`）。

- 場所を Web メルカトルの画面座標（dp）に投影し、ID の順に起点を取って、起点から一定距離以内の残りを
  まとめる（貪欲法）。ズームは 0.5 段に丸めてから使う。
- 1 件ずつに戻ったものはこれまでどおり `MapMarker` で、まとまりは `MarkerComposable` で置く。
  どちらも普通の Compose のマーカーなので、地図の他のマーカーと同じ仕組みで動く。

## Alternatives（検討した没案）

- **maps-compose-utils の `Clustering`** … Google 公式で、まとまりの分かれるアニメーションもある。
  → **却下**。マーカーを `ClusterManager` が自前で作るので `MarkerState` に手が届かず、
  吹き出しをバックで閉じる仕組み（`MapInfoWindowState`）をまるごと作り直すことになる。
  `ClusterManager` が地図のクリックの受け口を差し替えるのを、別の受け口へ戻す回避
  （ライブラリ自身のコメントで "This is a hack"）にも乗る。API は `@MapsComposeExperimentalApi` で、
  依存も 2 つ（maps-compose-utils と android-maps-utils）増える。
- **まとめず、寄ったときだけ出す・間引く** … 引いた地図では場所があること自体が見えなくなる。→ **却下**。

## Consequences（結果・トレードオフ）

- 既存のマーカーの仕組み（吹き出し・タップの意味・`key()` で 1 件ずつ独立させる約束）がそのまま使える。
- 計算が純粋な関数なので、ユニットテストで確かめられる。
- まとまりが分かれる・寄り集まるアニメーションは無い。ズームの段が変わった時点で描き直す。
- 計算は O(n²)。場所が数千件になって重くなったら、格子で近いものだけ比べる形に変える。
- ズームを 0.5 段に丸めるので、画面上でまとめる距離は 44dp の 1〜1.4 倍で揺れる。
