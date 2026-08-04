# アーキテクチャ設計

Pathly Android の構成方針と、その背景にある設計判断をまとめる。
**「どのパッケージに何があるか」「実装規約」は [CLAUDE.md](../../CLAUDE.md) が持つ**。本書は重複を避け、
**「なぜこの構成にしたか（How の根拠）」**に絞る。具体的なクラス・シグネチャは実コードを正とする。

> 対応する要望: [requirements.md](../requirements.md)（学習目標＝Kotlinコルーチンの習得、一人開発・段階的開発）。

---

## 採用パターン

**MVVM + Clean Architecture（軽量版）／ Hilt DI ／ Room ／ Coroutines + StateFlow**。

一人開発なので、教科書どおりの重厚な Clean Architecture ではなく、**保守できる範囲に間引いた**構成にしている。
下記「設計判断」がその間引きの記録。

---

## レイヤーと依存の向き

```
presentation ──▶ domain ◀── data
   (UI/VM)      (interface)   (impl/Room/Places)
                    ▲
         service ───┘（Repository 経由で書き込む）
```

- **presentation（プレゼンテーション）**: Compose 画面 + ViewModel。UI 状態を `StateFlow` で持ち、画面は購読するだけ。
- **domain（ドメイン）**: ドメインモデルと **Repository インターフェース**。他レイヤーに依存しない中心。
- **data（データ）**: Repository の実装、Room（DAO/Entity/マイグレーション）、Places 連携、設定の保存。
- **service（サービス）**: バックグラウンド GPS 追跡。UI ではなく **Repository を通して**永続化する。

**依存はすべて domain に向く**（presentation も data も domain の抽象に依存し、domain は誰にも依存しない）。
これにより data 層の差し替え（例: 将来のクラウド同期）が presentation に波及しない。

---

## データフロー

記録・表示の基本形（詳細な手順は [CLAUDE.md](../../CLAUDE.md) の「データフロー」）:

- **表示系**: Room（DAO）→ Repository → ViewModel（`StateFlow`）→ Compose（`collectAsState`）。
- **記録系**: `LocationTrackingService` が GPS 点を取得 → Repository → Room に永続化。
  補正・立ち寄り検出も記録中にこの流れへ相乗りする（[gps-smoothing.md](./gps-smoothing.md) / [places-and-stops.md](./places-and-stops.md)）。

UI とサービスの**唯一の合流点が Repository** で、記録中の書き込みと画面のリアクティブ更新がここで噛み合う。

---

## 設計判断（このプロジェクト固有の「なぜ」）

### UseCase 層を置かない

ViewModel から **Repository を直接呼ぶ**。一人開発で画面ごとのロジックが薄いため、UseCase を挟むと
ボイラープレートが増えるだけで得が小さい。ロジックが太ってきた画面が出たら、その画面にだけ UseCase を足す。

### Repository インターフェースは domain、実装は data

依存を反転させ、domain を技術（Room/Places）から独立させる。テストでは Repository をモックして
ViewModel を単体テストできる（[testing.md](./testing.md)）。

### Service も Repository 経由で書く

`LocationTrackingService` は Room を直接触らず Repository を通す。書き込み口を一本化することで、
記録中の増分保存（補正・立ち寄り）と画面表示が同じデータ経路に乗り、整合させやすい。

### Entity ↔ Domain を変換して分離

Room の Entity（永続化の都合）とドメインモデル（画面・ロジックの都合）を分け、境界で変換する。
DB スキーマの都合が presentation に漏れないようにするため。物理スキーマの詳細は
[model.md](../specs/model.md)（概念）／[places-and-stops.md](./places-and-stops.md)・[gps-smoothing.md](./gps-smoothing.md)（物理）。

### StateFlow に統一（LiveData 不使用）

コルーチンの学習目標に沿い、非同期・リアクティブは Coroutines + `StateFlow` に寄せる。

### DI は Hilt、手動 DI はしない

`@HiltViewModel` / `@AndroidEntryPoint` と DI モジュール（`di/`）で配線する。
アノテーション処理は KSP（kapt は廃止。理由は [CLAUDE.md](../../CLAUDE.md) のビルド制約）。

---

## 画面遷移

Navigation-Compose（`presentation/navigation/`）でボトムナビ4タブ＋詳細画面を構成する。
「場所」タブはネストグラフでグラフスコープの ViewModel を共有する。画面仕様は [screens.md](../specs/screens.md)。

---

## 関連ドキュメント

- 実装規約・パッケージツリー・ビルド制約: [CLAUDE.md](../../CLAUDE.md)
- 画面仕様: [screens.md](../specs/screens.md)
- データモデル（概念）: [model.md](../specs/model.md)
- 補正・立ち寄りの設計: [gps-smoothing.md](./gps-smoothing.md) / [places-and-stops.md](./places-and-stops.md)
- ログ / テスト / セキュリティ / パフォーマンス: [logging.md](./logging.md) / [testing.md](./testing.md) / [security.md](./security.md) / [performance.md](./performance.md)
