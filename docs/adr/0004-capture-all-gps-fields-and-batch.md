# 0004. GPS はバッチ全点を保存し、Location の付随情報も取りこぼさない（v9）

- Status: Accepted
- Date: 2026-08-07

関連: 現状の設計は [../designs/gps-capture.md](../designs/gps-capture.md)。

## Context（背景）

記録中の位置保存に 2 つの取りこぼしがあった。

1. **バッチの中間点が捨てられていた**。省電力のため `LocationRequest` はバッチを許容している
   （`setMaxUpdateDelayMillis`）が、`onLocationResult` で `locationResult.lastLocation`（最後の 1 点）
   しか保存していなかった。バッチが効くと 1 回のコールバックに複数点が届くため、最後以外が失われる。
   背景記録（画面 OFF・Doze）ほどバッチが効きやすく、このアプリの本命ケースで軌跡が間引かれていた。
2. **`Location` の付随情報を保存していなかった**。緯度・経度・高度・水平精度・速度・方位・時刻だけを保存し、
   鉛直/速度/方位の精度、`provider`、MSL 高度、`elapsedRealtimeNanos`、モック判定は捨てていた。
   これらは**記録時にしか取れない**（後からその瞬間の値を再取得できない）。

## Decision（決定）

- `onLocationResult` は `locationResult.locations`（古い順）を**全点保存**する。補正・立ち寄り検出は
  バッチ挿入後に 1 回だけ回す。UI・通知・件数は最後の点／点数で更新。
- `gps_points` に付随情報の列を追加し（**DB v9** `MIGRATION_8_9`）、提供されていれば（`has*` 判定）保存する。
  minSdk 34 なので API26/31/34 のアクセサはバージョンガード不要。
- 付随情報は**保存のみ**。ドメイン `GpsPoint` には載せない（距離・表示に使わないため。必要時に読み出す）。

## Alternatives（検討した没案）

- **バッチをやめる（`setMaxUpdateDelayMillis` を外す）** … 取りこぼしは消えるが省電力を捨てる。
  全点保存すればバッチの利点（省電力）と情報量（間引かない）を両立できる → 却下。
- **付随情報を JSON 1 列にまとめて保存** … 追加は楽だが型付きクエリ・集計ができず、Room スキーマ検証の
  恩恵も失う。列で持つのが素直（数も多くない） → 却下。
- **`GpsPoint` ドメインにも全フィールドを足す** … 変換層・テストの波及が大きい割に今は使い道が無い。
  保存を先行し、利用時に読み出す方が軽い → 却下（保存だけ先に確実にやる）。
- **`extras`（衛星数など）も保存** … FusedLocationProvider では信頼できる形で載らず、意味が薄い → 却下。

## Consequences（結果・トレードオフ）

- 背景記録でも中間点を取りこぼさず、軌跡が間引かれない。バッチ挿入は 1 トランザクション相当で効率も良い。
- その瞬間の精度内訳・MSL 高度・provider・単調時刻・モック判定が後から参照できる（今すぐ使わなくても失わない）。
- 追加列は既存行で NULL / 0。`elapsedRealtimeNanos`・`isMock` は `NOT NULL DEFAULT 0`（バックフィル用）で、
  エンティティ側に既定を宣言しないため Room の起動時検証は DB の DEFAULT を無視する（[0003](0003-track-name-and-favorite.md) と同じ扱い）。
