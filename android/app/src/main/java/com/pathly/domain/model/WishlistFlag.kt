package com.pathly.domain.model

/**
 * 行きたいの旗の見た目（→ docs/specs/places.md「かつて行きたいだったことを見せる」）。
 * 行きたいは行けば終わるので、いまは行きたいでなくても「行きたいで行ったことがある」場所を見分けられるようにする。
 */
enum class WishlistFlag {
  /** いま行きたい（塗りの旗）。押すと外す。 */
  ACTIVE,

  /** いまは行きたいでないが、行きたいで行ったことがある（輪郭の旗にチェック）。押すと入れ直す。 */
  FULFILLED,

  /** 一度も行きたいで行っていない（輪郭の旗）。押すと入れる。 */
  NONE,
  ;

  companion object {
    fun of(isWishlisted: Boolean, fulfilledWishCount: Int): WishlistFlag = when {
      isWishlisted -> ACTIVE
      fulfilledWishCount > 0 -> FULFILLED
      else -> NONE
    }
  }
}
