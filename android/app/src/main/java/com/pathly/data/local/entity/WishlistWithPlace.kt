package com.pathly.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 行きたい場所と、その場所を結合した読み取り用の型。
 */
data class WishlistWithPlace(
  @Embedded val wishlist: WishlistEntity,
  @Relation(parentColumn = "placeId", entityColumn = "id")
  val place: PlaceEntity,
)
