package com.pathly.presentation.common

/**
 * 分けた一覧のまとまり 1 つ（場所一覧の地域・業種、履歴一覧の年月 → specs/screens.md）。
 *
 * [key] は開閉を覚えるための名前（分け方の中で一意・アプリを閉じても変わらない）。[count] は中の項目すべて
 * （[children] の中も含む）の数。[items] はこのまとまりの直下に置く項目（地域なら市区町村が分からない場所）。
 */
data class ListGroup<T>(
  val key: String,
  val label: String,
  val count: Int,
  val children: List<ListGroup<T>> = emptyList(),
  val items: List<T> = emptyList(),
) {
  /** 中の項目すべて（下の段のまとまりの中も含む）。 */
  fun allItems(): List<T> = children.flatMap { it.allItems() } + items
}

/** 分けた一覧の 1 行（見出しか項目）。[depth] は字下げの段（いちばん外の見出しが 0）。 */
sealed interface ListGroupRow<out T> {
  val depth: Int

  /** 一覧（LazyColumn）での行の名前。項目は [Item.key]、見出しはまとまりの [ListGroup.key]。 */
  val listKey: Any
    get() = when (this) {
      is Header -> "group:${group.key}"
      is Item -> key
    }

  data class Header<T>(val group: ListGroup<T>, override val depth: Int, val expanded: Boolean) : ListGroupRow<T>

  data class Item<T>(val item: T, override val depth: Int, val key: Any) : ListGroupRow<T>
}

/**
 * まとまりを、一覧に並べる行にする。閉じたまとまりは見出しだけ。[expandAll] なら（名前の検索中）すべて開いて見せる。
 * 開いたまとまりの中は、下の段のまとまり → 直下の項目の順。[itemKey] は項目の行の名前（id など）。
 */
fun <T> flattenGroups(
  groups: List<ListGroup<T>>,
  itemKey: (T) -> Any,
  expandAll: Boolean,
  isOpen: (ListGroup<T>) -> Boolean,
): List<ListGroupRow<T>> {
  val rows = mutableListOf<ListGroupRow<T>>()
  fun add(group: ListGroup<T>, depth: Int) {
    val expanded = expandAll || isOpen(group)
    rows += ListGroupRow.Header(group, depth, expanded)
    if (!expanded) return
    group.children.forEach { add(it, depth + 1) }
    group.items.forEach { rows += ListGroupRow.Item(it, depth + 1, itemKey(it)) }
  }
  groups.forEach { add(it, 0) }
  return rows
}
