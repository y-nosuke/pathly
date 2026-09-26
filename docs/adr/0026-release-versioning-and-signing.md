# 0026. 版は git のタグから決め、開発版を別アプリに分け、リリース版は専用の鍵で署名する

- Status: Accepted
- Date: 2026-09-26

関連: 手順と現状は [../designs/release.md](../designs/release.md)。

## Context（背景）

- 版は CI の run 番号から `1.0.<run>` として付けていた。push でも PR でも増えるので飛び飛びで、
  番号から「どの機能が入った版か」が分からない。ローカルのビルドは `1.0` になる。
- git のタグ（`0.1.0` / `0.2.0`）はアプリの版と繋がっておらず、ほぼ更新されていなかった。
- 端末には CI の debug APK（`com.pathly`・リポジトリのデバッグ鍵）を入れて**普段使い**していた。
  開発中の版を試すと実データに直接触れてしまう。
- デバッグ鍵はパスワードが公知で、リリースの署名には向かない。
- クラウド同期（Phase 3）が無いので、**入れ直し＝記録の喪失**になる。署名の扱いを誤れない。

## Decision（決定）

- **版は git から決める。** `versionName` は直近の `vX.Y.Z` タグ（SemVer、タグの後は `+dev.N.hash`）、
  `versionCode` は HEAD までのコミット数。タグは手で付け、タグの push で CI が GitHub Release を作る。
- **開発版を `com.pathly.debug` として別アプリにする。** 普段使いのリリース版（`com.pathly`）とデータを分ける。
- **リリース版は専用のリリース鍵で署名する。** 鍵はリポジトリに含めず、Gradle プロパティ／CI の Secrets で渡す。
- 既存のタグは `v0.1.0` / `v0.2.0` に付け直し、今の main を `v0.3.0` とする。
- 鍵の切り替えで失う端末のデータは、`run-as` で退避して戻す（一時的に debuggable なリリース版を経由する）。

## Alternatives（検討した没案）

- **run 番号のまま versionName だけ手で付ける** … 版の正が build.gradle.kts と CI に分かれ、ずれうる → **却下**。
- **versionCode を versionName から計算する（`MAJOR*10000+MINOR*100+PATCH`）** … タグの間のビルドが同じ値になり、
  開発版を上書きできない → **却下**。コミット数なら常に増え、CI とローカルで一致する。
- **release-please で版上げ・CHANGELOG を自動化** … 一人開発でまず回すには大げさ。手でタグを付ける運用に
  慣れてから検討する → **見送り**。
- **リリース版もデバッグ鍵で署名し続ける** … データ移行が要らず楽だが、公知の鍵で署名した APK を普段使いすることになり、
  将来の Play 公開にも使えない。移行の手間は一度きり → **却下**。
- **アプリにエクスポート／インポート機能を作ってから鍵を替える** … 本来あるべき機能だが、鍵の切り替えのためだけなら
  `run-as` で足りる → **別の機能として roadmap で扱う**。

## Consequences（結果・トレードオフ）

- 端末の APK の版からタグ・コミット・GitHub Release を一意に辿れる。
- 開発版では実データを気にせず試せる。ただし API キーの制限に `com.pathly.debug` の登録が要る。
- **リリース鍵を失うと、以後リリース版を上書きできない。** 鍵とパスワードの保管が新たな責任になる。
- CI は全履歴を取得する必要がある（今の規模では数秒以内）。squash マージをすると versionCode が戻りうる。
- リリース版は debuggable でないので、以後 adb から実データを退避できない。鍵を替え直すには
  エクスポート機能が先に要る。

## 付録: 鍵の切り替えで行ったデータ移行（2026-09-27）

旧アプリ（`com.pathly`・デバッグ鍵・**debuggable**）から、v0.3.0（リリース鍵）へ移した記録。
Git Bash から Wi-Fi 接続の実機に対して行った。

1. 退避（アプリを止め、DB と設定を丸ごと取り出す）

   ```bash
   adb shell am force-stop com.pathly
   adb exec-out run-as com.pathly tar -cf - databases shared_prefs > pathly-data.tar
   ```

   PC 側で展開して `pragma integrity_check` と件数を確かめ、別の場所にも控えた。

2. 旧アプリを消す: `adb uninstall com.pathly`
3. 新しい鍵で一時的に debuggable なリリース版を作って入れる（**起動しない**。空の DB ができるため）

   ```bash
   ./gradlew assembleRelease -PpathlyDebuggableRelease=true
   adb install app/build/outputs/apk/release/app-release.apk
   ```

4. 書き戻す（端末に送ってから展開する）

   ```bash
   export MSYS_NO_PATHCONV=1
   adb push pathly-data.tar /data/local/tmp/pathly-data.tar
   adb shell chmod 644 /data/local/tmp/pathly-data.tar
   adb shell run-as com.pathly tar -xf /data/local/tmp/pathly-data.tar
   adb shell run-as com.pathly sha256sum databases/pathly_database databases/pathly_database-wal
   ```

   ハッシュが退避したファイルと一致することを確かめた。

5. 正式な `pathly-v0.3.0.apk` を上書きする: `adb install -r pathly-v0.3.0.apk`（同じ鍵・同じ versionCode なので可）
6. 一時ファイルを消す: `adb shell rm /data/local/tmp/pathly-data.tar`。起動して権限を許可し直し、件数を確かめた。

開発版にも同じ tar を `run-as com.pathly.debug` で展開し、実データの写しを入れた。

踏んだ落とし穴:

- **標準入力での流し込み**（`adb shell "run-as com.pathly tar -xf -" < pathly-data.tar`）は
  `Illegal seek` で失敗し、壊れた部分ファイルが残った。起動前だったので消してやり直した。
- **Git Bash のパス変換**: `/data/local/tmp/...` が `C:/Program Files/Git/data/...` に書き換えられる。
  `MSYS_NO_PATHCONV=1` で止める。逆に PC 側の `tar -xf D:/...` は `D:` をリモートと解釈するので `/d/...` と書く。
- アンインストールで権限（位置情報・通知・電池の最適化の除外）は消える。設定値は `shared_prefs` と一緒に戻る。
