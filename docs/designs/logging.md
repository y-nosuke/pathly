# ログ管理ガイドライン

Pathly のログ出力の方針。デバッグしやすさとコードの読みやすさを両立させ、
**位置情報アプリとして出してはいけないものを出さない**ことを目的にする。

## Logger の使い方

`android.util.Log` を直接呼ばず、`com.pathly.util.Logger` を使う。
**クラスごとにインスタンスを 1 つ持つ**スタイル（タグを毎回書かない）。

```kotlin
import com.pathly.util.Logger

class TrackingController {
  private val logger = Logger("TrackingController")

  fun start() {
    logger.d("start() called")      // DEBUGビルドのみ
    logger.i("Tracking started")    // 常に出力
    logger.w("Location is off")     // 常に出力
    logger.e("Failed to bind", e)   // 常に出力（Throwable 付きは w / e のみ）
  }
}
```

- タグには `Pathly-` の接頭辞が自動で付く（`Logger("TrackingController")` → `Pathly-TrackingController`）。
  接頭辞を自分で書かないこと。
- `d` だけが `BuildConfig.DEBUG` を見て出し分ける。`i` / `w` / `e` は常に出る。
- レベルは 4 つだけ。`verbose` は用意していない（詳細ログも `d` に寄せる）。
- タグ名は**クラス名に揃える**（`Logger("PlacesViewModel")`）。画面・レイヤー横断の
  独自命名はしない。

## レベルの使い分け

| レベル | 出力       | 用途                                                    |
| ------ | ---------- | ------------------------------------------------------- |
| `e`    | 常に       | 動作に影響する失敗。例外は第2引数で渡す                 |
| `w`    | 常に       | 動くが注意が要る状況（権限なし・位置情報 OFF・0件応答） |
| `i`    | 常に       | 重要な状態変化（記録の開始・終了、サービスの起動）      |
| `d`    | DEBUG のみ | 処理の入口・分岐の確認、開発中の調査                    |

## 出してはいけないもの

### 座標・位置に紐づく値

**最優先の禁止事項**。リリースビルドで R8 を有効にしていない（`isMinifyEnabled = false`）ため、
出力した文字列はそのまま logcat に残る。

```kotlin
// ❌ 絶対に書かない
logger.d("Location: lat=$lat, lon=$lng")
logger.d("Saved stop at ${place.latitude},${place.longitude}")
logger.e("Failed to handle $command")   // データクラスの toString に座標が乗る

// ✅ 件数・ID・型名など、場所を特定できない情報にする
logger.d("Saved ${points.size} points to track $trackId")
logger.e("Failed to handle ${command::class.simpleName}", e)
```

住所・施設名も「どこにいたか」が分かるので同じ扱いにする。

### 高頻度で呼ばれる箇所

GPS のコールバックや Compose の再コンポジションの中でログを出すと、
実質的に座標の軌跡がログに落ちるうえ、ログが流れて読めなくなる。バッチ単位・
状態変化のタイミングにまとめる。

### 文字列の組み立てコスト

`logger.d("...${heavy()}")` は**リリースビルドでも `heavy()` が走る**（引数の評価が先）。
重い処理を挟むときだけ `if (BuildConfig.DEBUG)` で囲む。

## 確認のしかた

```bash
adb logcat -s Pathly-LocationTrackingService Pathly-TrackingController
```

```bash
adb logcat | grep "^.*Pathly-"
```

接頭辞が共通なので、`Pathly-` で絞ればアプリのログだけを追える。

## 運用

- 新機能の開発中は `d` を厚めに入れてよいが、**完成時に必要なものだけ残す**。
- 調査のために一時的に足したログは、原因が分かった時点で消す。
- 「動いていることの確認」だけのログは残さない（`i` が増えるとリリースビルドで邪魔になる）。
