# Supabase データベース設計

> **注意**: このファイルは将来の実装用です。  
> 現在はAndroid Roomのみを使用してローカル完結機能を実装中です。

## 概要

Supabase（PostgreSQL）を使用したクラウドデータベース設計。
複数デバイス間でのデータ同期、カップル間でのデータ共有、Webアプリからのアクセス機能を提供予定。

## 将来の設計予定

### 主な機能

- Android Roomとの双方向同期
- カップル間でのリアルタイムデータ共有
- Webブラウザからのデータアクセス
- Row Level Security（RLS）によるセキュリティ

### 設計方針

- Android Roomと同一のデータ構造を維持
- UUIDを使用した重複回避
- リアルタイム同期対応
- 適切なアクセス制御

## TODO: 詳細設計項目

以下の項目について詳細設計を行う予定：

### テーブル設計

- [ ] users テーブル（ユーザー情報）
- [ ] tracks テーブル（Android Roomと同期）
- [ ] gps_points テーブル（Android Roomと同期）
- [ ] user_relationships テーブル（カップル関係管理）

### セキュリティ設計

- [ ] Row Level Security（RLS）ポリシー
- [ ] 認証・認可機能
- [ ] データ暗号化方針

### API設計

- [ ] Edge Functions設計
- [ ] リアルタイム同期機能
- [ ] データ競合解決ロジック

### 同期設計

- [ ] Android Room ↔ Supabase同期戦略
- [ ] オフライン対応
- [ ] 競合解決アルゴリズム

---

**このファイルは Android Room実装完了後に詳細化予定**
