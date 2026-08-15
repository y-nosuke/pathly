# アーキテクチャ設計

Pathly Android の構成方針と、その背景にある設計判断をまとめる。
**「どのパッケージに何があるか」「実装規約」は [CLAUDE.md](../../CLAUDE.md) が持つ**。本書は重複を避け、
**「なぜこの構成にしたか（How の根拠）」**に絞る。具体的なクラス・シグネチャは実コードを正とする。

> 前提: 一人開発・段階的開発（[../roadmap.md](../roadmap.md)）と、学習目標＝Kotlin コルーチンの習得
> （[CLAUDE.md](../../CLAUDE.md)）。要望書に対応する項目がある機能設計とは違い、本書は開発体制側の都合で決まっている。

---

## 採用パターン

**MVVM + Clean Architecture（軽量版）／ Hilt DI ／ Room ／ Coroutines + StateFlow**。

一人開発なので、教科書どおりの重厚な Clean Architecture ではなく、**保守できる範囲に間引いた**構成にしている。
下記「設計判断」がその間引きの記録。

---

## レイヤーと依存の向き

```text
presentation ──▶ domain ◀── data
   (UI/VM)    (interface/UseCase)  (impl/Room/Places/Work)
                    ▲
         service ───┘（Repository と DAO で書き込む）
```

- **presentation（プレゼンテーション）**: Compose 画面 + ViewModel。UI 状態を `StateFlow` で持ち、画面は購読するだけ。
  画面別のパッケージに加え、画面をまたぐ部品を `common/`（`FloatingSheet`・地図描画・確認ダイアログ）と
  `stops/`（立ち寄りの追加・付け替え UI）に置く。
- **domain（ドメイン）**: ドメインモデル、**Repository インターフェース**、複数画面で共有する UseCase。
  他レイヤーに依存しない中心。
- **data（データ）**: Repository の実装、Room（DAO/Entity/マイグレーション）、Places 連携、設定の保存に加え、
  記録サービスの制御（`tracking/`）とバックグラウンドジョブ（`work/`）。
- **service（サービス）**: バックグラウンド GPS 追跡。UI からは切り離され、Repository と DAO で永続化する。

**依存はすべて domain に向く**（presentation も data も domain の抽象に依存し、domain は誰にも依存しない）。
これにより data 層の差し替え（例: 将来のクラウド同期）が presentation に波及しない。

---

## データフロー

記録・表示の基本形（詳細な手順は [CLAUDE.md](../../CLAUDE.md) の「データフロー」）:

- **表示系**: Room（DAO）→ Repository → ViewModel（`StateFlow`）→ Compose（`collectAsState`）。
- **記録系**: `LocationTrackingService` が GPS 点を取得 → Repository → Room に永続化。
  補正・立ち寄り検出も記録中にこの流れへ相乗りする（[smoothing.md](smoothing.md) / [stops.md](stops.md)）。

UI とサービスの**唯一の合流点が Repository** で、記録中の書き込みと画面のリアクティブ更新がここで噛み合う。

---

## 設計判断（このプロジェクト固有の「なぜ」）

### UseCase は「複数画面で重複したもの」だけ置く

原則は **ViewModel から Repository を直接呼ぶ**。一人開発で画面ごとのロジックが薄く、
全操作に UseCase を用意してもボイラープレートが増えるだけだから。

例外として、**同じ手順が 3 画面に重複した**ものだけ `domain/usecase/` に切り出してある。

- `PlaceEditUseCase` … 場所の登録・近接確認・紐付け・編集の差分適用。記録画面・経路詳細・場所タブに
  同じコードが散らばっており、しかも近接確認の分岐が Composable 側にあってテストできなかった。
- `AddManualStopUseCase` … 手動での立ち寄り追加（近接確認の要否判断を含む）。記録画面と経路詳細で共有。

「層として全部に置く」のではなく「重複とテスト不能を潰すために置く」という基準にしている。
1 画面でしか使わない操作は ViewModel に置いたままでよい。

### Repository インターフェースは domain、実装は data

依存を反転させ、domain を技術（Room/Places）から独立させる。テストでは Repository をモックして
ViewModel・UseCase を単体テストできる（[testing.md](./testing.md)）。

### Service の Android 依存は data 層へ寄せる

記録サービスは 2 つに分かれている。

- `service/LocationTrackingService` … フォアグラウンドサービス本体。位置の受信・通知・永続化。
  補正と立ち寄り検出は Repository（`updateSmoothedForTrack` / `updateStopsForTrack`）を通すが、
  トラックと生点の insert は DAO を直接呼ぶ（Repository を挟んでも素通しになるだけのため）。
- `data/tracking/TrackingController` … サービスの起動・停止・バインドと、権限／位置情報 ON-OFF／
  電池の最適化といった**端末側の状態**をまとめて扱うアプリスコープのクラス。

分けたのは、これらを ViewModel が直接持つと (1) Service 参照を抱えて lint の `StaticFieldLeak` が出る、
(2) Android framework 依存で ViewModel のユニットテストがほぼ書けない、という 2 つの実害があったため。
ViewModel は `TrackingController` が公開する Flow と suspend 関数だけを見る。

### バックグラウンドジョブは WorkManager

「オフラインで記録した立ち寄りの名前解決」のように、**アプリが起動していなくても・通信が戻ってから
一度走ればよい**処理は WorkManager に載せる（`data/work/PlaceNameCatchUpWorker`）。
起動時に自前で叩くと、そのとき圏外なら次の起動まで解決されないため。

ワーカーの組み立てに Hilt を使うので、WorkManager の自動初期化はマニフェストで止め、
`PathlyApplication`（`Configuration.Provider`）が初期化を担う。

### Entity ↔ Domain を変換して分離

Room の Entity（永続化の都合）とドメインモデル（画面・ロジックの都合）を分け、境界で変換する。
DB スキーマの都合が presentation に漏れないようにするため。物理スキーマの詳細は
[model.md](../specs/model.md)（概念）／[places.md](places.md)・[smoothing.md](smoothing.md)（物理）。

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
- 補正・立ち寄り・場所の設計: [smoothing.md](smoothing.md) / [stops.md](stops.md) / [places.md](places.md)
- ログ / テスト / セキュリティ: [logging.md](logging.md) / [testing.md](testing.md) / [security.md](security.md)
