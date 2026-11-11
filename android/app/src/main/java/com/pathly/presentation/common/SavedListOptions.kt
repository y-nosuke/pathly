package com.pathly.presentation.common

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.RegionKey

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

// 地域の選択は「都道府県<区切り>市区町村」の文字列のリストで持つ。地域不明は都道府県を空にする。
private const val REGION_SEPARATOR = '\u001F'

/** [key] に保存した地域の選択を読む。無ければ空。 */
fun SavedStateHandle.getRegionSet(key: String): Set<RegionKey> {
  val encoded = get<ArrayList<String>>(key) ?: return emptySet()
  return encoded.map { s ->
    val prefecture = s.substringBefore(REGION_SEPARATOR).ifEmpty { null }
    val city = s.substringAfter(REGION_SEPARATOR, "").ifEmpty { null }
    RegionKey(prefecture, city)
  }.toSet()
}

/** 地域の選択を [key] に保存する。 */
fun SavedStateHandle.putRegionSet(key: String, values: Set<RegionKey>) {
  this[key] = ArrayList(values.map { "${it.prefecture.orEmpty()}$REGION_SEPARATOR${it.city.orEmpty()}" })
}
