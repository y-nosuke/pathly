package com.pathly.data.local.entity

import java.util.Date

/**
 * 「場所」タブの一覧1行。places に wishlist（あれば）と立ち寄り件数を LEFT JOIN した射影。
 *
 * `@Relation` ではなく明示的な JOIN クエリの結果にすることで、Room が places / wishlist /
 * stops すべてのテーブルを購読し、行きたいの付け外しや立ち寄り検出に即座に追従する。
 */
data class PlaceWithWishlist(
  val id: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  val address: String?,
  val createdAt: Date,
  val updatedAt: Date,
  // wishlist（未登録なら null）
  val wishlistId: Long?,
  val priority: Int?,
  val memo: String?,
  val visitedAt: Date?,
  // この場所への立ち寄り（訪問）件数。>0 なら実際に訪れている。
  val visitCount: Int,
)
