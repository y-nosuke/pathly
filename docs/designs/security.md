# セキュリティ設計

**何を守り、何を守っていないか**は [../specs/security.md](../specs/security.md) を正とする。
ここには**その実現方法**と、変えるときに必要になるものだけを書く。

---

## 暗号化していない理由と、やるなら何が要るか

Room の DB は平文で置いている。理由は 2 つ。

1. 端末内で完結していて他人に渡らない。
2. 一人で使うアプリで鍵管理まで抱えると、**壊したときの被害（記録が読めなくなる）の方が大きい**。

将来暗号化するなら **SQLCipher** 等の導入が要る。`androidx.security:security-crypto`
（Jetpack Security）は **Deprecated** なのでその路線は取らない。導入時に決めることは:

- 鍵の生成・保管（Android Keystore）と、**鍵を失ったときの扱い**（＝復旧不能を受け入れるか）
- 既存の平文 DB からの移行

## 端末バックアップ

`android:allowBackup="true"` かつ `backup_rules.xml` / `data_extraction_rules.xml` は
**テンプレートのまま（中身が空）**にしてある。Auto Backup が DB ごと持っていくのは
**意図した状態**（→ [ADR-0016](../adr/0016-allow-device-backup.md)）。

締めるなら `allowBackup="false"` か、抽出ルールで DB だけ `<exclude>` する。どちらも
機種変更で引き継げなくなるため採らなかった。**ローカル DB を暗号化するときは、この設定も
一緒に見直すこと**（暗号化した DB を上げても、鍵が Keystore にあるため復元先で読めない）。

## 権限と端末状態の持ち場

- 権限の有無の判定は `util/PermissionUtils` に集約する。
- 権限・位置情報サービスの ON/OFF・電池の最適化といった**端末側の状態**は
  `data/tracking/TrackingController`（アプリスコープ）が持ち、画面はそれを購読する。
  ViewModel から Android framework を直接触らないための置き場所（[architecture.md](architecture.md)）。
- 権限ダイアログの起動は Activity のランチャー経由。前景の位置が許可されたら、
  そのコールバックから続けてバックグラウンド位置を要求する（別々に要求する必要があるため）。

## API キー

Google Maps / Places の API キーは `local.properties` の `GOOGLE_MAPS_API_KEY` から
マニフェストと `BuildConfig` に注入する（**リポジトリには含めない**）。

APK に埋まる以上キー自体は秘匿できない。守りは **Cloud Console 側のアプリ制限**
（パッケージ名＋署名証明書のフィンガープリント）と API 制限で行う。

デバッグ用の keystore は**リポジトリに含めている**。パスワードが公知（`android`）で秘密情報ではなく、
CI とローカルで署名を揃えないと更新インストールできないため。**リリース鍵は含めない**。

## 座標をログに出さない

リリースビルドで R8 を有効にしていない（`isMinifyEnabled = false`）ため、出力した文字列は
そのまま logcat に残る。**座標・住所・施設名はログに載せない**（詳細は [logging.md](logging.md)）。

---

## 関連

- データモデル・削除規則: [../specs/model.md](../specs/model.md)
- ログ: [logging.md](logging.md)
- 将来のクラウド構成: [../roadmap.md](../roadmap.md) の「温めている案」
