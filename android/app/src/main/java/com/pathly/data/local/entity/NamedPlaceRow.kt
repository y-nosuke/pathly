package com.pathly.data.local.entity

/**
 * 表示名の解決に必要な最小限の場所情報（ユーザー名と Google 由来の情報を1行で取る）。
 *
 * 以前は「全 place を読む → 近いものを探す → place ごとに google_places を引く」という
 * N+1 をしていた。近傍だけを1クエリで取れるようにするための行型。
 */
data class NamedPlaceRow(
  val id: Long,
  val name: String?,
  val latitude: Double,
  val longitude: Double,
  val googlePlaceId: String?,
  val googleName: String?,
  val googleAddress: String?,
  val category: String?,
)
