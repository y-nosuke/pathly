package com.pathly.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 画面表示用の日時フォーマット。
 *
 * 以前は `SimpleDateFormat` のインスタンスを object の val で共有していたが、
 * これには2つの問題があった。
 *  - `SimpleDateFormat` は**スレッド安全でない**のに、singleton を各所から呼んでいた
 *  - `Locale.getDefault()` をクラス初期化時に固定するため、実行中にロケールを変えても追随しない
 *
 * [DateTimeFormatter] は不変・スレッド安全なので共有でき、ロケールとタイムゾーンは
 * 整形のたびに現在値を適用する（[withLocale] / [withZone] はパターンを解析し直さない）。
 */
object DateFormatters {

  private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
  private val SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm")
  private val DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
  private val SHORT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")

  /** 時刻（HH:mm:ss）。 */
  fun time(date: Date): String = TIME.render(date)

  /** 時刻（HH:mm）。 */
  fun shortTime(date: Date): String = SHORT_TIME.render(date)

  /** 日付（yyyy年MM月dd日）。 */
  fun date(date: Date): String = DATE.render(date)

  /** 日付（yyyy/MM/dd）。 */
  fun shortDate(date: Date): String = SHORT_DATE.render(date)

  private fun DateTimeFormatter.render(date: Date): String = withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(date.toInstant())
}
