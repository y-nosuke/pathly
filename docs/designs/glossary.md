# 用語集（Glossary）

位置情報の処理で使う用語を、**日本語（議論用）**と**英語（コードの識別子）**で対応させる。
変数名・関数名・テーブル名をブレさせないための共通辞書。

関連設計: [gps-smoothing.md](./gps-smoothing.md)（補正）／[places-and-stops.md](./places-and-stops.md)（場所・立ち寄り）

---

## データ（名詞）

位置に関するデータは、段階を追って「点の集まり」から「意味のある場所」へ変換される。

| 日本語               | 英語（コード）        | 意味                                                             | 実体                                              |
| -------------------- | --------------------- | ---------------------------------------------------------------- | ------------------------------------------------- |
| 軌跡（経路）         | track                 | 1回の記録（GPSの点列＋期間）                                     | `GpsTrack` / `gps_tracks`                         |
| 生データ（生点）     | raw point             | 端末が記録したそのままの座標。ノイズを含む                       | `GpsPoint` / `gps_points`                         |
| 補正後の点列         | smoothed point(s)     | 生データを補正した軌跡の点。「**どこを通ったか**」               | `SmoothedPoint` / `smoothed_points`（`GpsTrack.smoothedPoints`） |
| 検出された滞在       | detected stop         | 補正後点列で「50m圏内に3分以上」の箇所。**一時結果・非永続**     | `DetectedStop`                                    |
| 場所                 | place                 | 場所そのもの（座標・名前・住所）。経路から独立                   | `Place` / `places`                               |
| 立ち寄り（訪問）     | stop                  | どの経路でどの場所にいつ居たか（place × track）                 | `Stop` / `stops`                                 |
| 解決記録             | resolution (record)   | 場所名を Google に問い合わせた記録（叩いたか・結果）             | `PlaceResolution` / `place_resolutions`          |

データの流れ:

```
生データ（gps_points・どんな座標を拾ったか）
   ↓ 補正（smoothing）
補正後の点列（smoothed_points・どこを通ったか）
   ↓ 立ち寄り検出（detection）
検出された滞在（DetectedStop・どこに滞在したか／一時結果）
   ↓ 確定（commit）＋場所の同定（find-or-create）
場所・立ち寄り（Place / Stop・永続化）
   ↓ 名前解決（resolve）
場所名（places.name / place_resolutions）
```

---

## 処理（動詞）

| 日本語                     | 英語（コード）                | 意味                                                                       | 実体                                        |
| -------------------------- | ----------------------------- | -------------------------------------------------------------------------- | ------------------------------------------- |
| **解析**                   | analyze / analysis            | GPSデータから構造を導く**ローカル計算全体**（補正＋立ち寄り検出）。外部通信なし | 記録中に逐次／再解析で一括                  |
| 　└ **補正**（スムージング） | smooth / smoothing            | 生データ → 補正後の点列                                                     | `TrackSmoother.smooth`                      |
| 　　└ ジャンプ除外         | remove jumps                  | 速度が非現実的な外れ値を除外                                               | `removeJumps`                               |
| 　　└ 平滑化               | accuracy-weighted smoothing   | 精度重み付き移動平均でならす                                               | `accuracyWeightedSmooth`                    |
| 　└ **立ち寄り検出**       | detect / detection            | 補正後点列 → DetectedStop                                                   | `StopDetector.detect`                       |
| **確定**                   | finalize / commit             | 暫定でなくすこと（下記補足）。立ち寄りは DetectedStop を Place+Stop として保存 | `updateSmoothedForTrack` / `updateStopsForTrack` |
| 　└ 場所の同定（重複排除） | find-or-create                | 30m以内の既存 place を再利用、無ければ新規作成                             | `findOrCreatePlace`                         |
| **名前解決**               | resolve / resolution          | Google から場所名を**取得**（外部・オンライン・課金）。**自動**             | `PlacesNameResolver.resolve`                |
| **命名**（手動）           | rename / edit name            | ユーザーが名前を付け替える                                                 | `updatePlaceName`                           |
| **再解析**                 | reanalyze                     | 解析（＋名前解決）をやり直す（詳細画面のボタン）                           | 再補正＋再検出＋名前解決                    |
| 　└ 再補正                 | recompute (smoothed)          | 補正だけ作り直す（再解析の部分集合）                                       | `recomputeSmoothed`                         |

---

## 補足（紛らわしい語の使い分け）

- **命名 vs 名前解決**  
  自動（Google Places）は「**名前解決 resolve**」、手動（ユーザー）は「**命名 rename**」。
  名前解決は既にある施設名を*引いてくる*、命名はユーザーが*付ける*。`place_resolutions`・`resolve` と一貫。

- **確定（finalize）**  
  「暫定 → 確定」を指す共通語。
  - 補正: 末尾 `half` 点が確定する（未来の点が来るまで暫定）。
  - 立ち寄り: 滞在中の最後のクラスタが離れて確定し、保存される。

- **解析と名前解決の関係**  
  解析は**ローカル計算のみ**（補正＋検出）。名前解決は**外部通信**なので解析に含めない。
  「再解析」ボタンは利便上、解析＋名前解決の両方を再実行する。
