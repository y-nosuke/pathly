package com.pathly.data.local.entity

import com.pathly.domain.model.PlaceCategory

/**
 * 表示名の解決に必要な最小限の場所情報（ユーザー名と Google 由来の情報を1行で取る）。
 *
 * 以前は「全 place を読む → 近いものを探す → place ごとに google_places を引く」という
 * N+1 をしていた。近傍だけを1クエリで取れるようにするための行型。
 */
data class NamedPlaceRow(
  val id: Long,
  val name: String?,
  /** 同定用のアンカー（places の座標）。距離判定はこれで行う（→ adr/0023）。 */
  val latitude: Double,
  val longitude: Double,
  val googlePlaceId: String?,
  val googleName: String?,
  val googleAddress: String?,
  /** Google が持つ施設の代表点（表示用）。候補に焼き込んで表示へ引き継ぐ。無ければ null。 */
  val googleLatitude: Double?,
  val googleLongitude: Double?,
  // 業種はマスタ（google_place_categories）から結合して持つ。code が無ければ業種なし。
  val categoryCode: String?,
  val categoryDisplayName: String?,
) {
  val category: PlaceCategory? get() = categoryCode?.let { PlaceCategory(it, categoryDisplayName) }
}
