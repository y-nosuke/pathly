package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 場所そのもの。経路とは独立して管理する（docs/designs/places-and-stops.md）。
 * ユーザー入力（自分で付けた名前・メモ）と座標だけを持つ。Google 由来の名前・住所・カテゴリは
 * google_places に分離する（docs/designs/place-info-enrichment.md / adr/0001）。
 */
@Entity(tableName = "places")
data class PlaceEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 自分で付けた名前（null=未命名。Google 由来の名前は入れない）。 */
  val name: String? = null,
  val latitude: Double,
  val longitude: Double,
  /** 場所のメモ（「行きたい」登録と独立に持てる。null/空=メモ無し）。 */
  val note: String? = null,
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
