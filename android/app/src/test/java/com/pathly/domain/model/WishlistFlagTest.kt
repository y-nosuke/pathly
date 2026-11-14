package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WishlistFlagTest {

  @Test
  fun currentWishWinsOverHistory() {
    assertEquals(WishlistFlag.ACTIVE, WishlistFlag.of(isWishlisted = true, fulfilledWishCount = 2))
    assertEquals(WishlistFlag.ACTIVE, WishlistFlag.of(isWishlisted = true, fulfilledWishCount = 0))
  }

  @Test
  fun fulfilledOnlyWhenNotWishedNowButHasHistory() {
    assertEquals(WishlistFlag.FULFILLED, WishlistFlag.of(isWishlisted = false, fulfilledWishCount = 1))
    assertEquals(WishlistFlag.NONE, WishlistFlag.of(isWishlisted = false, fulfilledWishCount = 0))
  }
}
