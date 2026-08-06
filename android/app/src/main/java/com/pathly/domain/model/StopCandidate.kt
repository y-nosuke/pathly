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
  val category: String? = null,
  val googlePlaceId: String? = null,
)
