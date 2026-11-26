package com.pathly.data.repository

import com.pathly.data.local.dao.PlaceMergeDao
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.domain.model.Stop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 場所と立ち寄りの 3 つの窓口（[LiveStopRepositoryImpl]・[StopRepositoryImpl]・[PlaceRepositoryImpl]）が共有する、
 * ロックとメモリ上の状態（→ adr/0049）。
 *
 * 1 回の操作が複数の状態をまたぐ（場所をまとめると、記録中の場所を差し替え、立ち寄りの取り消しの控えを捨てる など）ので、
 * ロックは 1 つだけにする。**状態に触るのは必ず [mutex] の下**。[mutex] は再入できないので、ロックを持ったまま
 * 別の窓口の公開メソッドを呼ばない（共通の処理は [PlaceOperations] を直接呼ぶ）。
 * どの状態もプロセスが終われば消える。
 */
@Singleton
class PlaceStopSession @Inject constructor() {

  /** 記録中の検出・保存・命名と、立ち寄り・場所の編集を直列化する。 */
  val mutex = Mutex()

  /** 記録中の「立ち寄り中」（[com.pathly.domain.repository.LiveStopRepository.currentStop] の元）。 */
  val currentStop = MutableStateFlow<Stop?>(null)

  // 直近の破壊的な変更（削除・統合）の取り消し用スナップショット（1件だけ保持。次の変更で置き換わる）。
  // 画面のスナックバーは常に最新の1件しか出さないため、単一スロットで十分。
  var lastStopChange: StopChangeSnapshot? = null

  // 直近の場所のまとめの取り消し用（1件だけ。立ち寄りの変更とは別に持つ → adr/0037）。
  var lastPlaceMerge: PlaceMergeDao.MergeSnapshot? = null

  // 直近のまとめで、滞在中の place を差し替えた経路（取り消しで元に戻す）。
  var lastMergeLiveTracks: Set<Long> = emptySet()

  // 記録中に確定・追記した立ち寄りの「最後の departure（ミリ秒）」をトラック単位で覚える。
  // これを削除では下げずセッション中は単調増加させることで、ユーザーが記録中に削除した
  // 立ち寄りを、検出（GPSは変わらないので同じ結果になる）が再挿入して復活させないようにする。
  val detectionHighWaterMillis = mutableMapOf<Long, Long>()

  // いま滞在中の立ち寄りに確保した place を、トラック単位で覚える（→ adr/0023）。
  // **ひとつの滞在につき確保は1回**。位置バッチは既定10秒ごとに届くので、毎回確保しにいくと
  // 同じ滞在で place と Places 呼び出しが積み上がる。滞在の同一性は「クラスタの到着時刻」で見る
  // （クラスタが伸びても到着時刻は動かない）。離脱・確定・記録終了で捨てる。
  val liveStopPlaces = mutableMapOf<Long, LiveStopPlace>()

  // 「今ここを立ち寄り」で手で始めた立ち寄り中（トラック単位・メモリだけ）。あるあいだは検出の立ち寄り中より優先し、
  // 同じ時間帯に検出した立ち寄りは保存しない（二重にしない）。
  val manualLiveStops = mutableMapOf<Long, ManualLiveStop>()

  /** 滞在中に覚えていたのが統合された側なら、寄せ先に差し替える（消えた id を使い続けない）。 */
  fun repointLiveStopPlace(fromPlaceId: Long, intoPlaceId: Long) {
    for ((trackId, live) in liveStopPlaces.toList()) {
      if (live.placeId == fromPlaceId) liveStopPlaces[trackId] = LiveStopPlace(live.arrivalMillis, intoPlaceId)
    }
    for ((trackId, live) in manualLiveStops.toList()) {
      if (live.placeId == fromPlaceId) manualLiveStops[trackId] = live.copy(placeId = intoPlaceId)
    }
  }

  /** 滞在中に [placeId] を覚えている経路（場所をまとめる前に、取り消しで戻す先として控える）。 */
  fun liveTracksAt(placeId: Long): Set<Long> = liveStopPlaces.filterValues { it.placeId == placeId }.keys +
    manualLiveStops.filterValues { it.placeId == placeId }.keys

  /** 滞在中に先行確保した place（[arrivalMillis] がその滞在の同一性）。 */
  class LiveStopPlace(val arrivalMillis: Long, val placeId: Long)

  /** 「今ここを立ち寄り」で手で始めた立ち寄り中（→ [LiveStopRepositoryImpl.startLiveStop]）。離れるか記録を止めると確定する。 */
  data class ManualLiveStop(
    val placeId: Long,
    val arrivalTime: Date,
    val anchorLatitude: Double,
    val anchorLongitude: Double,
    /** 最後に押した地点の半径の中にいた時刻（確定したときの出発）。 */
    val lastInsideTime: Date,
    /** この立ち寄り中のために場所を新しく作ったか（選び直したときに片付ける）。 */
    val createdPlace: Boolean = false,
    /** 半径の外に出た時刻（中に戻ると null）。外にいるのが一定時間続いたら確定する。 */
    val outsideSince: Date? = null,
    /** 立ち寄り中に付けたメモ（確定した立ち寄りに入る）。 */
    val note: String? = null,
    /** やめた（誤検知の取り消し）。表示も保存もしないが、離れるまで同じ滞在を検出に立ち寄りにさせない。 */
    val dismissed: Boolean = false,
  )

  /**
   * 削除・統合の取り消しに必要な実体一式。消した行は元IDのまま再挿入し、
   * 書き換えた行（統合で期間・メモが変わった残り1件）は [modifiedStops] で変更前に戻す。
   */
  class StopChangeSnapshot(
    val stops: List<StopEntity>,
    val places: List<PlaceEntity> = emptyList(),
    val resolutions: List<PlaceResolutionEntity> = emptyList(),
    val googlePlaces: List<GooglePlaceEntity> = emptyList(),
    val modifiedStops: List<StopEntity> = emptyList(),
  )
}
