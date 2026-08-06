package com.pathly.data.local.entity

import java.util.Date

/**
 * 「場所」タブの一覧1行。places に google_places（あれば）・wishlist（あれば）・立ち寄り件数を
 * LEFT JOIN した射影。
 *
 * `@Relation` ではなく明示的な JOIN クエリの結果にすることで、Room が places / google_places /
 * wishlist / stops すべてのテーブルを購読し、命名・行きたいの付け外し・立ち寄り検出に即座に追従する。
 */
data class PlaceWithWishlist(
  val id: Long,
  // places（ユーザー入力）
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  val note: String?,
  val createdAt: Date,
  val updatedAt: Date,
  // google_places（未解決なら null）
  val googlePlaceId: String?,
  val googleName: String?,
  val googleAddress: String?,
  val category: String?,
  // wishlist（未登録なら null）
  val wishlistId: Long?,
  val priority: Int?,
  val visitedAt: Date?,
  // この場所への立ち寄り（訪問）件数。>0 なら実際に訪れている。
  val visitCount: Int,
  // 直近の立ち寄り（arrivalTime の最大）。立ち寄りが無ければ null。訪問順の並べ替えに使う。
  val lastStopAt: Date?,
)
