package com.pathly.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 立ち寄りと、その場所（ユーザー入力）＋Google 由来データを結合した読み取り用の型。
 * 表示名は places.name → google_places.name → 住所 → 座標 の順にフォールバックする
 * （docs/designs/place-info-enrichment.md）。
 */
data class StopWithPlace(
  @Embedded val stop: StopEntity,
  @Relation(parentColumn = "placeId", entityColumn = "id")
  val place: PlaceEntity,
  @Relation(parentColumn = "placeId", entityColumn = "placeId", entity = GooglePlaceEntity::class)
  val google: GooglePlaceWithCategory?,
)
