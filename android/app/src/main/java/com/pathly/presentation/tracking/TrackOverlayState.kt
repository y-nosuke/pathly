package com.pathly.presentation.tracking

import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.Stop

/** 記録画面の地図に同時に重ねられる過去の経路の本数。色の数と同じ。 */
const val TRACK_OVERLAY_MAX = 8

/** 地図に重ねている過去の経路。色は [colorIndex]（0 から [TRACK_OVERLAY_MAX] 未満）で持つ。 */
data class OverlayTrack(
  val track: GpsTrack,
  val colorIndex: Int,
  // 線の点（補正後・欠落で分けた区間ごと）。読み込みが終わるまでは空。
  val segments: List<List<GpsPoint>> = emptyList(),
  // この経路の立ち寄り（訪問順）。線と同じ色の番号つきバッジで出す。
  val stops: List<Stop> = emptyList(),
  // 線と立ち寄りを読み終えたか（地図を寄せるのは読み終えてから）。
  val isLoaded: Boolean = false,
) {
  /** 線と立ち寄りが全部入る範囲。何も無ければ null。 */
  val bounds: GeoBounds?
    get() {
      val lats = segments.flatten().map { it.latitude } + stops.map { it.place.latitude }
      val lngs = segments.flatten().map { it.longitude } + stops.map { it.place.longitude }
      if (lats.isEmpty()) return null
      return GeoBounds(south = lats.min(), west = lngs.min(), north = lats.max(), east = lngs.max())
    }
}

/** 一覧の行から「この経路へ寄せる」を押した合図。同じ経路を押し直しても効くよう [nonce] で区別する。 */
data class TrackFocusRequest(val trackId: Long, val nonce: Int)

/**
 * 記録画面の「経路」（地図に映っている範囲の過去の経路を一覧から選んで重ねる）の状態。
 * 選んだ経路は範囲を探し直しても外すまで残る。一覧を閉じると重ねた線も消す（一覧と線をひと続きで使う）。
 */
data class TrackOverlayState(
  // 一覧のシートを出しているか（閉じると重ねた線も消す）。
  val isSheetOpen: Boolean = false,
  val isSearching: Boolean = false,
  // 最後に探した範囲に入る経路（新しい順）。
  val inRange: List<GpsTrack> = emptyList(),
  // 地図に重ねている経路（選んだ順）。
  val selected: List<OverlayTrack> = emptyList(),
  val errorMessage: String? = null,
  // 地図を寄せる対象（線を読み終えたら画面が寄せて、[TrackOverlayViewModel.consumeFocus] で消す）。
  val focusRequest: TrackFocusRequest? = null,
) {
  val selectedIds: Set<Long>
    get() = selected.mapTo(mutableSetOf()) { it.track.id }

  /** まだ選べるか（上限は [TRACK_OVERLAY_MAX] 本）。 */
  val canSelectMore: Boolean
    get() = selected.size < TRACK_OVERLAY_MAX

  /** 選んでいるが、最後に探した範囲には入っていない経路（一覧の下に出し、外せるようにする）。 */
  val selectedOutOfRange: List<GpsTrack>
    get() {
      val inRangeIds = inRange.mapTo(mutableSetOf()) { it.id }
      return selected.map { it.track }.filter { it.id !in inRangeIds }
    }

  fun colorIndexOf(trackId: Long): Int? = selected.firstOrNull { it.track.id == trackId }?.colorIndex
}

/** 空いている色のうち最も小さい番号。すべて使っていれば null。 */
internal fun nextFreeColorIndex(used: Collection<Int>): Int? = (0 until TRACK_OVERLAY_MAX).firstOrNull { it !in used }
