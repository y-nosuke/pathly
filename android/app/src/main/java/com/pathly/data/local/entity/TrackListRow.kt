package com.pathly.data.local.entity

import androidx.room.Embedded

/**
 * 履歴一覧の1行。GPS点そのものはロードせず、件数だけを集計で取る。
 *
 * 以前は [GpsTrackWithPoints] で全経路の全点を読み込み、表示のたびに平滑化して距離を
 * 計算し直していた。経路が増えるほど重くなるうえ UI スレッドで走っていたため、
 * 距離は gps_tracks に焼き込んだ値（[GpsTrackEntity.totalDistanceMeters]）を使い、
 * 点数はこの集計で賄う。
 */
data class TrackListRow(
  @Embedded
  val track: GpsTrackEntity,
  /** この経路の生GPS点の件数（一覧の「◯点」表示用）。 */
  val pointCount: Int,
)
