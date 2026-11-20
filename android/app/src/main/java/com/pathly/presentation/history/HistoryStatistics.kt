package com.pathly.presentation.history

import com.pathly.domain.model.GpsTrack
import java.time.YearMonth
import java.time.ZoneId

/**
 * 履歴の統計のカードに出す数字（→ docs/specs/screens.md の「統計のカード」）。数える経路は統計の帯と同じく
 * 絞り込み後の経路。
 */
data class HistoryStatistics(
  /** 数えた経路の数。 */
  val count: Int,
  /** 総距離（m）。 */
  val distanceMeters: Double,
  /** 総時間（分）。 */
  val minutes: Long,
  /** 直近 12 か月（古い月から今月まで）の距離。 */
  val monthly: List<MonthDistance>,
  /** 距離がいちばん長い経路（同じなら新しいほう）。経路が無ければ null。 */
  val longest: GpsTrack?,
) {
  /** 今月の距離（m）。 */
  val thisMonthMeters: Double get() = monthly.lastOrNull()?.meters ?: 0.0

  /** 12 か月の中でいちばん長い月の距離（m）。棒の高さはこれに合わせる。 */
  val maxMonthMeters: Double get() = monthly.maxOfOrNull { it.meters } ?: 0.0

  companion object {
    const val MONTHS = 12

    /** [tracks] を数える。月は [now] の月を今月として、経路の開始の月（[zone] の暦）で分ける。 */
    fun of(tracks: List<GpsTrack>, now: YearMonth = YearMonth.now(), zone: ZoneId = ZoneId.systemDefault()): HistoryStatistics {
      val first = now.minusMonths((MONTHS - 1).toLong())
      val byMonth = tracks.groupBy { YearMonth.from(it.startTime.toInstant().atZone(zone)) }
      val monthly = (0 until MONTHS).map { i ->
        val month = first.plusMonths(i.toLong())
        MonthDistance(month, byMonth[month].orEmpty().sumOf { it.totalDistanceMeters })
      }
      return HistoryStatistics(
        count = tracks.size,
        distanceMeters = tracks.sumOf { it.totalDistanceMeters },
        minutes = tracks.sumOf { durationMinutesOf(it) },
        monthly = monthly,
        longest = tracks.maxWithOrNull(compareBy<GpsTrack> { it.totalDistanceMeters }.thenBy { it.startTime }),
      )
    }
  }
}

/** 1 か月の距離（m）。 */
data class MonthDistance(val month: YearMonth, val meters: Double)

/** 経路の時間（分）。終わっていなければ 0（並べ替えの [durationMillisOf] と同じ数え方）。 */
internal fun durationMinutesOf(track: GpsTrack): Long = durationMillisOf(track) / 60_000
