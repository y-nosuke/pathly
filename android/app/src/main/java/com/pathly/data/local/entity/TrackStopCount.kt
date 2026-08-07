package com.pathly.data.local.entity

/** 経路（trackId）ごとの立ち寄り件数。一覧の件数表示・並べ替えに使う集計結果。 */
data class TrackStopCount(
  val trackId: Long,
  val count: Int,
)
