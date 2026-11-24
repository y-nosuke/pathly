package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import com.pathly.presentation.common.ListGroup
import java.time.LocalDate
import java.time.ZoneId

/**
 * 経路を、お出掛けの開始の日の年 → 月の 2 段に分ける（統計のカードの「月ごとの距離」と同じ数え方）。
 * 年・月の並びは [newestFirst] なら新しい順。まとまりの中は [tracks]（絞り込み・並べ替えの後）の並びのまま。
 * 経路の無い年・月は作らない。
 */
fun groupTracksByMonth(
  tracks: List<GpsTrack>,
  newestFirst: Boolean,
  zone: ZoneId = ZoneId.systemDefault(),
): List<ListGroup<GpsTrack>> {
  val dateOf = tracks.associate { it.id to startDateOf(it, zone) }
  fun <K : Comparable<K>> List<Map.Entry<K, List<GpsTrack>>>.ordered() = if (newestFirst) sortedByDescending { it.key } else sortedBy { it.key }
  return tracks.groupBy { dateOf.getValue(it.id).year }.entries.toList().ordered().map { (year, yearTracks) ->
    val months = yearTracks.groupBy { dateOf.getValue(it.id).monthValue }.entries.toList().ordered().map { (month, monthTracks) ->
      ListGroup(key = monthGroupKey(year, month), label = "${month}月", count = monthTracks.size, items = monthTracks)
    }
    ListGroup(key = yearGroupKey(year), label = "${year}年", count = yearTracks.size, children = months)
  }
}

/**
 * はじめに開いておくまとまり（いちばん新しい経路の年と月）。押して開閉を変えていなければこれだけが開く。
 * 経路が無ければ空。
 */
fun defaultOpenMonthKeys(tracks: List<GpsTrack>, zone: ZoneId = ZoneId.systemDefault()): Set<String> {
  val latest = tracks.maxByOrNull { it.startTime } ?: return emptySet()
  val date = startDateOf(latest, zone)
  return setOf(yearGroupKey(date.year), monthGroupKey(date.year, date.monthValue))
}

private fun startDateOf(track: GpsTrack, zone: ZoneId): LocalDate = track.startTime.toInstant().atZone(zone).toLocalDate()

private fun yearGroupKey(year: Int): String = "year:$year"

private fun monthGroupKey(year: Int, month: Int): String = "month:$year-${month.toString().padStart(2, '0')}"
