package com.pathly.domain.model

/**
 * 場所の業種（Google Places のカテゴリ）。
 *
 * [code] が正の値で、[displayName] は人に見せるためだけの値。表示名はロケール依存なので、
 * 判定（アイコンの出し分けなど）は必ず [code] で行うこと。
 *
 * Google が業種を返さなかった場所ではカテゴリそのものを持たない（この型が null になる）。
 * 表示名だけあって [code] が無い状態は作らない。
 *
 * @property code Google の primaryType（`cafe` / `park` / `restaurant` など）。
 * @property displayName Google の primaryTypeDisplayName（「カフェ」）。取れなければ null。
 */
data class PlaceCategory(
  val code: String,
  val displayName: String? = null,
) {
  /** 一覧などに出す文字列。表示名が無ければ code をそのまま見せる（空欄よりは手掛かりになる）。 */
  val label: String get() = displayName ?: code
}
