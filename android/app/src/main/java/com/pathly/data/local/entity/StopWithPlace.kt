package com.pathly.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 立ち寄りと、その場所を結合した読み取り用の型。
 */
data class StopWithPlace(
  @Embedded val stop: StopEntity,
  @Relation(parentColumn = "placeId", entityColumn = "id")
  val place: PlaceEntity,
)
