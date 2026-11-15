package com.pathly.domain.model

/**
 * 場所一覧の分け方（→ specs/screens.md「5. 場所画面」）。分けたまとまりは見出しで開閉できる。
 * 絞り込みとは独立した見せ方の設定で、アプリを閉じても残す。
 */
enum class PlaceGrouping(val label: String) {
  NONE("分けない"),

  /** 都道府県 → 市区町村（海外は国 → 都市）の 2 段。 */
  REGION("地域で分ける"),

  /** 業種（絞り込みの業種と同じ区分）の 1 段。 */
  CATEGORY("業種で分ける"),
}
