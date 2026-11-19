package com.pathly.presentation.common

import java.text.Normalizer
import java.util.Locale

// 一覧（場所・経路）を名前で絞り込むための照合（入力の窓は ListHeader）。端末内のデータだけで絞る（Places API は使わない）。

/**
 * [text] が検索語 [query] を含むか。検索語が空（空白だけ）なら絞らない（true）。
 *
 * 全角・半角（英数字・記号・カタカナ）、英字の大文字・小文字、ひらがな・カタカナの違いは無視する
 * （NFKC で揃えて小文字にし、カタカナをひらがなに置き換えてから比べる）。漢字の読みでは探さない。
 */
fun matchesNameQuery(text: String, query: String): Boolean {
  val q = normalizeForSearch(query.trim())
  if (q.isEmpty()) return true
  return normalizeForSearch(text).contains(q)
}

private fun normalizeForSearch(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT).katakanaToHiragana()

// カタカナ（ァ〜ヶ）を、同じ並びにあるひらがな（ぁ〜ゖ）に置き換える。「ヴ」は「ゔ」になる。
// 対応するひらがなが無い文字（ヷ〜ヺ など）と、ひらがな・カタカナで共通の「ー」はそのまま。
private const val KATAKANA_FIRST = 'ァ'
private const val KATAKANA_LAST = 'ヶ'
private const val KATAKANA_TO_HIRAGANA = 0x60

private fun String.katakanaToHiragana(): String = map { c -> if (c in KATAKANA_FIRST..KATAKANA_LAST) c - KATAKANA_TO_HIRAGANA else c }.joinToString("")
