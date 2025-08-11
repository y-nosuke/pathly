# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

**アプリ名：** Pathly（お出掛け記録アプリ）  
**目的：** お出掛けの記録・計画・振り返りを行う  
**主要ユーザー：** 出掛けるのが好きな人、カップルなど（Android + iPhone環境）  
**開発方針：** 段階的開発（リアルタイム記録 → 事後振り返り → 事前計画）  
**開発体制：** 一人開発、アジャイル手法、Claude Code活用

## 技術スタック

### メインプラットフォーム

- **Android：** Kotlin + コルーチン + Jetpack Compose
- **状態管理：** ViewModel + StateFlow（コルーチン対応）
- **アーキテクチャ：** MVVM + Clean Architecture

### バックエンド・インフラ

- **BaaS：** Supabase（PostgreSQL + Edge Functions）
- **Web：** Next.js + Vercel
- **地図：** Google Maps SDK
- **アーキテクチャ：** サーバーレス構成（常駐サーバーなし）

### データ管理

- **データベース：** PostgreSQL（Supabase）
- **ローカル保存：** Android Encrypted SharedPreferences
- **同期：** リアルタイム同期（Supabase Realtime）

## 現在の開発フェーズ

### Phase 1: リアルタイム記録（MVP）- 現在開発中

**確定機能：**

1. ✅ GPS経路の自動記録・保存
2. ✅ 記録したデータの基本的な一覧表示
3. ✅ 地図上での軌跡表示
4. ✅ ローカルデータ保存

**Phase 2以降に先送りされた機能：**

- 外出の自動検知→記録開始
- 立ち寄り場所の自動検出（50m圏内+3分滞在）
- 手動での場所記録
- 写真撮影機能
- クラウド同期（二人での共有）

## データベース構造（PostgreSQL）

### 主要テーブル

- **users** - ユーザー情報
- **dates** - デート情報（計画・実行済み）
- **date_participants** - デート参加者（カップル）
- **tracks** - GPS軌跡データ
- **gps_points** - GPS座標点（原データ・補正後）
- **stops** - 立ち寄り場所
- **media** - 写真・動画・音声メモ
- **stop_media** - 場所とメディアの関連
- **tags** - タグ情報
- **stop_tags** - 場所とタグの関連

### 重要な設計思想

- GPS座標は原データと補正後データの両方を保存
- メディアファイルは位置情報付きで管理
- タグシステムによる柔軟な分類

## 主要機能概要

### Phase 1（MVP）機能

1. **GPS軌跡記録** - バックグラウンド動作、30秒間隔取得
2. **軌跡一覧表示** - 日付別の記録一覧
3. **地図表示** - Google Maps上での軌跡表示
4. **ローカル保存** - オフライン対応

### 将来実装予定機能

- **立ち寄り判定** - 50m圏内+3分滞在の自動検出
- **写真・動画記録** - 位置情報付きメディア管理
- **事後編集** - 場所名、評価、コメント追加
- **事前計画** - 行きたい場所リスト、ルート計画
- **データ共有** - カップル間でのリアルタイム同期

## 開発環境・コマンド

### 初期セットアップ（予定）

```bash
# Android開発環境
# TODO: Kotlin + Jetpack Composeプロジェクト初期化
# TODO: Supabaseクライアント設定
# TODO: Google Maps SDK設定

# Web管理画面
# TODO: Next.jsプロジェクト初期化
# TODO: Vercelデプロイ設定

# データベース
# TODO: Supabaseプロジェクト作成
# TODO: テーブルスキーマ適用
```

### 開発コマンド（将来追加予定）

```bash
# TODO: Android build/test commands
# TODO: Web build/test commands
# TODO: Database migration commands
# TODO: Linting/formatting commands
```

## セキュリティ・パフォーマンス考慮事項

### セキュリティ

- **認証：** Supabase Auth（ID+パスワード）
- **データ暗号化：** Supabase自動暗号化 + 機密データはアプリレベル暗号化
- **通信：** HTTPS/TLS必須
- **ローカル：** Android Encrypted SharedPreferences

### パフォーマンス

- **GPS取得：** 30秒間隔、PRIORITY_BALANCED_POWER_ACCURACY
- **データ同期：** 差分同期、バッチ処理
- **画像：** 最大2MB圧縮
- **オフライン：** ローカル保存→後で同期

### エラー対応

- **GPS失敗：** 権限要求、設定案内、最後の既知位置使用
- **ネットワーク：** 自動リトライ（指数バックオフ）、オフライン対応
- **クラッシュ：** Supabase Error Tracking、graceful degradation

## UI設計方針

### ナビゲーション構造

- **タブ型：** [記録] [履歴] [地図] [計画] [設定]
- **記録開始：** ホーム画面の大きなボタン + 通知バーのクイックアクセス

### デート中操作（Phase 2以降）

- ワンタップ操作重視
- 大きなボタン設計
- ステータス表示
- リアルタイム情報更新

## 料金・コスト管理

### 控えめ利用（月額$0-5）

- Supabase: $0（無料枠内）
- Vercel: $0（Hobbyプラン）
- Google Maps: $0-5（無料枠内）

### 中程度利用（月額$35-45）

- Supabase: $25（Proプラン）
- Vercel: $0（Hobbyプラン）
- Google Maps: $10-20

## その他重要な開発指針

- **通知機能：** なし（自動動作を優先）
- **学習目標：** Kotlinコルーチンの習得
- **コスト重視：** 無料枠最大活用、段階的スケールアップ
- **実装方針：** 詳細設計は実装時に決定（アジャイル）
- **プライバシー：** 位置情報削除は個人に委ね、自動削除なし
