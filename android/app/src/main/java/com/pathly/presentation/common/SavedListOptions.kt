package com.pathly.presentation.common

import androidx.lifecycle.SavedStateHandle

// 一覧の絞り込み・並べ替えを SavedStateHandle に置くための小道具。
// アプリが裏で落とされても、戻ったときに同じ条件で一覧を出せるようにする（アプリを閉じて開き直したら初期状態）。
// enum は名前（文字列）で持つ。版が変わって名前が無くなっていたら既定値に戻す。

/** [key] に保存した enum を読む。無い・知らない名前なら [default]。 */
inline fun <reified E : Enum<E>> SavedStateHandle.getEnum(key: String, default: E): E = get<String>(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** enum を名前で [key] に保存する。 */
fun SavedStateHandle.putEnum(key: String, value: Enum<*>) {
  this[key] = value.name
}

/** [key] に保存した enum の集合を読む。無ければ空。知らない名前は読み飛ばす。 */
inline fun <reified E : Enum<E>> SavedStateHandle.getEnumSet(key: String): Set<E> {
  val names = get<ArrayList<String>>(key) ?: return emptySet()
  return enumValues<E>().filter { it.name in names }.toSet()
}

/** enum の集合を名前のリストで [key] に保存する。 */
fun SavedStateHandle.putEnumSet(key: String, values: Set<Enum<*>>) {
  this[key] = ArrayList(values.map { it.name })
}
