package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import java.text.Collator
import java.util.Locale

/** お気に入りの絞り込み（1軸・3状態）。命名・立ち寄り絞り込みとは独立。 */
enum class TrackFavoriteFilter(val chipLabel: String) {
  ANY("お気に入り"),
  FAVORITE("お気に入り"),
  NOT_FAVORITE("お気に入り以外"),
}

/** 命名状況の絞り込み（1軸・3状態）。 */
enum class TrackNamedFilter(val chipLabel: String) {
  ANY("名前"),
  NAMED("名前あり"),
  UNNAMED("未命名"),
}

/** 立ち寄りの有無の絞り込み（1軸・3状態）。 */
enum class TrackStopFilter(val chipLabel: String) {
  ANY("立ち寄り"),
  HAS_STOPS("立ち寄りあり"),
  NO_STOPS("立ち寄りなし"),
}

/** 一覧の並べ替え軸。既定の向き（降順=大きい/新しいが先）も持つ。 */
enum class TrackSort(val label: String, val defaultDescending: Boolean) {
  DATE("日付順", true),
  STOP_COUNT("立ち寄り数順", true),
  DISTANCE("距離順", true),
  DURATION("時間順", true),
  NAME("名前順", false),
}

data class HistoryState(
  val tracks: List<GpsTrack> = emptyList(),
  val activeTrack: GpsTrack? = null,
  // 絞り込みは3軸独立（お気に入り / 命名 / 立ち寄り）。それぞれ3状態。
  val favoriteFilter: TrackFavoriteFilter = TrackFavoriteFilter.ANY,
  val namedFilter: TrackNamedFilter = TrackNamedFilter.ANY,
  val stopFilter: TrackStopFilter = TrackStopFilter.ANY,
  val sort: TrackSort = TrackSort.DATE,
  val sortDescending: Boolean = TrackSort.DATE.defaultDescending,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
) {
  /** 絞り込みを一切かけていないか（「すべて」チップの選択表示に使う）。 */
  val noFilter: Boolean
    get() = favoriteFilter == TrackFavoriteFilter.ANY &&
      namedFilter == TrackNamedFilter.ANY &&
      stopFilter == TrackStopFilter.ANY

  /** 現在の絞り込み・並べ替えを適用した経路一覧。 */
  val visibleTracks: List<GpsTrack>
    get() {
      val filtered = tracks.filter { track ->
        val favoriteOk = when (favoriteFilter) {
          TrackFavoriteFilter.ANY -> true
          TrackFavoriteFilter.FAVORITE -> track.isFavorite
          TrackFavoriteFilter.NOT_FAVORITE -> !track.isFavorite
        }
        val namedOk = when (namedFilter) {
          TrackNamedFilter.ANY -> true
          TrackNamedFilter.NAMED -> track.hasName
          TrackNamedFilter.UNNAMED -> !track.hasName
        }
        val stopOk = when (stopFilter) {
          TrackStopFilter.ANY -> true
          TrackStopFilter.HAS_STOPS -> track.stopCount > 0
          TrackStopFilter.NO_STOPS -> track.stopCount == 0
        }
        favoriteOk && namedOk && stopOk
      }

      // 各軸は昇順の比較器で定義し、降順ならまとめて反転する。
      // 元の tracks は開始が新しい順なので、同値のときはその並び（＝安定ソート）を保つ。
      val ascending: Comparator<GpsTrack> = when (sort) {
        TrackSort.DATE -> compareBy { it.startTime }
        TrackSort.STOP_COUNT -> compareBy { it.stopCount }
        TrackSort.DISTANCE -> compareBy { it.totalDistanceMeters }
        TrackSort.DURATION -> compareBy { durationMillisOf(it) }
        TrackSort.NAME -> {
          val collator = Collator.getInstance(Locale.JAPANESE)
          // 未命名は名前が無いので昇順で先頭・降順で末尾（null 相当）。
          compareBy(nullsFirst(collator)) { it.name?.takeIf { n -> n.isNotBlank() } }
        }
      }
      return filtered.sortedWith(if (sortDescending) ascending.reversed() else ascending)
    }
}

/** 経路の所要時間（ミリ秒）。終了時刻が無ければ 0（並べ替えの安定用）。 */
private fun durationMillisOf(track: GpsTrack): Long = track.endTime?.let { it.time - track.startTime.time }?.coerceAtLeast(0L) ?: 0L
