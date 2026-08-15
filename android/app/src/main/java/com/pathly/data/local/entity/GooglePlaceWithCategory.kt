package com.pathly.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Google 由来の場所データと、その業種（マスタ）を結合した読み取り用の型。
 * 業種は正規化して別テーブルに置いたので、表示名を出すにはこの結合が要る。
 */
data class GooglePlaceWithCategory(
  @Embedded val google: GooglePlaceEntity,
  @Relation(parentColumn = "categoryId", entityColumn = "id")
  val category: GooglePlaceCategoryEntity?,
)
