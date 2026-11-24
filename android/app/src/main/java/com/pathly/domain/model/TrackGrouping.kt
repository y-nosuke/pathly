package com.pathly.domain.model

/**
 * 履歴一覧の分け方（→ specs/screens.md「2. 履歴画面」）。分けたまとまりは見出しで開閉できる。
 * 絞り込みとは独立した見せ方の設定で、アプリを閉じても残す。
 */
enum class TrackGrouping(val label: String) {
  NONE("分けない"),

  /** 年 → 月の 2 段。経路はお出掛けの開始の日の年月に入れる。 */
  MONTH("年月で分ける"),
}

/**
 * 既定の開閉が決まっている一覧の、押して変えた開閉（履歴の年月）。開いたもの・閉じたものを別に覚え、
 * どちらにも無いまとまりは既定（いちばん新しい年と月だけ開く）に従う。既定が移っても（月が替わる）、
 * 押して決めたものはそのまま残る。
 */
data class GroupOpenState(
  val opened: Set<String> = emptySet(),
  val closed: Set<String> = emptySet(),
) {
  /** [key] のまとまりが開いているか。押していなければ [openByDefault]。 */
  fun isOpen(key: String, openByDefault: Boolean): Boolean = when (key) {
    in opened -> true
    in closed -> false
    else -> openByDefault
  }

  /** [key] を、いま開いているか（[currentlyOpen]）の逆にした状態。 */
  fun toggled(key: String, currentlyOpen: Boolean): GroupOpenState = if (currentlyOpen) copy(opened = opened - key, closed = closed + key) else copy(opened = opened + key, closed = closed - key)
}
