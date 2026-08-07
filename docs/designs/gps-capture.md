# GPS 点の取り込み（保存する情報とバッチの扱い）

記録中に `LocationTrackingService` が受け取った位置を `gps_points` に保存する部分の設計。
「記録時にしか取れない情報を取り逃さない」ことを方針にする。決定の背景は
[../adr/0004-capture-all-gps-fields-and-batch.md](../adr/0004-capture-all-gps-fields-and-batch.md)。

## バッチは全点保存する

`LocationRequest` は省電力のためバッチを許容している（`setMaxUpdateDelayMillis`）。
バッチが効くと 1 回の `onLocationResult` に**複数の位置がまとまって**届く
（`LocationResult.locations`・古い順）。`lastLocation`（最後の 1 点）だけを保存すると
中間点が失われるため、**`locations` を全部保存する**。

- 補正（`updateSmoothedForTrack`）と立ち寄り検出（`updateStopsForTrack`）は、
  バッチ挿入後に**1 回だけ**回す（点ごとに回す必要はない）。
- `_currentLocation`・通知はバッチの**最後の点**で更新。件数は受け取った**点数分**進める。

## 保存するフィールド（`gps_points`・v9）

`Location` が提供する値のうち、後から再取得できないものは提供されていれば保存する（minSdk 34 なので
`has*` で判定できるものはバージョンガード不要）。

| 列                             | 由来 / 判定                                |
| ------------------------------ | ------------------------------------------ |
| `latitude` / `longitude`       | 常時                                       |
| `altitude`                     | `hasAltitude()`                            |
| `accuracy`                     | 水平精度（常時）                           |
| `speed`                        | `hasSpeed()`                               |
| `bearing`                      | `hasBearing()`                             |
| `provider`                     | `provider`（gps / network / fused など）   |
| `verticalAccuracyMeters`       | `hasVerticalAccuracy()`                    |
| `speedAccuracyMetersPerSecond` | `hasSpeedAccuracy()`                       |
| `bearingAccuracyDegrees`       | `hasBearingAccuracy()`                     |
| `mslAltitudeMeters`            | `hasMslAltitude()`（API34+）               |
| `mslAltitudeAccuracyMeters`    | `hasMslAltitudeAccuracy()`（API34+）       |
| `elapsedRealtimeNanos`         | 端末起動からの単調時刻（壁時計ズレに強い） |
| `isMock`                       | モック位置の識別                           |
| `extrasJson`                   | `extras` を JSON 文字列化                  |

`extras`（Bundle）は provider 依存で中身が不透明だが、「記録時にしか取れない」ため**丸ごと保存する**。
方式は**テキスト（JSON 文字列）**：スカラー/文字列はそのまま、それ以外は `toString()` で文字列化する。
生バイナリ（`Parcel.marshall()`）では保存しない — その形式は将来の Android で読めなくなり得るため、
かえって「あとで取れない」を招く。空/無し・直列化失敗なら `null`。

これらは**保存だけ**行い、ドメインモデル `GpsPoint` にはまだ載せていない（距離計算・表示に使わないため）。
必要になったら変換層（`GpsTrackRepositoryImpl`）で読み出す。

## 実装マップ

| 何を                 | どこで                                                                 |
| -------------------- | ---------------------------------------------------------------------- |
| バッチ全点保存       | `LocationTrackingService.onLocationResult` → `saveLocationsToDatabase` |
| Location→Entity 変換 | `LocationTrackingService.Location.toGpsPointEntity`                    |
| extras の JSON 化    | `LocationTrackingService.serializeExtras`（Bundle→JSON 文字列）        |
| 列（付随情報）       | `GpsPointEntity` / `DatabaseMigrations.MIGRATION_8_9`（v9）            |
