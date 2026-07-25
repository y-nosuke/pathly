# パフォーマンス設計

GPS 記録アプリとして、バッテリー・メモリ・ストレージの消費を抑えつつ記録精度を保つための設計方針。

## バッテリー最適化

- GPS 取得間隔を状況に応じて調整（既定は設定値。バッテリー低下時は間隔を延ばす）
- `PRIORITY_BALANCED_POWER_ACCURACY` を基本とし、常時高精度は避ける
- 不要な処理・監視は停止する
- Android のバックグラウンド実行制限に対応（記録はフォアグラウンドサービスで継続）

## メモリ管理

- GPS ポイントはバッチでまとめて保存し、都度の I/O を避ける
- Flow（StateFlow）でリアクティブに更新し、保持データを最小化する
- 大量データは将来ページングに対応する
- 不要データの削除はユーザー操作に委ねる（自動削除はしない → [security.md](security.md)）

## データベース最適化

- 検索・ソートに使う列へインデックスを付与（付与状況は [../specs/model.md](../specs/model.md) 参照）
- バッチ Insert とトランザクションで書き込みを効率化する
- 原データ（gps_points）は無改変で保持し、補正結果は smoothed_points に分離する

## 関連

- データモデル: [../specs/model.md](../specs/model.md)
- GPS 補正: [gps-smoothing.md](gps-smoothing.md)
- セキュリティ・プライバシー: [security.md](security.md)
