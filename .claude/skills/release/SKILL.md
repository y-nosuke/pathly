---
name: release
description: Pathly のリリース（vX.Y.Z のタグ → CI が署名済み APK の GitHub Release を作る）と、スマホのリリース版の更新の手順。「リリースして」「v0.5.0 を出して」「スマホのアプリを更新して」と頼まれたときに使う。
---

# リリースする

正は `docs/designs/release.md`。ユーザーへの報告は日本語。
**タグの push とスマホへのインストールは、ユーザーの確認を取ってから**（タグは消しにくい・スマホは普段使いの実データ）。

## 1. 出してよいか確かめる

```bash
gh run list --repo y-nosuke/pathly --branch main --limit 1 --json headSha,conclusion,status
git fetch --tags && git describe --tags          # 前のタグから何コミット先か
gh pr list --repo y-nosuke/pathly --state merged --search "merged:>=<前のリリースの日付>" --json number,title
```

- main の最新の CI（`build`・`instrumented-test`）が成功していること。
- 前のリリース以降に**依存・ビルド・R8 に効く変更**があれば、`emulator-check` スキルでリリース版（R8 込み）を触って確かめる。
- 実機でしか見られない項目（`docs/designs/testing.md`）で、今回の変更に関わるものはユーザーに頼む。

## 2. 版を決める（ユーザーと合意）

SemVer。`0.x` の間は: 機能の追加 → マイナー（0.4.0 → 0.5.0）、修正だけ → パッチ（0.4.0 → 0.4.1）。
入る PR の一覧と、提案する版をユーザーに示して確認を取る。

## 3. タグを付けて push（確認を取ってから）

```bash
git checkout main && git pull --ff-only
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin vX.Y.Z
```

## 4. CI を見届ける

```bash
ID=$(gh run list --repo y-nosuke/pathly --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$ID" --repo y-nosuke/pathly --exit-status   # 途中で抜けたら gh run view で状態を見直す
```

失敗したら `gh run view "$ID" --log-failed`。鍵の Secret が無い・versionName がタグと合わないときは配らずに止まる作り。

## 5. 出来たものを確かめる

```bash
D=<scratchpad>/vX.Y.Z
gh release view vX.Y.Z --repo y-nosuke/pathly --json isDraft,isPrerelease,assets --jq '.'
gh release download vX.Y.Z --repo y-nosuke/pathly -D "$D"
BT=$(ls -d /c/Users/yoichi/AppData/Local/Android/Sdk/build-tools/* | grep -v rc | sort -V | tail -1)
"$BT/apksigner.bat" verify --print-certs "$D/pathly-vX.Y.Z.apk" | grep -E "DN|SHA-1"
"$BT/aapt2" dump badging "$D/pathly-vX.Y.Z.apk" | grep -oE "package: name='[^']+' versionCode='[0-9]+' versionName='[^']+'|application-debuggable"
```

- 添付が `pathly-vX.Y.Z.apk` と `mapping-vX.Y.Z.txt` の 2 つ。
- 署名が `CN=Pathly`・SHA-1 `b59d7a65ee92a65fdb12af97bb6304f5401a83ac`（リリース鍵）。
- `com.pathly`・versionName が `X.Y.Z`・**debuggable でない**。
- リリースノート（PR のタイトルから自動生成）が読める内容か。

## 6. スマホのリリース版を更新する（確認を取ってから）

```bash
export MSYS_NO_PATHCONV=1
ADB=/c/Users/yoichi/AppData/Local/Android/Sdk/platform-tools/adb
PHONE=$($ADB devices | grep -oE '^adb-[^[:space:]]+' | head -1)     # 見つからなければ $ADB mdns services
$ADB -s "$PHONE" shell dumpsys activity services com.pathly | grep -c LocationTrackingService   # 0 = 記録中でない
$ADB -s "$PHONE" install -r 'D:/.../pathly-vX.Y.Z.apk'
$ADB -s "$PHONE" shell dumpsys package com.pathly | grep -E "versionName|versionCode"
```

- **記録中なら入れない**（上書きで記録が止まる）。ユーザーに記録を止めてもらう。
- 同じ鍵なのでデータは残る。入れたあと、設定の「書き出す」でバックアップを取るよう勧める。
- 開発版も揃えるなら、main で `ANDROID_SERIAL=$PHONE ./gradlew installDebug`（`android/` で）。
