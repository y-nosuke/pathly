package com.pathly.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 場所と、その行きたい登録（あれば）を結合した読み取り用の型。
 * wishlist は 0..1（未登録なら null）。「場所」タブの一覧に使う。
 */
data class PlaceWithWishlist(
  @Embedded val place: PlaceEntity,
  @Relation(parentColumn = "id", entityColumn = "placeId")
  val wishlist: WishlistEntity?,
)
