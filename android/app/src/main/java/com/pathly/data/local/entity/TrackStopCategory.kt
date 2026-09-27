package com.pathly.data.local.entity

/**
 * 経路（trackId）に立ち寄った場所の業種。経路一覧を業種で絞り込むための集計結果。
 *
 * @property hasGoogleInfo その場所に Google の施設情報（`google_places` の行）があるか。無ければ未分類。
 * @property categoryCode Google の業種（`cafe` など）。施設情報が無い・業種が無ければ null。
 */
data class TrackStopCategory(
  val trackId: Long,
  val hasGoogleInfo: Boolean,
  val categoryCode: String?,
)
