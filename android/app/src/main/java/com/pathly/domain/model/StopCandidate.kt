package com.pathly.domain.model

/**
 * 再解析の候補（「一覧に無い立ち寄り」）。検出結果 [detected] に、**追加前の判断用**の
 * 表示名を添えたもの。名前は近くの命名済み place の再利用か Places 解決で用意する
 * （オフライン等で付かなければ null）。永続化はしない。追加時に [name]/[googlePlaceId] を
 * そのまま焼き込むことで Places を二度叩かない。
 */
data class StopCandidate(
  val detected: DetectedStop,
  val name: String? = null,
  val address: String? = null,
  val category: PlaceCategory? = null,
  val googlePlaceId: String? = null,
  /**
   * Google が持つ施設の代表点（表示用）。追加時に google_places へ焼き込む。
   * 場所の同定に使うアンカーは [detected] の重心なので、こちらは同定には使わない（→ adr/0023）。
   */
  val googleLatitude: Double? = null,
  val googleLongitude: Double? = null,
)
