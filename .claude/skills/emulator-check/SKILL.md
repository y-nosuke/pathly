---
name: emulator-check
description: エミュレータを起動して instrumented test を回す、または APK（開発版・リリース版）を入れて画面を触って確かめる手順。UI や DB を変えたとき、スマホに開発版を入れる前に自分で画面を確かめるとき、リリース前に R8 をかけたリリース版を確かめるとき、画面を見て確認したいときに使う。
---

# エミュレータで確かめる

Windows + Git Bash 前提。ユーザーへの報告は日本語。**Wi-Fi でつないだユーザーのスマホを巻き込まない**のが最重要。

## 準備（毎回）

```bash
SDK=/c/Users/yoichi/AppData/Local/Android/Sdk
ADB=$SDK/platform-tools/adb
export MSYS_NO_PATHCONV=1   # 端末側のパス（/data/local/tmp 等）を Git Bash に書き換えさせない
```

- `adb devices` にスマホ（`adb-…._adb-tls-connect._tcp`）が居ても、以下はすべて **`-s emulator-5554` / `ANDROID_SERIAL=emulator-5554`** で打つ。
- PC 側のパスを adb.exe に渡すときは `D:/root/...` の形（`/d/root/...` は adb.exe が読めない）。逆に `tar` など Git Bash のコマンドには `/d/...`。

## 起動

```bash
"$SDK/emulator/emulator" -avd Pixel_10_API36 -no-snapshot-save -no-audio -no-boot-anim > /dev/null 2>&1 &
for i in $(seq 1 60); do
  [ "$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 5
done
```

Bash ツールで起動するときは、起動と待ちを 1 回の呼び出しにまとめる（`&` で残したまま次の呼び出しに進める）。

### 固まる・「応答していません」が続くとき

起動に 2 分近くかかる、System UI・電話・Pathly などが次々「応答していません」になる、`system_server` が
再起動する、といった症状は、**Windows の電源調整（効率モード）でエミュレータに CPU が回っていない**のが
原因だった（2026-09-27）。ウィンドウが前面に無いと効率モードにされ、混成 CPU（P＋E コア）では特に効く。
メモリ不足に見えるが、メモリを増やしても直らない。

- 確かめ方: PC の CPU 使用率は低いのに、エミュレータの中の負荷（`$ADB -s emulator-5554 shell cat /proc/loadavg`）が
  コア数（4）を大きく超え、`qemu-system-x86_64` の CPU 時間がほとんど増えない
  （PowerShell で `(Get-Process qemu-system-x86_64).TotalProcessorTime` を 10 秒あけて 2 回見る）。
- 直し方: **ユーザーに**管理者の PowerShell で次を実行してもらう（システムの設定なので Claude は実行しない）。
  設定済み（2026-09-27）。戻すときは `disable` を `reset` に。

  ```powershell
  powercfg /powerthrottling disable /path "C:\Users\yoichi\AppData\Local\Android\Sdk\emulator\qemu\windows-x86_64\qemu-system-x86_64.exe"
  ```

- AVD（`D:\dev\avd\Pixel_10_API36.avd\config.ini`）は `hw.ramSize=4096M`・`hw.gpu.enabled=yes`・`hw.gpu.mode=host`。
  2G・GPU なしだった頃はスクリーンショットが真っ白になることがあった。
- 効いていれば、起動（`boot_completed` まで）は 30 秒前後、落ち着いた後の負荷は 2 前後。
- **起動したままのエミュレータは、PC のスリープ明けに固まっていることがある**（qemu の CPU 時間が 0 のまま、
  `adb shell` も戻らない。2026-11-18）。`run-instrumented.sh` は始める前にこれを見て、起動し直す。

## instrumented test

**見張り付きのスクリプトで回す**（リポジトリのルートで）。エミュレータが固まっても待ち続けないため。

```bash
.claude/skills/emulator-check/run-instrumented.sh
# 1 クラスだけ: .claude/skills/emulator-check/run-instrumented.sh -Pandroid.testInstrumentationRunnerArguments.class=com.pathly.xxx.YyyTest
```

- 始める前に、エミュレータが応答して起動し終えているかを見る。応答しなければ止めて起動し直す。起動していなければ起動する。
- テスト中は 1 分ごとに、adb が応答するかと、終わったテストの数（logcat の `TestRunner` の `finished:`）が
  増えているかを見る。どちらかが 3 回続けて駄目なら、gradle とエミュレータを止めて
  「エミュレータが固まった（最後に確かめたときは N 件まで終わっていた）」と出し、終了コード 125 で終える。
- テストが始まる前（コンパイル・インストール中）は、ログが 10 分増えなければ止まったとみなす（`WATCH_STALL_MIN` で変えられる）。
- 終わったら `tests= failures= errors=` の 1 行と `exit=` を出す。前の結果は始める前に消すので、古い結果を読むことはない。
- adb はすべて `timeout` 付き・`-s emulator-5554`（gradle にも `ANDROID_SERIAL=emulator-5554` を渡す）。
- **Claude Desktop では `run_in_background` で回す**。終わったとき（固まって止めたときも）に知らせが来る。
- 1 件で 3 分を超えるテストは、エミュレータが正常でも固まったとみなされる（今のテストは 1 件数秒）。そういうテストを足すなら見張りの回数を見直す。
- 全部（200 件余り）で 3 分半ほどかかる。**直している途中は変えた画面のクラスだけ**を回し、全部は push の前に 1 回にする
  （`start-issue` の 5。DB を変えたときは途中でも全部）。

失敗したら `android/app/build/reports/androidTests/connected/debug/` の HTML を読む。

## ビルドを見張り付きで回す

エミュレータを使わないビルドでも止まることがある（ユニットテストの段でログが 2 時間進まなかった。2026-11-18）。

```bash
.claude/skills/emulator-check/run-build.sh                         # ./gradlew build
.claude/skills/emulator-check/run-build.sh spotlessCheck --rerun-tasks
```

- ログ（`--console=plain` の出力）が 10 分（`WATCH_STALL_MIN`）増えなければ止まったとみなし、`./gradlew --stop` で
  止めて「ビルドが止まった（最後のタスク: …）」と出し、終了コード 124 で終える。それ以外は gradle の終了コードのまま。
- 見張りの仕組みは `watch-lib.sh` を 2 つのスクリプトで使い回している。
- 止めるときの `./gradlew --stop` は、この PC の同じ版の gradle のデーモンを**すべて**止める。別のセッション（別のワークツリー）で
  ビルドしている最中だと、そちらも落ちる。

## APK を入れて触る

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew installDebug             # 開発版 com.pathly.debug
./gradlew assembleRelease && $ADB -s emulator-5554 install -r 'D:/root/opt/pathly/android/app/build/outputs/apk/release/app-release.apk'   # リリース版 com.pathly
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do
  $ADB -s emulator-5554 shell pm grant <パッケージ> android.permission.$p
done
$ADB -s emulator-5554 shell am start -n <パッケージ>/com.pathly.MainActivity
```

- 位置を動かす: `$ADB -s emulator-5554 emu geo fix <経度> <緯度>`（`bc` は無い。計算は `awk "BEGIN{printf \"%.6f\", 139.7671 + $i*0.0006}"`）。
- 画面を見る: `$ADB -s emulator-5554 exec-out screencap -p > <scratchpad>/x.png` を Read する（画像は 1080x2424、表示は縮小されるので座標は元の大きさに直す）。
- 要素をタップする: 1 操作ごとに dump して位置を取る。**メニューやダイアログが開き切る前に次を押すと空振りする**（不具合と取り違えやすい）。

  ```bash
  tap() { $ADB -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
    b=$($ADB -s emulator-5554 shell cat /sdcard/ui.xml | grep -oE "text=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 | grep -oE '[0-9]+' | tail -4 | tr '\n' ' ')
    set -- $b; $ADB -s emulator-5554 shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
  ```

  `text="…"` は完全一致で探す（前方一致にすると「再起動します」と「再起動」のように取り違える）。

- 落ちていないか: `$ADB -s emulator-5554 logcat -d | grep -E "FATAL|AndroidRuntime: [^>US]|ClassNotFound|NoSuchMethod| [EW] Pathly-"`

## スマホに入れる前に画面を確かめる

UI を変えたら、ユーザーのスマホに入れる前に開発版をエミュレータに入れ、変えた画面をスクリーンショットで見る（`start-issue` の 5）。

- 作る前に決めた形（置き場所・縦か横か）どおりか。
- **あふれたとき**: 数を多く（チップ・行を上限の先まで）・名前を長く・記録中のピルや案内の帯と並べる・シートを畳む。
  はみ出す・重なる・画面の外に押し出されて押せない、が無いか。
- 消す・戻す・まとめて操作するボタンが、その状態で押せるか（押して画面が変わることまで見る）。
- Places の候補など、ネットや API キーが要るものは開発版のエミュレータでも出る。地図の上のものは画面テストでは地図を
  差し替えるので確かめられない。ここで見る。

## リリース版（R8）の確認で触る範囲

R8 はリリース版にしかかからない。記録の開始・移動・停止 → 履歴 → 経路詳細、場所のキーワード検索（Places）→ 登録、
設定の書き出し → 読み込み → 再起動 → 「読み込む前の状態に戻す」。クラッシュとアプリのエラーログが無いこと。

## 後片付け

```bash
$ADB -s emulator-5554 emu kill
```

「応答していません（No response to onStartJob）」はエミュレータの負荷が高いときに出る。`logcat` で原因を見て、アプリの不具合でなければ「待つ」でよい。
何度も続くなら、「起動」の「固まる・「応答していません」が続くとき」を見る。
