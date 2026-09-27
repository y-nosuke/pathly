---
name: emulator-check
description: エミュレータを起動して instrumented test を回す、または APK（開発版・リリース版）を入れて画面を触って確かめる手順。UI や DB を変えたとき、リリース前に R8 をかけたリリース版を確かめるとき、画面を見て確認したいときに使う。
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

## instrumented test

```bash
cd android
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
# 1 クラスだけ: -Pandroid.testInstrumentationRunnerArguments.class=com.pathly.xxx.YyyTest
grep -hoE 'tests="[0-9]+" failures="[0-9]+" errors="[0-9]+"' app/build/outputs/androidTest-results/connected/debug/*.xml | head -1
```

失敗したら `app/build/reports/androidTests/connected/debug/` の HTML を読む。

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

## リリース版（R8）の確認で触る範囲

R8 はリリース版にしかかからない。記録の開始・移動・停止 → 履歴 → 経路詳細、場所のキーワード検索（Places）→ 登録、
設定の書き出し → 読み込み → 再起動 → 「読み込む前の状態に戻す」。クラッシュとアプリのエラーログが無いこと。

## 後片付け

```bash
$ADB -s emulator-5554 emu kill
```

「応答していません（No response to onStartJob）」はエミュレータの負荷が高いときに出る。`logcat` で原因を見て、アプリの不具合でなければ「待つ」でよい。
何度も続くなら、「起動」の「固まる・「応答していません」が続くとき」を見る。
