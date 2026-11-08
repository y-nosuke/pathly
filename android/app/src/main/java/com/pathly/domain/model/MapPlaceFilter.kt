package com.pathly.domain.model

/**
 * 地図（記録画面・経路詳細・場所詳細）に出す「登録済みの場所」の絞り込み。3 つの地図で 1 つを共有する。
 *
 * 場所一覧の絞り込みとは**別に持つ**（連動しない）。一覧は特定の場所を探す一時的な絞り込み、地図はしばらく
 * 続けて使う見方なので、共有すると一覧で絞ったあと地図から場所が知らないうちに消えて見えるため（#128）。
 * アプリを閉じても残す（「登録済みの場所を表示」と同じく設定に保存する）。
 *
 * 軸は一覧と同じ 3 つで、すべて AND。行きたい・訪問は 3 状態（null＝指定なし）、業種は複数選べて OR。
 *
 * @property wishlisted true＝行きたいだけ、false＝行きたい以外だけ、null＝指定なし。
 * @property visited true＝訪問済みだけ、false＝未訪問だけ、null＝指定なし。
 * @property categories 選んだ業種のどれかに当たる場所を残す。空＝指定なし。
 */
data class MapPlaceFilter(
  val wishlisted: Boolean? = null,
  val visited: Boolean? = null,
  val categories: Set<PlaceCategoryFacet> = emptySet(),
) {
  /** 何も絞っていないか。 */
  val isEmpty: Boolean get() = wishlisted == null && visited == null && categories.isEmpty()

  /** [place] がこの条件に合うか（地図に出すか）。 */
  fun matches(place: RegisteredPlace): Boolean = (wishlisted == null || place.isWishlisted == wishlisted) &&
    (visited == null || place.isVisited == visited) &&
    (categories.isEmpty() || place.categoryFacet in categories)
}

/** 3 状態の切り替え（指定なし → あり → なし → 指定なし…）。地図のチップをタップしたときの次の状態。 */
fun Boolean?.nextTriState(): Boolean? = when (this) {
  null -> true
  true -> false
  false -> null
}
