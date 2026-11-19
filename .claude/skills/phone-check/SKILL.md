---
name: phone-check
description: 実装をコミットしたあと、スマホ（Wi-Fi の adb）に開発版 com.pathly.debug を入れ、動作確認の手順を示してユーザーに確かめてもらう手順。「スマホに開発版を入れて」「スマホで確かめたい」「動作確認の手順を教えて」と頼まれたとき、start-issue の 7 から使う。
---

# スマホに開発版を入れて、動作確認してもらう

ユーザーへの返答は日本語。入れるのは**開発版 `com.pathly.debug`**（リリース版 `com.pathly` とは別アプリ・別データ）。
リリース版の更新は `release` スキル。エミュレータでの確認は `emulator-check` スキル。

## 1. スマホを見つける

```bash
export MSYS_NO_PATHCONV=1
ADB=/c/Users/yoichi/AppData/Local/Android/Sdk/platform-tools/adb
$ADB devices -l                       # adb-…._adb-tls-connect._tcp が device ならそれ
$ADB mdns services                    # 居なければ探す（Wi-Fi のワイヤレスデバッグ）
PHONE=$($ADB devices | grep -oE '^adb-[^[:space:]]+' | head -1)
```

- `adb devices` に出ないとき:
  1. `$ADB mdns services` に `_adb-tls-connect._tcp` が出ていれば、`$ADB connect <IP>:<ポート>`（名前ではなく IP:ポートで）。
  2. それでも `failed to connect` で、`ping <IP>` は通る → **PC とのペアリングが切れている**。
     ユーザーに次をしてもらう（ペアリングの 6 桁のコードはその場限りの確認コードなので、Claude は打たない）。
     - スマホの「設定 → 開発者向けオプション → ワイヤレスデバッグ → ペア設定コードによるデバイスのペア設定」を開く
     - PC のターミナルで `adb pair <IP>:<ペア用のポート>` を実行し、6 桁のコードを入れる（ペア用のポートは接続用とは別）
     - ペアリングできると、少しして `adb devices` に自動で出る
  3. `mdns services` にも出ない → スマホのワイヤレスデバッグがオフか、別のネットワーク。ユーザーにオンにしてもらう。
- **エミュレータの作業と混ぜない**。以降は必ず `-s "$PHONE"` / `ANDROID_SERIAL="$PHONE"` で打つ。

## 2. 入れる

```bash
$ADB -s "$PHONE" shell dumpsys activity services com.pathly.debug | grep -c LocationTrackingService   # 0 = 開発版で記録中でない
cd android && ANDROID_SERIAL="$PHONE" ./gradlew installDebug
$ADB -s "$PHONE" shell dumpsys package com.pathly.debug | grep -m1 lastUpdateTime                      # 入れた時刻
```

- 開発版で記録中なら、上書きで記録が止まる。ユーザーに止めてもらってから入れる。
- **入れるのはコミットした中身**（作業中の変更を混ぜない）。何を入れたか（コミットの短い ID）を伝える。

### Wi-Fi でつながらないとき（外出中など）

スマホが PC と同じ Wi-Fi に無いと adb で入れられない（ワイヤレスデバッグは Wi-Fi が要る。モバイルデータからはつなげない）。
外出中はこれが決まった手段（ユーザーと決めた。2026-11-19）: **ビルドした APK を zip にして添付する**。
1 で見つからず、ユーザーが外出中と分かっていれば、ペアリングし直しを頼まずにこちらへ進む。

```bash
cd android && ./gradlew assembleDebug
cd app/build/outputs/apk/debug && powershell.exe -NoProfile -Command \
  "Compress-Archive -Force -Path app-debug.apk -DestinationPath app-debug.zip"
```

- 置き場所は `android/app/build/outputs/apk/debug/`（git に入らない）。リポジトリ直下に作らない。
- SendUserFile（`display: "attach"`）で添付し、PC 上のパス（`D:\root\opt\pathly\android\app\build\outputs\apk\debug\app-debug.apk`）も伝える。
- スマホでは展開して `app-debug.apk` を開く（「提供元不明のアプリ」の許可が要ることがある）。
- 開発版は PC の debug 鍵で署名しているので、adb で入れた開発版にそのまま上書きで入る（データは残る）。
- **入れるのはコミットした中身**（作業中の変更を混ぜない）。何を入れたか（コミットの短い ID）を伝える。記録中の確認は 2 と同じく、
  ユーザーに見てもらってから入れてもらう。
- まれにスマホのアプリから添付を落とせないことがある。そのときはもう一度添付し、それでも駄目なら帰ってから adb で入れる。

## 3. 動作確認の手順を示す

変更ごとに書く。ユーザーはその場で手元で試すので、**上から順に押していけば確かめられる**形にする。

- **準備**: 要るデータと作り方（例:「200m 以内に場所を 2 つ。無ければ場所タブの追加 → 地図で追加」）。
  開発版は別データなので、空のことがある前提で書く。
- **操作**: 番号付きで、画面・ボタンの名前は画面の文言どおりに（「ほかの場所とまとめる」）。
- **確かめること**: 各操作の下に「〜こと」で、見て分かるもの（出る・出ない・戻る）を書く。
- 取り消し・戻る・失敗のときなど、**エミュレータで確かめにくかったもの**を入れる。
- 実機でしか見られないもの（GPS の長時間記録など）は、あとで確かめる項目として分けて書く。

## 4. 結果を受ける

- 問題なし → `start-issue` の 8（push して PR）へ。PR の検証の欄に「スマホ（開発版）で確かめた（ユーザー）」と書く。
- 問題あり → 直してコミットし、1 からやり直す（同じ PR の中で）。仕様が違ったなら、docs も同じ PR で直す。
- 確かめられなかった項目は、PR の検証の欄に「未確認」と理由を書く。
