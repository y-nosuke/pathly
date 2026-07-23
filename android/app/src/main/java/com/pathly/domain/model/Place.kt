package com.pathly.domain.model

import java.util.Date

/**
 * 場所そのもの（経路とは独立）。立ち寄りで検出した場所・手動追加・将来の行きたい場所、
 * すべてここで管理する。詳細は docs/designs/places-and-stops.md を参照。
 *
 * @property name 表示名。null は未命名（Places 未解決 or 手動未入力）。
 */
data class Place(
  val id: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  val address: String?,
  val createdAt: Date,
  val updatedAt: Date,
)
