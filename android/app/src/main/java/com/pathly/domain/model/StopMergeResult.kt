package com.pathly.domain.model

/**
 * 立ち寄り（訪問）の手動統合の結果。同じ場所への複数の訪問を1件にまとめたときに返す。
 * まとめる条件を満たさない（2件未満・場所や経路が混ざる）ときは統合そのものを行わない（→ adr/0024）。
 */
data class StopMergeResult(
  /** 残った立ち寄りの id。到着が最も早かった1件をそのまま使う（番号・地図の対応が飛ばない）。 */
  val survivingStopId: Long,
  /** 統合の対象になった立ち寄りの件数（残った1件を含む）。 */
  val mergedCount: Int,
)
