package com.pathly.domain.model

/**
 * 立ち寄り（stop）の一括削除の結果。
 * 選択した訪問はすべて削除し、参照する立ち寄りが無くなった場所だけを場所ごと削除する。
 * 他の訪問（＝他の履歴など）が残る場所は削除せず保持する（[placesKept]）。
 */
data class StopDeletionResult(
  /** 実際に削除した立ち寄り（訪問）の件数。 */
  val stopsDeleted: Int,
  /** 参照が無くなり場所ごと削除した件数。 */
  val placesDeleted: Int,
  /** 他に訪問が残るため保持した場所の件数。 */
  val placesKept: Int,
)
