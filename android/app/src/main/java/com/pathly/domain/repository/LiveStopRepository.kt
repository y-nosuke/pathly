package com.pathly.domain.repository

import com.pathly.domain.model.Stop
import kotlinx.coroutines.flow.StateFlow
import java.util.Date

/**
 * 記録中の立ち寄り（検出した立ち寄り中・「今ここを立ち寄り」で手で始めた立ち寄り中）を担う。
 * 立ち寄り中はメモリだけに持ち、離れたら（記録を止めたら）立ち寄りとして保存する。
 * 詳細は docs/designs/places-and-stops.md を参照。分け方は adr/0048。
 */
interface LiveStopRepository {

  /** 記録中の「立ち寄り中」（メモリ保持・非永続）。3分超で place を先行確定して表示する。 */
  val currentStop: StateFlow<Stop?>

  /**
   * 記録中に呼ばれ、確定した立ち寄り（離れたもの）だけを差分保存する（案A）。
   * 末尾の滞在中クラスタが3分を超えたら place を先行確定して [currentStop] に流す。
   * オンラインなら未解決の place を名前解決する。[isFinal] で末尾も確定する。
   */
  suspend fun updateStopsForTrack(trackId: Long, isFinal: Boolean)

  /**
   * 「今ここを立ち寄り」（記録中だけ）: その場で「立ち寄り中」を始める（3 分待たない）。到着は [arrivalTime]。
   * 場所は手動追加と同じ決め方（候補を選んだら施設・名前だけなら 30m 以内の既存か新規）。
   *
   * 押した地点（[anchorLatitude]・[anchorLongitude]）から検出と同じ半径の外に出たら、自動検出の立ち寄りと同じく確定する
   * （出発は最後に半径の中にいた時刻。3 分に満たなくても残す）。記録を止めたときは、その時刻で確定する。
   * 手で始めた立ち寄り中のあいだ、同じ時間帯に検出した立ち寄りは出さず、保存もしない。
   *
   * すでに立ち寄り中（検出・手動のどちらでも）なら、到着はそのままに場所だけ選び直す。[currentStop] にすぐ流す。
   */
  suspend fun startLiveStop(
    trackId: Long,
    anchorLatitude: Double,
    anchorLongitude: Double,
    arrivalTime: Date,
    latitude: Double,
    longitude: Double,
    name: String?,
    googlePlaceId: String?,
    googleName: String?,
  )

  /**
   * 立ち寄り中（検出・手動のどちらでも）のメモを付ける・直す（空なら消す）。確定した立ち寄りに入る。
   * 検出の立ち寄り中は、これ以後は手で始めたものと同じ扱いになる（離れたかの判定も同じ）。
   */
  suspend fun updateLiveStopNote(trackId: Long, note: String?)

  /** 立ち寄り中の到着時刻を直す（滞在の始まり）。出発は離れたときに決まるので直さない。 */
  suspend fun updateLiveStopArrival(trackId: Long, arrivalTime: Date)

  /**
   * 立ち寄り中をやめる（誤検知の取り消し）。立ち寄り中の表示を消し、その場を離れるまで同じ滞在を立ち寄りにしない。
   * 離れたあと・記録を止めたあとも保存しない。
   */
  suspend fun dismissLiveStop(trackId: Long)
}
