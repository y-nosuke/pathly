# リリースとバージョン管理

版の付け方・開発版とリリース版の分け方・リリース鍵・リリースの手順。
決定の背景と没案は [ADR-0026](../adr/0026-release-versioning-and-signing.md)。

---

## 版の付け方

**正は git のタグ `vX.Y.Z`**（[SemVer](https://semver.org/lang/ja/)）。`app/build.gradle.kts` がビルドのたびに git から決める。

| 値            | 決め方                | 例                                                     |
| ------------- | --------------------- | ------------------------------------------------------ |
| `versionName` | 直近の `vX.Y.Z` タグ  | タグの上 `0.3.0`／タグの後 `0.3.0+dev.5.abc1234`       |
| `versionCode` | HEAD までのコミット数 | `358`（main はマージで増える一方なので常に上書き可能） |

- タグの後の `+dev.5.abc1234` は「0.3.0 から 5 コミット先の abc1234」。SemVer のビルドメタデータ。
- `v1.0.0-rc.1` のようなプレリリースのタグも使える（GitHub Release もプレリリース扱いになる）。
- **浅い clone ではコミット数を数え間違う。** CI は `fetch-depth: 0` で全履歴を取る。
- **squash マージはしない。** ブランチ上のコミット数がマージ後の main を上回り、versionCode が戻りうる。

### どの数字を上げるか

| 上げる所 | いつ                                                                    |
| -------- | ----------------------------------------------------------------------- |
| パッチ   | バグ修正だけ                                                            |
| マイナー | 機能の追加（ロードマップの区切りごとが目安）                            |
| メジャー | 大きな節目（Phase 2 を終えて普段使いできる＝`1.0.0`、クラウド同期など） |

`0.x` の間は開発中の扱い。DB のバージョン（Room）とは独立で、連動させない。

---

## 開発版とリリース版

同じ端末に**別アプリとして**両方入る。データも別。

|          | リリース版            | 開発版                                      |
| -------- | --------------------- | ------------------------------------------- |
| ID       | `com.pathly`          | `com.pathly.debug`（`applicationIdSuffix`） |
| 表示名   | Pathly                | Pathly 開発版（`src/debug/res`）            |
| アイコン | オレンジ              | 青緑（背景だけ `src/debug/res` で差し替え） |
| 署名     | リリース鍵            | リポジトリのデバッグ鍵                      |
| 入れ方   | GitHub Release の APK | `./gradlew installDebug`・CI の debug APK   |
| 用途     | 普段使い（実データ）  | 自由に試す・データをいじる                  |

Google Maps / Places の API キーは Cloud Console で**パッケージ名＋署名の SHA-1** に制限している
（[security.md](security.md)）。**両方の組を登録すること**。登録が無い方は地図と場所検索が動かない。

| パッケージ名       | SHA-1                                                                       |
| ------------------ | --------------------------------------------------------------------------- |
| `com.pathly.debug` | `F2:6E:E8:EF:B3:AA:D7:D3:00:B3:53:1E:B9:64:0A:C9:BB:10:39:CF`（デバッグ鍵） |
| `com.pathly`       | リリース鍵の SHA-1（`keytool -list -v -keystore <鍵> -alias pathly`）       |

---

## リリース鍵

**リポジトリに含めない**（`*.jks` は `.gitignore` 済み）。Gradle プロパティで渡す。

| プロパティ                   | 中身                 | CI の Secret                                                |
| ---------------------------- | -------------------- | ----------------------------------------------------------- |
| `pathlyReleaseStoreFile`     | 鍵ファイルのパス     | `RELEASE_KEYSTORE_BASE64`（鍵ファイルを base64 にしたもの） |
| `pathlyReleaseStorePassword` | 鍵ストアのパスワード | `RELEASE_STORE_PASSWORD`                                    |
| `pathlyReleaseKeyAlias`      | 鍵の別名（`pathly`） | `RELEASE_KEY_ALIAS`                                         |
| `pathlyReleaseKeyPassword`   | 鍵のパスワード       | `RELEASE_KEY_PASSWORD`                                      |

- ローカルでは `$GRADLE_USER_HOME/gradle.properties`（既定は `~/.gradle/gradle.properties`）に書く。
  リポジトリの `gradle.properties` には**書かない**。
- 渡されていなければリリース版は**未署名**で出る。手元の build や PR の CI はそれでよい。
- **鍵をなくすと、以後リリース版を上書きインストールできない**（入れ直し＝端末のデータが消える）。
  鍵ファイルとパスワードは PC とは別の場所（パスワードマネージャー等）にも控える。

### 鍵の作り方（一度だけ）

```bash
keytool -genkeypair -v -keystore <保管場所>/pathly-release.jks -storetype PKCS12 -alias pathly -keyalg RSA -keysize 4096 -validity 36500
```

パスワードと名前（CN など）を聞かれる。PKCS12 では鍵ストアと鍵のパスワードは同じになる。

Secret に登録する base64 は Git Bash で作ってクリップボードへ送る（`-w 0` で改行を入れず 1 行にする。
`clip` は Windows のクリップボード）:

```bash
base64 -w 0 <保管場所>/pathly-release.jks | clip
```

base64 にした文字列は鍵そのものと同じ扱い。貼り付けたらクリップボードを別の内容で上書きする。

---

## リリースの手順

1. main が CI でグリーンなことを確かめる。
2. [testing.md の「実機でしか確かめられないもの」](testing.md#実機でしか確かめられないもの)を開発版で確認する。
3. main の先頭にタグを付けて push する。

   ```bash
   git tag -a v0.3.0 -m "v0.3.0"
   git push origin v0.3.0
   ```

4. `.github/workflows/release.yml` が、リリース鍵で署名した `pathly-v0.3.0.apk` を作り、
   前のリリースからの PR のタイトルを変更点にして GitHub Release を作る。
   署名が無い・versionName がタグと合わないときは配らずに止まる。
5. 端末に APK を上書きインストールする（スマホで Release から入れても、PC から adb で入れてもよい）。

   ```bash
   gh release download v0.3.0 -p "*.apk"
   adb install -r pathly-v0.3.0.apk
   ```

---

## データの持ち出し

リリース版は debuggable でないため、**adb からデータを読み書きできない**（`run-as` が使えない）。
リリース版の実データを開発版に写す・バックアップするには、アプリのエクスポート機能が要る
（[roadmap](../roadmap.md) の「データのエクスポート／インポート」）。

リリース鍵へ切り替えたとき（2026-09）のデータ移行の手順と落とし穴は
[ADR-0026](../adr/0026-release-versioning-and-signing.md#付録-鍵の切り替えで行ったデータ移行2026-09-27) に残している。
あの手順は旧アプリが debuggable だったから使えた。今のリリース版からは退避できないので、
**鍵を替え直すならエクスポート機能が先に要る**。
